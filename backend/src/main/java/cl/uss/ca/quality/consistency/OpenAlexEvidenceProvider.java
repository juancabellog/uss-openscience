package cl.uss.ca.quality.consistency;

import cl.uss.ca.external.openalex.OpenAlexStatus;
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
public class OpenAlexEvidenceProvider implements ExternalEvidenceProvider {
    public static final String SOURCE = "OPENALEX";

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
                ConsistencyField.ACCESS_TYPE,
                ConsistencyField.AUTHORS
        );
    }

    @Override
    public boolean available() {
        return EvidenceSql.table(repository, "openalex_work");
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
                    SELECT id, openalex_work_id, normalized_doi, display_name, publication_year,
                           primary_source_display_name, primary_source_issn_l, is_oa, oa_status,
                           authorships_truncated, last_sync_at
                    FROM %s
                    WHERE last_outcome = ? AND normalized_doi IN (%s)
                    """.formatted(repository.qualifiedPublic("openalex_work"), EvidenceSql.placeholders(chunk.size()));
            List<Object> params = new ArrayList<>();
            params.add(OpenAlexStatus.FOUND);
            params.addAll(chunk);
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = new SourceSnapshot();
                snapshot.source = SOURCE;
                snapshot.sourceRecordId = EvidenceSql.str(rs, "openalex_work_id");
                if (snapshot.sourceRecordId == null) {
                    snapshot.sourceRecordId = String.valueOf(rs.getLong("id"));
                }
                snapshot.normalizedDoi = EvidenceSql.str(rs, "normalized_doi");
                snapshot.lastSyncAt = EvidenceSql.offset(rs, "last_sync_at");
                snapshot.title = EvidenceSql.str(rs, "display_name");
                snapshot.publicationYear = EvidenceSql.integer(rs, "publication_year");
                snapshot.journalTitle = EvidenceSql.str(rs, "primary_source_display_name");
                String issn = EvidenceSql.str(rs, "primary_source_issn_l");
                if (issn != null) {
                    snapshot.issns.add(issn);
                }
                Boolean oa = EvidenceSql.bool(rs, "is_oa");
                snapshot.accessRaw = EvidenceSql.str(rs, "oa_status");
                snapshot.access = AccessMapper.fromOa(oa, snapshot.accessRaw);
                snapshot.completeAuthorList = !Boolean.TRUE.equals(EvidenceSql.bool(rs, "authorships_truncated"));
                byDoi.put(snapshot.normalizedDoi, snapshot);
                byId.put(rs.getLong("id"), snapshot);
                return snapshot;
            }, params.toArray());
        }
        loadAuthors(byId);
        return byDoi;
    }

    private void loadAuthors(Map<Long, SourceSnapshot> byId) {
        if (byId.isEmpty() || !EvidenceSql.table(repository, "openalex_authorship")) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(ids(byId))) {
            String sql = """
                    SELECT openalex_work_id, source_order, raw_author_name, author_display_name,
                           openalex_author_id, author_orcid, observed_orcid
                    FROM %s WHERE openalex_work_id IN (%s) ORDER BY source_order
                    """.formatted(
                    repository.qualifiedPublic("openalex_authorship"), EvidenceSql.placeholders(chunk.size()));
            repository.select(sql, rs -> {
                SourceSnapshot snapshot = byId.get(rs.getLong("openalex_work_id"));
                if (snapshot == null) {
                    return null;
                }
                String name = EvidenceSql.str(rs, "author_display_name");
                if (name == null) {
                    name = EvidenceSql.str(rs, "raw_author_name");
                }
                if (EvidenceSql.blank(name)) {
                    return null;
                }
                String orcid = EvidenceSql.str(rs, "author_orcid");
                if (orcid == null) {
                    orcid = EvidenceSql.str(rs, "observed_orcid");
                }
                snapshot.authors.add(EvidenceSql.author(
                        name, orcid, null, EvidenceSql.str(rs, "openalex_author_id"),
                        EvidenceSql.integer(rs, "source_order")));
                return name;
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
