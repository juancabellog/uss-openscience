package cl.uss.ca.repository;

import cl.uss.ca.events.EventDateTimes;
import cl.uss.ca.events.EventRules;
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
import java.util.Locale;

@ApplicationScoped
public class EventRepository extends JdbcRepository {

    private static final String EVENT_COLUMNS = """
            e.id, e.public_slug, e.title, e.description, e.event_type, e.starts_at, e.ends_at, e.timezone,
            e.modality, e.location_name, e.campus_name, e.organizing_unit, e.publication_status,
            e.registration_opens_at, e.registration_closes_at, e.capacity, e.blank_signature_rows,
            e.created_by, e.created_at, e.updated_at
            """;

    private String eventColumns() {
        String extra = "";
        if (hasVirtualAccessColumn()) {
            extra += ", e.virtual_access";
        }
        if (hasAllowDuringColumn()) {
            extra += ", e.allow_registration_during_event";
        }
        if (hasTitleEnColumn()) {
            extra += ", e.title_en, e.description_en, e.primary_language";
        }
        if (hasOrganizingUnitCodeColumn()) {
            extra += ", e.organizing_unit_code";
        }
        return EVENT_COLUMNS + extra;
    }

    public boolean available() {
        return tableExists("event")
                && tableExists("event_participant")
                && tableExists("event_registration")
                && tableExists("event_registration_status_history")
                && tableExists("event_email_challenge")
                && tableExists("event_verified_session");
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

    public Connection openTransaction() {
        try {
            Connection connection = connection();
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to open events transaction", ex);
        }
    }

    public int countPublished() {
        if (!tableExists("event")) {
            return 0;
        }
        return (int) scalarLong(
                "SELECT COUNT(*) FROM " + qualified("event") + " WHERE publication_status = 'PUBLISHED'"
        );
    }

    public List<EventRecord> findPublished() {
        if (!tableExists("event")) {
            return List.of();
        }
        String sql = "SELECT " + eventColumns() + ", " + confirmedSelect() + " AS confirmed_count FROM "
                + qualified("event") + " e WHERE e.publication_status = 'PUBLISHED' "
                + "ORDER BY e.starts_at IS NULL, e.starts_at ASC, e.id DESC LIMIT 100";
        return queryList(sql);
    }

    public EventRecord findPublishedBySlug(String slug) {
        EventRecord record = findBySlug(slug);
        if (record == null || !"PUBLISHED".equals(record.publicationStatus)) {
            return null;
        }
        return record;
    }

    public EventRecord findBySlug(String slug) {
        if (!available() || slug == null || slug.isBlank()) {
            return null;
        }
        String sql = "SELECT " + eventColumns() + ", " + confirmedSelect() + " AS confirmed_count FROM "
                + qualified("event") + " e WHERE e.public_slug = ?";
        return queryOne(sql, slug.trim().toLowerCase(Locale.ROOT));
    }

    public EventRecord findById(long id) {
        if (!available()) {
            return null;
        }
        String sql = "SELECT " + eventColumns() + ", " + confirmedSelect() + " AS confirmed_count FROM "
                + qualified("event") + " e WHERE e.id = ?";
        return queryOne(sql, id);
    }

    public EventRecord requireById(long id) {
        EventRecord record = findById(id);
        if (record == null) {
            throw new NotFoundException("EVENT_NOT_FOUND", "Event not found");
        }
        return record;
    }

    public List<EventRecord> listAdmin(String status, String type, String query) {
        if (!available()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("SELECT " + eventColumns() + ", " + confirmedSelect()
                + " AS confirmed_count FROM " + qualified("event") + " e WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND e.publication_status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND e.event_type = ?");
            params.add(type.trim().toUpperCase(Locale.ROOT));
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (e.title LIKE ? ESCAPE '!' OR e.public_slug LIKE ? ESCAPE '!' OR e.organizing_unit LIKE ? ESCAPE '!')");
            String like = "%" + query.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY e.starts_at IS NULL, e.starts_at DESC, e.id DESC LIMIT 200");
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                List<EventRecord> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapEvent(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list events", ex);
        }
    }

    public boolean slugTaken(String slug, Long excludeId) {
        if (!tableExists("event") || slug == null || slug.isBlank()) {
            return false;
        }
        if (excludeId == null) {
            return scalarLong("SELECT COUNT(*) FROM " + qualified("event") + " WHERE public_slug = ?", slug) > 0;
        }
        return scalarLong(
                "SELECT COUNT(*) FROM " + qualified("event") + " WHERE public_slug = ? AND id <> ?",
                slug,
                excludeId
        ) > 0;
    }

    public boolean hasVirtualAccessColumn() {
        return columnExists("event", "virtual_access");
    }

    public boolean hasAllowDuringColumn() {
        return columnExists("event", "allow_registration_during_event");
    }

    public boolean hasTitleEnColumn() {
        return columnExists("event", "title_en");
    }

    public boolean hasOrganizingUnitCodeColumn() {
        return columnExists("event", "organizing_unit_code");
    }

    public long insert(EventRecord record) {
        String sql = """
                INSERT INTO """ + " " + qualified("event") + """
                 (public_slug, title, description, event_type, starts_at, ends_at, timezone, modality,
                  location_name, campus_name, organizing_unit, publication_status, registration_opens_at,
                  registration_closes_at, capacity, blank_signature_rows, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindWritable(statement, record);
            statement.setString(17, record.createdBy);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("event id was not generated");
                }
                long id = keys.getLong(1);
                persistExtras(id, record);
                return id;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to create event", ex);
        }
    }

    public void update(EventRecord record) {
        String sql = """
                UPDATE """ + " " + qualified("event") + """
                 SET public_slug = ?, title = ?, description = ?, event_type = ?, starts_at = ?, ends_at = ?,
                     timezone = ?, modality = ?, location_name = ?, campus_name = ?, organizing_unit = ?,
                     publication_status = ?, registration_opens_at = ?, registration_closes_at = ?,
                     capacity = ?, blank_signature_rows = ?
                 WHERE id = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindWritable(statement, record);
            statement.setLong(17, record.id);
            if (statement.executeUpdate() == 0) {
                throw new NotFoundException("EVENT_NOT_FOUND", "Event not found");
            }
            persistExtras(record.id, record);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to update event", ex);
        }
    }

    public EventRecord lockById(Connection connection, long id) throws SQLException {
        String sql = "SELECT " + eventColumns() + ", 0 AS confirmed_count FROM " + qualified("event")
                + " e WHERE e.id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapEvent(rs) : null;
            }
        }
    }

    public int countConfirmed(Connection connection, long eventId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + qualified("event_registration")
                + " WHERE event_id = ? AND status = 'CONFIRMED'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private String confirmedSelect() {
        if (!tableExists("event_registration")) {
            return "0";
        }
        return "(SELECT COUNT(*) FROM " + qualified("event_registration")
                + " r WHERE r.event_id = e.id AND r.status = 'CONFIRMED')";
    }

    private List<EventRecord> queryList(String sql, Object... params) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<EventRecord> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapEvent(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list published events", ex);
        }
    }

    private EventRecord queryOne(String sql, Object... params) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapEvent(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load event", ex);
        }
    }

    private void bindWritable(PreparedStatement statement, EventRecord record) throws SQLException {
        statement.setString(1, record.publicSlug);
        statement.setString(2, record.title);
        statement.setString(3, record.description);
        statement.setString(4, record.eventType);
        EventDateTimes.setWallClock(statement, 5, record.startsAt);
        EventDateTimes.setWallClock(statement, 6, record.endsAt);
        statement.setString(7, record.timezone);
        statement.setString(8, record.modality);
        statement.setString(9, record.locationName);
        statement.setString(10, record.campusName);
        statement.setString(11, record.organizingUnit);
        statement.setString(12, record.publicationStatus);
        EventDateTimes.setWallClock(statement, 13, record.registrationOpensAt);
        EventDateTimes.setWallClock(statement, 14, record.registrationClosesAt);
        if (record.capacity == null) {
            statement.setNull(15, Types.INTEGER);
        } else {
            statement.setInt(15, record.capacity);
        }
        statement.setInt(16, record.blankSignatureRows);
    }

    private static void setTimestamp(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, EventDateTimes.timestamp(value));
        }
    }

    private static EventRecord mapEvent(ResultSet rs) throws SQLException {
        EventRecord record = new EventRecord();
        record.id = rs.getLong("id");
        record.publicSlug = rs.getString("public_slug");
        record.title = rs.getString("title");
        record.description = nullableString(rs, "description");
        record.eventType = rs.getString("event_type");
        record.startsAt = EventDateTimes.readWallClock(rs, "starts_at");
        record.endsAt = EventDateTimes.readWallClock(rs, "ends_at");
        record.timezone = nullableString(rs, "timezone");
        record.modality = nullableString(rs, "modality");
        record.locationName = nullableString(rs, "location_name");
        record.campusName = nullableString(rs, "campus_name");
        record.organizingUnit = nullableString(rs, "organizing_unit");
        record.publicationStatus = rs.getString("publication_status");
        record.registrationOpensAt = EventDateTimes.readWallClock(rs, "registration_opens_at");
        record.registrationClosesAt = EventDateTimes.readWallClock(rs, "registration_closes_at");
        int capacity = rs.getInt("capacity");
        record.capacity = rs.wasNull() ? null : capacity;
        record.blankSignatureRows = rs.getInt("blank_signature_rows");
        record.createdBy = nullableString(rs, "created_by");
        record.createdAt = local(rs.getTimestamp("created_at"));
        record.updatedAt = local(rs.getTimestamp("updated_at"));
        record.confirmedCount = rs.getInt("confirmed_count");
        if (hasColumn(rs, "virtual_access")) {
            record.virtualAccess = nullableString(rs, "virtual_access");
        }
        if (hasColumn(rs, "allow_registration_during_event")) {
            record.allowRegistrationDuringEvent = rs.getBoolean("allow_registration_during_event");
        } else {
            record.allowRegistrationDuringEvent = EventRules.inferAllowDuring(record.startsAt, record.registrationClosesAt);
        }
        if (hasColumn(rs, "title_en")) {
            record.titleEn = nullableString(rs, "title_en");
            record.descriptionEn = nullableString(rs, "description_en");
            record.primaryLanguage = nullableString(rs, "primary_language");
        }
        if (hasColumn(rs, "organizing_unit_code")) {
            record.organizingUnitCode = nullableString(rs, "organizing_unit_code");
        }
        return record;
    }

    private void persistExtras(long id, EventRecord record) {
        if (hasVirtualAccessColumn()) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE " + qualified("event") + " SET virtual_access = ? WHERE id = ?"
                 )) {
                statement.setString(1, record.virtualAccess);
                statement.setLong(2, id);
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new DatabaseException("Failed to store virtual access", ex);
            }
        }
        if (hasAllowDuringColumn()) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE " + qualified("event") + " SET allow_registration_during_event = ? WHERE id = ?"
                 )) {
                statement.setBoolean(1, record.allowRegistrationDuringEvent);
                statement.setLong(2, id);
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new DatabaseException("Failed to store registration-during-event flag", ex);
            }
        }
        if (hasTitleEnColumn()) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE " + qualified("event")
                                 + " SET title_en = ?, description_en = ?, primary_language = ? WHERE id = ?"
                 )) {
                statement.setString(1, record.titleEn);
                statement.setString(2, record.descriptionEn);
                statement.setString(3, record.primaryLanguage == null ? "es" : record.primaryLanguage);
                statement.setLong(4, id);
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new DatabaseException("Failed to store event translations", ex);
            }
        }
        if (hasOrganizingUnitCodeColumn()) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE " + qualified("event") + " SET organizing_unit_code = ? WHERE id = ?"
                 )) {
                statement.setString(1, record.organizingUnitCode);
                statement.setLong(2, id);
                statement.executeUpdate();
            } catch (SQLException ex) {
                throw new DatabaseException("Failed to store organizing unit code", ex);
            }
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

    private static LocalDateTime local(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
