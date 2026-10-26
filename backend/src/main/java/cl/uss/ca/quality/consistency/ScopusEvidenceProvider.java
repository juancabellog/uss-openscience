package cl.uss.ca.quality.consistency;

import cl.uss.ca.domain.DoiNormalizer;
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
public class ScopusEvidenceProvider implements ExternalEvidenceProvider {
    public static final String SOURCE = "SCOPUS";

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
                ConsistencyField.AUTHORS
        );
    }

    @Override
    public boolean available() {
        return EvidenceSql.table(repository, "scopus_publication");
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
                    SELECT id, eid, doi, doi_normalized, title, publication_year, source_title, issn,
                           volume, issue, article_number, page_start, page_end, updated_at
                    FROM %s
                    WHERE doi_normalized IN (%s)
                    """.formatted(
                    repository.qualifiedPublic("scopus_publication"), EvidenceSql.placeholders(chunk.size()));
            repository.select(sql, rs -> {
                String doi = DoiNormalizer.normalize(EvidenceSql.str(rs, "doi_normalized"));
                if (doi == null) {
                    doi = DoiNormalizer.normalize(EvidenceSql.str(rs, "doi"));
                }
                if (doi == null) {
                    return null;
                }
                SourceSnapshot snapshot = new SourceSnapshot();
                snapshot.source = SOURCE;
                snapshot.sourceRecordId = EvidenceSql.str(rs, "eid");
                if (snapshot.sourceRecordId == null) {
                    snapshot.sourceRecordId = String.valueOf(rs.getLong("id"));
                }
                snapshot.normalizedDoi = doi;
                snapshot.lastSyncAt = EvidenceSql.offset(rs, "updated_at");
                snapshot.title = EvidenceSql.str(rs, "title");
                snapshot.publicationYear = EvidenceSql.integer(rs, "publication_year");
                snapshot.journalTitle = EvidenceSql.str(rs, "source_title");
                String issn = EvidenceSql.str(rs, "issn");
                if (issn != null) {
                    snapshot.issns.add(issn);
                }
                snapshot.volume = EvidenceSql.str(rs, "volume");
                snapshot.issue = EvidenceSql.str(rs, "issue");
                snapshot.articleNumber = EvidenceSql.str(rs, "article_number");
                snapshot.pages = pages(EvidenceSql.str(rs, "page_start"), EvidenceSql.str(rs, "page_end"));
                snapshot.locatorKind = BibliographicComparators.locatorKind(snapshot.pages, snapshot.articleNumber);
                snapshot.completeAuthorList = Boolean.TRUE;
                byDoi.putIfAbsent(doi, snapshot);
                byId.put(rs.getLong("id"), snapshot);
                return snapshot;
            }, chunk.toArray());
        }
        loadAuthors(byId);
        return byDoi;
    }

    private void loadAuthors(Map<Long, SourceSnapshot> byId) {
        if (byId.isEmpty() || !EvidenceSql.table(repository, "scopus_publication_author")) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(ids(byId))) {
            String sql = """
                    SELECT scopus_publication_id, author_position, auth_id, author_name_full, author_name_short
                    FROM %s
                    WHERE scopus_publication_id IN (%s)
                    ORDER BY author_position
                    """.formatted(
                    repository.qualifiedPublic("scopus_publication_author"),
                    EvidenceSql.placeholders(chunk.size())
            );
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = byId.get(rs.getLong("scopus_publication_id"));
                if (snapshot == null) {
                    return null;
                }
                String name = EvidenceSql.str(rs, "author_name_full");
                if (name == null) {
                    name = EvidenceSql.str(rs, "author_name_short");
                }
                if (EvidenceSql.blank(name)) {
                    return null;
                }
                snapshot.authors.add(EvidenceSql.author(
                        name, null, EvidenceSql.str(rs, "auth_id"), null,
                        EvidenceSql.integer(rs, "author_position")));
                return name;
            }, chunk.toArray());
        }
    }

    private static String pages(String start, String end) {
        if (start == null && end == null) {
            return null;
        }
        if (start != null && end != null) {
            return start + "-" + end;
        }
        return start != null ? start : end;
    }

    private static List<String> ids(Map<Long, SourceSnapshot> byId) {
        List<String> ids = new ArrayList<>();
        for (Long id : byId.keySet()) {
            ids.add(String.valueOf(id));
        }
        return ids;
    }
}
