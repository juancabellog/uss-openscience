package cl.uss.ca.repository;

import cl.uss.ca.api.dto.OrganizationDetailDto;
import cl.uss.ca.api.dto.OrganizationFiltersDto;
import cl.uss.ca.api.dto.OrganizationPersonDto;
import cl.uss.ca.api.dto.OrganizationProjectDto;
import cl.uss.ca.api.dto.OrganizationStatsDto;
import cl.uss.ca.api.dto.OrganizationSummaryDto;
import cl.uss.ca.api.dto.OrganizationTypeOptionDto;
import cl.uss.ca.api.dto.PersonAffiliationDto;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class OrganizationRepository extends JdbcRepository {

    public record OrganizationQuery(
            String search,
            String typeUri,
            String presence,
            String hasParent,
            String sort,
            String direction,
            int page,
            int size
    ) {
    }

    private static final class SqlParts {
        final String where;
        final List<Object> params;

        SqlParts(String where, List<Object> params) {
            this.where = where;
            this.params = params;
        }
    }

    public long count(OrganizationQuery query) {
        if (!tableExists("pure_organization")) {
            return 0L;
        }
        SqlParts parts = buildWhere(query);
        String sql = "SELECT COUNT(*) FROM " + qualified("pure_organization") + " o" + parts.where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count organizations", ex);
        }
    }

    public List<OrganizationSummaryDto> find(OrganizationQuery query) {
        if (!tableExists("pure_organization")) {
            return List.of();
        }
        SqlParts parts = buildWhere(query);
        String parentJoin = tableExists("pure_organization")
                ? " LEFT JOIN " + qualified("pure_organization") + " parent ON parent.uuid = o.parent_uuid"
                : "";
        String sql = """
                SELECT
                    o.id, o.pure_id, o.uuid,
                    %s AS name,
                    o.name_es, o.name_en,
                    %s AS type,
                    o.type_es, o.type_en, o.type_uri,
                    o.parent_uuid,
                    %s AS parent_name,
                    parent.name_es AS parent_name_es,
                    parent.name_en AS parent_name_en,
                    %s,
                    o.modified_date_pure,
                    o.created_by, o.modified_by
                FROM %s o
                %s
                %s
                %s
                """.formatted(
                displayNameExpr("o"),
                displayTypeExpr("o"),
                displayNameExpr("parent"),
                activeInPureSelect("o"),
                qualified("pure_organization"),
                parentJoin,
                parts.where,
                orderBy(query.sort(), query.direction())
        );
        List<Object> params = new ArrayList<>(parts.params);
        if (query.size() > 0) {
            sql += " LIMIT ? OFFSET ?";
            params.add(query.size());
            params.add((long) query.page() * query.size());
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                List<OrganizationSummaryDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapSummary(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list organizations", ex);
        }
    }

    public OrganizationStatsDto stats(OrganizationQuery query) {
        OrganizationStatsDto dto = new OrganizationStatsDto();
        if (!tableExists("pure_organization")) {
            return dto;
        }
        SqlParts parts = buildWhere(query);
        String activeExpr = columnExists("pure_organization", "active_in_pure")
                ? "COALESCE(o.active_in_pure, 1) = 1"
                : "1=1";
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(CASE WHEN " + activeExpr + " THEN 1 ELSE 0 END), 0) AS active, "
                + "COALESCE(SUM(CASE WHEN NOT (" + activeExpr + ") THEN 1 ELSE 0 END), 0) AS inactive, "
                + "COALESCE(SUM(CASE WHEN o.parent_uuid IS NOT NULL AND TRIM(o.parent_uuid) <> '' THEN 1 ELSE 0 END), 0) AS with_parent "
                + "FROM " + qualified("pure_organization") + " o "
                + parts.where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    dto.total = rs.getLong("total");
                    dto.active = rs.getLong("active");
                    dto.inactive = rs.getLong("inactive");
                    dto.withParent = rs.getLong("with_parent");
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load organization stats", ex);
        }
        return dto;
    }

    public OrganizationFiltersDto filters() {
        OrganizationFiltersDto dto = new OrganizationFiltersDto();
        if (!tableExists("pure_organization")) {
            return dto;
        }
        String sql = """
                SELECT DISTINCT type_uri, type_en, type_es
                FROM """ + " " + qualified("pure_organization") + """
                
                WHERE type_uri IS NOT NULL AND TRIM(type_uri) <> ''
                ORDER BY type_es, type_en
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                OrganizationTypeOptionDto option = new OrganizationTypeOptionDto();
                option.uri = nullableString(rs, "type_uri");
                option.typeEn = nullableString(rs, "type_en");
                option.typeEs = nullableString(rs, "type_es");
                dto.types.add(option);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load organization filters", ex);
        }
        return dto;
    }

    public OrganizationDetailDto findById(long id) {
        return loadDetail("o.id = ?", id);
    }

    public long countRelatedPersons(long organizationId) {
        OrganizationDetailDto detail = findById(organizationId);
        return countRelatedPersonsByUuid(detail.uuid);
    }

    public List<OrganizationPersonDto> findRelatedPersons(long organizationId, int page, int size) {
        OrganizationDetailDto detail = findById(organizationId);
        return findRelatedPersonsByUuid(detail.uuid, page, size);
    }

    public long countRelatedProjects(long organizationId) {
        OrganizationDetailDto detail = findById(organizationId);
        return countRelatedProjectsByUuid(detail.uuid);
    }

    public List<OrganizationProjectDto> findRelatedProjects(long organizationId, int page, int size) {
        OrganizationDetailDto detail = findById(organizationId);
        return findRelatedProjectsByUuid(detail.uuid, page, size);
    }

    public List<PersonAffiliationDto> findPersonAffiliations(Connection connection, long personId)
            throws SQLException {
        if (!tableExists("pure_person_organization")) {
            return List.of();
        }
        boolean catalog = tableExists("pure_organization");
        String join = catalog
                ? " LEFT JOIN " + qualified("pure_organization") + " o ON o.uuid = po.organization_uuid"
                : "";
        String extra = catalog
                ? ", o.id AS organization_id, o.name_es, o.name_en, o.type_es, o.type_en, o.active_in_pure AS org_active"
                : ", CAST(NULL AS UNSIGNED) AS organization_id, CAST(NULL AS CHAR) AS name_es, CAST(NULL AS CHAR) AS name_en, CAST(NULL AS CHAR) AS type_es, CAST(NULL AS CHAR) AS type_en, CAST(NULL AS UNSIGNED) AS org_active";
        String sql = """
                SELECT po.pure_id, po.organization_uuid, po.employment_type, po.email,
                       po.fecha_inicio, po.fecha_termino, po.primary_association, po.contract_type,
                       po.fte, po.job_description, po.job_title, po.staff_type
                """ + extra + """
                
                FROM """ + " " + qualified("pure_person_organization") + """
                 po
                """ + join + """
                
                WHERE po.person_id = ?
                ORDER BY po.primary_association DESC, po.fecha_inicio DESC, po.id
                """;
        List<PersonAffiliationDto> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    PersonAffiliationDto dto = new PersonAffiliationDto();
                    dto.personId = personId;
                    dto.pureId = nullableLong(rs, "pure_id");
                    dto.organizationUuid = nullableString(rs, "organization_uuid");
                    dto.organizationId = nullableLong(rs, "organization_id");
                    dto.nameEs = nullableString(rs, "name_es");
                    dto.nameEn = nullableString(rs, "name_en");
                    dto.typeEs = nullableString(rs, "type_es");
                    dto.typeEn = nullableString(rs, "type_en");
                    dto.organizationActiveInPure = nullableBoolean(rs, "org_active");
                    dto.resolved = dto.organizationId != null;
                    dto.employmentType = nullableString(rs, "employment_type");
                    dto.email = nullableString(rs, "email");
                    dto.startDate = localDate(rs, "fecha_inicio");
                    dto.endDate = localDate(rs, "fecha_termino");
                    dto.primaryAssociation = nullableBoolean(rs, "primary_association");
                    dto.contractType = nullableString(rs, "contract_type");
                    BigDecimal fte = rs.getBigDecimal("fte");
                    dto.fte = rs.wasNull() ? null : fte;
                    dto.jobDescription = nullableString(rs, "job_description");
                    dto.jobTitle = nullableString(rs, "job_title");
                    dto.staffType = nullableString(rs, "staff_type");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private OrganizationDetailDto loadDetail(String predicate, Object key) {
        if (!tableExists("pure_organization")) {
            throw new NotFoundException("ORGANIZATION_NOT_FOUND", "Organization catalog is not available");
        }
        String parentJoin = " LEFT JOIN " + qualified("pure_organization") + " parent ON parent.uuid = o.parent_uuid";
        String sql = """
                SELECT
                    o.id, o.pure_id, o.uuid, o.type_discriminator,
                    %s AS name,
                    o.name_es, o.name_en,
                    %s AS type,
                    o.type_es, o.type_en, o.type_uri,
                    o.parent_uuid,
                    %s AS parent_name,
                    parent.id AS parent_id,
                    parent.name_es AS parent_name_es,
                    parent.name_en AS parent_name_en,
                    parent.active_in_pure AS parent_active,
                    %s,
                    o.created_by, o.created_date_pure, o.modified_by, o.modified_date_pure,
                    o.pure_version, o.system_name
                FROM %s o
                %s
                WHERE %s
                """.formatted(
                displayNameExpr("o"),
                displayTypeExpr("o"),
                displayNameExpr("parent"),
                activeInPureSelect("o"),
                qualified("pure_organization"),
                parentJoin,
                predicate
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("ORGANIZATION_NOT_FOUND", "Organization was not found");
                }
                OrganizationDetailDto dto = new OrganizationDetailDto();
                copySummary(mapSummary(rs), dto);
                dto.typeDiscriminator = nullableString(rs, "type_discriminator");
                dto.createdDatePure = timestamp(rs, "created_date_pure");
                dto.version = nullableString(rs, "pure_version");
                dto.systemName = nullableString(rs, "system_name");
                dto.parentId = nullableLong(rs, "parent_id");
                dto.parentResolved = dto.parentId != null;
                dto.parentActiveInPure = nullableBoolean(rs, "parent_active");
                return dto;
            }
        } catch (NotFoundException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load organization", ex);
        }
    }

    private long countRelatedPersonsByUuid(String uuid) {
        if (uuid == null || !tableExists("pure_person_organization")) {
            return 0L;
        }
        String sql = "SELECT COUNT(*) FROM " + qualified("pure_person_organization") + " WHERE organization_uuid = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count organization persons", ex);
        }
    }

    private List<OrganizationPersonDto> findRelatedPersonsByUuid(String uuid, int page, int size) {
        if (uuid == null || !tableExists("pure_person_organization") || !tableExists("pure_person")) {
            return List.of();
        }
        String sql = """
                SELECT p.id AS person_id, p.pure_id AS person_pure_id, p.uuid AS person_uuid,
                       p.first_name, p.last_name, po.job_title, po.employment_type, po.fte,
                       po.fecha_inicio, po.fecha_termino, po.primary_association,
                       p.active_in_pure
                FROM """ + " " + qualified("pure_person_organization") + """
                 po
                JOIN """ + " " + qualified("pure_person") + """
                 p ON p.id = po.person_id
                WHERE po.organization_uuid = ?
                ORDER BY p.last_name, p.first_name, p.id
                LIMIT ? OFFSET ?
                """;
        if (!columnExists("pure_person", "active_in_pure")) {
            sql = sql.replace("p.active_in_pure", "CAST(1 AS UNSIGNED) AS active_in_pure");
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setInt(2, size);
            statement.setLong(3, (long) page * size);
            try (ResultSet rs = statement.executeQuery()) {
                List<OrganizationPersonDto> items = new ArrayList<>();
                while (rs.next()) {
                    OrganizationPersonDto dto = new OrganizationPersonDto();
                    dto.personId = nullableLong(rs, "person_id");
                    dto.personPureId = nullableLong(rs, "person_pure_id");
                    dto.personUuid = nullableString(rs, "person_uuid");
                    dto.firstName = nullableString(rs, "first_name");
                    dto.lastName = nullableString(rs, "last_name");
                    dto.displayName = displayName(dto.firstName, dto.lastName);
                    dto.jobTitle = nullableString(rs, "job_title");
                    dto.employmentType = nullableString(rs, "employment_type");
                    BigDecimal fte = rs.getBigDecimal("fte");
                    dto.fte = rs.wasNull() ? null : fte;
                    dto.startDate = localDate(rs, "fecha_inicio");
                    dto.endDate = localDate(rs, "fecha_termino");
                    dto.primaryAssociation = nullableBoolean(rs, "primary_association");
                    dto.personActiveInPure = nullableBoolean(rs, "active_in_pure");
                    items.add(dto);
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list organization persons", ex);
        }
    }

    private long countRelatedProjectsByUuid(String uuid) {
        if (uuid == null || !tableExists("pure_project")) {
            return 0L;
        }
        String sql = "SELECT COUNT(*) FROM (" + relatedProjectsUnion() + ") related";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRelatedProjectUuid(statement, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count organization projects", ex);
        }
    }

    private List<OrganizationProjectDto> findRelatedProjectsByUuid(String uuid, int page, int size) {
        if (uuid == null || !tableExists("pure_project")) {
            return List.of();
        }
        String sql = "SELECT * FROM (" + relatedProjectsUnion() + ") related"
                + " ORDER BY title, project_id, relation_type LIMIT ? OFFSET ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindRelatedProjectUuid(statement, uuid);
            statement.setInt(index, size);
            statement.setLong(index + 1, (long) page * size);
            try (ResultSet rs = statement.executeQuery()) {
                List<OrganizationProjectDto> items = new ArrayList<>();
                while (rs.next()) {
                    OrganizationProjectDto dto = new OrganizationProjectDto();
                    dto.projectId = nullableLong(rs, "project_id");
                    dto.projectPureId = nullableLong(rs, "project_pure_id");
                    dto.projectUuid = nullableString(rs, "project_uuid");
                    dto.title = nullableString(rs, "title");
                    dto.titleEs = nullableString(rs, "title_es");
                    dto.titleEn = nullableString(rs, "title_en");
                    dto.relationType = nullableString(rs, "relation_type");
                    dto.projectActiveInPure = nullableBoolean(rs, "active_in_pure");
                    items.add(dto);
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list organization projects", ex);
        }
    }

    private String relatedProjectsUnion() {
        String title = "COALESCE(NULLIF(TRIM(pr.title_es), ''), NULLIF(TRIM(pr.title_en), ''), pr.uuid)";
        String active = columnExists("pure_project", "active_in_pure")
                ? "pr.active_in_pure"
                : "CAST(1 AS UNSIGNED) AS active_in_pure";
        String managing = """
                SELECT pr.id AS project_id, pr.pure_id AS project_pure_id, pr.uuid AS project_uuid,
                       %s AS title, pr.title_es, pr.title_en, 'MANAGING' AS relation_type, %s
                FROM %s pr
                WHERE pr.managing_organization_uuid = ?
                """.formatted(title, active, qualified("pure_project"));
        String associated = tableExists("pure_project_organization")
                ? """
                UNION ALL
                SELECT pr.id, pr.pure_id, pr.uuid, %s, pr.title_es, pr.title_en, 'ASSOCIATED', %s
                FROM %s po
                JOIN %s pr ON pr.id = po.project_id
                WHERE po.organization_uuid = ?
                """.formatted(title, active, qualified("pure_project_organization"), qualified("pure_project"))
                : "";
        String participant = tableExists("pure_project_participant_organization")
                && tableExists("pure_project_participant")
                ? """
                UNION ALL
                SELECT pr.id, pr.pure_id, pr.uuid, %s, pr.title_es, pr.title_en, 'PARTICIPANT', %s
                FROM %s ppo
                JOIN %s pp ON pp.id = ppo.participant_id
                JOIN %s pr ON pr.id = pp.project_id
                WHERE ppo.organization_uuid = ?
                """.formatted(
                        title,
                        active,
                        qualified("pure_project_participant_organization"),
                        qualified("pure_project_participant"),
                        qualified("pure_project"))
                : "";
        return managing + associated + participant;
    }

    private int bindRelatedProjectUuid(PreparedStatement statement, String uuid) throws SQLException {
        int index = 1;
        statement.setString(index++, uuid);
        if (tableExists("pure_project_organization")) {
            statement.setString(index++, uuid);
        }
        if (tableExists("pure_project_participant_organization") && tableExists("pure_project_participant")) {
            statement.setString(index++, uuid);
        }
        return index;
    }

    private SqlParts buildWhere(OrganizationQuery query) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE ");
        String presence = query.presence() == null ? "ACTIVE" : query.presence();
        if ("ALL".equals(presence) || !columnExists("pure_organization", "active_in_pure")) {
            where.append("1=1");
        } else if ("INACTIVE".equals(presence)) {
            where.append(" COALESCE(o.active_in_pure, 1) = 0");
        } else {
            where.append(activeInPurePredicate("pure_organization", "o"));
        }
        if (query.search() != null && !query.search().isBlank()) {
            String raw = query.search().trim();
            String like = likeContains(raw);
            Long pureId = parseNumericId(raw);
            where.append(" AND (o.uuid LIKE ? ESCAPE '!' OR o.name_es LIKE ? ESCAPE '!' OR o.name_en LIKE ? ESCAPE '!'");
            params.add(like);
            params.add(like);
            params.add(like);
            if (pureId != null) {
                where.append(" OR o.pure_id = ?");
                params.add(pureId);
            }
            where.append(" OR o.type_es LIKE ? ESCAPE '!' OR o.type_en LIKE ? ESCAPE '!')");
            params.add(like);
            params.add(like);
        }
        if (query.typeUri() != null && !query.typeUri().isBlank()) {
            where.append(" AND o.type_uri = ?");
            params.add(query.typeUri().trim());
        }
        if ("WITH".equals(query.hasParent())) {
            where.append(" AND o.parent_uuid IS NOT NULL AND TRIM(o.parent_uuid) <> ''");
        } else if ("WITHOUT".equals(query.hasParent())) {
            where.append(" AND (o.parent_uuid IS NULL OR TRIM(o.parent_uuid) = '')");
        }
        return new SqlParts(where.toString(), params);
    }

    private String orderBy(String sort, String direction) {
        boolean desc = "DESC".equalsIgnoreCase(direction);
        String order = desc ? " DESC" : " ASC";
        String key = sort == null ? "name" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "pureid" -> " ORDER BY " + orderByNullsLast("o.pure_id", desc) + ", name ASC, o.id ASC";
            case "type" -> " ORDER BY " + orderByNullsLast("type", desc) + ", name ASC, o.id ASC";
            case "parent" -> " ORDER BY " + orderByNullsLast("parent_name", desc) + ", name ASC, o.id ASC";
            case "modifieddatepure" -> " ORDER BY " + orderByNullsLast("o.modified_date_pure", desc) + ", name ASC, o.id ASC";
            case "activeinpure" -> " ORDER BY " + orderByNullsLast("o.active_in_pure", desc) + ", name ASC, o.id ASC";
            default -> " ORDER BY name" + order + ", o.id ASC";
        };
    }

    private OrganizationSummaryDto mapSummary(ResultSet rs) throws SQLException {
        OrganizationSummaryDto dto = new OrganizationSummaryDto();
        dto.id = rs.getLong("id");
        dto.pureId = nullableLong(rs, "pure_id");
        dto.uuid = nullableString(rs, "uuid");
        dto.name = nullableString(rs, "name");
        dto.nameEs = nullableString(rs, "name_es");
        dto.nameEn = nullableString(rs, "name_en");
        dto.type = nullableString(rs, "type");
        dto.typeEs = nullableString(rs, "type_es");
        dto.typeEn = nullableString(rs, "type_en");
        dto.typeUri = nullableString(rs, "type_uri");
        dto.parentUuid = nullableString(rs, "parent_uuid");
        dto.parentName = nullableString(rs, "parent_name");
        dto.parentNameEs = nullableString(rs, "parent_name_es");
        dto.parentNameEn = nullableString(rs, "parent_name_en");
        dto.activeInPure = columnExists("pure_organization", "active_in_pure")
                ? nullableBoolean(rs, "active_in_pure")
                : Boolean.TRUE;
        dto.modifiedDatePure = timestamp(rs, "modified_date_pure");
        dto.createdBy = nullableString(rs, "created_by");
        dto.modifiedBy = nullableString(rs, "modified_by");
        return dto;
    }

    private static void copySummary(OrganizationSummaryDto source, OrganizationDetailDto target) {
        target.id = source.id;
        target.pureId = source.pureId;
        target.uuid = source.uuid;
        target.name = source.name;
        target.nameEs = source.nameEs;
        target.nameEn = source.nameEn;
        target.type = source.type;
        target.typeEs = source.typeEs;
        target.typeEn = source.typeEn;
        target.typeUri = source.typeUri;
        target.parentUuid = source.parentUuid;
        target.parentName = source.parentName;
        target.parentNameEs = source.parentNameEs;
        target.parentNameEn = source.parentNameEn;
        target.activeInPure = source.activeInPure;
        target.modifiedDatePure = source.modifiedDatePure;
        target.createdBy = source.createdBy;
        target.modifiedBy = source.modifiedBy;
    }

    private String activeInPureSelect(String alias) {
        if (!columnExists("pure_organization", "active_in_pure")) {
            return "CAST(1 AS UNSIGNED) AS active_in_pure";
        }
        return alias + ".active_in_pure";
    }

    private static String displayNameExpr(String alias) {
        return "COALESCE(NULLIF(TRIM(" + alias + ".name_es), ''), NULLIF(TRIM(" + alias + ".name_en), ''), " + alias + ".uuid)";
    }

    private static String displayTypeExpr(String alias) {
        return "COALESCE(NULLIF(TRIM(" + alias + ".type_es), ''), NULLIF(TRIM(" + alias + ".type_en), ''), " + alias + ".type_uri)";
    }

    private static String timestamp(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

    private static String localDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate().toString();
    }

    private static String displayName(String first, String last) {
        String left = first == null ? "" : first.trim();
        String right = last == null ? "" : last.trim();
        String full = (left + " " + right).trim();
        return full.isEmpty() ? null : full;
    }
}
