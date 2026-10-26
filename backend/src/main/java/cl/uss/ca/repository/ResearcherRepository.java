package cl.uss.ca.repository;

import cl.uss.ca.api.dto.IdentifierDto;
import cl.uss.ca.api.dto.ResearchOutputSummaryDto;
import cl.uss.ca.api.dto.ResearcherDetailDto;
import cl.uss.ca.api.dto.ResearcherSummaryDto;
import cl.uss.ca.exception.DatabaseException;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy JDBC access for the previous researchers directory.
 * Canonical queries live in {@link PersonRepository}.
 */
@ApplicationScoped
@Deprecated
public class ResearcherRepository extends JdbcRepository {

    public long count(ResearcherQuery query) {
        SqlParts parts = buildWhere(query);
        String sql = "SELECT COUNT(*) FROM " + qualified("pure_person") + " p" + parts.where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count researchers", ex);
        }
    }

    public List<ResearcherSummaryDto> list(ResearcherQuery query) {
        SqlParts parts = buildWhere(query);
        boolean identifiers = tableExists("pure_person_identifiers");
        boolean contributors = tableExists("pure_research_output_contributor");
        String sql = """
                SELECT
                    p.id,
                    p.uuid,
                    p.first_name,
                    p.last_name,
                    p.email,
                    p.cargo,
                    p.orcid,
                    p.orcid_authenticated,
                """ + scopusIdsSelect(identifiers) + ",\n"
                + publicationCountSelect(contributors) + "\n" + """
                FROM """ + " " + qualified("pure_person") + """
                 p
                """ + parts.where + """
                ORDER BY p.last_name, p.first_name, p.id
                LIMIT ? OFFSET ?
                """;
        List<Object> params = new ArrayList<>();
        if (identifiers) {
            params.add(SCOPUS_IDENTIFIER_TYPE);
        }
        params.addAll(parts.params);
        params.add(query.size());
        params.add((long) query.page() * query.size());
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                List<ResearcherSummaryDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapSummary(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list researchers", ex);
        }
    }

    public ResearcherDetailDto findById(long id) {
        boolean identifiers = tableExists("pure_person_identifiers");
        boolean contributors = tableExists("pure_research_output_contributor");
        String sql = """
                SELECT
                    p.id,
                    p.uuid,
                    p.first_name,
                    p.last_name,
                    p.email,
                    p.cargo,
                    p.orcid,
                    p.orcid_authenticated,
                    p.gender,
                    p.`primaryId` AS primary_id,
                """ + scopusIdsSelect(identifiers) + ",\n"
                + publicationCountSelect(contributors) + "\n" + """
                FROM """ + " " + qualified("pure_person") + """
                 p
                WHERE p.id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (identifiers) {
                statement.setString(index++, SCOPUS_IDENTIFIER_TYPE);
            }
            statement.setLong(index, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ResearcherDetailDto detail = new ResearcherDetailDto();
                copySummary(mapSummary(rs), detail);
                detail.gender = nullableString(rs, "gender");
                detail.primaryId = nullableString(rs, "primary_id");
                detail.identities = findIdentities(connection, id);
                detail.publications = findPublications(connection, detail.uuid);
                return detail;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load researcher", ex);
        }
    }

    private List<IdentifierDto> findIdentities(Connection connection, long personId) throws SQLException {
        if (!tableExists("pure_person_identifiers")) {
            return List.of();
        }
        String sql = """
                SELECT identifier_typeDiscriminator, identifier_type, identifier_value
                FROM """ + " " + qualified("pure_person_identifiers") + """
                WHERE person_id = ?
                ORDER BY identifier_type, identifier_value
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                List<IdentifierDto> items = new ArrayList<>();
                while (rs.next()) {
                    IdentifierDto dto = new IdentifierDto();
                    dto.typeDiscriminator = nullableString(rs, "identifier_typeDiscriminator");
                    dto.type = nullableString(rs, "identifier_type");
                    dto.value = nullableString(rs, "identifier_value");
                    items.add(dto);
                }
                return items;
            }
        }
    }

    private List<ResearchOutputSummaryDto> findPublications(Connection connection, String personUuid)
            throws SQLException {
        if (personUuid == null || !tableExists("pure_research_output")) {
            return List.of();
        }
        String sql = """
                SELECT
                    o.id,
                    o.uuid,
                    o.title,
                    o.submission_year,
                    o.type_en,
                    o.type_es,
                    o.total_number_of_contributors,
                    (
                        SELECT i.value
                        FROM """ + " " + qualified("pure_research_output_identifier") + """
                         i
                        WHERE i.research_output_id = o.id
                          AND (
                              LOWER(i.id_source) LIKE '%doi%'
                              OR LOWER(i.type_discriminator) LIKE '%doi%'
                          )
                        ORDER BY i.id
                        LIMIT 1
                    ) AS doi
                FROM """ + " " + qualified("pure_research_output") + """
                 o
                JOIN """ + " " + qualified("pure_research_output_contributor") + """
                 c
                    ON c.research_output_id = o.id
                WHERE c.person_uuid = ?
                GROUP BY o.id, o.uuid, o.title, o.submission_year, o.type_en, o.type_es, o.total_number_of_contributors
                ORDER BY o.submission_year DESC, o.id DESC
                LIMIT 50
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, personUuid);
            try (ResultSet rs = statement.executeQuery()) {
                List<ResearchOutputSummaryDto> items = new ArrayList<>();
                while (rs.next()) {
                    ResearchOutputSummaryDto dto = new ResearchOutputSummaryDto();
                    dto.id = rs.getLong("id");
                    dto.uuid = nullableString(rs, "uuid");
                    dto.title = nullableString(rs, "title");
                    dto.year = nullableInt(rs, "submission_year");
                    dto.type = firstNonBlank(nullableString(rs, "type_en"), nullableString(rs, "type_es"));
                    dto.doi = nullableString(rs, "doi");
                    dto.contributorCount = nullableInt(rs, "total_number_of_contributors");
                    items.add(dto);
                }
                return items;
            }
        } catch (SQLException ex) {
            return List.of();
        }
    }

    private SqlParts buildWhere(ResearcherQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (query.search() != null && !query.search().isBlank()) {
            String like = likeContains(query.search().trim());
            where.append("""
                     AND (
                        p.first_name LIKE ? ESCAPE '!'
                        OR p.last_name LIKE ? ESCAPE '!'
                        OR p.email LIKE ? ESCAPE '!'
                        OR p.orcid LIKE ? ESCAPE '!'
                     )
                    """);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (query.position() != null && !query.position().isBlank()) {
            where.append(" AND p.cargo LIKE ? ESCAPE '!'");
            params.add(likeContains(query.position().trim()));
        }
        if (Boolean.TRUE.equals(query.hasOrcid())) {
            where.append(" AND p.orcid IS NOT NULL AND TRIM(p.orcid) <> ''");
        } else if (Boolean.FALSE.equals(query.hasOrcid())) {
            where.append(" AND (p.orcid IS NULL OR TRIM(p.orcid) = '')");
        }
        if (query.hasScopus() != null) {
            if (!tableExists("pure_person_identifiers")) {
                where.append(Boolean.TRUE.equals(query.hasScopus()) ? " AND 1=0" : "");
            } else if (Boolean.TRUE.equals(query.hasScopus())) {
                where.append(scopusPresenceExistsSql(false));
                params.add(SCOPUS_IDENTIFIER_TYPE);
            } else {
                where.append(scopusPresenceExistsSql(true));
                params.add(SCOPUS_IDENTIFIER_TYPE);
            }
        }
        return new SqlParts(where.toString(), params);
    }

    private String scopusPresenceExistsSql(boolean negated) {
        return (negated ? " AND NOT EXISTS (" : " AND EXISTS (")
                + """
                SELECT 1
                FROM %s i
                WHERE i.person_id = p.id
                  AND i.identifier_type = ?
                  AND i.identifier_value IS NOT NULL
                  AND TRIM(i.identifier_value) <> ''
                )""".formatted(qualified("pure_person_identifiers"));
    }

    private static String scopusIdsSelect(boolean identifiersAvailable) {
        if (!identifiersAvailable) {
            return "NULL AS scopus_ids";
        }
        return """
                    (
                        SELECT GROUP_CONCAT(DISTINCT TRIM(i.identifier_value) ORDER BY i.identifier_value SEPARATOR ',')
                        FROM """ + " " + qualified("pure_person_identifiers") + """
                         i
                        WHERE i.person_id = p.id
                          AND i.identifier_type = ?
                          AND i.identifier_value IS NOT NULL
                          AND TRIM(i.identifier_value) <> ''
                    ) AS scopus_ids""";
    }

    private static String publicationCountSelect(boolean contributorsAvailable) {
        if (!contributorsAvailable) {
            return "NULL AS publication_count";
        }
        return """
                    (
                        SELECT COUNT(DISTINCT c.research_output_id)
                        FROM """ + " " + qualified("pure_research_output_contributor") + """
                         c
                        WHERE c.person_uuid = p.uuid
                    ) AS publication_count""";
    }

    private ResearcherSummaryDto mapSummary(ResultSet rs) throws SQLException {
        ResearcherSummaryDto dto = new ResearcherSummaryDto();
        dto.id = rs.getLong("id");
        dto.uuid = nullableString(rs, "uuid");
        dto.firstName = nullableString(rs, "first_name");
        dto.lastName = nullableString(rs, "last_name");
        dto.fullName = fullName(dto.firstName, dto.lastName);
        dto.email = nullableString(rs, "email");
        dto.position = nullableString(rs, "cargo");
        dto.faculty = null;
        dto.orcid = nullableString(rs, "orcid");
        dto.orcidAuthenticated = nullableBoolean(rs, "orcid_authenticated");
        dto.scopusIds = splitCsv(rs.getString("scopus_ids"));
        dto.publicationCount = nullableInt(rs, "publication_count");
        dto.dataQualityStatus = null;
        return dto;
    }

    private static void copySummary(ResearcherSummaryDto source, ResearcherDetailDto target) {
        target.id = source.id;
        target.uuid = source.uuid;
        target.firstName = source.firstName;
        target.lastName = source.lastName;
        target.fullName = source.fullName;
        target.email = source.email;
        target.position = source.position;
        target.faculty = source.faculty;
        target.orcid = source.orcid;
        target.orcidAuthenticated = source.orcidAuthenticated;
        target.scopusIds = source.scopusIds;
        target.publicationCount = source.publicationCount;
        target.dataQualityStatus = source.dataQualityStatus;
    }

    private static String fullName(String first, String last) {
        if (first == null && last == null) {
            return null;
        }
        if (first == null) {
            return last;
        }
        if (last == null) {
            return first;
        }
        return first + " " + last;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    public record ResearcherQuery(
            String search,
            String position,
            Boolean hasOrcid,
            Boolean hasScopus,
            int page,
            int size
    ) {
    }

    private record SqlParts(String where, List<Object> params) {
    }
}
