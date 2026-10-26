package cl.uss.ca.repository;

import cl.uss.ca.external.pure.model.ProjectDescriptionData;
import cl.uss.ca.external.pure.model.ProjectIdentifierData;
import cl.uss.ca.external.pure.model.ProjectNatureTypeData;
import cl.uss.ca.external.pure.model.ProjectOrganizationData;
import cl.uss.ca.external.pure.model.ProjectParticipantData;
import cl.uss.ca.external.pure.model.PureProject;
import cl.uss.ca.sync.diff.AggregateOutcome;
import cl.uss.ca.sync.diff.ProjectSnapshot;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ProjectSyncRepository extends JdbcRepository {

    public Connection begin() throws SQLException {
        Connection connection = connection();
        connection.setAutoCommit(false);
        return connection;
    }

    public Map<String, ProjectSnapshot> loadSnapshots(Connection connection, Collection<String> uuids)
            throws SQLException {
        Map<String, ProjectSnapshot> byUuid = new LinkedHashMap<>();
        if (uuids == null || uuids.isEmpty() || !tableExists("pure_project")) {
            return byUuid;
        }
        List<String> values = new ArrayList<>(uuids);
        String sql = """
                SELECT id, uuid, pure_id, type_discriminator, title_es, title_en, acronym,
                       created_by, created_date_pure, modified_by, modified_date_pure, portal_url, pure_version,
                       period_start_date, period_end_date, type_uri, type_en, type_es,
                       workflow_step, workflow_description_en, workflow_description_es,
                       visibility_key, visibility_en, visibility_es, managing_organization_uuid, system_name
                """ + (columnExists("pure_project", "active_in_pure") ? ", active_in_pure" : "") + """
                
                FROM """ + " " + qualified("pure_project") + """
                
                WHERE uuid IN (""" + inPlaceholders(values.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                statement.setString(i + 1, values.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectSnapshot snapshot = mapParent(rs);
                    byUuid.put(snapshot.uuid, snapshot);
                }
            }
        } catch (SQLException ex) {
            if (isUnknownColumn(ex)) {
                return byUuid;
            }
            throw ex;
        }
        if (byUuid.isEmpty()) {
            return byUuid;
        }
        List<Long> ids = byUuid.values().stream().map(item -> item.id).toList();
        if (tableExists("pure_project_pretty_url_identifier")) {
            loadPrettyUrls(connection, ids, byUuid);
        }
        if (tableExists("pure_project_participant")) {
            loadParticipants(connection, ids, byUuid);
        }
        if (tableExists("pure_project_description")) {
            loadDescriptions(connection, ids, byUuid);
        }
        if (tableExists("pure_project_identifier")) {
            loadIdentifiers(connection, ids, byUuid);
        }
        if (tableExists("pure_project_nature_type")) {
            loadNatureTypes(connection, ids, byUuid);
        }
        if (tableExists("pure_project_organization")) {
            loadOrganizations(connection, ids, byUuid);
        }
        return byUuid;
    }

    public Long apply(Connection connection, PureProject project, AggregateOutcome outcome) throws SQLException {
        Long projectId = outcome.localId;
        if (outcome.persistParent() || projectId == null) {
            projectId = upsertProject(connection, project);
        }
        if (outcome.persistChildren()) {
            replacePrettyUrls(connection, projectId, project.getPrettyUrlIdentifiers());
            replaceParticipants(connection, projectId, project.getParticipants());
            replaceDescriptions(connection, projectId, project.getDescriptions());
            replaceIdentifiers(connection, projectId, project.getIdentifiers());
            replaceNatureTypes(connection, projectId, project.getNatureTypes());
            replaceOrganizations(connection, projectId, project.getOrganizations());
        }
        return projectId;
    }

    public int resolvePersonReferences(Connection connection) throws SQLException {
        if (!tableExists("pure_project_participant") || !tableExists("pure_person")
                || !columnExists("pure_project_participant", "person_uuid")) {
            return 0;
        }
        String sql = """
                UPDATE """ + " " + qualified("pure_project_participant") + """
                 pp
                JOIN """ + " " + qualified("pure_person") + """
                 p ON p.uuid = pp.person_uuid
                SET pp.person_id = p.id
                WHERE pp.person_uuid IS NOT NULL
                  AND (pp.person_id IS NULL OR pp.person_id <> p.id)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            return statement.executeUpdate();
        }
    }

    private Long upsertProject(Connection connection, PureProject project) throws SQLException {
        String sql = """
                INSERT INTO """ + " " + qualified("pure_project") + """
                 (
                    uuid, pure_id, type_discriminator, title_es, title_en, acronym,
                    created_by, created_date_pure, modified_by, modified_date_pure, portal_url, pure_version,
                    period_start_date, period_end_date, type_uri, type_en, type_es,
                    workflow_step, workflow_description_en, workflow_description_es,
                    visibility_key, visibility_en, visibility_es, managing_organization_uuid, system_name
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    id = LAST_INSERT_ID(id),
                    pure_id = VALUES(pure_id),
                    type_discriminator = VALUES(type_discriminator),
                    title_es = VALUES(title_es),
                    title_en = VALUES(title_en),
                    acronym = VALUES(acronym),
                    created_by = VALUES(created_by),
                    created_date_pure = VALUES(created_date_pure),
                    modified_by = VALUES(modified_by),
                    modified_date_pure = VALUES(modified_date_pure),
                    portal_url = VALUES(portal_url),
                    pure_version = VALUES(pure_version),
                    period_start_date = VALUES(period_start_date),
                    period_end_date = VALUES(period_end_date),
                    type_uri = VALUES(type_uri),
                    type_en = VALUES(type_en),
                    type_es = VALUES(type_es),
                    workflow_step = VALUES(workflow_step),
                    workflow_description_en = VALUES(workflow_description_en),
                    workflow_description_es = VALUES(workflow_description_es),
                    visibility_key = VALUES(visibility_key),
                    visibility_en = VALUES(visibility_en),
                    visibility_es = VALUES(visibility_es),
                    managing_organization_uuid = VALUES(managing_organization_uuid),
                    system_name = VALUES(system_name)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, project.getUuid());
            setLong(statement, 2, project.getPureId());
            statement.setString(3, project.getTypeDiscriminator());
            statement.setString(4, project.getTitleEs());
            statement.setString(5, project.getTitleEn());
            statement.setString(6, project.getAcronym());
            statement.setString(7, project.getCreatedBy());
            setTimestamp(statement, 8, project.getCreatedDatePure());
            statement.setString(9, project.getModifiedBy());
            setTimestamp(statement, 10, project.getModifiedDatePure());
            statement.setString(11, project.getPortalUrl());
            statement.setString(12, project.getVersion());
            setDate(statement, 13, project.getPeriodStartDate());
            setDate(statement, 14, project.getPeriodEndDate());
            statement.setString(15, project.getTypeUri());
            statement.setString(16, project.getTypeEn());
            statement.setString(17, project.getTypeEs());
            statement.setString(18, project.getWorkflowStep());
            statement.setString(19, project.getWorkflowDescriptionEn());
            statement.setString(20, project.getWorkflowDescriptionEs());
            statement.setString(21, project.getVisibilityKey());
            statement.setString(22, project.getVisibilityEn());
            statement.setString(23, project.getVisibilityEs());
            statement.setString(24, project.getManagingOrganizationUuid());
            statement.setString(25, project.getSystemName());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next() && keys.getLong(1) > 0) {
                    return keys.getLong(1);
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM " + qualified("pure_project") + " WHERE uuid = ?")) {
            statement.setString(1, project.getUuid());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Unable to persist project uuid=" + project.getUuid());
    }

    private void replacePrettyUrls(Connection connection, Long projectId, List<String> values) throws SQLException {
        if (!tableExists("pure_project_pretty_url_identifier")) {
            return;
        }
        executeDelete(connection, "pure_project_pretty_url_identifier", projectId);
        if (values == null || values.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + qualified("pure_project_pretty_url_identifier")
                + " (project_id, identifier, sort_order) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int order = 0;
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                statement.setLong(1, projectId);
                statement.setString(2, value.trim());
                statement.setInt(3, order++);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceParticipants(Connection connection, Long projectId, List<ProjectParticipantData> participants)
            throws SQLException {
        if (!tableExists("pure_project_participant")) {
            return;
        }
        if (tableExists("pure_project_participant_organization")) {
            executeDeleteByParent(connection, "pure_project_participant_organization", "pure_project_participant", projectId);
        }
        executeDelete(connection, "pure_project_participant", projectId);
        if (participants == null || participants.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO """ + " " + qualified("pure_project_participant") + """
                 (project_id, pure_id, type_discriminator, first_name, last_name, role_uri, role_term_en, role_term_es,
                  association_hidden, person_uuid)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (ProjectParticipantData participant : participants) {
                statement.setLong(1, projectId);
                setLong(statement, 2, participant.getPureId());
                statement.setString(3, participant.getTypeDiscriminator());
                statement.setString(4, participant.getFirstName());
                statement.setString(5, participant.getLastName());
                statement.setString(6, participant.getRoleUri());
                statement.setString(7, participant.getRoleTermEn());
                statement.setString(8, participant.getRoleTermEs());
                setBoolean(statement, 9, participant.getAssociationHidden());
                statement.setString(10, participant.getPersonUuid());
                statement.executeUpdate();
                Long participantId = null;
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        participantId = keys.getLong(1);
                    }
                }
                if (participantId != null) {
                    insertParticipantOrganizations(connection, participantId, participant.getOrganizations());
                }
            }
        }
    }

    private void insertParticipantOrganizations(
            Connection connection,
            Long participantId,
            List<ProjectOrganizationData> organizations
    ) throws SQLException {
        if (!tableExists("pure_project_participant_organization") || organizations == null || organizations.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + qualified("pure_project_participant_organization")
                + " (participant_id, organization_uuid, organization_system_name, sort_order) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int order = 0;
            for (ProjectOrganizationData organization : organizations) {
                if (organization.getOrganizationUuid() == null) {
                    continue;
                }
                statement.setLong(1, participantId);
                statement.setString(2, organization.getOrganizationUuid());
                statement.setString(3, organization.getOrganizationSystemName());
                statement.setInt(4, order++);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceDescriptions(Connection connection, Long projectId, List<ProjectDescriptionData> descriptions)
            throws SQLException {
        if (!tableExists("pure_project_description")) {
            return;
        }
        executeDelete(connection, "pure_project_description", projectId);
        if (descriptions == null || descriptions.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + qualified("pure_project_description")
                + " (project_id, pure_id, type_uri, type_en, type_es) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ProjectDescriptionData description : descriptions) {
                statement.setLong(1, projectId);
                setLong(statement, 2, description.getPureId());
                statement.setString(3, description.getTypeUri());
                statement.setString(4, description.getTypeEn());
                statement.setString(5, description.getTypeEs());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceIdentifiers(Connection connection, Long projectId, List<ProjectIdentifierData> identifiers)
            throws SQLException {
        if (!tableExists("pure_project_identifier")) {
            return;
        }
        executeDelete(connection, "pure_project_identifier", projectId);
        if (identifiers == null || identifiers.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO """ + " " + qualified("pure_project_identifier") + """
                 (project_id, pure_id, type_discriminator, identifier_value, type_uri, type_en, type_es)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ProjectIdentifierData identifier : identifiers) {
                statement.setLong(1, projectId);
                setLong(statement, 2, identifier.getPureId());
                statement.setString(3, identifier.getTypeDiscriminator());
                statement.setString(4, identifier.getValue());
                statement.setString(5, identifier.getTypeUri());
                statement.setString(6, identifier.getTypeEn());
                statement.setString(7, identifier.getTypeEs());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceNatureTypes(Connection connection, Long projectId, List<ProjectNatureTypeData> natures)
            throws SQLException {
        if (!tableExists("pure_project_nature_type")) {
            return;
        }
        executeDelete(connection, "pure_project_nature_type", projectId);
        if (natures == null || natures.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + qualified("pure_project_nature_type")
                + " (project_id, uri, term_en, term_es, sort_order) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int order = 0;
            for (ProjectNatureTypeData nature : natures) {
                if (nature.getUri() == null) {
                    continue;
                }
                statement.setLong(1, projectId);
                statement.setString(2, nature.getUri());
                statement.setString(3, nature.getTermEn());
                statement.setString(4, nature.getTermEs());
                statement.setInt(5, order++);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceOrganizations(Connection connection, Long projectId, List<ProjectOrganizationData> organizations)
            throws SQLException {
        if (!tableExists("pure_project_organization")) {
            return;
        }
        executeDelete(connection, "pure_project_organization", projectId);
        if (organizations == null || organizations.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + qualified("pure_project_organization")
                + " (project_id, organization_uuid, organization_system_name, sort_order) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int order = 0;
            for (ProjectOrganizationData organization : organizations) {
                if (organization.getOrganizationUuid() == null) {
                    continue;
                }
                statement.setLong(1, projectId);
                statement.setString(2, organization.getOrganizationUuid());
                statement.setString(3, organization.getOrganizationSystemName());
                statement.setInt(4, order++);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void loadPrettyUrls(Connection connection, List<Long> ids, Map<String, ProjectSnapshot> byUuid)
            throws SQLException {
        Map<Long, ProjectSnapshot> byId = byId(byUuid);
        String sql = "SELECT project_id, identifier FROM " + qualified("pure_project_pretty_url_identifier")
                + " WHERE project_id IN (" + inPlaceholders(ids.size()) + ") ORDER BY sort_order, id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectSnapshot snapshot = byId.get(rs.getLong("project_id"));
                    if (snapshot != null) {
                        snapshot.prettyUrlIdentifiers.add(rs.getString("identifier"));
                    }
                }
            }
        }
    }

    private void loadParticipants(Connection connection, List<Long> ids, Map<String, ProjectSnapshot> byUuid)
            throws SQLException {
        Map<Long, ProjectSnapshot> byId = byId(byUuid);
        Map<Long, ProjectParticipantData> byParticipantId = new LinkedHashMap<>();
        String sql = """
                SELECT id, project_id, pure_id, type_discriminator, first_name, last_name, role_uri, role_term_en,
                       role_term_es, association_hidden, person_uuid
                FROM """ + " " + qualified("pure_project_participant") + """
                
                WHERE project_id IN (""" + inPlaceholders(ids.size()) + ") ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectSnapshot snapshot = byId.get(rs.getLong("project_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    ProjectParticipantData data = new ProjectParticipantData();
                    data.setPureId(nullableLong(rs, "pure_id"));
                    data.setTypeDiscriminator(nullableString(rs, "type_discriminator"));
                    data.setFirstName(nullableString(rs, "first_name"));
                    data.setLastName(nullableString(rs, "last_name"));
                    data.setRoleUri(nullableString(rs, "role_uri"));
                    data.setRoleTermEn(nullableString(rs, "role_term_en"));
                    data.setRoleTermEs(nullableString(rs, "role_term_es"));
                    data.setAssociationHidden(nullableBoolean(rs, "association_hidden"));
                    data.setPersonUuid(nullableString(rs, "person_uuid"));
                    snapshot.participants.add(data);
                    byParticipantId.put(rs.getLong("id"), data);
                }
            }
        }
        if (byParticipantId.isEmpty() || !tableExists("pure_project_participant_organization")) {
            return;
        }
        List<Long> participantIds = new ArrayList<>(byParticipantId.keySet());
        String orgSql = """
                SELECT participant_id, organization_uuid, organization_system_name
                FROM """ + " " + qualified("pure_project_participant_organization") + """
                
                WHERE participant_id IN (""" + inPlaceholders(participantIds.size()) + ") ORDER BY sort_order, id";
        try (PreparedStatement statement = connection.prepareStatement(orgSql)) {
            bindLongs(statement, participantIds);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectParticipantData participant = byParticipantId.get(rs.getLong("participant_id"));
                    if (participant == null) {
                        continue;
                    }
                    ProjectOrganizationData org = new ProjectOrganizationData();
                    org.setOrganizationUuid(nullableString(rs, "organization_uuid"));
                    org.setOrganizationSystemName(nullableString(rs, "organization_system_name"));
                    participant.getOrganizations().add(org);
                }
            }
        }
    }

    private void loadDescriptions(Connection connection, List<Long> ids, Map<String, ProjectSnapshot> byUuid)
            throws SQLException {
        Map<Long, ProjectSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT project_id, pure_id, type_uri, type_en, type_es
                FROM """ + " " + qualified("pure_project_description") + """
                
                WHERE project_id IN (""" + inPlaceholders(ids.size()) + ") ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectSnapshot snapshot = byId.get(rs.getLong("project_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    ProjectDescriptionData data = new ProjectDescriptionData();
                    data.setPureId(nullableLong(rs, "pure_id"));
                    data.setTypeUri(nullableString(rs, "type_uri"));
                    data.setTypeEn(nullableString(rs, "type_en"));
                    data.setTypeEs(nullableString(rs, "type_es"));
                    snapshot.descriptions.add(data);
                }
            }
        }
    }

    private void loadIdentifiers(Connection connection, List<Long> ids, Map<String, ProjectSnapshot> byUuid)
            throws SQLException {
        Map<Long, ProjectSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT project_id, pure_id, type_discriminator, identifier_value, type_uri, type_en, type_es
                FROM """ + " " + qualified("pure_project_identifier") + """
                
                WHERE project_id IN (""" + inPlaceholders(ids.size()) + ") ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectSnapshot snapshot = byId.get(rs.getLong("project_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    ProjectIdentifierData data = new ProjectIdentifierData();
                    data.setPureId(nullableLong(rs, "pure_id"));
                    data.setTypeDiscriminator(nullableString(rs, "type_discriminator"));
                    data.setValue(nullableString(rs, "identifier_value"));
                    data.setTypeUri(nullableString(rs, "type_uri"));
                    data.setTypeEn(nullableString(rs, "type_en"));
                    data.setTypeEs(nullableString(rs, "type_es"));
                    snapshot.identifiers.add(data);
                }
            }
        }
    }

    private void loadNatureTypes(Connection connection, List<Long> ids, Map<String, ProjectSnapshot> byUuid)
            throws SQLException {
        Map<Long, ProjectSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT project_id, uri, term_en, term_es
                FROM """ + " " + qualified("pure_project_nature_type") + """
                
                WHERE project_id IN (""" + inPlaceholders(ids.size()) + ") ORDER BY sort_order, id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectSnapshot snapshot = byId.get(rs.getLong("project_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    ProjectNatureTypeData data = new ProjectNatureTypeData();
                    data.setUri(nullableString(rs, "uri"));
                    data.setTermEn(nullableString(rs, "term_en"));
                    data.setTermEs(nullableString(rs, "term_es"));
                    snapshot.natureTypes.add(data);
                }
            }
        }
    }

    private void loadOrganizations(Connection connection, List<Long> ids, Map<String, ProjectSnapshot> byUuid)
            throws SQLException {
        Map<Long, ProjectSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT project_id, organization_uuid, organization_system_name
                FROM """ + " " + qualified("pure_project_organization") + """
                
                WHERE project_id IN (""" + inPlaceholders(ids.size()) + ") ORDER BY sort_order, id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ProjectSnapshot snapshot = byId.get(rs.getLong("project_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    ProjectOrganizationData data = new ProjectOrganizationData();
                    data.setOrganizationUuid(nullableString(rs, "organization_uuid"));
                    data.setOrganizationSystemName(nullableString(rs, "organization_system_name"));
                    snapshot.organizations.add(data);
                }
            }
        }
    }

    private ProjectSnapshot mapParent(ResultSet rs) throws SQLException {
        ProjectSnapshot snapshot = new ProjectSnapshot();
        snapshot.id = rs.getLong("id");
        snapshot.uuid = rs.getString("uuid");
        snapshot.pureId = nullableLong(rs, "pure_id");
        snapshot.typeDiscriminator = nullableString(rs, "type_discriminator");
        snapshot.titleEs = nullableString(rs, "title_es");
        snapshot.titleEn = nullableString(rs, "title_en");
        snapshot.acronym = nullableString(rs, "acronym");
        snapshot.createdBy = nullableString(rs, "created_by");
        snapshot.createdDatePure = offset(rs.getTimestamp("created_date_pure"));
        snapshot.modifiedBy = nullableString(rs, "modified_by");
        snapshot.modifiedDatePure = offset(rs.getTimestamp("modified_date_pure"));
        snapshot.portalUrl = nullableString(rs, "portal_url");
        snapshot.version = nullableString(rs, "pure_version");
        snapshot.periodStartDate = localDate(rs.getDate("period_start_date"));
        snapshot.periodEndDate = localDate(rs.getDate("period_end_date"));
        snapshot.typeUri = nullableString(rs, "type_uri");
        snapshot.typeEn = nullableString(rs, "type_en");
        snapshot.typeEs = nullableString(rs, "type_es");
        snapshot.workflowStep = nullableString(rs, "workflow_step");
        snapshot.workflowDescriptionEn = nullableString(rs, "workflow_description_en");
        snapshot.workflowDescriptionEs = nullableString(rs, "workflow_description_es");
        snapshot.visibilityKey = nullableString(rs, "visibility_key");
        snapshot.visibilityEn = nullableString(rs, "visibility_en");
        snapshot.visibilityEs = nullableString(rs, "visibility_es");
        snapshot.managingOrganizationUuid = nullableString(rs, "managing_organization_uuid");
        snapshot.systemName = nullableString(rs, "system_name");
        if (hasColumn(rs, "active_in_pure")) {
            snapshot.activeInPure = nullableBoolean(rs, "active_in_pure");
        }
        return snapshot;
    }

    private void executeDelete(Connection connection, String table, Long projectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + qualified(table) + " WHERE project_id = ?")) {
            statement.setLong(1, projectId);
            statement.executeUpdate();
        }
    }

    private void executeDeleteByParent(Connection connection, String child, String parent, Long projectId)
            throws SQLException {
        String sql = "DELETE FROM " + qualified(child)
                + " WHERE participant_id IN (SELECT id FROM " + qualified(parent) + " WHERE project_id = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.executeUpdate();
        }
    }

    private static Map<Long, ProjectSnapshot> byId(Map<String, ProjectSnapshot> byUuid) {
        Map<Long, ProjectSnapshot> byId = new LinkedHashMap<>();
        for (ProjectSnapshot snapshot : byUuid.values()) {
            byId.put(snapshot.id, snapshot);
        }
        return byId;
    }

    private static void bindLongs(PreparedStatement statement, List<Long> ids) throws SQLException {
        for (int i = 0; i < ids.size(); i++) {
            statement.setLong(i + 1, ids.get(i));
        }
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void setBoolean(PreparedStatement statement, int index, Boolean value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TINYINT);
        } else {
            statement.setBoolean(index, value);
        }
    }

    private static void setTimestamp(PreparedStatement statement, int index, OffsetDateTime value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value.toInstant()));
        }
    }

    private static void setDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(value));
        }
    }

    private static OffsetDateTime offset(Timestamp value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private static LocalDate localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private static boolean isUnknownColumn(SQLException ex) {
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unknown column");
    }
}
