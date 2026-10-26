package cl.uss.ca.sync;

import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.repository.JdbcRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class SyncPresenceRepository extends JdbcRepository {
    private static final Logger LOG = Logger.getLogger(SyncPresenceRepository.class.getName());

    public boolean canTrackPeople() {
        return columnExists("pure_person", "active_in_pure")
                && columnExists("pure_person", "last_seen_sync_id");
    }

    public boolean canTrackResearchOutputs() {
        return columnExists("pure_research_output", "active_in_pure")
                && columnExists("pure_research_output", "last_seen_sync_id");
    }

    public void markPersonSeen(Connection connection, String uuid, String syncId, boolean touchChangeDetected) {
        markSeen(connection, "pure_person", uuid, syncId, touchChangeDetected);
    }

    public void markResearchOutputSeen(Connection connection, String uuid, String syncId, boolean touchChangeDetected) {
        markSeen(connection, "pure_research_output", uuid, syncId, touchChangeDetected);
    }

    public boolean canTrackJournals() {
        return columnExists("pure_journal", "active_in_pure")
                && columnExists("pure_journal", "last_seen_sync_id");
    }

    public void markJournalSeen(Connection connection, String uuid, String syncId, boolean touchChangeDetected) {
        markSeen(connection, "pure_journal", uuid, syncId, touchChangeDetected);
    }

    public List<PresenceRecord> markMissingPeople(String syncId) {
        return markMissing("pure_person", syncId);
    }

    public List<PresenceRecord> markMissingResearchOutputs(String syncId) {
        return markMissing("pure_research_output", syncId);
    }

    public List<PresenceRecord> markMissingJournals(String syncId) {
        return markMissing("pure_journal", syncId);
    }

    public boolean canTrackProjects() {
        return columnExists("pure_project", "active_in_pure")
                && columnExists("pure_project", "last_seen_sync_id");
    }

    public void markProjectSeen(Connection connection, String uuid, String syncId, boolean touchChangeDetected) {
        markSeen(connection, "pure_project", uuid, syncId, touchChangeDetected);
    }

    public List<PresenceRecord> markMissingProjects(String syncId) {
        return markMissing("pure_project", syncId);
    }

    public boolean canTrackOrganizations() {
        return columnExists("pure_organization", "active_in_pure")
                && columnExists("pure_organization", "last_seen_sync_id");
    }

    public void markOrganizationSeen(Connection connection, String uuid, String syncId, boolean touchChangeDetected) {
        markSeen(connection, "pure_organization", uuid, syncId, touchChangeDetected);
    }

    public List<PresenceRecord> markMissingOrganizations(String syncId) {
        return markMissing("pure_organization", syncId);
    }

    private void markSeen(Connection connection, String table, String uuid, String syncId, boolean touchChangeDetected) {
        if (!columnExists(table, "active_in_pure") || !columnExists(table, "last_seen_sync_id")) {
            return;
        }
        String changeSql = columnExists(table, "last_change_detected_at") && touchChangeDetected
                ? ", last_change_detected_at = NOW(6)"
                : "";
        String sql = "UPDATE " + qualified(table) + """
                 SET active_in_pure = 1,
                     last_seen_in_pure_at = NOW(6),
                     last_seen_sync_id = ?,
                     removed_from_pure_at = NULL
                """ + changeSql + """
                 
                 WHERE uuid = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, syncId);
            statement.setString(2, uuid);
            statement.executeUpdate();
        } catch (SQLException ex) {
            LOG.warning("Unable to mark " + table + " seen for uuid=" + uuid + ": " + ex.getMessage());
        }
    }

    private List<PresenceRecord> markMissing(String table, String syncId) {
        if (!columnExists(table, "active_in_pure") || !columnExists(table, "last_seen_sync_id")) {
            return List.of();
        }
        String select = """
                SELECT id, uuid, pure_id
                FROM """ + " " + qualified(table) + """
                
                WHERE COALESCE(active_in_pure, 1) = 1
                  AND (last_seen_sync_id IS NULL OR last_seen_sync_id <> ?)
                """;
        String changeSql = columnExists(table, "last_change_detected_at")
                ? ", last_change_detected_at = NOW(6)"
                : "";
        String update = "UPDATE " + qualified(table) + """
                 SET active_in_pure = 0,
                     removed_from_pure_at = NOW(6)
                """ + changeSql + """
                 
                 WHERE COALESCE(active_in_pure, 1) = 1
                   AND (last_seen_sync_id IS NULL OR last_seen_sync_id <> ?)
                """;
        try (Connection connection = connection()) {
            List<PresenceRecord> missing = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(select)) {
                statement.setString(1, syncId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        String uuid = rs.getString("uuid");
                        long pureId = rs.getLong("pure_id");
                        missing.add(new PresenceRecord(id, uuid, rs.wasNull() ? null : pureId));
                    }
                }
            }
            if (missing.isEmpty()) {
                return missing;
            }
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setString(1, syncId);
                statement.executeUpdate();
            }
            return missing;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to mark missing " + table, ex);
        }
    }
}
