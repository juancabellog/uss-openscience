package cl.uss.ca.repository;

import cl.uss.ca.api.dto.DirectoryEmailDto;
import cl.uss.ca.api.dto.DirectoryImportSummaryDto;
import cl.uss.ca.api.dto.DirectoryIssueDto;
import cl.uss.ca.api.dto.DirectoryPersonDetailDto;
import cl.uss.ca.api.dto.DirectoryPersonSummaryDto;
import cl.uss.ca.api.dto.DirectoryStatsDto;
import cl.uss.ca.directory.DirectoryCatalog;
import cl.uss.ca.directory.DirectoryImportRecoveryPolicy.DirectoryImportSideEffects;
import cl.uss.ca.directory.DirectoryIncomingRow;
import cl.uss.ca.directory.DirectoryIssue;
import cl.uss.ca.directory.DirectoryPersonRef;
import cl.uss.ca.directory.DirectoryPlannedRow;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class DirectoryRepository extends JdbcRepository {

    public boolean available() {
        return tableExists("uss_directory_person") && tableExists("uss_directory_import");
    }

    public List<String> listCampusNames() {
        if (!tableExists("uss_directory_person") || !columnExists("uss_directory_person", "campus_name")) {
            return List.of();
        }
        String sql = "SELECT DISTINCT campus_name FROM " + qualified("uss_directory_person")
                + " WHERE campus_name IS NOT NULL AND TRIM(campus_name) <> '' ORDER BY campus_name ASC LIMIT 80";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<String> items = new ArrayList<>();
            while (rs.next()) {
                String value = nullableString(rs, "campus_name");
                if (value != null) {
                    items.add(value);
                }
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list directory campus names", ex);
        }
    }

    public boolean hasRunningImport() {
        if (!tableExists("uss_directory_import")) {
            return false;
        }
        return scalarLong("SELECT COUNT(*) FROM " + qualified("uss_directory_import") + " WHERE status = 'RUNNING'") > 0;
    }

    public List<DirectoryImportSummaryDto> findRunningImports() {
        if (!tableExists("uss_directory_import")) {
            return List.of();
        }
        String sql = "SELECT " + importSelectColumns() + " FROM " + qualified("uss_directory_import")
                + " WHERE status = 'RUNNING' ORDER BY id ASC";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<DirectoryImportSummaryDto> items = new ArrayList<>();
            while (rs.next()) {
                items.add(mapImport(rs));
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list running directory imports", ex);
        }
    }

    public DirectoryImportSideEffects countSideEffects(long importId) {
        DirectoryImportSideEffects effects = DirectoryImportSideEffects.empty();
        if (tableExists("uss_directory_person")) {
            effects.people = scalarLong(
                    "SELECT COUNT(*) FROM " + qualified("uss_directory_person") + " WHERE last_seen_import_id = ?",
                    importId
            );
        }
        if (tableExists("uss_directory_import_row")) {
            effects.importRows = scalarLong(
                    "SELECT COUNT(*) FROM " + qualified("uss_directory_import_row") + " WHERE import_id = ?",
                    importId
            );
        }
        if (tableExists("uss_directory_import_issue")) {
            effects.issues = scalarLong(
                    "SELECT COUNT(*) FROM " + qualified("uss_directory_import_issue") + " WHERE import_id = ?",
                    importId
            );
        }
        if (tableExists("uss_directory_person_email")) {
            effects.emails = scalarLong(
                    "SELECT COUNT(*) FROM " + qualified("uss_directory_person_email")
                            + " WHERE first_seen_import_id = ? OR last_seen_import_id = ?",
                    importId,
                    importId
            );
        }
        if (tableExists("uss_directory_person_history")) {
            effects.history = scalarLong(
                    "SELECT COUNT(*) FROM " + qualified("uss_directory_person_history") + " WHERE import_id = ?",
                    importId
            );
        }
        return effects;
    }

    public Connection openTransaction() {
        try {
            Connection connection = connection();
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to open directory transaction", ex);
        }
    }

    public Connection openConnection() {
        try {
            return connection();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to open directory connection", ex);
        }
    }

    public boolean beginWork(Connection connection) {
        try {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            return previous;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to begin directory transaction", ex);
        }
    }

    public static void rollback(Connection connection) {
        rollbackQuietly(connection);
    }

    public static void restore(Connection connection, boolean previous) {
        restoreAutoCommit(connection, previous);
    }

    public static void close(Connection connection) {
        closeQuietly(connection);
    }

    public long insertImportCommitted(
            String publicId,
            String filename,
            String storageKey,
            String sha256,
            long byteSize,
            String sheetName,
            String headerJson,
            String importedBy,
            String ownerToken,
            int expectedCount
    ) {
        try (Connection connection = connection()) {
            return insertImport(
                    connection,
                    publicId,
                    filename,
                    storageKey,
                    sha256,
                    byteSize,
                    sheetName,
                    headerJson,
                    importedBy,
                    ownerToken,
                    expectedCount
            );
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to start directory import", ex);
        }
    }

    public boolean finishImportCommitted(long importId, String status, DirectoryImportCounts counts, String errorMessage) {
        try (Connection connection = connection()) {
            return finishImport(connection, importId, status, counts, errorMessage) > 0;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to finish directory import", ex);
        }
    }

    public boolean heartbeat(long importId, String ownerToken, int processed, int expected) {
        if (!tableExists("uss_directory_import")) {
            return false;
        }
        if (!hasOwnershipColumns()) {
            return scalarLong(
                    "SELECT COUNT(*) FROM " + qualified("uss_directory_import") + " WHERE id = ? AND status = 'RUNNING'",
                    importId
            ) > 0;
        }
        String sql = """
                UPDATE """ + " " + qualified("uss_directory_import") + """
                 SET heartbeat_at = CURRENT_TIMESTAMP(6), processed_count = ?, expected_count = ?
                WHERE id = ? AND status = 'RUNNING' AND (owner_token IS NULL OR owner_token = ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, processed);
            statement.setInt(2, expected);
            statement.setLong(3, importId);
            statement.setString(4, ownerToken);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to persist directory import heartbeat", ex);
        }
    }

    public DirectoryCatalog loadCatalog() {
        DirectoryCatalog catalog = new DirectoryCatalog();
        if (!tableExists("uss_directory_person")) {
            return catalog;
        }
        Map<Long, Set<String>> emails = loadEmails();
        String sql = """
                SELECT id, uuid, rut_hmac, active_in_directory, given_name, middle_name, family_name,
                       second_family_name, campus_name, faculty_or_vr_raw, position_title, primary_email_normalized
                FROM """ + " " + qualified("uss_directory_person");
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                Set<String> personEmails = emails.getOrDefault(id, Set.of());
                String snapshot = snapshotHint(
                        nullableString(rs, "given_name"),
                        nullableString(rs, "middle_name"),
                        nullableString(rs, "family_name"),
                        nullableString(rs, "second_family_name"),
                        nullableString(rs, "campus_name"),
                        nullableString(rs, "faculty_or_vr_raw"),
                        nullableString(rs, "position_title"),
                        nullableString(rs, "primary_email_normalized")
                );
                catalog.add(new DirectoryPersonRef(
                        id,
                        rs.getString("uuid"),
                        nullableString(rs, "rut_hmac"),
                        rs.getBoolean("active_in_directory"),
                        snapshot,
                        personEmails
                ));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load directory catalog", ex);
        }
        return catalog;
    }

    private Map<Long, Set<String>> loadEmails() {
        Map<Long, Set<String>> emails = new HashMap<>();
        if (!tableExists("uss_directory_person_email")) {
            return emails;
        }
        String sql = "SELECT directory_person_id, email_normalized FROM " + qualified("uss_directory_person_email");
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                emails.computeIfAbsent(rs.getLong("directory_person_id"), key -> new HashSet<>())
                        .add(rs.getString("email_normalized"));
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load directory emails", ex);
        }
        return emails;
    }

    public long insertImport(
            Connection connection,
            String publicId,
            String filename,
            String storageKey,
            String sha256,
            long byteSize,
            String sheetName,
            String headerJson,
            String importedBy,
            String ownerToken,
            int expectedCount
    ) throws SQLException {
        boolean ownership = hasOwnershipColumns();
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(qualified("uss_directory_import"));
        sql.append(" (public_id, original_filename, storage_key, sha256, byte_size, sheet_name, header_json, status, imported_by");
        if (ownership) {
            sql.append(", owner_token, heartbeat_at, expected_count, processed_count");
        }
        sql.append(") VALUES (?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?");
        if (ownership) {
            sql.append(", ?, CURRENT_TIMESTAMP(6), ?, 0");
        }
        sql.append(")");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, publicId);
            statement.setString(2, filename);
            statement.setString(3, storageKey);
            statement.setString(4, sha256);
            statement.setLong(5, byteSize);
            statement.setString(6, sheetName);
            statement.setString(7, headerJson);
            statement.setString(8, importedBy);
            if (ownership) {
                statement.setString(9, ownerToken);
                statement.setInt(10, expectedCount);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("directory import id was not generated");
                }
                return keys.getLong(1);
            }
        }
    }

    public int finishImport(
            Connection connection,
            long importId,
            String status,
            DirectoryImportCounts counts,
            String errorMessage
    ) throws SQLException {
        String sql = """
                UPDATE """ + " " + qualified("uss_directory_import") + """
                 SET status = ?, finished_at = CURRENT_TIMESTAMP(6), row_count = ?, created_count = ?,
                     updated_count = ?, unchanged_count = ?, reactivated_count = ?, inactivated_count = ?,
                     issue_count = ?, skipped_count = ?, error_message = ?
                WHERE id = ? AND status = 'RUNNING'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, counts.rowCount);
            statement.setInt(3, counts.created);
            statement.setInt(4, counts.updated);
            statement.setInt(5, counts.unchanged);
            statement.setInt(6, counts.reactivated);
            statement.setInt(7, counts.inactivated);
            statement.setInt(8, counts.issues);
            statement.setInt(9, counts.rejected);
            statement.setString(10, errorMessage);
            statement.setLong(11, importId);
            return statement.executeUpdate();
        }
    }

    public long insertPerson(
            Connection connection,
            DirectoryIncomingRow row,
            byte[] rutEncrypted,
            long importId
    ) throws SQLException {
        String uuid = UUID.randomUUID().toString();
        String display = cl.uss.ca.directory.DirectoryDisplayName.of(
                row.givenName, row.middleName, row.familyName, row.secondFamilyName);
        String sql = """
                INSERT INTO """ + " " + qualified("uss_directory_person") + """
                 (uuid, given_name, middle_name, family_name, second_family_name, display_name,
                  campus_name, faculty_or_vr_raw, faculty_or_vr_label, position_title, primary_email_normalized,
                  rut_encrypted, rut_hmac, active_in_directory, last_seen_in_directory_at, last_seen_import_id,
                  last_change_detected_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP(6), ?, CURRENT_TIMESTAMP(6))
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            statement.setString(i++, uuid);
            statement.setString(i++, row.givenName);
            statement.setString(i++, row.middleName);
            statement.setString(i++, row.familyName);
            statement.setString(i++, row.secondFamilyName);
            statement.setString(i++, display);
            statement.setString(i++, row.campusName);
            statement.setString(i++, row.facultyRaw);
            statement.setString(i++, cl.uss.ca.directory.DirectoryFaculty.label(row.facultyRaw));
            statement.setString(i++, row.positionTitle);
            statement.setString(i++, row.emailNormalized);
            if (rutEncrypted == null) {
                statement.setNull(i++, Types.VARBINARY);
            } else {
                statement.setBytes(i++, rutEncrypted);
            }
            statement.setString(i++, row.rutHmac);
            statement.setLong(i, importId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("directory person id was not generated");
                }
                return keys.getLong(1);
            }
        }
    }

    public void updatePerson(
            Connection connection,
            long personId,
            DirectoryIncomingRow row,
            byte[] rutEncrypted,
            long importId,
            boolean reactivate
    ) throws SQLException {
        String display = cl.uss.ca.directory.DirectoryDisplayName.of(
                row.givenName, row.middleName, row.familyName, row.secondFamilyName);
        String sql = """
                UPDATE """ + " " + qualified("uss_directory_person") + """
                 SET given_name = ?, middle_name = ?, family_name = ?, second_family_name = ?, display_name = ?,
                     campus_name = ?, faculty_or_vr_raw = ?, faculty_or_vr_label = ?, position_title = ?,
                     primary_email_normalized = ?, rut_encrypted = COALESCE(?, rut_encrypted), rut_hmac = COALESCE(?, rut_hmac),
                     active_in_directory = 1, last_seen_in_directory_at = CURRENT_TIMESTAMP(6),
                     last_seen_import_id = ?, last_change_detected_at = CURRENT_TIMESTAMP(6),
                     removed_from_directory_at = NULL
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            statement.setString(i++, row.givenName);
            statement.setString(i++, row.middleName);
            statement.setString(i++, row.familyName);
            statement.setString(i++, row.secondFamilyName);
            statement.setString(i++, display);
            statement.setString(i++, row.campusName);
            statement.setString(i++, row.facultyRaw);
            statement.setString(i++, cl.uss.ca.directory.DirectoryFaculty.label(row.facultyRaw));
            statement.setString(i++, row.positionTitle);
            statement.setString(i++, row.emailNormalized);
            if (rutEncrypted == null) {
                statement.setNull(i++, Types.VARBINARY);
            } else {
                statement.setBytes(i++, rutEncrypted);
            }
            statement.setString(i++, row.rutHmac);
            statement.setLong(i++, importId);
            statement.setLong(i, personId);
            statement.executeUpdate();
        }
        if (!reactivate) {
            // last_change already set
        }
    }

    public void touchUnchanged(Connection connection, long personId, long importId) throws SQLException {
        String sql = """
                UPDATE """ + " " + qualified("uss_directory_person") + """
                 SET last_seen_in_directory_at = CURRENT_TIMESTAMP(6), last_seen_import_id = ?,
                     active_in_directory = 1, removed_from_directory_at = NULL
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, importId);
            statement.setLong(2, personId);
            statement.executeUpdate();
        }
    }

    public void upsertPrimaryEmail(Connection connection, long personId, String original, String normalized, long importId)
            throws SQLException {
        retireOtherPrimaries(connection, personId, normalized, importId);
        String sql = """
                INSERT INTO """ + " " + qualified("uss_directory_person_email") + """
                 (directory_person_id, email_original, email_normalized, source, kind, active, first_seen_import_id, last_seen_import_id)
                VALUES (?, ?, ?, 'EMAIL_COLUMN', 'PRIMARY', 1, ?, ?)
                ON DUPLICATE KEY UPDATE
                    directory_person_id = VALUES(directory_person_id),
                    email_original = VALUES(email_original),
                    source = VALUES(source),
                    kind = 'PRIMARY',
                    active = 1,
                    last_seen_import_id = VALUES(last_seen_import_id)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            statement.setString(2, original == null ? normalized : original);
            statement.setString(3, normalized);
            statement.setLong(4, importId);
            statement.setLong(5, importId);
            statement.executeUpdate();
        }
    }

    public void upsertAlternateEmail(Connection connection, long personId, String original, String normalized, long importId)
            throws SQLException {
        String sql = """
                INSERT INTO """ + " " + qualified("uss_directory_person_email") + """
                 (directory_person_id, email_original, email_normalized, source, kind, active, first_seen_import_id, last_seen_import_id)
                VALUES (?, ?, ?, 'EMPRESA_COLUMN', 'ALTERNATE', 1, ?, ?)
                ON DUPLICATE KEY UPDATE
                    last_seen_import_id = VALUES(last_seen_import_id),
                    active = 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            statement.setString(2, original);
            statement.setString(3, normalized);
            statement.setLong(4, importId);
            statement.setLong(5, importId);
            statement.executeUpdate();
        }
    }

    private void retireOtherPrimaries(Connection connection, long personId, String keepNormalized, long importId)
            throws SQLException {
        String sql = """
                UPDATE """ + " " + qualified("uss_directory_person_email") + """
                 SET kind = 'FORMER', last_seen_import_id = ?
                WHERE directory_person_id = ? AND kind = 'PRIMARY' AND email_normalized <> ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, importId);
            statement.setLong(2, personId);
            statement.setString(3, keepNormalized);
            statement.executeUpdate();
        }
    }

    public void insertHistory(Connection connection, long personId, long importId, String snapshotJson, String hash)
            throws SQLException {
        String sql = """
                INSERT INTO """ + " " + qualified("uss_directory_person_history") + """
                 (directory_person_id, import_id, snapshot_json, source_row_hash)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE snapshot_json = VALUES(snapshot_json), source_row_hash = VALUES(source_row_hash)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            statement.setLong(2, importId);
            statement.setString(3, snapshotJson);
            statement.setString(4, hash);
            statement.executeUpdate();
        }
    }

    public void insertImportRow(
            Connection connection,
            long importId,
            DirectoryPlannedRow planned,
            Long personId,
            byte[] rutEncrypted
    ) throws SQLException {
        DirectoryIncomingRow row = planned.incoming;
        String sql = """
                INSERT INTO """ + " " + qualified("uss_directory_import_row") + """
                 (import_id, source_row_number, empresa_raw, rut_encrypted, rut_hmac, given_name, middle_name,
                  family_name, second_family_name, campus_name, faculty_or_vr_raw, faculty_or_vr_label, position_title,
                  email_raw, email_normalized, source_row_hash, outcome, directory_person_id, issue_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            statement.setLong(i++, importId);
            statement.setInt(i++, row.sourceRowNumber);
            statement.setString(i++, row.empresaRaw);
            if (rutEncrypted == null) {
                statement.setNull(i++, Types.VARBINARY);
            } else {
                statement.setBytes(i++, rutEncrypted);
            }
            statement.setString(i++, row.rutHmac);
            statement.setString(i++, row.givenName);
            statement.setString(i++, row.middleName);
            statement.setString(i++, row.familyName);
            statement.setString(i++, row.secondFamilyName);
            statement.setString(i++, row.campusName);
            statement.setString(i++, row.facultyRaw);
            statement.setString(i++, cl.uss.ca.directory.DirectoryFaculty.label(row.facultyRaw));
            statement.setString(i++, row.positionTitle);
            statement.setString(i++, row.emailNormalized);
            statement.setString(i++, row.emailNormalized);
            statement.setString(i++, row.snapshotHash);
            statement.setString(i++, planned.decision.name());
            if (personId == null) {
                statement.setNull(i++, Types.BIGINT);
            } else {
                statement.setLong(i++, personId);
            }
            statement.setString(i, planned.issueCode);
            statement.executeUpdate();
        }
    }

    public void insertIssue(Connection connection, long importId, DirectoryIssue issue) throws SQLException {
        String sql = """
                INSERT INTO """ + " " + qualified("uss_directory_import_issue") + """
                 (import_id, issue_code, detail, left_person_id, right_person_id)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, importId);
            statement.setString(2, issue.code);
            statement.setString(3, "row=" + issue.sourceRowNumber);
            if (issue.leftPersonId == null) {
                statement.setNull(4, Types.BIGINT);
            } else {
                statement.setLong(4, issue.leftPersonId);
            }
            if (issue.rightPersonId == null) {
                statement.setNull(5, Types.BIGINT);
            } else {
                statement.setLong(5, issue.rightPersonId);
            }
            statement.executeUpdate();
        }
    }

    public int markMissing(Connection connection, long importId) throws SQLException {
        String select = """
                SELECT id FROM """ + " " + qualified("uss_directory_person") + """
                 WHERE COALESCE(active_in_directory, 1) = 1
                   AND (last_seen_import_id IS NULL OR last_seen_import_id <> ?)
                """;
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setLong(1, importId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        if (ids.isEmpty()) {
            return 0;
        }
        String update = """
                UPDATE """ + " " + qualified("uss_directory_person") + """
                 SET active_in_directory = 0, removed_from_directory_at = CURRENT_TIMESTAMP(6),
                     last_change_detected_at = CURRENT_TIMESTAMP(6)
                WHERE COALESCE(active_in_directory, 1) = 1
                  AND (last_seen_import_id IS NULL OR last_seen_import_id <> ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setLong(1, importId);
            statement.executeUpdate();
        }
        return ids.size();
    }

    public DirectoryStatsDto stats(String search, String presence) {
        DirectoryStatsDto dto = new DirectoryStatsDto();
        if (!tableExists("uss_directory_person")) {
            return dto;
        }
        dto.total = countPeople(search, "ALL");
        dto.active = countPeople(search, "ACTIVE");
        dto.inactive = countPeople(search, "INACTIVE");
        if (tableExists("uss_directory_person_pure_link")) {
            List<Object> params = new ArrayList<>();
            String where = personWhere(search, presence, params);
            String sql = "SELECT COUNT(*) FROM " + qualified("uss_directory_person") + " p JOIN "
                    + qualified("uss_directory_person_pure_link") + " l ON l.directory_person_id = p.id" + where;
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params.toArray());
                try (ResultSet rs = statement.executeQuery()) {
                    dto.withPureLink = rs.next() ? rs.getLong(1) : 0L;
                }
            } catch (SQLException ex) {
                throw new DatabaseException("Failed to count directory PURE links", ex);
            }
        }
        return dto;
    }

    public long countPeople(String search, String presence) {
        if (!tableExists("uss_directory_person")) {
            return 0L;
        }
        List<Object> params = new ArrayList<>();
        String where = personWhere(search, presence, params);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + qualified("uss_directory_person") + " p" + where)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count directory people", ex);
        }
    }

    public List<DirectoryPersonSummaryDto> findPeople(String search, String presence, int page, int size) {
        if (!tableExists("uss_directory_person")) {
            return List.of();
        }
        List<Object> params = new ArrayList<>();
        String where = personWhere(search, presence, params);
        String link = tableExists("uss_directory_person_pure_link")
                ? " LEFT JOIN " + qualified("uss_directory_person_pure_link") + " l ON l.directory_person_id = p.id"
                : "";
        String sql = """
                SELECT p.id, p.uuid, p.display_name, p.primary_email_normalized, p.campus_name,
                       p.faculty_or_vr_label, p.position_title, p.active_in_directory
                """ + (tableExists("uss_directory_person_pure_link") ? ", l.pure_person_uuid" : ", CAST(NULL AS CHAR) AS pure_person_uuid") + """
                
                FROM """ + " " + qualified("uss_directory_person") + " p" + link + where + """
                
                ORDER BY p.display_name ASC, p.id ASC
                LIMIT ? OFFSET ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindList(statement, params);
            statement.setInt(index, size);
            statement.setLong(index + 1, (long) page * size);
            try (ResultSet rs = statement.executeQuery()) {
                List<DirectoryPersonSummaryDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapSummary(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list directory people", ex);
        }
    }

    public DirectoryPersonDetailDto findPerson(long id) {
        if (!tableExists("uss_directory_person")) {
            throw new NotFoundException("DIRECTORY_PERSON_NOT_FOUND", "Directory person not found");
        }
        String sql = """
                SELECT p.id, p.uuid, p.display_name, p.primary_email_normalized, p.campus_name,
                       p.faculty_or_vr_label, p.faculty_or_vr_raw, p.position_title, p.active_in_directory,
                       p.given_name, p.middle_name, p.family_name, p.second_family_name,
                       p.last_seen_in_directory_at, p.removed_from_directory_at
                FROM """ + " " + qualified("uss_directory_person") + """
                 p WHERE p.id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("DIRECTORY_PERSON_NOT_FOUND", "Directory person not found");
                }
                DirectoryPersonDetailDto dto = new DirectoryPersonDetailDto();
                fillSummary(dto, rs);
                dto.givenName = nullableString(rs, "given_name");
                dto.middleName = nullableString(rs, "middle_name");
                dto.familyName = nullableString(rs, "family_name");
                dto.secondFamilyName = nullableString(rs, "second_family_name");
                dto.facultyRaw = nullableString(rs, "faculty_or_vr_raw");
                Timestamp seen = rs.getTimestamp("last_seen_in_directory_at");
                dto.lastSeenAt = seen == null ? null : seen.toInstant().toString();
                Timestamp removed = rs.getTimestamp("removed_from_directory_at");
                dto.removedAt = removed == null ? null : removed.toInstant().toString();
                dto.emails = findEmails(connection, id);
                dto.linkedToPure = tableExists("uss_directory_person_pure_link")
                        && scalarLong(
                                "SELECT COUNT(*) FROM " + qualified("uss_directory_person_pure_link")
                                        + " WHERE directory_person_id = ?",
                                id
                        ) > 0;
                return dto;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load directory person", ex);
        }
    }

    public DirectoryImportSummaryDto findImport(long id) {
        if (!tableExists("uss_directory_import")) {
            throw new NotFoundException("DIRECTORY_IMPORT_NOT_FOUND", "Directory import not found");
        }
        String sql = "SELECT " + importSelectColumns() + " FROM " + qualified("uss_directory_import") + " WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("DIRECTORY_IMPORT_NOT_FOUND", "Directory import not found");
                }
                return mapImport(rs);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load directory import", ex);
        }
    }

    public List<DirectoryImportSummaryDto> findImports() {
        if (!tableExists("uss_directory_import")) {
            return List.of();
        }
        String sql = "SELECT " + importSelectColumns() + " FROM " + qualified("uss_directory_import")
                + " ORDER BY id DESC LIMIT 50";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<DirectoryImportSummaryDto> items = new ArrayList<>();
            while (rs.next()) {
                items.add(mapImport(rs));
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list directory imports", ex);
        }
    }

    public List<DirectoryIssueDto> findIssues(long importId) {
        if (!tableExists("uss_directory_import_issue")) {
            return List.of();
        }
        String sql = """
                SELECT issue_code, detail, left_person_id, right_person_id, resolved
                FROM """ + " " + qualified("uss_directory_import_issue") + """
                 WHERE import_id = ? ORDER BY id
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, importId);
            try (ResultSet rs = statement.executeQuery()) {
                List<DirectoryIssueDto> items = new ArrayList<>();
                while (rs.next()) {
                    DirectoryIssueDto dto = new DirectoryIssueDto();
                    dto.code = rs.getString("issue_code");
                    String detail = rs.getString("detail");
                    dto.sourceRowNumber = parseRow(detail);
                    dto.leftPersonId = nullableLong(rs, "left_person_id");
                    dto.rightPersonId = nullableLong(rs, "right_person_id");
                    dto.resolved = rs.getBoolean("resolved");
                    items.add(dto);
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list directory issues", ex);
        }
    }

    private List<DirectoryEmailDto> findEmails(Connection connection, long personId) throws SQLException {
        if (!tableExists("uss_directory_person_email")) {
            return List.of();
        }
        String sql = """
                SELECT email_normalized, kind, source, active
                FROM """ + " " + qualified("uss_directory_person_email") + """
                 WHERE directory_person_id = ? ORDER BY kind, id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                List<DirectoryEmailDto> items = new ArrayList<>();
                while (rs.next()) {
                    DirectoryEmailDto dto = new DirectoryEmailDto();
                    dto.email = rs.getString("email_normalized");
                    dto.kind = rs.getString("kind");
                    dto.source = rs.getString("source");
                    dto.active = rs.getBoolean("active");
                    items.add(dto);
                }
                return items;
            }
        }
    }

    private String personWhere(String search, String presence, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        String value = presence == null ? "ACTIVE" : presence.trim().toUpperCase(Locale.ROOT);
        if ("INACTIVE".equals(value)) {
            where.append(" AND COALESCE(p.active_in_directory, 1) = 0");
        } else if (!"ALL".equals(value)) {
            where.append(" AND COALESCE(p.active_in_directory, 1) = 1");
        }
        if (search != null && !search.isBlank()) {
            String like = likeContains(search.trim());
            where.append(" AND (p.display_name LIKE ? ESCAPE '!' OR p.primary_email_normalized LIKE ? ESCAPE '!'");
            where.append(" OR p.campus_name LIKE ? ESCAPE '!' OR p.faculty_or_vr_label LIKE ? ESCAPE '!'");
            where.append(" OR p.position_title LIKE ? ESCAPE '!' OR p.uuid LIKE ? ESCAPE '!')");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        return where.toString();
    }

    private static int bindList(PreparedStatement statement, List<Object> params) throws SQLException {
        int index = 1;
        for (Object param : params) {
            statement.setObject(index++, param);
        }
        return index;
    }

    private String importSelectColumns() {
        StringBuilder columns = new StringBuilder();
        columns.append("id, public_id, original_filename, status, imported_by, started_at, finished_at, ");
        columns.append("row_count, created_count, updated_count, unchanged_count, reactivated_count, ");
        columns.append("inactivated_count, issue_count, error_message");
        if (hasOwnershipColumns()) {
            columns.append(", owner_token, heartbeat_at, expected_count, processed_count");
        }
        return columns.toString();
    }

    private boolean hasOwnershipColumns() {
        return columnExists("uss_directory_import", "owner_token")
                && columnExists("uss_directory_import", "heartbeat_at")
                && columnExists("uss_directory_import", "expected_count")
                && columnExists("uss_directory_import", "processed_count");
    }

    private DirectoryImportSummaryDto mapImport(ResultSet rs) throws SQLException {
        DirectoryImportSummaryDto dto = new DirectoryImportSummaryDto();
        dto.id = rs.getLong("id");
        dto.publicId = rs.getString("public_id");
        dto.filename = rs.getString("original_filename");
        dto.status = rs.getString("status");
        dto.importedBy = rs.getString("imported_by");
        Timestamp started = rs.getTimestamp("started_at");
        dto.startedAt = started == null ? null : started.toInstant().toString();
        Timestamp finished = rs.getTimestamp("finished_at");
        dto.finishedAt = finished == null ? null : finished.toInstant().toString();
        dto.rowCount = rs.getInt("row_count");
        dto.createdCount = rs.getInt("created_count");
        dto.updatedCount = rs.getInt("updated_count");
        dto.unchangedCount = rs.getInt("unchanged_count");
        dto.reactivatedCount = rs.getInt("reactivated_count");
        dto.inactivatedCount = rs.getInt("inactivated_count");
        dto.issueCount = rs.getInt("issue_count");
        dto.errorMessage = nullableString(rs, "error_message");
        if (hasOwnershipColumns()) {
            Timestamp heartbeat = rs.getTimestamp("heartbeat_at");
            dto.heartbeatAt = heartbeat == null ? null : heartbeat.toInstant().toString();
            dto.expectedCount = rs.getInt("expected_count");
            dto.processedCount = rs.getInt("processed_count");
        }
        return dto;
    }

    /**
     * Lookup used by Events after the person verified the email.
     * Does not expose RUT. Absence is not classified as "external".
     */
    public DirectoryPersonSummaryDto findActiveByNormalizedEmail(String emailNormalized) {
        if (!available() || emailNormalized == null || emailNormalized.isBlank()) {
            return null;
        }
        String emailJoin = tableExists("uss_directory_person_email")
                ? " LEFT JOIN " + qualified("uss_directory_person_email")
                + " e ON e.directory_person_id = p.id AND e.active = 1"
                : "";
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.id, p.uuid, p.display_name, p.primary_email_normalized, p.campus_name, ");
        sql.append("p.faculty_or_vr_label, p.position_title, p.active_in_directory, ");
        sql.append("CAST(NULL AS CHAR) AS pure_person_uuid FROM ");
        sql.append(qualified("uss_directory_person")).append(" p").append(emailJoin);
        sql.append(" WHERE p.active_in_directory = 1 AND (p.primary_email_normalized = ?");
        if (tableExists("uss_directory_person_email")) {
            sql.append(" OR e.email_normalized = ?");
        }
        sql.append(") ORDER BY p.id ASC LIMIT 1");
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, emailNormalized);
            if (tableExists("uss_directory_person_email")) {
                statement.setString(2, emailNormalized);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapSummary(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to match directory email", ex);
        }
    }

    private DirectoryPersonSummaryDto mapSummary(ResultSet rs) throws SQLException {
        DirectoryPersonSummaryDto dto = new DirectoryPersonSummaryDto();
        fillSummary(dto, rs);
        return dto;
    }

    private static void fillSummary(DirectoryPersonSummaryDto dto, ResultSet rs) throws SQLException {
        dto.id = rs.getLong("id");
        dto.uuid = rs.getString("uuid");
        dto.displayName = nullableString(rs, "display_name");
        dto.email = nullableString(rs, "primary_email_normalized");
        dto.campusName = nullableString(rs, "campus_name");
        dto.faculty = nullableString(rs, "faculty_or_vr_label");
        dto.positionTitle = nullableString(rs, "position_title");
        dto.activeInDirectory = rs.getBoolean("active_in_directory");
        try {
            dto.linkedToPure = nullableString(rs, "pure_person_uuid") != null;
        } catch (SQLException ignored) {
            dto.linkedToPure = false;
        }
    }

    private static String snapshotHint(
            String given,
            String middle,
            String family,
            String second,
            String campus,
            String faculty,
            String position,
            String email
    ) {
        DirectoryIncomingRow row = new DirectoryIncomingRow(
                0, email, null, null, given, middle, family, second, campus, faculty, position, email, null
        );
        return cl.uss.ca.directory.DirectorySnapshotHash.of(row);
    }

    private static int parseRow(String detail) {
        if (detail == null || !detail.startsWith("row=")) {
            return 0;
        }
        try {
            return Integer.parseInt(detail.substring(4));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public static final class DirectoryImportCounts {
        public int rowCount;
        public int created;
        public int updated;
        public int unchanged;
        public int reactivated;
        public int rejected;
        public int inactivated;
        public int issues;
    }
}
