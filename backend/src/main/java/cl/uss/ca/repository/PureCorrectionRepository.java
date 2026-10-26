package cl.uss.ca.repository;

import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.sync.PureCorrectionTarget;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@ApplicationScoped
public class PureCorrectionRepository extends JdbcRepository {

    public PureCorrectionTarget requirePerson(long personId) {
        String sql = """
                SELECT id, uuid, pure_id, first_name, last_name
                FROM """ + " " + qualified("pure_person") + """
                
                WHERE id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("PERSON_NOT_FOUND", "Person was not found");
                }
                PureCorrectionTarget target = new PureCorrectionTarget();
                target.localParentId = rs.getLong("id");
                target.parentPureId = nullableLong(rs, "pure_id");
                target.parentUuid = rs.getString("uuid");
                String first = rs.getString("first_name");
                String last = rs.getString("last_name");
                target.parentName = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
                return target;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load person for correction", ex);
        }
    }

    public PureCorrectionTarget loadPersonIdentifier(long personId, long identifierPureId) {
        PureCorrectionTarget person = requirePerson(personId);
        if (!tableExists("pure_person_identifiers")) {
            return person;
        }
        String sql = """
                SELECT pure_id, identifier_type, identifier_value
                FROM """ + " " + qualified("pure_person_identifiers") + """
                
                WHERE person_id = ? AND pure_id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            statement.setLong(2, identifierPureId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    person.childPureId = nullableLong(rs, "pure_id");
                    person.childType = rs.getString("identifier_type");
                    person.childValue = rs.getString("identifier_value");
                }
            }
            return person;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load person identifier", ex);
        }
    }

    public PureCorrectionTarget requireResearchOutput(long researchOutputId) {
        String sql = """
                SELECT id, uuid, pure_id, title
                FROM """ + " " + qualified("pure_research_output") + """
                
                WHERE id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("RESEARCH_OUTPUT_NOT_FOUND", "Research output was not found");
                }
                PureCorrectionTarget target = new PureCorrectionTarget();
                target.localParentId = rs.getLong("id");
                target.parentPureId = nullableLong(rs, "pure_id");
                target.parentUuid = rs.getString("uuid");
                target.parentName = rs.getString("title");
                return target;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load research output for correction", ex);
        }
    }

    public PureCorrectionTarget loadDoiVersion(long researchOutputId, long versionPureId) {
        PureCorrectionTarget output = requireResearchOutput(researchOutputId);
        if (!tableExists("pure_research_output_electronic_version")) {
            return output;
        }
        String sql = """
                SELECT pure_id, type_discriminator, doi, access_type_es, access_type_en,
                       version_type_es, version_type_en
                FROM """ + " " + qualified("pure_research_output_electronic_version") + """
                
                WHERE research_output_id = ? AND pure_id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            statement.setLong(2, versionPureId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    output.childPureId = nullableLong(rs, "pure_id");
                    output.childType = rs.getString("type_discriminator");
                    output.childValue = rs.getString("doi");
                    String access = firstNonBlank(rs.getString("access_type_es"), rs.getString("access_type_en"));
                    String version = firstNonBlank(rs.getString("version_type_es"), rs.getString("version_type_en"));
                    output.extra = ((access == null ? "" : access) + " / " + (version == null ? "" : version)).trim();
                }
            }
            return output;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load DOI version", ex);
        }
    }

    public void recordAudit(
            String performedBy,
            String entity,
            String scope,
            String action,
            String status,
            PureCorrectionTarget target,
            String errorCode,
            String errorMessage
    ) {
        if (!tableExists("pure_correction_audit")) {
            return;
        }
        String sql = """
                INSERT INTO """ + " " + qualified("pure_correction_audit") + """
                
                (performed_at, performed_by, entity, scope, action, status,
                 local_parent_id, parent_pure_id, parent_uuid, child_pure_id, child_value,
                 extra, error_code, error_message)
                VALUES (NOW(6), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, clip(performedBy, 128));
            statement.setString(2, clip(entity, 50));
            statement.setString(3, clip(scope, 64));
            statement.setString(4, clip(action, 64));
            statement.setString(5, clip(status, 64));
            setLong(statement, 6, target == null ? null : target.localParentId);
            setLong(statement, 7, target == null ? null : target.parentPureId);
            statement.setString(8, target == null ? null : clip(target.parentUuid, 36));
            setLong(statement, 9, target == null ? null : target.childPureId);
            statement.setString(10, target == null ? null : clip(target.childValue, 500));
            statement.setString(11, target == null ? null : clip(target.extra, 500));
            statement.setString(12, clip(errorCode, 100));
            statement.setString(13, clip(errorMessage, 1000));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to persist correction audit", ex);
        }
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left.trim();
        }
        if (right != null && !right.isBlank()) {
            return right.trim();
        }
        return null;
    }
}
