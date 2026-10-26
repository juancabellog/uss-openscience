package cl.uss.ca.quality.consistency;

import cl.uss.ca.domain.AuthorNameMatcher;
import cl.uss.ca.external.crossref.CrossrefStatus;
import cl.uss.ca.repository.DataQualityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CrossrefEvidenceProvider implements ExternalEvidenceProvider {
    public static final String SOURCE = "CROSSREF";

    @Inject
    DataQualityRepository repository;

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public Set<ConsistencyField> capabilities() {
        return EnumSet.of(
                ConsistencyField.TITLE,
                ConsistencyField.PUBLICATION_YEAR,
                ConsistencyField.JOURNAL_TITLE,
                ConsistencyField.ISSN,
                ConsistencyField.VOLUME,
                ConsistencyField.ISSUE,
                ConsistencyField.PAGES,
                ConsistencyField.PUBLISHER,
                ConsistencyField.LICENSE,
                ConsistencyField.FUNDING_AWARD,
                ConsistencyField.AUTHORS
        );
    }

    @Override
    public boolean available() {
        return EvidenceSql.table(repository, "crossref_work");
    }

    @Override
    public Map<String, SourceSnapshot> loadByDois(List<String> normalizedDois) {
        Map<String, SourceSnapshot> byDoi = new LinkedHashMap<>();
        if (!available()) {
            return byDoi;
        }
        Map<Long, SourceSnapshot> byId = new LinkedHashMap<>();
        for (List<String> chunk : EvidenceSql.chunks(normalizedDois)) {
            String sql = """
                    SELECT id, normalized_doi, title, container_title, publisher, volume, issue, page_range,
                           article_number, published_date, published_print_date, published_online_date, issued_date,
                           last_crossref_sync_at
                    FROM %s
                    WHERE status = ? AND normalized_doi IN (%s)
                    """.formatted(repository.qualifiedPublic("crossref_work"), EvidenceSql.placeholders(chunk.size()));
            List<Object> params = new ArrayList<>();
            params.add(CrossrefStatus.FOUND);
            params.addAll(chunk);
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = new SourceSnapshot();
                snapshot.source = SOURCE;
                snapshot.sourceRecordId = String.valueOf(rs.getLong("id"));
                snapshot.normalizedDoi = EvidenceSql.str(rs, "normalized_doi");
                snapshot.lastSyncAt = EvidenceSql.offset(rs, "last_crossref_sync_at");
                snapshot.title = EvidenceSql.str(rs, "title");
                snapshot.publicationYear = PublishedYearExtractor.from(
                        EvidenceSql.str(rs, "published_date"),
                        EvidenceSql.str(rs, "published_print_date"),
                        EvidenceSql.str(rs, "published_online_date"),
                        EvidenceSql.str(rs, "issued_date")
                );
                snapshot.journalTitle = EvidenceSql.str(rs, "container_title");
                snapshot.publisher = EvidenceSql.str(rs, "publisher");
                snapshot.volume = EvidenceSql.str(rs, "volume");
                snapshot.issue = EvidenceSql.str(rs, "issue");
                snapshot.pages = EvidenceSql.str(rs, "page_range");
                snapshot.articleNumber = EvidenceSql.str(rs, "article_number");
                snapshot.locatorKind = BibliographicComparators.locatorKind(snapshot.pages, snapshot.articleNumber);
                snapshot.completeAuthorList = Boolean.TRUE;
                byDoi.put(snapshot.normalizedDoi, snapshot);
                byId.put(rs.getLong("id"), snapshot);
                return snapshot;
            }, params.toArray());
        }
        loadIssns(byId);
        loadAuthors(byId);
        loadAwards(byId);
        loadLicenses(byId);
        return byDoi;
    }

    private void loadIssns(Map<Long, SourceSnapshot> byId) {
        if (byId.isEmpty() || !EvidenceSql.table(repository, "crossref_issn")) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(ids(byId))) {
            String sql = "SELECT crossref_work_id, issn FROM " + repository.qualifiedPublic("crossref_issn")
                    + " WHERE crossref_work_id IN (" + EvidenceSql.placeholders(chunk.size()) + ")";
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = byId.get(rs.getLong("crossref_work_id"));
                String issn = EvidenceSql.str(rs, "issn");
                if (snapshot != null && issn != null) {
                    snapshot.issns.add(issn);
                }
                return issn;
            }, chunk.toArray());
        }
    }

    private void loadAuthors(Map<Long, SourceSnapshot> byId) {
        if (byId.isEmpty() || !EvidenceSql.table(repository, "crossref_author")) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(ids(byId))) {
            String sql = """
                    SELECT crossref_work_id, sequence_no, given_name, family_name, full_name, orcid
                    FROM %s WHERE crossref_work_id IN (%s) ORDER BY sequence_no
                    """.formatted(repository.qualifiedPublic("crossref_author"), EvidenceSql.placeholders(chunk.size()));
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = byId.get(rs.getLong("crossref_work_id"));
                if (snapshot == null) {
                    return null;
                }
                String full = EvidenceSql.str(rs, "full_name");
                if (full == null) {
                    full = AuthorNameMatcher.fullName(EvidenceSql.str(rs, "given_name"), EvidenceSql.str(rs, "family_name"));
                }
                if (!EvidenceSql.blank(full)) {
                    snapshot.authors.add(EvidenceSql.author(
                            full, EvidenceSql.str(rs, "orcid"), null, null, EvidenceSql.integer(rs, "sequence_no")));
                }
                return full;
            }, chunk.toArray());
        }
    }

    private void loadAwards(Map<Long, SourceSnapshot> byId) {
        if (byId.isEmpty() || !EvidenceSql.table(repository, "crossref_funder")) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(ids(byId))) {
            String sql = "SELECT crossref_work_id, award_number FROM " + repository.qualifiedPublic("crossref_funder")
                    + " WHERE crossref_work_id IN (" + EvidenceSql.placeholders(chunk.size()) + ")";
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = byId.get(rs.getLong("crossref_work_id"));
                String award = EvidenceSql.str(rs, "award_number");
                if (snapshot != null && award != null) {
                    snapshot.awardNumbers.add(award);
                }
                return award;
            }, chunk.toArray());
        }
    }

    private void loadLicenses(Map<Long, SourceSnapshot> byId) {
        if (byId.isEmpty() || !EvidenceSql.table(repository, "crossref_license")) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(ids(byId))) {
            String sql = "SELECT crossref_work_id, license_url FROM " + repository.qualifiedPublic("crossref_license")
                    + " WHERE crossref_work_id IN (" + EvidenceSql.placeholders(chunk.size()) + ")";
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = byId.get(rs.getLong("crossref_work_id"));
                String url = EvidenceSql.str(rs, "license_url");
                if (snapshot != null && url != null && snapshot.licenseRaw == null) {
                    snapshot.licenseRaw = url;
                    snapshot.licenseNormalized = LicenseNormalizer.normalize(url);
                }
                return url;
            }, chunk.toArray());
        }
    }

    private static List<String> ids(Map<Long, SourceSnapshot> byId) {
        List<String> ids = new ArrayList<>();
        for (Long id : byId.keySet()) {
            ids.add(String.valueOf(id));
        }
        return ids;
    }
}
