package cl.uss.ca.quality.consistency;

import cl.uss.ca.domain.AuthorNameMatcher;
import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.quality.DataQualityIdentifiers;
import cl.uss.ca.repository.DataQualityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ConsistencyEvidenceRepository {
    @Inject
    DataQualityRepository repository;

    public List<PureSnapshot> loadPure(Long researchOutputId) {
        if (!EvidenceSql.table(repository, "pure_research_output")) {
            return List.of();
        }
        String idClause = researchOutputId == null ? "" : " AND o.id = ?";
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title, o.volume, o.journal_number, o.pages
                FROM %s o
                WHERE %s
                %s
                """.formatted(
                repository.qualifiedPublic("pure_research_output"),
                repository.activePredicatePublic("pure_research_output", "o"),
                idClause
        );
        Object[] params = researchOutputId == null ? new Object[0] : new Object[]{researchOutputId};
        Map<Long, PureSnapshot> byId = new LinkedHashMap<>();
        repository.select(sql, rs -> {
            PureSnapshot snapshot = new PureSnapshot();
            snapshot.id = rs.getLong("id");
            snapshot.uuid = EvidenceSql.str(rs, "uuid");
            snapshot.pureId = nullable(rs.getLong("pure_id"), rs.wasNull());
            snapshot.title = EvidenceSql.str(rs, "title");
            snapshot.volume = EvidenceSql.str(rs, "volume");
            snapshot.issue = EvidenceSql.str(rs, "journal_number");
            snapshot.pages = EvidenceSql.str(rs, "pages");
            byId.put(snapshot.id, snapshot);
            return snapshot;
        }, params);
        if (byId.isEmpty()) {
            return List.of();
        }
        loadDois(byId);
        byId.values().removeIf(item -> item.normalizedDois.isEmpty());
        if (byId.isEmpty()) {
            return List.of();
        }
        loadYears(byId);
        loadJournals(byId);
        loadAccess(byId);
        loadAuthors(byId);
        return new ArrayList<>(byId.values());
    }

    private void loadDois(Map<Long, PureSnapshot> byId) {
        if (!EvidenceSql.table(repository, "pure_research_output_electronic_version")) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(ids(byId))) {
            String sql = """
                    SELECT research_output_id, doi
                    FROM %s
                    WHERE research_output_id IN (%s)
                      AND doi IS NOT NULL AND TRIM(doi) <> ''
                    """.formatted(
                    repository.qualifiedPublic("pure_research_output_electronic_version"),
                    EvidenceSql.placeholders(chunk.size())
            );
            repository.select(sql, rs -> {
                PureSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                String doi = EvidenceSql.str(rs, "doi");
                if (snapshot == null || !DataQualityIdentifiers.isValidDoi(doi)) {
                    return null;
                }
                String normalized = DoiNormalizer.normalize(doi);
                if (normalized != null && !snapshot.normalizedDois.contains(normalized)) {
                    snapshot.normalizedDois.add(normalized);
                }
                return normalized;
            }, chunk.toArray());
        }
    }

    private void loadYears(Map<Long, PureSnapshot> byId) {
        if (!EvidenceSql.table(repository, "pure_research_output_status")) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(ids(byId))) {
            String sql = """
                    SELECT research_output_id, publication_year, current_status
                    FROM %s
                    WHERE research_output_id IN (%s)
                      AND publication_year IS NOT NULL
                    ORDER BY current_status DESC, publication_year DESC, id
                    """.formatted(
                    repository.qualifiedPublic("pure_research_output_status"),
                    EvidenceSql.placeholders(chunk.size())
            );
            repository.select(sql, rs -> {
                PureSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                if (snapshot != null && snapshot.publicationYear == null) {
                    snapshot.publicationYear = EvidenceSql.integer(rs, "publication_year");
                }
                return snapshot;
            }, chunk.toArray());
        }
    }

    private void loadJournals(Map<Long, PureSnapshot> byId) {
        if (!EvidenceSql.table(repository, "pure_research_output_journal")
                || !EvidenceSql.table(repository, "pure_journal")) {
            return;
        }
        boolean titles = EvidenceSql.table(repository, "pure_journal_title");
        boolean issns = EvidenceSql.table(repository, "pure_journal_issn");
        boolean publisher = repository.columnExistsPublic("pure_journal", "publisher_system_name");
        for (List<String> chunk : EvidenceSql.chunks(ids(byId))) {
            String titleSelect = titles
                    ? "(SELECT t.title FROM " + repository.qualifiedPublic("pure_journal_title")
                    + " t WHERE t.journal_id = j.id AND t.title IS NOT NULL AND TRIM(t.title) <> '' ORDER BY t.id LIMIT 1)"
                    : "j.title";
            String pubSelect = publisher ? "j.publisher_system_name" : "NULL";
            String sql = """
                    SELECT oj.research_output_id, %s AS journal_title, %s AS publisher
                    FROM %s oj
                    JOIN %s j ON j.id = oj.journal_id
                    WHERE oj.research_output_id IN (%s)
                    """.formatted(
                    titleSelect,
                    pubSelect,
                    repository.qualifiedPublic("pure_research_output_journal"),
                    repository.qualifiedPublic("pure_journal"),
                    EvidenceSql.placeholders(chunk.size())
            );
            repository.select(sql, rs -> {
                PureSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                if (snapshot == null) {
                    return null;
                }
                if (snapshot.journalTitle == null) {
                    snapshot.journalTitle = EvidenceSql.str(rs, "journal_title");
                }
                if (snapshot.publisher == null) {
                    snapshot.publisher = EvidenceSql.str(rs, "publisher");
                }
                return snapshot;
            }, chunk.toArray());
            if (issns) {
                String issnSql = """
                        SELECT oj.research_output_id, i.issn
                        FROM %s oj
                        JOIN %s i ON i.journal_id = oj.journal_id
                        WHERE oj.research_output_id IN (%s)
                        """.formatted(
                        repository.qualifiedPublic("pure_research_output_journal"),
                        repository.qualifiedPublic("pure_journal_issn"),
                        EvidenceSql.placeholders(chunk.size())
                );
                repository.select(issnSql, rs -> {
                    PureSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                    String issn = EvidenceSql.str(rs, "issn");
                    if (snapshot != null && issn != null && !snapshot.issns.contains(issn)) {
                        snapshot.issns.add(issn);
                    }
                    return issn;
                }, chunk.toArray());
            }
        }
    }

    private void loadAccess(Map<Long, PureSnapshot> byId) {
        if (!EvidenceSql.table(repository, "pure_research_output_electronic_version")) {
            return;
        }
        for (List<String> chunk : EvidenceSql.chunks(ids(byId))) {
            String sql = """
                    SELECT research_output_id, access_type_es, access_type_en, access_type_uri
                    FROM %s
                    WHERE research_output_id IN (%s)
                    """.formatted(
                    repository.qualifiedPublic("pure_research_output_electronic_version"),
                    EvidenceSql.placeholders(chunk.size())
            );
            repository.select(sql, rs -> {
                PureSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                if (snapshot == null || (snapshot.access != null && snapshot.access != AccessSemantic.UNKNOWN)) {
                    return null;
                }
                String raw = first(
                        EvidenceSql.str(rs, "access_type_es"),
                        EvidenceSql.str(rs, "access_type_en"),
                        EvidenceSql.str(rs, "access_type_uri")
                );
                if (raw == null) {
                    return null;
                }
                snapshot.accessRaw = raw;
                snapshot.access = AccessMapper.fromPure(raw);
                return raw;
            }, chunk.toArray());
        }
    }

    private void loadAuthors(Map<Long, PureSnapshot> byId) {
        if (!EvidenceSql.table(repository, "pure_research_output_contributor")) {
            return;
        }
        boolean person = EvidenceSql.table(repository, "pure_person");
        for (List<String> chunk : EvidenceSql.chunks(ids(byId))) {
            String join = person
                    ? " LEFT JOIN " + repository.qualifiedPublic("pure_person") + " p ON p.uuid = c.person_uuid "
                    : "";
            String orcid = person ? "p.orcid" : "NULL";
            String sql = """
                    SELECT c.research_output_id, c.first_name, c.last_name, c.person_uuid, %s AS orcid
                    FROM %s c
                    %s
                    WHERE c.research_output_id IN (%s)
                      AND (
                        LOWER(COALESCE(c.type_discriminator,'')) LIKE '%%author%%'
                        OR LOWER(COALESCE(c.role_en,'')) LIKE '%%author%%'
                        OR LOWER(COALESCE(c.role_es,'')) LIKE '%%autor%%'
                      )
                    ORDER BY c.id
                    """.formatted(
                    orcid,
                    repository.qualifiedPublic("pure_research_output_contributor"),
                    join,
                    EvidenceSql.placeholders(chunk.size())
            );
            repository.select(sql, rs -> {
                PureSnapshot snapshot = byId.get(rs.getLong("research_output_id"));
                if (snapshot == null) {
                    return null;
                }
                String name = AuthorNameMatcher.fullName(
                        EvidenceSql.str(rs, "first_name"), EvidenceSql.str(rs, "last_name"));
                if (EvidenceSql.blank(name)) {
                    return null;
                }
                snapshot.authors.add(EvidenceSql.author(
                        name, EvidenceSql.str(rs, "orcid"), null, null, snapshot.authors.size() + 1));
                return name;
            }, chunk.toArray());
        }
    }

    private static Long nullable(long value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private static String first(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static List<String> ids(Map<Long, PureSnapshot> byId) {
        List<String> ids = new ArrayList<>();
        for (Long id : byId.keySet()) {
            ids.add(String.valueOf(id));
        }
        return ids;
    }
}
