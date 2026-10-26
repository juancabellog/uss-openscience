package cl.uss.ca.sync;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.SyncChangeDto;
import cl.uss.ca.api.dto.SyncChangeGroupDto;
import cl.uss.ca.api.dto.SyncChangeLineDto;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.repository.JdbcRepository;
import cl.uss.ca.sync.diff.SyncChange;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SyncChangeRepository extends JdbcRepository {

    public boolean available() {
        return tableExists("sync_change");
    }

    public void save(String syncId, List<SyncChange> changes) {
        if (!available() || changes == null || changes.isEmpty()) {
            return;
        }
        try (Connection connection = connection()) {
            save(connection, syncId, changes);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to persist sync changes", ex);
        }
    }

    public void save(Connection connection, String syncId, List<SyncChange> changes) throws SQLException {
        if (!available() || changes == null || changes.isEmpty()) {
            return;
        }
        boolean hasKey = columnExists("sync_change", "record_key");
        String sql = hasKey
                ? """
                INSERT INTO """ + " " + qualified("sync_change") + """
                
                (sync_execution_id, entity, local_record_id, pure_id, record_uuid, record_key,
                 change_type, change_scope, description, old_value, new_value, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6))
                """
                : """
                INSERT INTO """ + " " + qualified("sync_change") + """
                
                (sync_execution_id, entity, local_record_id, pure_id, record_uuid,
                 change_type, change_scope, description, old_value, new_value, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6))
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (SyncChange change : changes) {
                bindSyncId(statement, 1, "sync_change", "sync_execution_id", syncId);
                statement.setString(2, change.entity);
                setLong(statement, 3, change.localRecordId);
                setLong(statement, 4, change.pureId);
                statement.setString(5, SyncAuditFields.uuid(change.recordUuid));
                int index = 6;
                if (hasKey) {
                    statement.setString(index++, SyncAuditFields.key(change.recordKey));
                }
                statement.setString(index++, change.changeType);
                statement.setString(index++, change.changeScope);
                statement.setString(index++, change.description);
                statement.setString(index++, change.oldValue);
                statement.setString(index, change.newValue);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public PageResponse<SyncChangeGroupDto> findGrouped(String syncId, String changeType, int page, int size) {
        if (!available() || syncId == null || syncId.isBlank()) {
            return PageResponse.of(List.of(), page, size, 0);
        }
        String typeFilter = normalizeParentType(changeType);
        String parentSql = """
                SELECT
                    COALESCE(local_record_id, 0) AS local_record_id,
                    COALESCE(record_uuid, '') AS record_uuid,
                    MAX(pure_id) AS pure_id,
                    MAX(entity) AS entity,
                    MIN(id) AS first_id,
                    CASE
                        WHEN SUM(CASE WHEN change_type = 'REACTIVATED' THEN 1 ELSE 0 END) > 0 THEN 'REACTIVATED'
                        WHEN SUM(CASE WHEN change_type = 'INACTIVATED' THEN 1 ELSE 0 END) > 0 THEN 'INACTIVATED'
                        WHEN SUM(CASE WHEN change_type = 'CREATED' THEN 1 ELSE 0 END) > 0 THEN 'CREATED'
                        ELSE 'UPDATED'
                    END AS parent_type
                FROM """ + " " + qualified("sync_change") + """
                
                WHERE sync_execution_id = ?
                  AND change_type <> 'UNCHANGED'
                GROUP BY COALESCE(local_record_id, 0), COALESCE(record_uuid, '')
                """;
        String filtered = """
                SELECT local_record_id, record_uuid, pure_id, entity, first_id, parent_type
                FROM (""" + parentSql + """
                ) p
                """ + (typeFilter == null ? "" : " WHERE p.parent_type = ?") + """
                
                ORDER BY first_id
                """;
        try (Connection connection = connection()) {
            long total = countParents(connection, filtered, syncId, typeFilter);
            List<ParentKey> parents = loadParents(connection, filtered + " LIMIT ? OFFSET ?", syncId, typeFilter, page, size);
            if (parents.isEmpty()) {
                return PageResponse.of(List.of(), page, size, total);
            }
            Map<String, SyncChangeGroupDto> groups = new LinkedHashMap<>();
            for (ParentKey parent : parents) {
                SyncChangeGroupDto group = new SyncChangeGroupDto();
                group.localRecordId = parent.localRecordId == 0L ? null : parent.localRecordId;
                group.pureId = parent.pureId;
                group.uuid = parent.recordUuid.isEmpty() ? null : parent.recordUuid;
                group.entity = parent.entity;
                group.changeType = parent.parentType;
                group.displayName = null;
                groups.put(parent.key(), group);
            }
            loadLines(connection, syncId, parents, groups);
            enrichNames(connection, groups);
            return PageResponse.of(new ArrayList<>(groups.values()), page, size, total);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list grouped sync changes", ex);
        }
    }

    public PageResponse<SyncChangeDto> findPage(String syncId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        if (!available() || syncId == null || syncId.isBlank()) {
            return PageResponse.of(List.of(), safePage - 1, safeSize, 0);
        }
        boolean hasKey = columnExists("sync_change", "record_key");
        long total = countBySyncId(syncId);
        String sql = """
                SELECT id, sync_execution_id, entity, local_record_id, pure_id, record_uuid,
                       %s change_type, change_scope, description, old_value, new_value, created_at
                FROM %s
                WHERE sync_execution_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """.formatted(hasKey ? "record_key," : "", qualified("sync_change"));
        List<SyncChangeDto> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSyncId(statement, 1, "sync_change", "sync_execution_id", syncId);
            statement.setInt(2, safeSize);
            statement.setInt(3, (safePage - 1) * safeSize);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs, hasKey));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list sync changes", ex);
        }
        return PageResponse.of(items, safePage - 1, safeSize, total);
    }

    public List<SyncChangeDto> findBySyncId(String syncId) {
        if (!available() || syncId == null || syncId.isBlank()) {
            return List.of();
        }
        String sql = """
                SELECT id, sync_execution_id, entity, local_record_id, pure_id, record_uuid,
                       change_type, change_scope, description, old_value, new_value, created_at
                FROM """ + " " + qualified("sync_change") + """
                
                WHERE sync_execution_id = ?
                ORDER BY id
                LIMIT 1000
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSyncId(statement, 1, "sync_change", "sync_execution_id", syncId);
            try (ResultSet rs = statement.executeQuery()) {
                List<SyncChangeDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(map(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list sync changes", ex);
        }
    }

    private long countParents(Connection connection, String filtered, String syncId, String typeFilter)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM (" + filtered + ") x";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParentFilter(statement, syncId, typeFilter);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private List<ParentKey> loadParents(
            Connection connection,
            String sql,
            String syncId,
            String typeFilter,
            int page,
            int size
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindParentFilter(statement, syncId, typeFilter);
            statement.setInt(index, size);
            statement.setInt(index + 1, page * size);
            try (ResultSet rs = statement.executeQuery()) {
                List<ParentKey> parents = new ArrayList<>();
                while (rs.next()) {
                    parents.add(new ParentKey(
                            rs.getLong("local_record_id"),
                            rs.getString("record_uuid"),
                            nullableLong(rs, "pure_id"),
                            rs.getString("entity"),
                            rs.getString("parent_type")
                    ));
                }
                return parents;
            }
        }
    }

    private void loadLines(
            Connection connection,
            String syncId,
            List<ParentKey> parents,
            Map<String, SyncChangeGroupDto> groups
    ) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT local_record_id, record_uuid, change_type, change_scope, description, old_value, new_value
                FROM """).append(" ").append(qualified("sync_change")).append("""
                
                WHERE sync_execution_id = ?
                  AND change_type <> 'UNCHANGED'
                  AND (
                """);
        for (int i = 0; i < parents.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("(COALESCE(local_record_id, 0) = ? AND COALESCE(record_uuid, '') = ?)");
        }
        sql.append(") ORDER BY id");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            bindSyncId(statement, index++, "sync_change", "sync_execution_id", syncId);
            for (ParentKey parent : parents) {
                statement.setLong(index++, parent.localRecordId);
                statement.setString(index++, parent.recordUuid);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long localId = rs.getLong("local_record_id");
                    if (rs.wasNull()) {
                        localId = 0L;
                    }
                    String uuid = rs.getString("record_uuid");
                    SyncChangeGroupDto group = groups.get(ParentKey.keyOf(localId, uuid));
                    if (group == null) {
                        continue;
                    }
                    SyncChangeLineDto line = new SyncChangeLineDto();
                    line.changeType = rs.getString("change_type");
                    line.scope = rs.getString("change_scope");
                    line.description = rs.getString("description");
                    line.oldValue = rs.getString("old_value");
                    line.newValue = rs.getString("new_value");
                    group.changes.add(line);
                }
            }
        }
    }

    private void enrichNames(Connection connection, Map<String, SyncChangeGroupDto> groups) throws SQLException {
        Map<Long, SyncChangeGroupDto> people = new LinkedHashMap<>();
        Map<Long, SyncChangeGroupDto> journals = new LinkedHashMap<>();
        Map<Long, SyncChangeGroupDto> projects = new LinkedHashMap<>();
        Map<Long, SyncChangeGroupDto> organizations = new LinkedHashMap<>();
        Map<Long, SyncChangeGroupDto> outputs = new LinkedHashMap<>();
        for (SyncChangeGroupDto group : groups.values()) {
            if (group.localRecordId == null) {
                continue;
            }
            if ("PEOPLE".equals(group.entity)) {
                people.put(group.localRecordId, group);
            } else if ("JOURNALS".equals(group.entity) || "JOURNAL".equals(group.entity)) {
                journals.put(group.localRecordId, group);
            } else if ("PROJECTS".equals(group.entity) || "PROJECT".equals(group.entity)) {
                projects.put(group.localRecordId, group);
            } else if ("ORGANIZATIONS".equals(group.entity) || "ORGANIZATION".equals(group.entity)) {
                organizations.put(group.localRecordId, group);
            } else {
                outputs.put(group.localRecordId, group);
            }
        }
        fillPeopleNames(connection, people);
        fillJournalNames(connection, journals);
        fillProjectNames(connection, projects);
        fillOrganizationNames(connection, organizations);
        fillOutputNames(connection, outputs);
        for (SyncChangeGroupDto group : groups.values()) {
            if (group.displayName == null || group.displayName.isBlank()) {
                group.displayName = fallbackName(group);
            }
        }
    }

    private void fillPeopleNames(Connection connection, Map<Long, SyncChangeGroupDto> byId) throws SQLException {
        if (byId.isEmpty() || !tableExists("pure_person")) {
            return;
        }
        List<Long> ids = new ArrayList<>(byId.keySet());
        String sql = """
                SELECT id, pure_id, TRIM(CONCAT_WS(' ', first_name, last_name)) AS display_name
                FROM """ + " " + qualified("pure_person") + """
                
                WHERE id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    SyncChangeGroupDto group = byId.get(rs.getLong("id"));
                    if (group == null) {
                        continue;
                    }
                    if (group.pureId == null) {
                        group.pureId = nullableLong(rs, "pure_id");
                    }
                    String name = rs.getString("display_name");
                    if (name != null && !name.isBlank()) {
                        group.displayName = name.trim();
                    }
                }
            }
        }
    }

    private void fillJournalNames(Connection connection, Map<Long, SyncChangeGroupDto> byId) throws SQLException {
        if (byId.isEmpty() || !tableExists("pure_journal")) {
            return;
        }
        List<Long> ids = new ArrayList<>(byId.keySet());
        String titleJoin = tableExists("pure_journal_title")
                ? """
                 , COALESCE((
                    SELECT t.title FROM """ + " " + qualified("pure_journal_title") + """
                     t WHERE t.journal_id = j.id ORDER BY t.id LIMIT 1
                 ), j.title) AS display_name
                """
                : ", j.title AS display_name";
        String sql = """
                SELECT j.id, j.pure_id""" + titleJoin + """
                
                FROM """ + " " + qualified("pure_journal") + """
                 j
                WHERE j.id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    SyncChangeGroupDto group = byId.get(rs.getLong("id"));
                    if (group == null) {
                        continue;
                    }
                    if (group.pureId == null) {
                        group.pureId = nullableLong(rs, "pure_id");
                    }
                    String name = rs.getString("display_name");
                    if (name != null && !name.isBlank()) {
                        group.displayName = name.trim();
                    }
                }
            }
        }
    }

    private void fillProjectNames(Connection connection, Map<Long, SyncChangeGroupDto> byId) throws SQLException {
        if (byId.isEmpty() || !tableExists("pure_project")) {
            return;
        }
        List<Long> ids = new ArrayList<>(byId.keySet());
        String sql = """
                SELECT id, pure_id,
                       COALESCE(NULLIF(TRIM(title_es), ''), NULLIF(TRIM(title_en), ''), NULLIF(TRIM(acronym), '')) AS display_name
                FROM """ + " " + qualified("pure_project") + """
                
                WHERE id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    SyncChangeGroupDto group = byId.get(rs.getLong("id"));
                    if (group == null) {
                        continue;
                    }
                    if (group.pureId == null) {
                        group.pureId = nullableLong(rs, "pure_id");
                    }
                    String name = rs.getString("display_name");
                    if (name != null && !name.isBlank()) {
                        group.displayName = name.trim();
                    }
                }
            }
        }
    }

    private void fillOrganizationNames(Connection connection, Map<Long, SyncChangeGroupDto> byId) throws SQLException {
        if (byId.isEmpty() || !tableExists("pure_organization")) {
            return;
        }
        List<Long> ids = new ArrayList<>(byId.keySet());
        String sql = """
                SELECT id, pure_id,
                       COALESCE(NULLIF(TRIM(name_es), ''), NULLIF(TRIM(name_en), ''), uuid) AS display_name
                FROM """ + " " + qualified("pure_organization") + """
                
                WHERE id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    SyncChangeGroupDto group = byId.get(rs.getLong("id"));
                    if (group == null) {
                        continue;
                    }
                    if (group.pureId == null) {
                        group.pureId = nullableLong(rs, "pure_id");
                    }
                    String name = rs.getString("display_name");
                    if (name != null && !name.isBlank()) {
                        group.displayName = name.trim();
                    }
                }
            }
        }
    }

    private void fillOutputNames(Connection connection, Map<Long, SyncChangeGroupDto> byId) throws SQLException {
        if (byId.isEmpty() || !tableExists("pure_research_output")) {
            return;
        }
        List<Long> ids = new ArrayList<>(byId.keySet());
        String sql = """
                SELECT id, pure_id, title
                FROM """ + " " + qualified("pure_research_output") + """
                
                WHERE id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    SyncChangeGroupDto group = byId.get(rs.getLong("id"));
                    if (group == null) {
                        continue;
                    }
                    if (group.pureId == null) {
                        group.pureId = nullableLong(rs, "pure_id");
                    }
                    String title = rs.getString("title");
                    if (title != null && !title.isBlank()) {
                        group.displayName = title.trim();
                    }
                }
            }
        }
    }

    private static String fallbackName(SyncChangeGroupDto group) {
        if (group.pureId != null) {
            return String.valueOf(group.pureId);
        }
        if (group.uuid != null && !group.uuid.isBlank()) {
            return group.uuid;
        }
        return group.localRecordId == null ? "—" : String.valueOf(group.localRecordId);
    }

    private int bindParentFilter(PreparedStatement statement, String syncId, String typeFilter)
            throws SQLException {
        bindSyncId(statement, 1, "sync_change", "sync_execution_id", syncId);
        if (typeFilter == null) {
            return 2;
        }
        statement.setString(2, typeFilter);
        return 3;
    }

    private static void bindLongs(PreparedStatement statement, List<Long> ids) throws SQLException {
        for (int i = 0; i < ids.size(); i++) {
            statement.setLong(i + 1, ids.get(i));
        }
    }

    private static String normalizeParentType(String changeType) {
        if (changeType == null || changeType.isBlank() || "ALL".equalsIgnoreCase(changeType.trim())) {
            return null;
        }
        return changeType.trim().toUpperCase();
    }

    private long countBySyncId(String syncId) {
        String sql = "SELECT COUNT(*) FROM " + qualified("sync_change") + " WHERE sync_execution_id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSyncId(statement, 1, "sync_change", "sync_execution_id", syncId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count sync changes", ex);
        }
    }

    private static SyncChangeDto map(ResultSet rs) throws SQLException {
        return map(rs, false);
    }

    private static SyncChangeDto map(ResultSet rs, boolean hasKey) throws SQLException {
        SyncChangeDto dto = new SyncChangeDto();
        dto.id = rs.getLong("id");
        dto.syncId = rs.getString("sync_execution_id");
        dto.entity = rs.getString("entity");
        long localId = rs.getLong("local_record_id");
        dto.localRecordId = rs.wasNull() ? null : localId;
        long pureId = rs.getLong("pure_id");
        dto.pureId = rs.wasNull() ? null : pureId;
        dto.recordUuid = rs.getString("record_uuid");
        if (hasKey) {
            dto.recordKey = rs.getString("record_key");
        }
        dto.changeType = rs.getString("change_type");
        dto.changeScope = rs.getString("change_scope");
        dto.description = rs.getString("description");
        dto.oldValue = rs.getString("old_value");
        dto.newValue = rs.getString("new_value");
        java.sql.Timestamp created = rs.getTimestamp("created_at");
        dto.createdAt = created == null ? null : OffsetDateTime.ofInstant(created.toInstant(), ZoneOffset.UTC);
        return dto;
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static final class ParentKey {
        final long localRecordId;
        final String recordUuid;
        final Long pureId;
        final String entity;
        final String parentType;

        ParentKey(long localRecordId, String recordUuid, Long pureId, String entity, String parentType) {
            this.localRecordId = localRecordId;
            this.recordUuid = recordUuid == null ? "" : recordUuid;
            this.pureId = pureId;
            this.entity = entity;
            this.parentType = parentType;
        }

        String key() {
            return keyOf(localRecordId, recordUuid);
        }

        static String keyOf(long localRecordId, String recordUuid) {
            return localRecordId + "|" + (recordUuid == null ? "" : recordUuid);
        }
    }
}
