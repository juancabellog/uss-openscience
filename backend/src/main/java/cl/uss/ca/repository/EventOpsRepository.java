package cl.uss.ca.repository;

import cl.uss.ca.events.EventDateTimes;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class EventOpsRepository extends JdbcRepository {

    public boolean artifactsAvailable() {
        return tableExists("event_generated_artifact") && tableExists("event_generated_artifact_row");
    }

    public boolean documentsAvailable() {
        return tableExists("event_document");
    }

    public boolean attendanceAvailable() {
        return tableExists("event_attendance")
                && tableExists("event_attendance_history")
                && tableExists("event_attendance_import")
                && tableExists("event_attendance_import_row");
    }

    public boolean documentationClosed(long eventId) {
        if (!columnExists("event", "documentation_closed_at")) {
            return false;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT documentation_closed_at FROM " + qualified("event") + " WHERE id = ?"
             )) {
            statement.setLong(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getTimestamp(1) != null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to read documentary close", ex);
        }
    }

    public void closeDocumentation(long eventId) {
        if (!columnExists("event", "documentation_closed_at")) {
            return;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + qualified("event") + " SET documentation_closed_at = CURRENT_TIMESTAMP(6) WHERE id = ?"
             )) {
            statement.setLong(1, eventId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to close documentation", ex);
        }
    }

    public int nextArtifactVersion(long eventId) {
        return (int) scalarLong(
                "SELECT COALESCE(MAX(version_no), 0) + 1 FROM " + qualified("event_generated_artifact") + " WHERE event_id = ?",
                eventId
        );
    }

    public long insertArtifact(EventOpsRecord.Artifact artifact, List<EventOpsRecord.ArtifactRow> rows) {
        String sql = """
                INSERT INTO """ + " " + qualified("event_generated_artifact") + """
                 (event_id, version_no, kind, original_filename, stored_name, sha256, byte_size, blank_rows, generated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, artifact.eventId);
            statement.setInt(2, artifact.versionNo);
            statement.setString(3, artifact.kind);
            statement.setString(4, artifact.originalFilename);
            statement.setString(5, artifact.storedName);
            statement.setString(6, artifact.sha256);
            statement.setLong(7, artifact.byteSize);
            statement.setInt(8, artifact.blankRows);
            statement.setString(9, artifact.generatedBy);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("artifact id was not generated");
                }
                artifact.id = keys.getLong(1);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to store generated minutes", ex);
        }
        insertArtifactRows(artifact.id, rows);
        return artifact.id;
    }

    private void insertArtifactRows(long artifactId, List<EventOpsRecord.ArtifactRow> rows) {
        String sql = """
                INSERT INTO """ + " " + qualified("event_generated_artifact_row") + """
                 (artifact_id, row_num, registration_id, public_code, display_name, faculty_or_institution,
                  position_title, email_normalized, blank_row)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (EventOpsRecord.ArtifactRow row : rows) {
                statement.setLong(1, artifactId);
                statement.setInt(2, row.rowNumber);
                if (row.registrationId == null) {
                    statement.setNull(3, Types.BIGINT);
                } else {
                    statement.setLong(3, row.registrationId);
                }
                statement.setString(4, row.publicCode);
                statement.setString(5, row.displayName);
                statement.setString(6, row.facultyOrInstitution);
                statement.setString(7, row.positionTitle);
                statement.setString(8, row.emailNormalized);
                statement.setBoolean(9, row.blankRow);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to store minutes rows", ex);
        }
    }

    public List<EventOpsRecord.Artifact> listArtifacts(long eventId) {
        if (!artifactsAvailable()) {
            return List.of();
        }
        String sql = """
                SELECT id, event_id, version_no, kind, original_filename, stored_name, sha256, byte_size,
                       blank_rows, generated_by, generated_at
                FROM """ + " " + qualified("event_generated_artifact") + """
                 WHERE event_id = ? ORDER BY version_no DESC
                """;
        return queryArtifacts(sql, eventId);
    }

    public EventOpsRecord.Artifact requireArtifact(long eventId, long artifactId) {
        EventOpsRecord.Artifact artifact = findArtifact(eventId, artifactId);
        if (artifact == null) {
            throw new NotFoundException("EVENT_ARTIFACT_NOT_FOUND", "Acta no encontrada");
        }
        return artifact;
    }

    public EventOpsRecord.Artifact findArtifact(long eventId, long artifactId) {
        List<EventOpsRecord.Artifact> items = queryArtifacts(
                "SELECT id, event_id, version_no, kind, original_filename, stored_name, sha256, byte_size, blank_rows, generated_by, generated_at FROM "
                        + qualified("event_generated_artifact") + " WHERE event_id = ? AND id = ?",
                eventId,
                artifactId
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public EventOpsRecord.Artifact findArtifactByVersion(long eventId, int version) {
        List<EventOpsRecord.Artifact> items = queryArtifacts(
                "SELECT id, event_id, version_no, kind, original_filename, stored_name, sha256, byte_size, blank_rows, generated_by, generated_at FROM "
                        + qualified("event_generated_artifact") + " WHERE event_id = ? AND version_no = ?",
                eventId,
                version
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public long insertDocument(EventOpsRecord.Document document) {
        String sql = """
                INSERT INTO """ + " " + qualified("event_document") + """
                 (event_id, kind, original_filename, stored_name, content_type, sha256, byte_size, note, uploaded_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, document.eventId);
            statement.setString(2, document.kind);
            statement.setString(3, document.originalFilename);
            statement.setString(4, document.storedName);
            statement.setString(5, document.contentType);
            statement.setString(6, document.sha256);
            statement.setLong(7, document.byteSize);
            statement.setString(8, document.note);
            statement.setString(9, document.uploadedBy);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("document id was not generated");
                }
                return keys.getLong(1);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to store event document", ex);
        }
    }

    public List<EventOpsRecord.Document> listDocuments(long eventId) {
        if (!documentsAvailable()) {
            return List.of();
        }
        String sql = """
                SELECT id, event_id, kind, original_filename, stored_name, content_type, sha256, byte_size, note,
                       uploaded_by, uploaded_at
                FROM """ + " " + qualified("event_document") + """
                 WHERE event_id = ? ORDER BY uploaded_at DESC
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                List<EventOpsRecord.Document> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapDocument(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list event documents", ex);
        }
    }

    public EventOpsRecord.Document requireDocument(long eventId, long documentId) {
        List<EventOpsRecord.Document> items = listDocuments(eventId).stream()
                .filter(item -> item.id == documentId)
                .toList();
        if (items.isEmpty()) {
            throw new NotFoundException("EVENT_DOCUMENT_NOT_FOUND", "Evidencia no encontrada");
        }
        return items.get(0);
    }

    public List<EventOpsRecord.Attendance> listAttendance(long eventId) {
        if (!attendanceAvailable()) {
            return List.of();
        }
        String sql = """
                SELECT a.id, a.event_id, a.registration_id, a.status, a.source, a.artifact_id, a.artifact_row_number,
                       a.walk_in, a.notes, a.confirmed_by, a.confirmed_at, r.public_code, r.display_name,
                       r.email_normalized, r.faculty_or_institution, r.position_title, r.affiliation_origin, r.seq_no
                FROM """ + " " + qualified("event_attendance") + """
                 a JOIN """ + " " + qualified("event_registration") + """
                 r ON r.id = a.registration_id
                 WHERE a.event_id = ?
                 ORDER BY r.seq_no ASC
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                List<EventOpsRecord.Attendance> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapAttendance(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list attendance", ex);
        }
    }

    public EventOpsRecord.Attendance findAttendance(long eventId, long registrationId) {
        return listAttendance(eventId).stream()
                .filter(item -> item.registrationId == registrationId)
                .findFirst()
                .orElse(null);
    }

    public long upsertAttendance(EventOpsRecord.Attendance attendance, String actor, String reason) {
        EventOpsRecord.Attendance current = findAttendance(attendance.eventId, attendance.registrationId);
        if (current == null) {
            String sql = """
                    INSERT INTO """ + " " + qualified("event_attendance") + """
                     (event_id, registration_id, status, source, artifact_id, artifact_row_number, walk_in, notes,
                      confirmed_by, confirmed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, attendance.eventId);
                statement.setLong(2, attendance.registrationId);
                statement.setString(3, attendance.status);
                statement.setString(4, attendance.source);
                if (attendance.artifactId == null) {
                    statement.setNull(5, Types.BIGINT);
                } else {
                    statement.setLong(5, attendance.artifactId);
                }
                if (attendance.artifactRowNumber == null) {
                    statement.setNull(6, Types.INTEGER);
                } else {
                    statement.setInt(6, attendance.artifactRowNumber);
                }
                statement.setBoolean(7, attendance.walkIn);
                statement.setString(8, attendance.notes);
                statement.setString(9, attendance.confirmedBy);
                statement.setTimestamp(10, EventDateTimes.timestamp(attendance.confirmedAt));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("attendance id was not generated");
                    }
                    attendance.id = keys.getLong(1);
                }
            } catch (SQLException ex) {
                throw new DatabaseException("Failed to store attendance", ex);
            }
            insertAttendanceHistory(attendance.id, null, attendance.status, actor, reason);
            return attendance.id;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + qualified("event_attendance")
                             + " SET status = ?, source = ?, artifact_id = ?, artifact_row_number = ?, walk_in = ?, notes = ?, confirmed_by = ?, confirmed_at = ? WHERE id = ?"
             )) {
            statement.setString(1, attendance.status);
            statement.setString(2, attendance.source);
            if (attendance.artifactId == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, attendance.artifactId);
            }
            if (attendance.artifactRowNumber == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, attendance.artifactRowNumber);
            }
            statement.setBoolean(5, attendance.walkIn);
            statement.setString(6, attendance.notes);
            statement.setString(7, attendance.confirmedBy);
            statement.setTimestamp(8, EventDateTimes.timestamp(attendance.confirmedAt));
            statement.setLong(9, current.id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update attendance", ex);
        }
        if (!attendance.status.equals(current.status)) {
            insertAttendanceHistory(current.id, current.status, attendance.status, actor, reason);
        }
        return current.id;
    }

    private void insertAttendanceHistory(long attendanceId, String from, String to, String actor, String reason) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + qualified("event_attendance_history")
                             + " (attendance_id, from_status, to_status, actor, reason) VALUES (?, ?, ?, ?, ?)"
             )) {
            statement.setLong(1, attendanceId);
            statement.setString(2, from);
            statement.setString(3, to);
            statement.setString(4, actor);
            statement.setString(5, reason);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to audit attendance", ex);
        }
    }

    public long insertImport(EventOpsRecord.Import imported, List<EventOpsRecord.ImportRow> rows) {
        String sql = """
                INSERT INTO """ + " " + qualified("event_attendance_import") + """
                 (event_id, artifact_id, source, original_filename, stored_name, sha256, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, imported.eventId);
            if (imported.artifactId == null) {
                statement.setNull(2, Types.BIGINT);
            } else {
                statement.setLong(2, imported.artifactId);
            }
            statement.setString(3, imported.source);
            statement.setString(4, imported.originalFilename);
            statement.setString(5, imported.storedName);
            statement.setString(6, imported.sha256);
            statement.setString(7, imported.status);
            statement.setString(8, imported.createdBy);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("import id was not generated");
                }
                imported.id = keys.getLong(1);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to store attendance import", ex);
        }
        String rowSql = """
                INSERT INTO """ + " " + qualified("event_attendance_import_row") + """
                 (import_id, row_num, registration_code, artifact_version, proposed_status, match_status,
                  matched_registration_id, display_name, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(rowSql)) {
            for (EventOpsRecord.ImportRow row : rows) {
                statement.setLong(1, imported.id);
                statement.setInt(2, row.rowNumber);
                statement.setString(3, row.registrationCode);
                if (row.artifactVersion == null) {
                    statement.setNull(4, Types.INTEGER);
                } else {
                    statement.setInt(4, row.artifactVersion);
                }
                statement.setString(5, row.proposedStatus);
                statement.setString(6, row.matchStatus);
                if (row.matchedRegistrationId == null) {
                    statement.setNull(7, Types.BIGINT);
                } else {
                    statement.setLong(7, row.matchedRegistrationId);
                }
                statement.setString(8, row.displayName);
                statement.setString(9, row.notes);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to store attendance import rows", ex);
        }
        return imported.id;
    }

    public EventOpsRecord.Import requireImport(long eventId, long importId) {
        if (!attendanceAvailable()) {
            throw new NotFoundException("EVENT_IMPORT_NOT_FOUND", "Importación no encontrada");
        }
        String sql = """
                SELECT id, event_id, artifact_id, source, original_filename, stored_name, sha256, status, created_by,
                       created_at, confirmed_at
                FROM """ + " " + qualified("event_attendance_import") + """
                 WHERE event_id = ? AND id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            statement.setLong(2, importId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("EVENT_IMPORT_NOT_FOUND", "Importación no encontrada");
                }
                return mapImport(rs);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load attendance import", ex);
        }
    }

    public List<EventOpsRecord.ImportRow> listImportRows(long importId) {
        String sql = """
                SELECT id, import_id, row_num, registration_code, artifact_version, proposed_status, match_status,
                       matched_registration_id, display_name, notes
                FROM """ + " " + qualified("event_attendance_import_row") + """
                 WHERE import_id = ? ORDER BY row_num ASC
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, importId);
            try (ResultSet rs = statement.executeQuery()) {
                List<EventOpsRecord.ImportRow> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapImportRow(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list attendance import rows", ex);
        }
    }

    public void confirmImport(long importId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + qualified("event_attendance_import")
                             + " SET status = 'CONFIRMED', confirmed_at = CURRENT_TIMESTAMP(6) WHERE id = ?"
             )) {
            statement.setLong(1, importId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to confirm attendance import", ex);
        }
    }

    public EventIndicatorTotals totals(LocalDateTime from, LocalDateTime to) {
        EventIndicatorTotals totals = new EventIndicatorTotals();
        if (!tableExists("event")) {
            return totals;
        }
        totals.totalEvents = (int) scalarLong(countEventsSql(false), from, to);
        totals.publishedEvents = (int) scalarLong(
                countEventsSql(true),
                from,
                to
        );
        if (tableExists("event_registration")) {
            totals.advanceRegistrations = (int) scalarLong(
                    "SELECT COUNT(*) FROM " + qualified("event_registration") + " r JOIN " + qualified("event")
                            + " e ON e.id = r.event_id WHERE r.status = 'CONFIRMED'" + periodClause("e") ,
                    from,
                    to
            );
            totals.uniquePeople = (int) scalarLong(
                    "SELECT COUNT(DISTINCT r.participant_id) FROM " + qualified("event_registration") + " r JOIN "
                            + qualified("event") + " e ON e.id = r.event_id WHERE r.status = 'CONFIRMED'"
                            + periodClause("e"),
                    from,
                    to
            );
            totals.recurringPeople = (int) scalarLong(
                    "SELECT COUNT(*) FROM (SELECT r.participant_id FROM " + qualified("event_registration")
                            + " r JOIN " + qualified("event") + " e ON e.id = r.event_id WHERE r.status = 'CONFIRMED'"
                            + periodClause("e") + " GROUP BY r.participant_id HAVING COUNT(DISTINCT r.event_id) > 1) x",
                    from,
                    to
            );
        }
        if (attendanceAvailable()) {
            totals.effectiveAttendees = (int) scalarLong(
                    "SELECT COUNT(*) FROM " + qualified("event_attendance") + " a JOIN " + qualified("event")
                            + " e ON e.id = a.event_id WHERE a.status = 'ASISTIO'" + periodClause("e"),
                    from,
                    to
            );
            totals.walkIns = (int) scalarLong(
                    "SELECT COUNT(*) FROM " + qualified("event_attendance") + " a JOIN " + qualified("event")
                            + " e ON e.id = a.event_id WHERE a.status = 'ASISTIO' AND a.walk_in = 1" + periodClause("e"),
                    from,
                    to
            );
            totals.confirmedAbsences = (int) scalarLong(
                    "SELECT COUNT(*) FROM " + qualified("event_attendance") + " a JOIN " + qualified("event")
                            + " e ON e.id = a.event_id WHERE a.status = 'NO_ASISTIO'" + periodClause("e"),
                    from,
                    to
            );
        }
        if (tableExists("event_participant") && tableExists("event_registration")) {
            String pureJoin = tableExists("uss_directory_person_pure_link") && tableExists("uss_directory_person")
                    ? " OR EXISTS (SELECT 1 FROM " + qualified("uss_directory_person_pure_link")
                    + " l JOIN " + qualified("uss_directory_person")
                    + " d ON d.id = l.directory_person_id WHERE d.uuid = p.directory_person_uuid)"
                    : "";
            totals.pureLinked = (int) scalarLong(
                    "SELECT COUNT(DISTINCT p.id) FROM " + qualified("event_participant") + " p JOIN "
                            + qualified("event_registration") + " r ON r.participant_id = p.id JOIN "
                            + qualified("event") + " e ON e.id = r.event_id WHERE r.status = 'CONFIRMED' AND ("
                            + "p.pure_person_uuid IS NOT NULL" + pureJoin + ")" + periodClause("e"),
                    from,
                    to
            );
        }
        return totals;
    }

    public List<EventNameCount> groupBy(String column, LocalDateTime from, LocalDateTime to) {
        if (!tableExists("event_registration")) {
            return List.of();
        }
        String safe = "faculty_or_institution".equals(column) ? "r.faculty_or_institution" : "r.position_title";
        String sql = "SELECT COALESCE(" + safe + ", 'Sin dato') AS label, COUNT(*) AS total FROM "
                + qualified("event_registration") + " r JOIN " + qualified("event")
                + " e ON e.id = r.event_id WHERE r.status = 'CONFIRMED'" + periodClause("e")
                + " GROUP BY label ORDER BY total DESC, label ASC LIMIT 20";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPeriod(statement, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                List<EventNameCount> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(new EventNameCount(rs.getString("label"), rs.getInt("total")));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to group event indicators", ex);
        }
    }

    private String countEventsSql(boolean publishedOnly) {
        return "SELECT COUNT(*) FROM " + qualified("event") + " e WHERE 1=1"
                + (publishedOnly ? " AND e.publication_status = 'PUBLISHED'" : "")
                + periodClause("e");
    }

    private static String periodClause(String alias) {
        return " AND (" + alias + ".starts_at IS NULL OR (? IS NULL OR " + alias + ".starts_at >= ?))"
                + " AND (" + alias + ".starts_at IS NULL OR (? IS NULL OR " + alias + ".starts_at <= ?))";
    }

    private static void bindPeriod(PreparedStatement statement, LocalDateTime from, LocalDateTime to) throws SQLException {
        statement.setTimestamp(1, EventDateTimes.timestamp(from));
        statement.setTimestamp(2, EventDateTimes.timestamp(from));
        statement.setTimestamp(3, EventDateTimes.timestamp(to));
        statement.setTimestamp(4, EventDateTimes.timestamp(to));
    }

    private long scalarLong(String sql, LocalDateTime from, LocalDateTime to) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPeriod(statement, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to compute event indicator", ex);
        }
    }

    private List<EventOpsRecord.Artifact> queryArtifacts(String sql, Object... params) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<EventOpsRecord.Artifact> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapArtifact(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load minutes", ex);
        }
    }

    private static EventOpsRecord.Artifact mapArtifact(ResultSet rs) throws SQLException {
        EventOpsRecord.Artifact artifact = new EventOpsRecord.Artifact();
        artifact.id = rs.getLong("id");
        artifact.eventId = rs.getLong("event_id");
        artifact.versionNo = rs.getInt("version_no");
        artifact.kind = rs.getString("kind");
        artifact.originalFilename = rs.getString("original_filename");
        artifact.storedName = rs.getString("stored_name");
        artifact.sha256 = rs.getString("sha256");
        artifact.byteSize = rs.getLong("byte_size");
        artifact.blankRows = rs.getInt("blank_rows");
        artifact.generatedBy = rs.getString("generated_by");
        Timestamp generated = rs.getTimestamp("generated_at");
        artifact.generatedAt = generated == null ? null : generated.toLocalDateTime();
        return artifact;
    }

    private static EventOpsRecord.Document mapDocument(ResultSet rs) throws SQLException {
        EventOpsRecord.Document document = new EventOpsRecord.Document();
        document.id = rs.getLong("id");
        document.eventId = rs.getLong("event_id");
        document.kind = rs.getString("kind");
        document.originalFilename = rs.getString("original_filename");
        document.storedName = rs.getString("stored_name");
        document.contentType = rs.getString("content_type");
        document.sha256 = rs.getString("sha256");
        document.byteSize = rs.getLong("byte_size");
        document.note = rs.getString("note");
        document.uploadedBy = rs.getString("uploaded_by");
        Timestamp uploaded = rs.getTimestamp("uploaded_at");
        document.uploadedAt = uploaded == null ? null : uploaded.toLocalDateTime();
        return document;
    }

    private static EventOpsRecord.Attendance mapAttendance(ResultSet rs) throws SQLException {
        EventOpsRecord.Attendance attendance = new EventOpsRecord.Attendance();
        attendance.id = rs.getLong("id");
        attendance.eventId = rs.getLong("event_id");
        attendance.registrationId = rs.getLong("registration_id");
        attendance.status = rs.getString("status");
        attendance.source = rs.getString("source");
        long artifactId = rs.getLong("artifact_id");
        attendance.artifactId = rs.wasNull() ? null : artifactId;
        int row = rs.getInt("artifact_row_number");
        attendance.artifactRowNumber = rs.wasNull() ? null : row;
        attendance.walkIn = rs.getBoolean("walk_in");
        attendance.notes = rs.getString("notes");
        attendance.confirmedBy = rs.getString("confirmed_by");
        Timestamp confirmed = rs.getTimestamp("confirmed_at");
        attendance.confirmedAt = confirmed == null ? null : confirmed.toLocalDateTime();
        attendance.publicCode = rs.getString("public_code");
        attendance.displayName = rs.getString("display_name");
        attendance.email = rs.getString("email_normalized");
        attendance.facultyOrInstitution = rs.getString("faculty_or_institution");
        attendance.positionTitle = rs.getString("position_title");
        attendance.affiliationOrigin = rs.getString("affiliation_origin");
        attendance.seqNo = rs.getInt("seq_no");
        return attendance;
    }

    private static EventOpsRecord.Import mapImport(ResultSet rs) throws SQLException {
        EventOpsRecord.Import imported = new EventOpsRecord.Import();
        imported.id = rs.getLong("id");
        imported.eventId = rs.getLong("event_id");
        long artifactId = rs.getLong("artifact_id");
        imported.artifactId = rs.wasNull() ? null : artifactId;
        imported.source = rs.getString("source");
        imported.originalFilename = rs.getString("original_filename");
        imported.storedName = rs.getString("stored_name");
        imported.sha256 = rs.getString("sha256");
        imported.status = rs.getString("status");
        imported.createdBy = rs.getString("created_by");
        Timestamp created = rs.getTimestamp("created_at");
        imported.createdAt = created == null ? null : created.toLocalDateTime();
        Timestamp confirmed = rs.getTimestamp("confirmed_at");
        imported.confirmedAt = confirmed == null ? null : confirmed.toLocalDateTime();
        return imported;
    }

    private static EventOpsRecord.ImportRow mapImportRow(ResultSet rs) throws SQLException {
        EventOpsRecord.ImportRow row = new EventOpsRecord.ImportRow();
        row.id = rs.getLong("id");
        row.importId = rs.getLong("import_id");
        row.rowNumber = rs.getInt("row_num");
        row.registrationCode = rs.getString("registration_code");
        int version = rs.getInt("artifact_version");
        row.artifactVersion = rs.wasNull() ? null : version;
        row.proposedStatus = rs.getString("proposed_status");
        row.matchStatus = rs.getString("match_status");
        long matched = rs.getLong("matched_registration_id");
        row.matchedRegistrationId = rs.wasNull() ? null : matched;
        row.displayName = rs.getString("display_name");
        row.notes = rs.getString("notes");
        return row;
    }

    public static final class EventIndicatorTotals {
        public int totalEvents;
        public int publishedEvents;
        public int advanceRegistrations;
        public int effectiveAttendees;
        public int walkIns;
        public int confirmedAbsences;
        public int uniquePeople;
        public int recurringPeople;
        public int pureLinked;
    }

    public record EventNameCount(String label, int total) {
    }
}
