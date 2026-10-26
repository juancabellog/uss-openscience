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
public class OrcidEvidenceProvider implements ExternalEvidenceProvider {
    public static final String SOURCE = "ORCID";

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
                ConsistencyField.AUTHORS
        );
    }

    @Override
    public boolean available() {
        return EvidenceSql.table(repository, "orcid_work_group")
                && EvidenceSql.table(repository, "orcid_work_group_external_id");
    }

    @Override
    public Map<String, SourceSnapshot> loadByDois(List<String> normalizedDois) {
        Map<String, SourceSnapshot> byDoi = new LinkedHashMap<>();
        if (!available()) {
            return byDoi;
        }
        Map<Long, String> groupToDoi = new LinkedHashMap<>();
        for (List<String> chunk : EvidenceSql.chunks(normalizedDois)) {
            String sql = """
                    SELECT g.id, g.title, g.journal_title, g.publication_year, g.last_seen_in_orcid_at,
                           e.normalized_value, e.identifier_value
                    FROM %s g
                    JOIN %s e ON e.work_group_id = g.id
                    WHERE g.active_in_orcid = 1
                      AND LOWER(e.identifier_type) = 'doi'
                      AND (
                            e.normalized_value IN (%s)
                         OR e.identifier_value IN (%s)
                      )
                    """.formatted(
                    repository.qualifiedPublic("orcid_work_group"),
                    repository.qualifiedPublic("orcid_work_group_external_id"),
                    EvidenceSql.placeholders(chunk.size()),
                    EvidenceSql.placeholders(chunk.size())
            );
            List<Object> params = new ArrayList<>();
            params.addAll(chunk);
            params.addAll(chunk);
            repository.select(sql, rs -> {
                String doi = DoiNormalizer.normalize(EvidenceSql.str(rs, "normalized_value"));
                if (doi == null) {
                    doi = DoiNormalizer.normalize(EvidenceSql.str(rs, "identifier_value"));
                }
                if (doi == null) {
                    return null;
                }
                SourceSnapshot snapshot = byDoi.get(doi);
                if (snapshot == null) {
                    snapshot = new SourceSnapshot();
                    snapshot.source = SOURCE;
                    snapshot.normalizedDoi = doi;
                    byDoi.put(doi, snapshot);
                }
                if (snapshot.sourceRecordId == null) {
                    snapshot.sourceRecordId = String.valueOf(rs.getLong("id"));
                }
                if (snapshot.title == null) {
                    snapshot.title = EvidenceSql.str(rs, "title");
                }
                if (snapshot.journalTitle == null) {
                    snapshot.journalTitle = EvidenceSql.str(rs, "journal_title");
                }
                if (snapshot.publicationYear == null) {
                    snapshot.publicationYear = EvidenceSql.integer(rs, "publication_year");
                }
                if (snapshot.lastSyncAt == null) {
                    snapshot.lastSyncAt = EvidenceSql.offset(rs, "last_seen_in_orcid_at");
                }
                groupToDoi.put(rs.getLong("id"), doi);
                return doi;
            }, params.toArray());
        }
        loadIssns(groupToDoi, byDoi);
        loadAuthors(groupToDoi, byDoi);
        return byDoi;
    }

    private void loadIssns(Map<Long, String> groupToDoi, Map<String, SourceSnapshot> byDoi) {
        if (groupToDoi.isEmpty()) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(ids(groupToDoi))) {
            String sql = """
                    SELECT work_group_id, identifier_value, normalized_value
                    FROM %s
                    WHERE work_group_id IN (%s)
                      AND LOWER(identifier_type) LIKE '%%issn%%'
                    """.formatted(
                    repository.qualifiedPublic("orcid_work_group_external_id"),
                    EvidenceSql.placeholders(chunk.size())
            );
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = byDoi.get(groupToDoi.get(rs.getLong("work_group_id")));
                String issn = EvidenceSql.str(rs, "normalized_value");
                if (issn == null) {
                    issn = EvidenceSql.str(rs, "identifier_value");
                }
                if (snapshot != null && issn != null && !snapshot.issns.contains(issn)) {
                    snapshot.issns.add(issn);
                }
                return issn;
            }, chunk.toArray());
        }
    }

    private void loadAuthors(Map<Long, String> groupToDoi, Map<String, SourceSnapshot> byDoi) {
        if (groupToDoi.isEmpty()
                || !EvidenceSql.table(repository, "orcid_work")
                || !EvidenceSql.table(repository, "orcid_work_contributor")) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(ids(groupToDoi))) {
            String sql = """
                    SELECT w.work_group_id, w.detail_loaded, c.position, c.credit_name, c.contributor_orcid
                    FROM %s w
                    JOIN %s c ON c.orcid_work_id = w.id
                    WHERE w.work_group_id IN (%s)
                      AND w.detail_loaded = 1
                      AND w.active_in_orcid = 1
                    ORDER BY c.position
                    """.formatted(
                    repository.qualifiedPublic("orcid_work"),
                    repository.qualifiedPublic("orcid_work_contributor"),
                    EvidenceSql.placeholders(chunk.size())
            );
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = byDoi.get(groupToDoi.get(rs.getLong("work_group_id")));
                if (snapshot == null) {
                    return null;
                }
                snapshot.completeAuthorList = Boolean.TRUE;
                String name = EvidenceSql.str(rs, "credit_name");
                if (EvidenceSql.blank(name)) {
                    return null;
                }
                snapshot.authors.add(EvidenceSql.author(
                        name, EvidenceSql.str(rs, "contributor_orcid"), null, null,
                        EvidenceSql.integer(rs, "position")));
                return name;
            }, chunk.toArray());
        }
    }

    private static List<String> ids(Map<Long, String> groupToDoi) {
        List<String> ids = new ArrayList<>();
        for (Long id : groupToDoi.keySet()) {
            ids.add(String.valueOf(id));
        }
        return ids;
    }
}
