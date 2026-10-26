package cl.uss.ca.repository;

import cl.uss.ca.events.EventDateTimes;
import cl.uss.ca.exception.DatabaseException;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EventSignupRepository extends JdbcRepository {

    public record Challenge(
            long id,
            long eventId,
            String emailNormalized,
            LocalDateTime expiresAt,
            LocalDateTime consumedAt
    ) {
    }

    public record VerifiedSession(long id, long eventId, String emailNormalized, LocalDateTime expiresAt) {
    }

    public record Participant(
            long id,
            String publicUuid,
            String emailNormalized,
            String displayName,
            String facultyOrInstitution,
            String campusName,
            String positionTitle,
            String affiliationOrigin,
            String directoryPersonUuid
    ) {
    }

    public void insertChallenge(long eventId, String email, String tokenHash, LocalDateTime expiresAt) {
        String sql = """
                INSERT INTO """ + " " + qualified("event_email_challenge") + """
                 (event_id, email_normalized, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            statement.setString(2, email);
            statement.setString(3, tokenHash);
            statement.setTimestamp(4, EventDateTimes.timestamp(expiresAt));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to store email challenge", ex);
        }
    }

    public Challenge findChallenge(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return null;
        }
        String sql = """
                SELECT id, event_id, email_normalized, expires_at, consumed_at
                FROM """ + " " + qualified("event_email_challenge") + """
                 WHERE token_hash = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Challenge(
                        rs.getLong("id"),
                        rs.getLong("event_id"),
                        rs.getString("email_normalized"),
                        local(rs.getTimestamp("expires_at")),
                        local(rs.getTimestamp("consumed_at"))
                );
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load email challenge", ex);
        }
    }

    public boolean consumeChallenge(long id, LocalDateTime now) {
        String sql = "UPDATE " + qualified("event_email_challenge") + " SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, EventDateTimes.timestamp(now));
            statement.setLong(2, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to consume email challenge", ex);
        }
    }

    public void insertSession(long eventId, String email, String sessionHash, LocalDateTime expiresAt) {
        String sql = """
                INSERT INTO """ + " " + qualified("event_verified_session") + """
                 (event_id, email_normalized, session_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            statement.setString(2, email);
            statement.setString(3, sessionHash);
            statement.setTimestamp(4, EventDateTimes.timestamp(expiresAt));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to store verified session", ex);
        }
    }

    public VerifiedSession findSession(String sessionHash, LocalDateTime now) {
        if (sessionHash == null || sessionHash.isBlank()) {
            return null;
        }
        String sql = """
                SELECT id, event_id, email_normalized, expires_at
                FROM """ + " " + qualified("event_verified_session") + """
                 WHERE session_hash = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionHash);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                LocalDateTime expires = local(rs.getTimestamp("expires_at"));
                if (expires == null || !expires.isAfter(now)) {
                    return null;
                }
                return new VerifiedSession(
                        rs.getLong("id"),
                        rs.getLong("event_id"),
                        rs.getString("email_normalized"),
                        expires
                );
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load verified session", ex);
        }
    }

    public EventSignupRecord findRegistration(long eventId, String email) {
        String sql = """
                SELECT id, event_id, participant_id, public_code, seq_no, status, email_normalized, display_name,
                       faculty_or_institution, campus_name, position_title, affiliation_origin, directory_person_uuid,
                       snapshot_json, created_at
                FROM """ + " " + qualified("event_registration") + """
                 WHERE event_id = ? AND email_normalized = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            statement.setString(2, email);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRegistration(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load registration", ex);
        }
    }

    public EventSignupRecord findRegistration(Connection connection, long eventId, String email) throws SQLException {
        String sql = """
                SELECT id, event_id, participant_id, public_code, seq_no, status, email_normalized, display_name,
                       faculty_or_institution, campus_name, position_title, affiliation_origin, directory_person_uuid,
                       snapshot_json, created_at
                FROM """ + " " + qualified("event_registration") + """
                 WHERE event_id = ? AND email_normalized = ? FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            statement.setString(2, email);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRegistration(rs) : null;
            }
        }
    }

    public EventSignupRecord findByPublicCode(long eventId, String publicCode) {
        if (publicCode == null || publicCode.isBlank()) {
            return null;
        }
        String sql = """
                SELECT id, event_id, participant_id, public_code, seq_no, status, email_normalized, display_name,
                       faculty_or_institution, campus_name, position_title, affiliation_origin, directory_person_uuid,
                       snapshot_json, created_at
                FROM """ + " " + qualified("event_registration") + """
                 WHERE event_id = ? AND public_code = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            statement.setString(2, publicCode.trim());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapRegistration(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load registration by code", ex);
        }
    }

    public int countByPublicCode(long eventId, String publicCode) {
        if (publicCode == null || publicCode.isBlank()) {
            return 0;
        }
        return (int) scalarLong(
                "SELECT COUNT(*) FROM " + qualified("event_registration") + " WHERE event_id = ? AND public_code = ?",
                eventId,
                publicCode.trim()
        );
    }

    public List<EventSignupRecord> listByEvent(long eventId) {
        String sql = """
                SELECT id, event_id, participant_id, public_code, seq_no, status, email_normalized, display_name,
                       faculty_or_institution, campus_name, position_title, affiliation_origin, directory_person_uuid,
                       snapshot_json, created_at
                FROM """ + " " + qualified("event_registration") + """
                 WHERE event_id = ?
                 ORDER BY seq_no ASC
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                List<EventSignupRecord> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapRegistration(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list registrations", ex);
        }
    }

    public long upsertParticipant(
            Connection connection,
            String email,
            String displayName,
            String faculty,
            String campus,
            String position,
            String origin,
            String directoryUuid
    ) throws SQLException {
        String select = "SELECT id FROM " + qualified("event_participant") + " WHERE email_normalized = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, email);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    updateParticipant(connection, id, displayName, faculty, campus, position, origin, directoryUuid);
                    return id;
                }
            }
        }
        String insert = """
                INSERT INTO """ + " " + qualified("event_participant") + """
                 (public_uuid, email_normalized, display_name, faculty_or_institution, campus_name, position_title,
                  affiliation_origin, directory_person_uuid)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, email);
            statement.setString(3, displayName);
            statement.setString(4, faculty);
            statement.setString(5, campus);
            statement.setString(6, position);
            statement.setString(7, origin);
            statement.setString(8, directoryUuid);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("participant id was not generated");
                }
                return keys.getLong(1);
            }
        }
    }

    public int nextSeq(Connection connection, long eventId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(seq_no), 0) + 1 FROM " + qualified("event_registration") + " WHERE event_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    public long insertRegistration(Connection connection, EventSignupRecord record) throws SQLException {
        String sql = """
                INSERT INTO """ + " " + qualified("event_registration") + """
                 (event_id, participant_id, public_code, seq_no, status, email_normalized, display_name,
                  faculty_or_institution, campus_name, position_title, affiliation_origin, directory_person_uuid,
                  snapshot_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, record.eventId);
            statement.setLong(2, record.participantId);
            statement.setString(3, record.publicCode);
            statement.setInt(4, record.seqNo);
            statement.setString(5, record.status);
            statement.setString(6, record.emailNormalized);
            statement.setString(7, record.displayName);
            statement.setString(8, record.facultyOrInstitution);
            statement.setString(9, record.campusName);
            statement.setString(10, record.positionTitle);
            statement.setString(11, record.affiliationOrigin);
            statement.setString(12, record.directoryPersonUuid);
            statement.setString(13, record.snapshotJson);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("registration id was not generated");
                }
                return keys.getLong(1);
            }
        }
    }

    public void reactivateRegistration(Connection connection, EventSignupRecord record) throws SQLException {
        String sql = """
                UPDATE """ + " " + qualified("event_registration") + """
                 SET participant_id = ?, status = ?, display_name = ?, faculty_or_institution = ?, campus_name = ?,
                     position_title = ?, affiliation_origin = ?, directory_person_uuid = ?, snapshot_json = ?,
                     cancelled_at = NULL
                 WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, record.participantId);
            statement.setString(2, record.status);
            statement.setString(3, record.displayName);
            statement.setString(4, record.facultyOrInstitution);
            statement.setString(5, record.campusName);
            statement.setString(6, record.positionTitle);
            statement.setString(7, record.affiliationOrigin);
            statement.setString(8, record.directoryPersonUuid);
            statement.setString(9, record.snapshotJson);
            statement.setLong(10, record.id);
            statement.executeUpdate();
        }
    }

    public void insertHistory(
            Connection connection,
            long registrationId,
            String fromStatus,
            String toStatus,
            String actor,
            String reason
    ) throws SQLException {
        String sql = """
                INSERT INTO """ + " " + qualified("event_registration_status_history") + """
                 (registration_id, from_status, to_status, actor, reason)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, registrationId);
            statement.setString(2, fromStatus);
            statement.setString(3, toStatus);
            statement.setString(4, actor);
            statement.setString(5, reason);
            statement.executeUpdate();
        }
    }

    private void updateParticipant(
            Connection connection,
            long id,
            String displayName,
            String faculty,
            String campus,
            String position,
            String origin,
            String directoryUuid
    ) throws SQLException {
        String sql = """
                UPDATE """ + " " + qualified("event_participant") + """
                 SET display_name = ?, faculty_or_institution = ?, campus_name = ?, position_title = ?,
                     affiliation_origin = ?, directory_person_uuid = COALESCE(?, directory_person_uuid)
                 WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, displayName);
            statement.setString(2, faculty);
            statement.setString(3, campus);
            statement.setString(4, position);
            statement.setString(5, origin);
            statement.setString(6, directoryUuid);
            statement.setLong(7, id);
            statement.executeUpdate();
        }
    }

    private static EventSignupRecord mapRegistration(ResultSet rs) throws SQLException {
        EventSignupRecord record = new EventSignupRecord();
        record.id = rs.getLong("id");
        record.eventId = rs.getLong("event_id");
        record.participantId = rs.getLong("participant_id");
        record.publicCode = rs.getString("public_code");
        record.seqNo = rs.getInt("seq_no");
        record.status = rs.getString("status");
        record.emailNormalized = rs.getString("email_normalized");
        record.displayName = nullableString(rs, "display_name");
        record.facultyOrInstitution = nullableString(rs, "faculty_or_institution");
        record.campusName = nullableString(rs, "campus_name");
        record.positionTitle = nullableString(rs, "position_title");
        record.affiliationOrigin = rs.getString("affiliation_origin");
        record.directoryPersonUuid = nullableString(rs, "directory_person_uuid");
        record.snapshotJson = nullableString(rs, "snapshot_json");
        Timestamp created = rs.getTimestamp("created_at");
        record.createdAt = created == null ? null : created.toLocalDateTime();
        return record;
    }

    private static LocalDateTime local(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
