package cl.uss.ca.repository;

import cl.uss.ca.api.dto.IdentifierDto;
import cl.uss.ca.api.dto.PersonDetailDto;
import cl.uss.ca.api.dto.PurePersonIdentity;
import cl.uss.ca.api.dto.PersonFiltersDto;
import cl.uss.ca.api.dto.PersonScopusIdentifierDto;
import cl.uss.ca.api.dto.PersonScopusIdentifiersDto;
import cl.uss.ca.api.dto.PersonStatsDto;
import cl.uss.ca.api.dto.PersonSummaryDto;
import cl.uss.ca.api.dto.PositionCountDto;
import cl.uss.ca.api.dto.ScopusCountDto;
import cl.uss.ca.api.dto.ScopusStatsDto;
import cl.uss.ca.api.dto.ResearchOutputSummaryDto;
import cl.uss.ca.domain.OrcidAdoptionPerson;
import cl.uss.ca.domain.ResearcherPositions;
import cl.uss.ca.exception.DatabaseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class PersonRepository extends JdbcRepository {

    @Inject
    OrganizationRepository organizationRepository;

    public enum Population {
        ALL,
        RESEARCHERS,
        WITHOUT_POSITION
    }

    public long countPeople(PersonQuery query) {
        SqlParts parts = buildWhere(query);
        String sql = "SELECT COUNT(*) FROM " + qualified("pure_person") + " p" + parts.where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count people", ex);
        }
    }

    public List<PersonSummaryDto> findPeople(PersonQuery query) {
        SqlParts parts = buildWhere(query);
        boolean identifiers = tableExists("pure_person_identifiers");
        boolean contributors = tableExists("pure_research_output_contributor");
        String sql = """
                SELECT
                    p.id,
                    p.pure_id,
                    p.uuid,
                    p.first_name,
                    p.last_name,
                    p.email,
                    p.cargo,
                    p.orcid,
                    p.orcid_authenticated,
                """ + personAuditSelect()
                + scopusIdsSelect(identifiers) + ",\n"
                + scopusIdCountSelect(identifiers) + ",\n"
                + publicationCountSelect(contributors) + ",\n"
                + activeInPureSelect("p") + ",\n"
                + scopusHindexSelect() + "\n" + """
                FROM """ + " " + qualified("pure_person") + """
                 p
                """ + parts.where
                + orderBy(query.sort(), query.direction());
        if (query.size() > 0) {
            sql += " LIMIT ? OFFSET ?";
        }
        List<Object> params = new ArrayList<>();
        if (identifiers) {
            params.add(SCOPUS_IDENTIFIER_TYPE);
            params.add(SCOPUS_IDENTIFIER_TYPE);
        }
        params.addAll(parts.params);
        if (query.size() > 0) {
            params.add(query.size());
            params.add((long) query.page() * query.size());
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                List<PersonSummaryDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapSummary(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list people", ex);
        }
    }

    public PersonDetailDto findPersonById(long id) {
        boolean identifiers = tableExists("pure_person_identifiers");
        boolean contributors = tableExists("pure_research_output_contributor");
        String sql = """
                SELECT
                    p.id,
                    p.pure_id,
                    p.uuid,
                    p.first_name,
                    p.last_name,
                    p.email,
                    p.cargo,
                    p.orcid,
                    p.orcid_authenticated,
                """ + orcidFirstSeenSelect() + """
                    p.gender,
                    p.`primaryId` AS primary_id,
                """ + personAuditSelect()
                + scopusIdsSelect(identifiers) + ",\n"
                + scopusIdCountSelect(identifiers) + ",\n"
                + publicationCountSelect(contributors) + ",\n"
                + activeInPureSelect("p") + ",\n"
                + scopusHindexSelect() + "\n" + """
                FROM """ + " " + qualified("pure_person") + """
                 p
                WHERE p.id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (identifiers) {
                statement.setString(index++, SCOPUS_IDENTIFIER_TYPE);
                statement.setString(index++, SCOPUS_IDENTIFIER_TYPE);
            }
            statement.setLong(index, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                PersonDetailDto detail = new PersonDetailDto();
                copySummary(mapSummary(rs), detail);
                detail.gender = nullableString(rs, "gender");
                detail.primaryId = nullableString(rs, "primary_id");
                detail.orcidFirstSeenAt = offsetUtc(rs.getTimestamp("orcid_first_seen_at"));
                detail.orcidFirstSeenSource = nullableString(rs, "orcid_first_seen_source");
                detail.identities = findIdentities(connection, id);
                detail.publications = findPublications(connection, detail.uuid);
                detail.affiliations = organizationRepository.findPersonAffiliations(connection, id);
                return detail;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load person", ex);
        }
    }

    public PersonScopusIdentifiersDto findScopusIdentifiers(long personId) {
        if (!personExists(personId)) {
            return null;
        }
        PersonScopusIdentifiersDto dto = new PersonScopusIdentifiersDto();
        dto.personId = personId;
        dto.items = findScopusIdentifierRows(personId);
        dto.count = dto.items.size();
        dto.distinctCount = countDistinctScopusIds(dto.items);
        dto.matchedCount = countMatchedScopusAuthors(dto.items);
        return dto;
    }

    public LocalDateTime minOrcidFirstSeenAt() {
        if (!columnExists("pure_person", "orcid_first_seen_at")) {
            return null;
        }
        String sql = "SELECT MIN(orcid_first_seen_at) FROM " + qualified("pure_person")
                + " WHERE orcid_first_seen_at IS NOT NULL";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return localDateTime(rs.getTimestamp(1));
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load ORCID tracking baseline", ex);
        }
    }

    /**
     * Tenure rows for the cumulative ORCID series. Does not apply current
     * {@code active_in_pure}: that snapshot is not a historical monthly denominator.
     * Optional cargo/population are current PURE attributes, not month-level history.
     */
    public List<OrcidAdoptionPerson> listOrcidAdoptionPeople(String cargo, Population population) {
        if (!columnExists("pure_person", "orcid_first_seen_at")) {
            return List.of();
        }
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendCargoPopulation(where, params, cargo, population);
        boolean removed = columnExists("pure_person", "removed_from_pure_at");
        String sql = """
                SELECT p.fecha_ingreso,
                """ + (removed ? "p.removed_from_pure_at" : "NULL AS removed_from_pure_at") + """
                ,
                       p.orcid_first_seen_at
                FROM """ + " " + qualified("pure_person") + """
                 p
                """ + where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                List<OrcidAdoptionPerson> items = new ArrayList<>();
                while (rs.next()) {
                    java.sql.Date ingreso = rs.getDate("fecha_ingreso");
                    items.add(new OrcidAdoptionPerson(
                            ingreso == null ? null : ingreso.toLocalDate(),
                            localDateTime(rs.getTimestamp("removed_from_pure_at")),
                            localDateTime(rs.getTimestamp("orcid_first_seen_at"))
                    ));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load ORCID adoption tenure", ex);
        }
    }

    public PersonFiltersDto listPositions() {
        PersonFiltersDto filters = new PersonFiltersDto();
        String sql = """
                SELECT
                    TRIM(cargo) AS cargo,
                    COUNT(*) AS total
                FROM """ + " " + qualified("pure_person") + """
                 p
                WHERE """ + " " + activeInPurePredicate("pure_person", "p") + """
                
                GROUP BY TRIM(cargo)
                ORDER BY TRIM(cargo)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String cargo = rs.getString("cargo");
                if (cargo == null || cargo.isBlank()) {
                    filters.withoutPositionAvailable = true;
                } else {
                    filters.positions.add(cargo);
                }
            }
            filters.scopusIdCounts.addAll(listScopusIdCounts());
            return filters;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list person positions", ex);
        }
    }

    public List<Integer> listScopusIdCounts() {
        List<Integer> counts = new ArrayList<>();
        for (ScopusCountDto bucket : listScopusDistribution()) {
            counts.add(bucket.count);
        }
        return counts.isEmpty() ? List.of(0) : counts;
    }

    public List<ScopusCountDto> listScopusDistribution() {
        return listScopusDistribution(defaultStatsQuery());
    }

    public List<ScopusCountDto> listScopusDistribution(PersonQuery query) {
        PersonQuery safe = query == null ? defaultStatsQuery() : query;
        if (!tableExists("pure_person_identifiers")) {
            return List.of(new ScopusCountDto(0, countPeople(safe)));
        }
        SqlParts parts = buildWhere(safe);
        String sql = """
                SELECT
                    x.scopus_count,
                    COUNT(*) AS people
                FROM (
                    SELECT
                        p.id,
                        COUNT(pi.id) AS scopus_count
                    FROM """ + " " + qualified("pure_person") + """
                     p
                    LEFT JOIN """ + " " + qualified("pure_person_identifiers") + """
                     pi
                        ON pi.person_id = p.id
                       AND pi.identifier_type = ?
                       AND pi.identifier_value IS NOT NULL
                       AND TRIM(pi.identifier_value) <> ''
                    """ + parts.where + """
                    
                    GROUP BY p.id
                ) x
                GROUP BY x.scopus_count
                ORDER BY x.scopus_count
                """;
        List<Object> params = new ArrayList<>();
        params.add(SCOPUS_IDENTIFIER_TYPE);
        params.addAll(parts.params);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                List<ScopusCountDto> distribution = new ArrayList<>();
                while (rs.next()) {
                    distribution.add(new ScopusCountDto(
                            rs.getInt("scopus_count"),
                            rs.getLong("people")
                    ));
                }
                return distribution.isEmpty() ? List.of(new ScopusCountDto(0, 0)) : distribution;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list Scopus ID distribution", ex);
        }
    }

    public PersonStatsDto stats() {
        return stats(defaultStatsQuery());
    }

    public PersonStatsDto stats(PersonQuery query) {
        PersonQuery safe = query == null ? defaultStatsQuery() : query;
        PersonStatsDto stats = new PersonStatsDto();
        SqlParts parts = buildWhere(safe);
        String sql = """
                SELECT TRIM(p.cargo) AS cargo, COUNT(*) AS total
                FROM """ + " " + qualified("pure_person") + """
                 p
                """ + parts.where + """
                
                GROUP BY TRIM(p.cargo)
                ORDER BY total DESC, TRIM(p.cargo)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String cargo = rs.getString("cargo");
                    long count = rs.getLong("total");
                    stats.total += count;
                    if (cargo == null || cargo.isBlank()) {
                        stats.withoutPosition += count;
                    } else {
                        stats.byPosition.add(new PositionCountDto(cargo, count));
                        if (ResearcherPositions.VALUES.contains(cargo)) {
                            stats.researchers += count;
                        }
                    }
                }
            }
            stats.totalPeople = stats.total;
            stats.scopus = toScopusStats(listScopusDistribution(safe));
            stats.withScopusHindex = countWithScopusHindex(safe);
            return stats;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load person stats", ex);
        }
    }

    private static PersonQuery defaultStatsQuery() {
        return new PersonQuery(null, null, Population.ALL, null, null, null, null, Boolean.TRUE, "name", "asc", 0, -1);
    }

    /**
     * Identidad institucional ya almacenada desde PURE. No inventa sede, facultad ni programa.
     */
    public PurePersonIdentity findIdentityByEmail(String email) {
        if (email == null || email.isBlank() || !tableExists("pure_person")) {
            return null;
        }
        String sql = """
                SELECT id, first_name, last_name, email, cargo
                FROM """ + " " + qualified("pure_person") + """
                 WHERE LOWER(email) = LOWER(?)
                 LIMIT 1
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email.trim());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                PurePersonIdentity identity = new PurePersonIdentity();
                identity.firstName = rs.getString("first_name");
                identity.lastName = rs.getString("last_name");
                identity.email = rs.getString("email");
                identity.position = rs.getString("cargo");
                long id = rs.getLong("id");
                identity.id = rs.wasNull() ? null : id;
                return identity;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load institutional profile", ex);
        }
    }

    private boolean personExists(long personId) {
        String sql = "SELECT 1 FROM " + qualified("pure_person") + " WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to check person", ex);
        }
    }

    private List<PersonScopusIdentifierDto> findScopusIdentifierRows(long personId) {
        if (!tableExists("pure_person_identifiers")) {
            return List.of();
        }
        String sql;
        if (tableExists("scopus_author")) {
            sql = """
                SELECT
                    ppi.id,
                    ppi.pure_id,
                    ppi.identifier_value,
                    sa.id AS scopus_author_id,
                    sa.author_name,
                    sa.number_documents,
                    sa.orcid
                FROM """ + " " + qualified("pure_person_identifiers") + """
                 ppi
                LEFT JOIN """ + " " + qualified("scopus_author") + """
                 sa
                    ON ppi.identifier_value IS NOT NULL
                   AND TRIM(ppi.identifier_value) <> ''
                   AND sa.id = (
                        SELECT MIN(x.id)
                        FROM """ + " " + qualified("scopus_author") + """
                         x
                        WHERE x.auth_id IS NOT NULL
                          AND TRIM(x.auth_id) <> ''
                          AND TRIM(x.auth_id) = TRIM(ppi.identifier_value)
                   )
                WHERE ppi.person_id = ?
                  AND ppi.identifier_type = ?
                ORDER BY ppi.id
                """;
        } else {
            sql = """
                SELECT
                    id,
                    pure_id,
                    identifier_value,
                    NULL AS scopus_author_id,
                    NULL AS author_name,
                    NULL AS number_documents,
                    NULL AS orcid
                FROM """ + " " + qualified("pure_person_identifiers") + """
                
                WHERE person_id = ?
                  AND identifier_type = ?
                ORDER BY id
                """;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            statement.setString(2, SCOPUS_IDENTIFIER_TYPE);
            try (ResultSet rs = statement.executeQuery()) {
                List<PersonScopusIdentifierDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapScopusIdentifier(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list Scopus identifiers", ex);
        }
    }

    private static PersonScopusIdentifierDto mapScopusIdentifier(ResultSet rs) throws SQLException {
        PersonScopusIdentifierDto dto = new PersonScopusIdentifierDto();
        dto.id = nullableLong(rs, "id");
        dto.pureId = nullableLong(rs, "pure_id");
        dto.value = nullableString(rs, "identifier_value");
        dto.scopusAuthorId = nullableLong(rs, "scopus_author_id");
        dto.authorName = nullableString(rs, "author_name");
        dto.numberDocuments = nullableInt(rs, "number_documents");
        dto.orcid = nullableString(rs, "orcid");
        dto.foundInScopus = dto.scopusAuthorId != null;
        return dto;
    }

    private static int countDistinctScopusIds(List<PersonScopusIdentifierDto> items) {
        Set<String> values = new HashSet<>();
        for (PersonScopusIdentifierDto item : items) {
            if (item.value != null && !item.value.isBlank()) {
                values.add(item.value.trim());
            }
        }
        return values.size();
    }

    private static int countMatchedScopusAuthors(List<PersonScopusIdentifierDto> items) {
        int matched = 0;
        for (PersonScopusIdentifierDto item : items) {
            if (item.foundInScopus) {
                matched++;
            }
        }
        return matched;
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
        if (personUuid == null || !tableExists("pure_research_output")
                || !tableExists("pure_research_output_contributor")) {
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

    private SqlParts buildWhere(PersonQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (query.search() != null && !query.search().isBlank()) {
            String raw = query.search().trim();
            String like = likeContains(raw);
            String orcid = stripKnownPrefix(raw, "https://orcid.org/", "http://orcid.org/");
            where.append("""
                     AND (
                        p.first_name LIKE ? ESCAPE '!'
                        OR p.last_name LIKE ? ESCAPE '!'
                        OR p.email LIKE ? ESCAPE '!'
                        OR p.orcid LIKE ? ESCAPE '!'
                        OR p.orcid = ?
                        OR p.uuid = ?
                        OR p.`primaryId` LIKE ? ESCAPE '!'
                    """);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(orcid);
            params.add(raw);
            params.add(like);
            Long pureId = parseNumericId(raw);
            if (pureId != null) {
                where.append(" OR p.pure_id = ?");
                params.add(pureId);
            }
            if (tableExists("pure_person_identifiers")) {
                where.append(scopusIdentifierExistsSql(" OR"));
                params.add(SCOPUS_IDENTIFIER_TYPE);
                params.add(like);
                params.add(raw);
            }
            where.append(" )");
        }

        appendCargoPopulation(where, params, query.cargo(), query.population());

        if (Boolean.TRUE.equals(query.hasOrcid())) {
            where.append(" AND p.orcid IS NOT NULL AND TRIM(p.orcid) <> ''");
        } else if (Boolean.FALSE.equals(query.hasOrcid())) {
            where.append(" AND (p.orcid IS NULL OR TRIM(p.orcid) = '')");
        }

        if (query.scopusCount() != null) {
            if (!tableExists("pure_person_identifiers")) {
                if (query.scopusCount() != 0) {
                    where.append(" AND 1=0");
                }
            } else {
                where.append(" AND ").append(scopusCountExpression()).append(" = ?");
                params.add(SCOPUS_IDENTIFIER_TYPE);
                params.add(query.scopusCount());
            }
        } else if (query.hasScopus() != null) {
            if (!tableExists("pure_person_identifiers")) {
                if (Boolean.TRUE.equals(query.hasScopus())) {
                    where.append(" AND 1=0");
                }
            } else if (Boolean.TRUE.equals(query.hasScopus())) {
                where.append(scopusPresenceExistsSql(false));
                params.add(SCOPUS_IDENTIFIER_TYPE);
            } else {
                where.append(scopusPresenceExistsSql(true));
                params.add(SCOPUS_IDENTIFIER_TYPE);
            }
        }
        if (query.minDistinctScopusCount() != null) {
            if (!tableExists("pure_person_identifiers")) {
                if (query.minDistinctScopusCount() > 0) {
                    where.append(" AND 1=0");
                }
            } else {
                where.append(" AND ").append(distinctScopusCountExpression()).append(" >= ?");
                params.add(SCOPUS_IDENTIFIER_TYPE);
                params.add(query.minDistinctScopusCount());
            }
        }
        appendActiveInPure(where, params, "p", query.activeInPure());
        return new SqlParts(where.toString(), params);
    }

    private static String orderBy(String sort, String direction) {
        boolean descending = "desc".equalsIgnoreCase(direction);
        String key = sort == null || sort.isBlank() ? "name" : sort;
        return switch (key) {
            case "pureId" -> " ORDER BY " + orderByNullsLast("p.pure_id", descending) + ", p.id ASC";
            case "name" -> " ORDER BY " + orderByNullsLast("p.last_name", descending) + ", "
                    + orderByNullsLast("p.first_name", descending) + ", p.id ASC";
            case "position" -> " ORDER BY " + orderByNullsLast("p.cargo", descending)
                    + ", p.last_name ASC, p.first_name ASC, p.id ASC";
            case "orcid" -> " ORDER BY " + orderByNullsLast("p.orcid", descending)
                    + ", p.last_name ASC, p.id ASC";
            case "publicationCount" -> " ORDER BY " + orderByNullsLast("publication_count", descending)
                    + ", p.last_name ASC, p.id ASC";
            case "scopusHindex" -> " ORDER BY " + orderByNullsLast("scopus_hindex", descending)
                    + ", p.last_name ASC, p.id ASC";
            case "createdBy" -> " ORDER BY " + orderByNullsLast("p.created_by", descending)
                    + ", p.last_name ASC, p.id ASC";
            case "modifiedBy" -> " ORDER BY " + orderByNullsLast("p.modified_by", descending)
                    + ", p.last_name ASC, p.id ASC";
            default -> " ORDER BY " + orderByNullsLast("p.last_name", false)
                    + ", " + orderByNullsLast("p.first_name", false) + ", p.id ASC";
        };
    }

    private String personAuditSelect() {
        if (!columnExists("pure_person", "created_by")) {
            return "CAST(NULL AS CHAR) AS created_by,\nCAST(NULL AS CHAR) AS modified_by,\n";
        }
        return "p.created_by,\np.modified_by,\n";
    }

    private void appendCargoPopulation(
            StringBuilder where,
            List<Object> params,
            String cargo,
            Population population
    ) {
        boolean withoutPosition = population == Population.WITHOUT_POSITION
                || ResearcherPositions.WITHOUT_POSITION_CODE.equalsIgnoreCase(blankToNull(cargo));
        if (withoutPosition) {
            where.append(" AND (p.cargo IS NULL OR TRIM(p.cargo) = '')");
        } else if (cargo != null && !cargo.isBlank()) {
            where.append(" AND TRIM(p.cargo) = ?");
            params.add(cargo.trim());
        }
        if (population == Population.RESEARCHERS) {
            where.append(" AND TRIM(p.cargo) IN (")
                    .append(ResearcherPositions.inClausePlaceholders())
                    .append(")");
            params.addAll(ResearcherPositions.VALUES);
        }
    }

    private String orcidFirstSeenSelect() {
        if (!columnExists("pure_person", "orcid_first_seen_at")) {
            return "NULL AS orcid_first_seen_at, NULL AS orcid_first_seen_source,\n";
        }
        return "p.orcid_first_seen_at, p.orcid_first_seen_source,\n";
    }

    private static OffsetDateTime offsetUtc(Timestamp value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String scopusHindexSelect() {
        if (!tableExists("pure_person_metric")) {
            return "CAST(NULL AS SIGNED) AS scopus_hindex, CAST(NULL AS SIGNED) AS scopus_citations";
        }
        return PureMetricSql.scopusHindexSelect(qualified("pure_person_metric"), "p") + " AS scopus_hindex, "
                + PureMetricSql.scopusCitationsSelect(qualified("pure_person_metric"), "p") + " AS scopus_citations";
    }

    private long countWithScopusHindex(PersonQuery query) {
        if (!tableExists("pure_person_metric")) {
            return 0L;
        }
        SqlParts parts = buildWhere(query);
        String sql = "SELECT COUNT(*) FROM " + qualified("pure_person") + " p" + parts.where
                + " AND " + PureMetricSql.scopusHindexSelect(qualified("pure_person_metric"), "p") + " IS NOT NULL";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count people with Scopus h-index", ex);
        }
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private String activeInPureSelect(String alias) {
        if (!columnExists("pure_person", "active_in_pure")) {
            return "1 AS active_in_pure";
        }
        return "COALESCE(" + alias + ".active_in_pure, 1) AS active_in_pure";
    }

    private void appendActiveInPure(StringBuilder where, List<Object> params, String alias, Boolean activeInPure) {
        if (activeInPure == null || !columnExists("pure_person", "active_in_pure")) {
            return;
        }
        where.append(" AND COALESCE(").append(alias).append(".active_in_pure, 1) = ?");
        params.add(Boolean.TRUE.equals(activeInPure) ? 1 : 0);
    }

    private String scopusPresenceExistsSql(boolean negated) {
        return (negated ? " AND NOT EXISTS (" : " AND EXISTS (")
                + scopusIdentifierExistsBody(false)
                + ")";
    }

    private String scopusIdentifierExistsSql(String connective) {
        return connective
                + " EXISTS ("
                + scopusIdentifierExistsBody(true)
                + ")";
    }

    private String scopusIdentifierExistsBody(boolean matchValue) {
        String valuePredicate = matchValue
                ? """
                      AND (
                          i.identifier_value LIKE ? ESCAPE '!'
                          OR i.identifier_value = ?
                      )
                    """
                : """
                      AND i.identifier_value IS NOT NULL
                      AND TRIM(i.identifier_value) <> ''
                    """;
        return """
                SELECT 1
                FROM %s i
                WHERE i.person_id = p.id
                  AND i.identifier_type = ?
                """.formatted(qualified("pure_person_identifiers")) + valuePredicate;
    }

    private static String scopusCountExpression() {
        return """
                COALESCE((
                    SELECT COUNT(*)
                    FROM %s i
                    WHERE i.person_id = p.id
                      AND i.identifier_type = ?
                      AND i.identifier_value IS NOT NULL
                      AND TRIM(i.identifier_value) <> ''
                ), 0)""".formatted(qualified("pure_person_identifiers"));
    }

    private static String distinctScopusCountExpression() {
        return """
                COALESCE((
                    SELECT COUNT(DISTINCT TRIM(i.identifier_value))
                    FROM %s i
                    WHERE i.person_id = p.id
                      AND i.identifier_type = ?
                      AND i.identifier_value IS NOT NULL
                      AND TRIM(i.identifier_value) <> ''
                ), 0)""".formatted(qualified("pure_person_identifiers"));
    }

    private static String scopusIdCountSelect(boolean identifiersAvailable) {
        if (!identifiersAvailable) {
            return "0 AS scopus_id_count";
        }
        return scopusCountExpression() + " AS scopus_id_count";
    }

    private static String scopusIdsSelect(boolean identifiersAvailable) {
        if (!identifiersAvailable) {
            return "NULL AS scopus_ids";
        }
        return """
                    (
                        SELECT GROUP_CONCAT(DISTINCT TRIM(i.identifier_value) ORDER BY i.identifier_value SEPARATOR ',')
                        FROM %s i
                        WHERE i.person_id = p.id
                          AND i.identifier_type = ?
                          AND i.identifier_value IS NOT NULL
                          AND TRIM(i.identifier_value) <> ''
                    ) AS scopus_ids""".formatted(qualified("pure_person_identifiers"));
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

    private PersonSummaryDto mapSummary(ResultSet rs) throws SQLException {
        PersonSummaryDto dto = new PersonSummaryDto();
        dto.id = rs.getLong("id");
        dto.pureId = nullableLong(rs, "pure_id");
        dto.uuid = nullableString(rs, "uuid");
        dto.firstName = nullableString(rs, "first_name");
        dto.lastName = nullableString(rs, "last_name");
        dto.fullName = fullName(dto.firstName, dto.lastName);
        dto.email = nullableString(rs, "email");
        dto.position = nullableString(rs, "cargo");
        dto.orcid = nullableString(rs, "orcid");
        dto.orcidAuthenticated = nullableBoolean(rs, "orcid_authenticated");
        dto.scopusIds = splitCsv(rs.getString("scopus_ids"));
        dto.scopusIdCount = nullableInt(rs, "scopus_id_count");
        dto.publicationCount = nullableInt(rs, "publication_count");
        dto.activeInPure = nullableBoolean(rs, "active_in_pure");
        dto.organization = null;
        dto.createdBy = nullableString(rs, "created_by");
        dto.modifiedBy = nullableString(rs, "modified_by");
        dto.scopusHindex = hasColumn(rs, "scopus_hindex") ? nullableInt(rs, "scopus_hindex") : null;
        dto.scopusCitations = hasColumn(rs, "scopus_citations") ? nullableInt(rs, "scopus_citations") : null;
        return dto;
    }

    private static void copySummary(PersonSummaryDto source, PersonDetailDto target) {
        target.id = source.id;
        target.pureId = source.pureId;
        target.uuid = source.uuid;
        target.firstName = source.firstName;
        target.lastName = source.lastName;
        target.fullName = source.fullName;
        target.email = source.email;
        target.position = source.position;
        target.orcid = source.orcid;
        target.orcidAuthenticated = source.orcidAuthenticated;
        target.scopusIds = source.scopusIds;
        target.scopusIdCount = source.scopusIdCount;
        target.publicationCount = source.publicationCount;
        target.activeInPure = source.activeInPure;
        target.organization = source.organization;
        target.createdBy = source.createdBy;
        target.modifiedBy = source.modifiedBy;
        target.scopusHindex = source.scopusHindex;
        target.scopusCitations = source.scopusCitations;
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

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private long countAllPeople() {
        String sql = "SELECT COUNT(*) FROM " + qualified("pure_person") + " p WHERE "
                + activeInPurePredicate("pure_person", "p");
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count people", ex);
        }
    }

    private static ScopusStatsDto toScopusStats(List<ScopusCountDto> distribution) {
        ScopusStatsDto scopus = new ScopusStatsDto();
        scopus.distribution.addAll(distribution);
        int maxCount = 0;
        for (ScopusCountDto bucket : distribution) {
            if (bucket.count > maxCount) {
                maxCount = bucket.count;
            }
            if (bucket.count == 0) {
                scopus.without += bucket.people;
            } else if (bucket.count == 1) {
                scopus.one += bucket.people;
            } else if (bucket.count == 2) {
                scopus.two += bucket.people;
            } else {
                scopus.threeOrMore += bucket.people;
            }
        }
        scopus.maxCount = maxCount;
        return scopus;
    }

    public record PersonQuery(
            String search,
            String cargo,
            Population population,
            Boolean hasOrcid,
            Boolean hasScopus,
            Integer scopusCount,
            Integer minDistinctScopusCount,
            Boolean activeInPure,
            String sort,
            String direction,
            int page,
            int size
    ) {
        public static Population parsePopulation(String raw) {
            if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) {
                return Population.ALL;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "RESEARCHERS" -> Population.RESEARCHERS;
                case "WITHOUT_POSITION", "SIN_CARGO" -> Population.WITHOUT_POSITION;
                default -> null;
            };
        }
    }

    private record SqlParts(String where, List<Object> params) {
    }
}
