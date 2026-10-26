package cl.uss.ca.repository;

import cl.uss.ca.api.dto.ProjectDescriptionDto;
import cl.uss.ca.api.dto.ProjectDetailDto;
import cl.uss.ca.api.dto.ProjectFiltersDto;
import cl.uss.ca.api.dto.ProjectIdentifierDto;
import cl.uss.ca.api.dto.ProjectNatureTypeDto;
import cl.uss.ca.api.dto.ProjectOrganizationDto;
import cl.uss.ca.api.dto.ProjectParticipantDto;
import cl.uss.ca.api.dto.ProjectStatsDto;
import cl.uss.ca.api.dto.ProjectSummaryDto;
import cl.uss.ca.api.dto.ProjectTypeOptionDto;
import cl.uss.ca.api.dto.ProjectVisibilityOptionDto;
import cl.uss.ca.api.dto.ProjectWorkflowOptionDto;
import cl.uss.ca.domain.PureProjectUris;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class ProjectRepository extends JdbcRepository {

    public record ProjectQuery(
            String search,
            String typeUri,
            String workflowStep,
            String visibilityKey,
            String presence,
            Integer startYear,
            String sort,
            String direction,
            int page,
            int size
    ) {
    }

    public long count(ProjectQuery query) {
        requireTable();
        SqlParts parts = buildWhere(query);
        String sql = "SELECT COUNT(*) FROM " + qualified("pure_project") + " p" + parts.where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count projects", ex);
        }
    }

    public List<ProjectSummaryDto> find(ProjectQuery query) {
        requireTable();
        SqlParts parts = buildWhere(query);
        String sql = """
                SELECT
                    p.id, p.pure_id, p.uuid,
                    %s AS title,
                    p.title_es, p.title_en, p.acronym,
                    %s AS project_id,
                    %s AS funder_reference,
                    %s AS type,
                    p.type_es, p.type_en,
                    p.period_start_date, p.period_end_date,
                    %s AS pi_name,
                    %s AS pi_person_id,
                    %s AS pi_person_uuid,
                    p.managing_organization_uuid,
                    %s
                    p.workflow_step, p.workflow_description_es, p.workflow_description_en,
                    p.visibility_key, p.visibility_es, p.visibility_en,
                    p.portal_url,
                    %s
                FROM %s p
                %s
                %s
                %s
                """.formatted(
                displayTitleExpr(),
                identifierSelect(PureProjectUris.PROJECT_ID),
                identifierSelect(PureProjectUris.FUNDER_REF),
                displayTypeExpr(),
                piSelect("name"),
                piSelect("person_id"),
                piSelect("person_uuid"),
                managingOrganizationSelect(),
                activeInPureSelect("p"),
                qualified("pure_project"),
                managingOrganizationJoin(),
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
                List<ProjectSummaryDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapSummary(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list projects", ex);
        }
    }

    public ProjectDetailDto findByIdOrUuid(String idOrUuid) {
        requireTable();
        Long numeric = parseNumericId(idOrUuid);
        if (numeric != null) {
            try {
                return findById(numeric);
            } catch (NotFoundException ex) {
                if (!looksLikeUuid(idOrUuid)) {
                    throw ex;
                }
            }
        }
        if (looksLikeUuid(idOrUuid)) {
            return findByUuid(idOrUuid.trim());
        }
        throw new NotFoundException("PROJECT_NOT_FOUND", "Project was not found");
    }

    public ProjectDetailDto findById(long id) {
        return loadDetail("p.id = ?", id);
    }

    public ProjectDetailDto findByUuid(String uuid) {
        return loadDetail("p.uuid = ?", uuid);
    }

    public ProjectStatsDto stats(ProjectQuery query) {
        requireTable();
        ProjectQuery safe = query == null
                ? new ProjectQuery(null, null, null, null, "ACTIVE", null, "title", "ASC", 0, 0)
                : query;
        SqlParts parts = buildWhere(safe);
        ProjectStatsDto dto = new ProjectStatsDto();
        String projectIdExists = identifierExistsSql(PureProjectUris.PROJECT_ID);
        String funderExists = identifierExistsSql(PureProjectUris.FUNDER_REF);
        String piExists = piExistsSql();
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(CASE WHEN " + projectIdExists + " THEN 1 ELSE 0 END), 0) AS with_project_id, "
                + "COALESCE(SUM(CASE WHEN " + funderExists + " THEN 1 ELSE 0 END), 0) AS with_funder, "
                + "COALESCE(SUM(CASE WHEN " + piExists + " THEN 1 ELSE 0 END), 0) AS with_pi "
                + "FROM " + qualified("pure_project") + " p "
                + parts.where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    dto.total = rs.getLong("total");
                    dto.withProjectId = rs.getLong("with_project_id");
                    dto.withFunderReference = rs.getLong("with_funder");
                    dto.withPrincipalInvestigator = rs.getLong("with_pi");
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load project stats", ex);
        }
        return dto;
    }

    public ProjectFiltersDto filters() {
        requireTable();
        ProjectFiltersDto dto = new ProjectFiltersDto();
        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT DISTINCT type_uri, type_en, type_es
                    FROM """ + " " + qualified("pure_project") + """
                    
                    WHERE type_uri IS NOT NULL
                    ORDER BY type_es, type_en
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectTypeOptionDto option = new ProjectTypeOptionDto();
                    option.uri = rs.getString("type_uri");
                    option.typeEn = rs.getString("type_en");
                    option.typeEs = rs.getString("type_es");
                    dto.types.add(option);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT DISTINCT workflow_step, workflow_description_en, workflow_description_es
                    FROM """ + " " + qualified("pure_project") + """
                    
                    WHERE workflow_step IS NOT NULL
                    ORDER BY workflow_step
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectWorkflowOptionDto option = new ProjectWorkflowOptionDto();
                    option.step = rs.getString("workflow_step");
                    option.labelEn = rs.getString("workflow_description_en");
                    option.labelEs = rs.getString("workflow_description_es");
                    dto.workflows.add(option);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT DISTINCT visibility_key, visibility_en, visibility_es
                    FROM """ + " " + qualified("pure_project") + """
                    
                    WHERE visibility_key IS NOT NULL
                    ORDER BY visibility_key
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectVisibilityOptionDto option = new ProjectVisibilityOptionDto();
                    option.key = rs.getString("visibility_key");
                    option.labelEn = rs.getString("visibility_en");
                    option.labelEs = rs.getString("visibility_es");
                    dto.visibilities.add(option);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT DISTINCT YEAR(period_start_date) AS start_year
                    FROM """ + " " + qualified("pure_project") + """
                    
                    WHERE period_start_date IS NOT NULL
                    ORDER BY start_year DESC
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Integer year = nullableInt(rs, "start_year");
                    if (year != null) {
                        dto.startYears.add(year);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load project filters", ex);
        }
        return dto;
    }

    private ProjectDetailDto loadDetail(String predicate, Object key) {
        String sql = """
                SELECT
                    p.*,
                    %s AS title,
                    %s AS project_id,
                    %s AS funder_reference,
                    %s AS type,
                    %s AS pi_name,
                    %s AS pi_person_id,
                    %s AS pi_person_uuid,
                    %s
                    %s
                FROM %s p
                %s
                WHERE %s
                """.formatted(
                displayTitleExpr(),
                identifierSelect(PureProjectUris.PROJECT_ID),
                identifierSelect(PureProjectUris.FUNDER_REF),
                displayTypeExpr(),
                piSelect("name"),
                piSelect("person_id"),
                piSelect("person_uuid"),
                managingOrganizationSelect(),
                activeInPureSelect("p"),
                qualified("pure_project"),
                managingOrganizationJoin(),
                predicate
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("PROJECT_NOT_FOUND", "Project was not found");
                }
                ProjectDetailDto dto = new ProjectDetailDto();
                copySummary(mapSummary(rs), dto);
                dto.typeDiscriminator = nullableString(rs, "type_discriminator");
                dto.createdBy = nullableString(rs, "created_by");
                dto.createdDatePure = timestamp(rs, "created_date_pure");
                dto.modifiedBy = nullableString(rs, "modified_by");
                dto.modifiedDatePure = timestamp(rs, "modified_date_pure");
                dto.version = nullableString(rs, "pure_version");
                dto.typeUri = nullableString(rs, "type_uri");
                dto.workflowDescriptionEn = nullableString(rs, "workflow_description_en");
                dto.workflowDescriptionEs = nullableString(rs, "workflow_description_es");
                dto.systemName = nullableString(rs, "system_name");
                long id = dto.id;
                dto.prettyUrlIdentifiers = loadPrettyUrls(connection, id);
                dto.participants = loadParticipants(connection, id);
                dto.identifiers = loadIdentifiers(connection, id);
                dto.descriptions = loadDescriptions(connection, id);
                dto.natureTypes = loadNatureTypes(connection, id);
                dto.organizations = loadOrganizations(connection, id, dto.managingOrganizationUuid);
                return dto;
            }
        } catch (NotFoundException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load project", ex);
        }
    }

    private List<String> loadPrettyUrls(Connection connection, long projectId) throws SQLException {
        if (!tableExists("pure_project_pretty_url_identifier")) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        String sql = "SELECT identifier FROM " + qualified("pure_project_pretty_url_identifier")
                + " WHERE project_id = ? ORDER BY sort_order, id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String value = nullableString(rs, "identifier");
                    if (value != null) {
                        values.add(value);
                    }
                }
            }
        }
        return values;
    }

    private List<ProjectParticipantDto> loadParticipants(Connection connection, long projectId) throws SQLException {
        if (!tableExists("pure_project_participant")) {
            return List.of();
        }
        Map<Long, ProjectParticipantDto> byId = new LinkedHashMap<>();
        String personJoin = tableExists("pure_person")
                ? " LEFT JOIN " + qualified("pure_person") + " per ON per.uuid = pp.person_uuid"
                : "";
        String personIdExpr = tableExists("pure_person")
                ? "COALESCE(pp.person_id, per.id) AS resolved_person_id"
                : "pp.person_id AS resolved_person_id";
        String sql = """
                SELECT pp.id, pp.pure_id, pp.type_discriminator, pp.first_name, pp.last_name,
                       pp.role_uri, pp.role_term_en, pp.role_term_es, pp.association_hidden,
                       pp.person_uuid, %s
                FROM %s pp
                %s
                WHERE pp.project_id = ?
                ORDER BY pp.id
                """.formatted(personIdExpr, qualified("pure_project_participant"), personJoin);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectParticipantDto dto = new ProjectParticipantDto();
                    dto.id = rs.getLong("id");
                    dto.pureId = nullableLong(rs, "pure_id");
                    dto.typeDiscriminator = nullableString(rs, "type_discriminator");
                    dto.firstName = nullableString(rs, "first_name");
                    dto.lastName = nullableString(rs, "last_name");
                    dto.displayName = displayName(dto.firstName, dto.lastName);
                    dto.roleUri = nullableString(rs, "role_uri");
                    dto.roleTermEn = nullableString(rs, "role_term_en");
                    dto.roleTermEs = nullableString(rs, "role_term_es");
                    dto.associationHidden = nullableBoolean(rs, "association_hidden");
                    dto.personUuid = nullableString(rs, "person_uuid");
                    dto.personId = nullableLong(rs, "resolved_person_id");
                    byId.put(dto.id, dto);
                }
            }
        }
        if (byId.isEmpty() || !tableExists("pure_project_participant_organization")) {
            return new ArrayList<>(byId.values());
        }
        List<Long> ids = new ArrayList<>(byId.keySet());
        String orgSql = """
                SELECT participant_id, organization_uuid, organization_system_name
                FROM """ + " " + qualified("pure_project_participant_organization") + """
                
                WHERE participant_id IN (""" + inPlaceholders(ids.size()) + ") ORDER BY sort_order, id";
        try (PreparedStatement statement = connection.prepareStatement(orgSql)) {
            for (int i = 0; i < ids.size(); i++) {
                statement.setLong(i + 1, ids.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectParticipantDto participant = byId.get(rs.getLong("participant_id"));
                    if (participant == null) {
                        continue;
                    }
                    ProjectOrganizationDto org = new ProjectOrganizationDto();
                    org.organizationUuid = nullableString(rs, "organization_uuid");
                    org.organizationSystemName = nullableString(rs, "organization_system_name");
                    participant.organizations.add(org);
                }
            }
        }
        List<ProjectOrganizationDto> catalog = new ArrayList<>();
        for (ProjectParticipantDto participant : byId.values()) {
            catalog.addAll(participant.organizations);
        }
        resolveOrganizationCatalog(connection, catalog);
        return new ArrayList<>(byId.values());
    }

    private List<ProjectIdentifierDto> loadIdentifiers(Connection connection, long projectId) throws SQLException {
        if (!tableExists("pure_project_identifier")) {
            return List.of();
        }
        List<ProjectIdentifierDto> items = new ArrayList<>();
        String sql = """
                SELECT pure_id, type_discriminator, identifier_value, type_uri, type_en, type_es
                FROM """ + " " + qualified("pure_project_identifier") + """
                
                WHERE project_id = ? ORDER BY id""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectIdentifierDto dto = new ProjectIdentifierDto();
                    dto.pureId = nullableLong(rs, "pure_id");
                    dto.typeDiscriminator = nullableString(rs, "type_discriminator");
                    dto.value = nullableString(rs, "identifier_value");
                    dto.typeUri = nullableString(rs, "type_uri");
                    dto.typeEn = nullableString(rs, "type_en");
                    dto.typeEs = nullableString(rs, "type_es");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<ProjectDescriptionDto> loadDescriptions(Connection connection, long projectId) throws SQLException {
        if (!tableExists("pure_project_description")) {
            return List.of();
        }
        List<ProjectDescriptionDto> items = new ArrayList<>();
        String sql = """
                SELECT pure_id, type_uri, type_en, type_es
                FROM """ + " " + qualified("pure_project_description") + """
                
                WHERE project_id = ? ORDER BY id""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectDescriptionDto dto = new ProjectDescriptionDto();
                    dto.pureId = nullableLong(rs, "pure_id");
                    dto.typeUri = nullableString(rs, "type_uri");
                    dto.typeEn = nullableString(rs, "type_en");
                    dto.typeEs = nullableString(rs, "type_es");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<ProjectNatureTypeDto> loadNatureTypes(Connection connection, long projectId) throws SQLException {
        if (!tableExists("pure_project_nature_type")) {
            return List.of();
        }
        List<ProjectNatureTypeDto> items = new ArrayList<>();
        String sql = "SELECT uri, term_en, term_es FROM " + qualified("pure_project_nature_type")
                + " WHERE project_id = ? ORDER BY sort_order, id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectNatureTypeDto dto = new ProjectNatureTypeDto();
                    dto.uri = nullableString(rs, "uri");
                    dto.termEn = nullableString(rs, "term_en");
                    dto.termEs = nullableString(rs, "term_es");
                    items.add(dto);
                }
            }
        }
        return items;
    }

    private List<ProjectOrganizationDto> loadOrganizations(
            Connection connection,
            long projectId,
            String managingUuid
    ) throws SQLException {
        if (!tableExists("pure_project_organization")) {
            return List.of();
        }
        List<ProjectOrganizationDto> items = new ArrayList<>();
        String sql = "SELECT organization_uuid, organization_system_name FROM "
                + qualified("pure_project_organization")
                + " WHERE project_id = ? ORDER BY sort_order, id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectOrganizationDto dto = new ProjectOrganizationDto();
                    dto.organizationUuid = nullableString(rs, "organization_uuid");
                    dto.organizationSystemName = nullableString(rs, "organization_system_name");
                    dto.managing = dto.organizationUuid != null && dto.organizationUuid.equals(managingUuid);
                    items.add(dto);
                }
            }
        }
        resolveOrganizationCatalog(connection, items);
        return items;
    }

    private ProjectSummaryDto mapSummary(ResultSet rs) throws SQLException {
        ProjectSummaryDto dto = new ProjectSummaryDto();
        dto.id = rs.getLong("id");
        dto.pureId = nullableLong(rs, "pure_id");
        dto.uuid = nullableString(rs, "uuid");
        dto.title = nullableString(rs, "title");
        dto.titleEs = nullableString(rs, "title_es");
        dto.titleEn = nullableString(rs, "title_en");
        dto.acronym = nullableString(rs, "acronym");
        dto.projectId = nullableString(rs, "project_id");
        dto.funderReference = nullableString(rs, "funder_reference");
        dto.type = nullableString(rs, "type");
        dto.typeEs = nullableString(rs, "type_es");
        dto.typeEn = nullableString(rs, "type_en");
        dto.periodStartDate = localDate(rs, "period_start_date");
        dto.periodEndDate = localDate(rs, "period_end_date");
        dto.principalInvestigatorName = nullableString(rs, "pi_name");
        dto.principalInvestigatorPersonId = nullableLong(rs, "pi_person_id");
        dto.principalInvestigatorPersonUuid = nullableString(rs, "pi_person_uuid");
        dto.managingOrganizationUuid = nullableString(rs, "managing_organization_uuid");
        dto.managingOrganizationId = hasColumn(rs, "managing_organization_id")
                ? nullableLong(rs, "managing_organization_id") : null;
        dto.managingOrganizationName = hasColumn(rs, "managing_organization_name")
                ? nullableString(rs, "managing_organization_name") : null;
        dto.managingOrganizationNameEs = hasColumn(rs, "managing_organization_name_es")
                ? nullableString(rs, "managing_organization_name_es") : null;
        dto.managingOrganizationNameEn = hasColumn(rs, "managing_organization_name_en")
                ? nullableString(rs, "managing_organization_name_en") : null;
        dto.managingOrganizationResolved = dto.managingOrganizationId != null;
        dto.workflowStep = nullableString(rs, "workflow_step");
        dto.workflowEs = nullableString(rs, "workflow_description_es");
        dto.workflowEn = nullableString(rs, "workflow_description_en");
        dto.visibilityKey = nullableString(rs, "visibility_key");
        dto.visibilityEs = nullableString(rs, "visibility_es");
        dto.visibilityEn = nullableString(rs, "visibility_en");
        dto.portalUrl = nullableString(rs, "portal_url");
        dto.activeInPure = columnExists("pure_project", "active_in_pure")
                ? nullableBoolean(rs, "active_in_pure")
                : Boolean.TRUE;
        return dto;
    }

    private SqlParts buildWhere(ProjectQuery query) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE ");
        String presence = query.presence() == null ? "ACTIVE" : query.presence();
        if ("ALL".equals(presence) || !columnExists("pure_project", "active_in_pure")) {
            where.append("1=1");
        } else if ("INACTIVE".equals(presence)) {
            where.append(" COALESCE(p.active_in_pure, 1) = 0");
        } else {
            where.append(activeInPurePredicate("pure_project", "p"));
        }
        if (query.search() != null && !query.search().isBlank()) {
            String raw = query.search().trim();
            String like = likeContains(raw);
            Long pureId = parseNumericId(raw);
            where.append(" AND (");
            where.append(" p.uuid LIKE ? ESCAPE '!'");
            params.add(like);
            if (pureId != null) {
                where.append(" OR p.pure_id = ?");
                params.add(pureId);
            }
            where.append(" OR ").append(displayTitleExpr()).append(" LIKE ? ESCAPE '!'");
            params.add(like);
            where.append(" OR p.acronym LIKE ? ESCAPE '!'");
            params.add(like);
            if (tableExists("pure_project_identifier")) {
                where.append("""
                         OR EXISTS (
                            SELECT 1 FROM """ + " " + qualified("pure_project_identifier") + """
                             i WHERE i.project_id = p.id AND i.identifier_value LIKE ? ESCAPE '!'
                         )
                        """);
                params.add(like);
            }
            if (tableExists("pure_project_participant")) {
                where.append("""
                         OR EXISTS (
                            SELECT 1 FROM """ + " " + qualified("pure_project_participant") + """
                             pp WHERE pp.project_id = p.id
                              AND (
                                  pp.first_name LIKE ? ESCAPE '!'
                                  OR pp.last_name LIKE ? ESCAPE '!'
                                  OR TRIM(CONCAT_WS(' ', pp.first_name, pp.last_name)) LIKE ? ESCAPE '!'
                                  OR pp.person_uuid LIKE ? ESCAPE '!'
                              )
                         )
                        """);
                params.add(like);
                params.add(like);
                params.add(like);
                params.add(like);
            }
            where.append(")");
        }
        if (query.typeUri() != null && !query.typeUri().isBlank()) {
            where.append(" AND p.type_uri = ?");
            params.add(query.typeUri().trim());
        }
        if (query.workflowStep() != null && !query.workflowStep().isBlank()) {
            where.append(" AND p.workflow_step = ?");
            params.add(query.workflowStep().trim());
        }
        if (query.visibilityKey() != null && !query.visibilityKey().isBlank()) {
            where.append(" AND p.visibility_key = ?");
            params.add(query.visibilityKey().trim());
        }
        if (query.startYear() != null) {
            where.append(" AND YEAR(p.period_start_date) = ?");
            params.add(query.startYear());
        }
        return new SqlParts(where.toString(), params);
    }

    private String orderBy(String sort, String direction) {
        boolean desc = "DESC".equalsIgnoreCase(direction);
        String order = desc ? " DESC" : " ASC";
        String field = sort == null ? "title" : sort.toLowerCase(Locale.ROOT);
        return switch (field) {
            case "pureid" -> " ORDER BY p.pure_id" + order + ", p.id ASC";
            case "acronym" -> " ORDER BY " + orderByNullsLast("p.acronym", desc) + ", title ASC, p.id ASC";
            case "projectid" -> " ORDER BY " + orderByNullsLast("project_id", desc) + ", title ASC, p.id ASC";
            case "type" -> " ORDER BY type" + order + ", p.id ASC";
            case "periodstart" -> " ORDER BY " + orderByNullsLast("p.period_start_date", desc) + ", title ASC, p.id ASC";
            case "periodend" -> " ORDER BY " + orderByNullsLast("p.period_end_date", desc) + ", title ASC, p.id ASC";
            case "principalinvestigator" -> " ORDER BY " + orderByNullsLast("pi_name", desc) + ", title ASC, p.id ASC";
            case "workflow" -> " ORDER BY p.workflow_step" + order + ", p.id ASC";
            default -> " ORDER BY title" + order + ", p.id ASC";
        };
    }

    private void requireTable() {
        if (!tableExists("pure_project")) {
            throw new DatabaseException("Table pure_project is not available", null);
        }
    }

    private String managingOrganizationJoin() {
        if (!tableExists("pure_organization")) {
            return "";
        }
        return " LEFT JOIN " + qualified("pure_organization") + " mo ON mo.uuid = p.managing_organization_uuid";
    }

    private String managingOrganizationSelect() {
        if (!tableExists("pure_organization")) {
            return """
                    CAST(NULL AS UNSIGNED) AS managing_organization_id,
                    CAST(NULL AS CHAR) AS managing_organization_name,
                    CAST(NULL AS CHAR) AS managing_organization_name_es,
                    CAST(NULL AS CHAR) AS managing_organization_name_en,
                    """;
        }
        return """
                mo.id AS managing_organization_id,
                COALESCE(NULLIF(TRIM(mo.name_es), ''), NULLIF(TRIM(mo.name_en), ''), mo.uuid) AS managing_organization_name,
                mo.name_es AS managing_organization_name_es,
                mo.name_en AS managing_organization_name_en,
                """;
    }

    private void resolveOrganizationCatalog(Connection connection, List<ProjectOrganizationDto> items)
            throws SQLException {
        if (items == null || items.isEmpty() || !tableExists("pure_organization")) {
            return;
        }
        List<String> uuids = new ArrayList<>();
        for (ProjectOrganizationDto item : items) {
            if (item.organizationUuid != null && !item.organizationUuid.isBlank() && !uuids.contains(item.organizationUuid)) {
                uuids.add(item.organizationUuid);
            }
        }
        if (uuids.isEmpty()) {
            return;
        }
        Map<String, ProjectOrganizationDto> byUuid = new HashMap<>();
        String sql = """
                SELECT id, uuid, name_es, name_en, type_es, type_en, active_in_pure
                FROM """ + " " + qualified("pure_organization") + """
                
                WHERE uuid IN (""" + inPlaceholders(uuids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < uuids.size(); i++) {
                statement.setString(i + 1, uuids.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectOrganizationDto catalog = new ProjectOrganizationDto();
                    catalog.organizationId = nullableLong(rs, "id");
                    catalog.organizationUuid = nullableString(rs, "uuid");
                    catalog.nameEs = nullableString(rs, "name_es");
                    catalog.nameEn = nullableString(rs, "name_en");
                    catalog.typeEs = nullableString(rs, "type_es");
                    catalog.typeEn = nullableString(rs, "type_en");
                    catalog.activeInPure = nullableBoolean(rs, "active_in_pure");
                    catalog.resolved = catalog.organizationId != null;
                    byUuid.put(catalog.organizationUuid, catalog);
                }
            }
        }
        for (ProjectOrganizationDto item : items) {
            ProjectOrganizationDto catalog = item.organizationUuid == null ? null : byUuid.get(item.organizationUuid);
            if (catalog == null) {
                item.resolved = false;
                continue;
            }
            item.organizationId = catalog.organizationId;
            item.nameEs = catalog.nameEs;
            item.nameEn = catalog.nameEn;
            item.typeEs = catalog.typeEs;
            item.typeEn = catalog.typeEn;
            item.activeInPure = catalog.activeInPure;
            item.resolved = true;
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
        if (!columnExists("pure_project", "active_in_pure")) {
            return "CAST(1 AS UNSIGNED) AS active_in_pure";
        }
        return alias + ".active_in_pure";
    }

    private static String displayTitleExpr() {
        return "COALESCE(NULLIF(TRIM(p.title_es), ''), NULLIF(TRIM(p.title_en), ''), NULLIF(TRIM(p.acronym), ''))";
    }

    private static String displayTypeExpr() {
        return "COALESCE(NULLIF(TRIM(p.type_es), ''), NULLIF(TRIM(p.type_en), ''))";
    }

    private String identifierSelect(String typeUri) {
        if (!tableExists("pure_project_identifier")) {
            return "CAST(NULL AS CHAR)";
        }
        return "(SELECT i.identifier_value FROM " + qualified("pure_project_identifier")
                + " i WHERE i.project_id = p.id AND i.type_uri = '" + typeUri + "' ORDER BY i.id LIMIT 1)";
    }

    private String identifierExistsSql(String typeUri) {
        if (!tableExists("pure_project_identifier")) {
            return "0";
        }
        return "EXISTS (SELECT 1 FROM " + qualified("pure_project_identifier")
                + " i WHERE i.project_id = p.id AND i.type_uri = '" + typeUri + "')";
    }

    private String piSelect(String field) {
        if (!tableExists("pure_project_participant")) {
            return "CAST(NULL AS CHAR)";
        }
        String personJoin = tableExists("pure_person")
                ? " LEFT JOIN " + qualified("pure_person") + " per ON per.uuid = pp.person_uuid"
                : "";
        String expr = switch (field) {
            case "name" -> "NULLIF(TRIM(CONCAT_WS(' ', pp.first_name, pp.last_name)), '')";
            case "person_id" -> tableExists("pure_person")
                    ? "COALESCE(pp.person_id, per.id)"
                    : "pp.person_id";
            case "person_uuid" -> "pp.person_uuid";
            default -> "NULL";
        };
        return "(SELECT " + expr + " FROM " + qualified("pure_project_participant") + " pp"
                + personJoin
                + " WHERE pp.project_id = p.id AND pp.role_uri = '" + PureProjectUris.PI_ROLE + "'"
                + " ORDER BY pp.id LIMIT 1)";
    }

    private String piExistsSql() {
        if (!tableExists("pure_project_participant")) {
            return "0";
        }
        return "EXISTS (SELECT 1 FROM " + qualified("pure_project_participant")
                + " pp WHERE pp.project_id = p.id AND pp.role_uri = '" + PureProjectUris.PI_ROLE + "'"
                + " AND pp.person_uuid IS NOT NULL AND TRIM(pp.person_uuid) <> '')";
    }

    private static void copySummary(ProjectSummaryDto source, ProjectDetailDto target) {
        target.id = source.id;
        target.pureId = source.pureId;
        target.uuid = source.uuid;
        target.title = source.title;
        target.titleEs = source.titleEs;
        target.titleEn = source.titleEn;
        target.acronym = source.acronym;
        target.projectId = source.projectId;
        target.funderReference = source.funderReference;
        target.type = source.type;
        target.typeEs = source.typeEs;
        target.typeEn = source.typeEn;
        target.periodStartDate = source.periodStartDate;
        target.periodEndDate = source.periodEndDate;
        target.principalInvestigatorName = source.principalInvestigatorName;
        target.principalInvestigatorPersonId = source.principalInvestigatorPersonId;
        target.principalInvestigatorPersonUuid = source.principalInvestigatorPersonUuid;
        target.managingOrganizationUuid = source.managingOrganizationUuid;
        target.workflowStep = source.workflowStep;
        target.workflowEs = source.workflowEs;
        target.workflowEn = source.workflowEn;
        target.visibilityKey = source.visibilityKey;
        target.visibilityEs = source.visibilityEs;
        target.visibilityEn = source.visibilityEn;
        target.activeInPure = source.activeInPure;
        target.portalUrl = source.portalUrl;
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
        String full = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return full.isEmpty() ? null : full;
    }

    private static boolean looksLikeUuid(String raw) {
        return raw != null && raw.trim().matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private record SqlParts(String where, List<Object> params) {
    }
}
