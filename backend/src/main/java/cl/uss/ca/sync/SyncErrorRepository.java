package cl.uss.ca.sync;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.SyncErrorDto;
import cl.uss.ca.api.dto.SyncErrorGroupDto;
import cl.uss.ca.api.dto.SyncErrorPageDto;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.repository.JdbcRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class SyncErrorRepository extends JdbcRepository {

    public boolean available() {
        return tableExists("sync_error");
    }

    public void save(SyncItemError error) {
        if (!available() || error == null || error.syncId == null) {
            return;
        }
        boolean hasKey = columnExists("sync_error", "record_key");
        String sql = hasKey
                ? """
                INSERT INTO """ + " " + qualified("sync_error") + """
                
                (sync_execution_id, entity, local_record_id, pure_id, record_uuid, record_key,
                 stage, error_code, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6))
                """
                : """
                INSERT INTO """ + " " + qualified("sync_error") + """
                
                (sync_execution_id, entity, local_record_id, pure_id, record_uuid,
                 stage, error_code, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(6))
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSyncId(statement, 1, "sync_error", "sync_execution_id", error.syncId);
            statement.setString(2, error.entity);
            setLong(statement, 3, error.localRecordId);
            setLong(statement, 4, error.pureId);
            statement.setString(5, SyncAuditFields.uuid(error.recordUuid));
            int index = 6;
            if (hasKey) {
                statement.setString(index++, SyncAuditFields.key(error.recordKey));
            }
            statement.setString(index++, clip(error.stage, 64));
            statement.setString(index++, clip(error.errorCode, 100));
            statement.setString(index, clip(error.errorMessage, 2000));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to persist sync error", ex);
        }
    }

    public PageResponse<SyncErrorDto> findByEntity(String entity, String stage, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        if (!available() || entity == null || entity.isBlank()) {
            return PageResponse.of(List.of(), safePage - 1, safeSize, 0);
        }
        boolean hasKey = columnExists("sync_error", "record_key");
        boolean hasCreated = columnExists("sync_error", "created_at");
        StringBuilder where = new StringBuilder(" WHERE entity = ?");
        List<Object> params = new ArrayList<>();
        params.add(entity.trim());
        if (stage != null && !stage.isBlank()) {
            where.append(" AND stage = ?");
            params.add(stage.trim());
        }
        long total = scalarLong("SELECT COUNT(*) FROM " + qualified("sync_error") + where, params.toArray());
        String sql = """
                SELECT id, sync_execution_id, entity, local_record_id, pure_id, record_uuid,
                       %s stage, error_code, error_message%s
                FROM %s
                %s
                ORDER BY %sid DESC
                LIMIT ? OFFSET ?
                """.formatted(
                hasKey ? "record_key," : "",
                hasCreated ? ", created_at" : "",
                qualified("sync_error"),
                where,
                hasCreated ? "created_at DESC, " : ""
        );
        List<SyncErrorDto> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object param : params) {
                statement.setObject(index++, param);
            }
            statement.setInt(index++, safeSize);
            statement.setInt(index, (safePage - 1) * safeSize);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(mapError(rs, hasKey, hasCreated));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list sync errors by entity", ex);
        }
        return PageResponse.of(items, safePage - 1, safeSize, total);
    }

    public PageResponse<SyncErrorDto> findItemsPage(String syncId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        if (!available() || syncId == null || syncId.isBlank()) {
            return PageResponse.of(List.of(), safePage - 1, safeSize, 0);
        }
        boolean hasKey = columnExists("sync_error", "record_key");
        boolean hasCreated = columnExists("sync_error", "created_at");
        try (Connection connection = connection()) {
            long total = count(connection, syncId);
            String sql = """
                    SELECT id, sync_execution_id, entity, local_record_id, pure_id, record_uuid,
                           %s stage, error_code, error_message%s
                    FROM %s
                    WHERE sync_execution_id = ?
                    ORDER BY %sid DESC
                    LIMIT ? OFFSET ?
                    """.formatted(
                    hasKey ? "record_key," : "",
                    hasCreated ? ", created_at" : "",
                    qualified("sync_error"),
                    hasCreated ? "created_at DESC, " : ""
            );
            List<SyncErrorDto> items = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindSyncId(statement, 1, "sync_error", "sync_execution_id", syncId);
                statement.setInt(2, safeSize);
                statement.setInt(3, (safePage - 1) * safeSize);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapError(rs, hasKey, hasCreated));
                    }
                }
            }
            return PageResponse.of(items, safePage - 1, safeSize, total);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list sync errors", ex);
        }
    }

    public SyncErrorPageDto findPage(String syncId, int page, int size) {
        SyncErrorPageDto dto = new SyncErrorPageDto();
        dto.syncId = syncId;
        dto.page = page;
        dto.size = size;
        if (!available() || syncId == null || syncId.isBlank()) {
            return dto;
        }
        try (Connection connection = connection()) {
            dto.groups.addAll(loadGroups(connection, syncId));
            dto.totalElements = count(connection, syncId);
            dto.totalPages = size <= 0 ? 0 : (int) Math.ceil((double) dto.totalElements / size);
            dto.items.addAll(loadItems(connection, syncId, page, size));
            return dto;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list sync errors", ex);
        }
    }

    private List<SyncErrorGroupDto> loadGroups(Connection connection, String syncId) throws SQLException {
        String sql = """
                SELECT stage, error_code, error_message, COUNT(*) AS total
                FROM """ + " " + qualified("sync_error") + """
                
                WHERE sync_execution_id = ?
                GROUP BY stage, error_code, error_message
                ORDER BY total DESC, stage
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSyncId(statement, 1, "sync_error", "sync_execution_id", syncId);
            try (ResultSet rs = statement.executeQuery()) {
                List<SyncErrorGroupDto> groups = new ArrayList<>();
                while (rs.next()) {
                    SyncErrorGroupDto group = new SyncErrorGroupDto();
                    group.stage = rs.getString("stage");
                    group.errorCode = rs.getString("error_code");
                    group.errorMessage = rs.getString("error_message");
                    group.count = rs.getLong("total");
                    groups.add(group);
                }
                return groups;
            }
        }
    }

    private long count(Connection connection, String syncId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + qualified("sync_error") + " WHERE sync_execution_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSyncId(statement, 1, "sync_error", "sync_execution_id", syncId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private List<SyncErrorDto> loadItems(Connection connection, String syncId, int page, int size)
            throws SQLException {
        boolean hasKey = columnExists("sync_error", "record_key");
        String sql = """
                SELECT id, sync_execution_id, entity, local_record_id, pure_id, record_uuid,
                       %s stage, error_code, error_message
                FROM """.formatted(hasKey ? "record_key," : "") + " " + qualified("sync_error") + """
                
                WHERE sync_execution_id = ?
                ORDER BY id
                LIMIT ? OFFSET ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSyncId(statement, 1, "sync_error", "sync_execution_id", syncId);
            statement.setInt(2, size);
            statement.setInt(3, page * size);
            try (ResultSet rs = statement.executeQuery()) {
                List<SyncErrorDto> items = new ArrayList<>();
                while (rs.next()) {
                    SyncErrorDto item = new SyncErrorDto();
                    item.id = rs.getLong("id");
                    item.syncId = rs.getString("sync_execution_id");
                    item.entity = rs.getString("entity");
                    item.localRecordId = nullableLong(rs, "local_record_id");
                    item.pureId = nullableLong(rs, "pure_id");
                    item.uuid = rs.getString("record_uuid");
                    if (hasKey) {
                        item.recordKey = rs.getString("record_key");
                    }
                    item.stage = rs.getString("stage");
                    item.errorCode = rs.getString("error_code");
                    item.errorMessage = rs.getString("error_message");
                    items.add(item);
                }
                return items;
            }
        }
    }

    private static SyncErrorDto mapError(ResultSet rs, boolean hasKey, boolean hasCreated) throws SQLException {
        SyncErrorDto item = new SyncErrorDto();
        item.id = rs.getLong("id");
        item.syncId = rs.getString("sync_execution_id");
        item.entity = rs.getString("entity");
        item.localRecordId = nullableLong(rs, "local_record_id");
        item.pureId = nullableLong(rs, "pure_id");
        item.uuid = rs.getString("record_uuid");
        if (hasKey) {
            item.recordKey = rs.getString("record_key");
        }
        item.stage = rs.getString("stage");
        item.errorCode = rs.getString("error_code");
        item.errorMessage = rs.getString("error_message");
        if (hasCreated) {
            Timestamp created = rs.getTimestamp("created_at");
            item.createdAt = created == null ? null : OffsetDateTime.ofInstant(created.toInstant(), ZoneOffset.UTC);
        }
        return item;
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
