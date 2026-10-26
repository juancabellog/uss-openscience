package cl.uss.ca.repository;

import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.audit.AuditListSupport;
import cl.uss.ca.exception.ConflictException;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.sync.SyncCancellation;
import cl.uss.ca.sync.SyncEntityPolicy;
import cl.uss.ca.sync.SyncMode;
import cl.uss.ca.sync.SyncOwnershipLostException;
import cl.uss.ca.sync.SyncRunState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL is the lock authority. The winner is decided by:
 *
 * UPDATE sync_control
 * SET active_sync_id = ?
 * WHERE entity = ? AND active_sync_id IS NULL
 *
 * InnoDB takes an exclusive row lock on the entity PK. The second concurrent
 * UPDATE waits, then sees active_sync_id already set and updates 0 rows.
 */
@ApplicationScoped
public class SyncRunRepository extends JdbcRepository {
    private static final Logger LOG = Logger.getLogger(SyncRunRepository.class.getName());
    static final String EXECUTION_TABLE = "sync_execution";
    static final String CONTROL_TABLE = "sync_control";
    /**
     * CAST(... AS CHAR) uses the connection default collation (utf8mb4_0900_ai_ci
     * on MySQL 8). sync_execution.started_by is utf8mb4_unicode_ci. Comparing
     * them without COLLATE raises ER_CANT_AGGREGATE_2COLLATIONS.
     */
    public static final String STARTED_BY_USER_ON =
            " ON CAST(u.id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin"
                    + " = e.started_by COLLATE utf8mb4_bin ";
    private static final String UNICODE = " COLLATE utf8mb4_unicode_ci";
    public static final String STALE_CODE = "STALE_SYNC_RECOVERED";
    private static final String STALE_MESSAGE =
            "Abandoned synchronization recovered because heartbeat expired. Inactive marks were not applied.";
    private static final String OPENALEX_STALE_MESSAGE =
            "Synchronization was recovered as abandoned because its heartbeat expired. The OpenAlex job did not reach normal completion.";

    @Inject
    @ConfigProperty(name = "openscience.sync.staleMinutes", defaultValue = "15")
    int staleMinutes;

    public boolean available() {
        return tableExists(EXECUTION_TABLE) && tableExists(CONTROL_TABLE);
    }

    public void assertAvailable() {
        if (!available()) {
            throw new ValidationException(
                    "SYNC_LOCK_UNAVAILABLE",
                    "sync_execution and sync_control are required before starting a synchronization"
            );
        }
    }

    /**
     * INSERT PENDING + conditional lock + mark RUNNING, or ROLLBACK.
     * Never leaves a PENDING orphan when the lock is not acquired.
     */
    public SyncRunState acquire(String entity, String startedBy) {
        return acquire(entity, startedBy, SyncMode.FULL);
    }

    public SyncRunState acquire(String entity, String startedBy, SyncMode syncMode) {
        assertAvailable();
        Connection connection = null;
        boolean previousAutoCommit = true;
        try {
            connection = connection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            ensureControlRow(connection, entity);
            recoverAbandonedCancellations(connection, entity, staleMinutes());
            recoverStale(connection, entity);
            SyncRunState run = new SyncRunState(entity, startedBy);
            run.syncMode = syncMode == null ? SyncMode.FULL : syncMode;
            insertPending(connection, run);
            int locked = tryAcquireLock(connection, entity, run.syncId, startedBy);
            if (locked != 1) {
                connection.rollback();
                throw alreadyRunning(entity);
            }
            markRunning(connection, run.syncId);
            connection.commit();
            run.status = "RUNNING";
            run.heartbeatAt = OffsetDateTime.now();
            LOG.info("syncId=" + run.syncId + " entity=" + entity
                    + " mode=" + run.syncMode
                    + " startedBy=" + startedBy + " lock acquired");
            return run;
        } catch (ConflictException | ValidationException ex) {
            rollbackQuietly(connection);
            throw ex;
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new DatabaseException("Failed to acquire sync lock", ex);
        } finally {
            restore(connection, previousAutoCommit);
        }
    }

    /**
     * @return true when this worker still owns a RUNNING execution (rows affected &gt; 0)
     */
    public boolean heartbeat(SyncRunState run) {
        if (run == null) {
            return false;
        }
        if (!available()) {
            return true;
        }
        String sql = """
                UPDATE """ + " " + qualified(EXECUTION_TABLE) + """
                
                SET heartbeat_at = NOW(6),
                    total_expected = ?,
                    processed = ?,
                    created_count = ?,
                    updated_count = ?,
                    unchanged_count = ?,
                    reactivated_count = ?,
                    marked_inactive_count = ?,
                    error_count = ?,
                    error_code = ?,
                    error_message = ?""" + updatedAtTail(EXECUTION_TABLE, null) + """
                
                WHERE id = ?
                  AND status = 'RUNNING'
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindCounters(statement, run);
            bindSyncId(statement, 11, EXECUTION_TABLE, "id", run.syncId);
            int rows = statement.executeUpdate();
            if (rows > 0) {
                run.heartbeatAt = OffsetDateTime.now();
                return true;
            }
            return false;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to persist sync heartbeat", ex);
        }
    }

    public void requireOwnership(SyncRunState run) {
        if (!heartbeat(run)) {
            throw new SyncOwnershipLostException(
                    run == null ? null : run.syncId,
                    run == null ? null : run.entity
            );
        }
    }

    public void save(SyncRunState run) {
        heartbeat(run);
    }

    /**
     * Terminal status + release of THIS execution's lock only.
     *
     * @return false when the row was no longer PENDING/RUNNING (cancel or stale)
     */
    public boolean finishAndRelease(SyncRunState run) {
        if (run == null) {
            return false;
        }
        assertAvailable();
        Connection connection = null;
        boolean previousAutoCommit = true;
        try {
            connection = connection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            int rows = finishExecution(connection, run, false);
            if (rows < 1) {
                connection.rollback();
                return false;
            }
            releaseOwnLock(connection, run.entity, run.syncId);
            connection.commit();
            run.heartbeatAt = OffsetDateTime.now();
            LOG.info("syncId=" + run.syncId + " entity=" + run.entity + " status=" + run.status + " lock released");
            return true;
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new DatabaseException("Failed to finish sync run", ex);
        } finally {
            restore(connection, previousAutoCommit);
        }
    }

    /**
     * RUNNING/PENDING → CANCEL_REQUESTED. Lock stays held until the worker stops.
     *
     * @return true when this call changed the row
     */
    public boolean requestCancel(String syncId, String requestedBy) {
        if (!available() || syncId == null || syncId.isBlank()) {
            return false;
        }
        String sql = """
                UPDATE """ + " " + qualified(EXECUTION_TABLE) + """
                
                SET status = ?,
                    heartbeat_at = NOW(6),
                    error_code = ?,
                    error_message = ?""" + updatedAtTail(EXECUTION_TABLE, null) + """
                
                WHERE id = ?
                  AND status IN ('PENDING', 'RUNNING')
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SyncCancellation.REQUESTED);
            statement.setString(2, SyncCancellation.REQUEST_CODE);
            statement.setString(3, SyncCancellation.requestMessage(requestedBy));
            bindSyncId(statement, 4, EXECUTION_TABLE, "id", syncId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to request sync cancellation", ex);
        }
    }

    /**
     * RUNNING or CANCEL_REQUESTED → CANCELLED, persist counters, release lock.
     */
    public boolean finalizeCancellation(SyncRunState run) {
        if (run == null) {
            return false;
        }
        assertAvailable();
        run.status = SyncCancellation.CANCELLED;
        run.finishedAt = OffsetDateTime.now();
        run.heartbeatAt = run.finishedAt;
        run.errorCode = SyncCancellation.CANCEL_CODE;
        run.message = SyncCancellation.cancelledMessage(
                run.cancelledBy != null ? run.cancelledBy : run.startedBy
        );
        Connection connection = null;
        boolean previousAutoCommit = true;
        try {
            connection = connection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            int rows = finishExecution(connection, run, true);
            if (rows < 1) {
                connection.rollback();
                return false;
            }
            releaseOwnLock(connection, run.entity, run.syncId);
            connection.commit();
            LOG.info("syncId=" + run.syncId + " entity=" + run.entity
                    + " status=" + SyncCancellation.CANCELLED + " lock released");
            return true;
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new DatabaseException("Failed to finalize sync cancellation", ex);
        } finally {
            restore(connection, previousAutoCommit);
        }
    }

    public void releaseOwnLock(String entity, String syncId) {
        if (!available() || entity == null || syncId == null) {
            return;
        }
        try (Connection connection = connection()) {
            releaseOwnLock(connection, entity, syncId);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to release sync lock", ex);
        }
    }

    public int recoverStaleLocks() {
        if (!available()) {
            return 0;
        }
        Connection connection = null;
        boolean previousAutoCommit = true;
        try {
            connection = connection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            int cancelled = recoverAbandonedCancellations(connection, null, 0);
            int recovered = recoverStale(connection, null) + cancelled;
            connection.commit();
            if (recovered > 0) {
                LOG.warning("Recovered stale sync locks count=" + recovered + " staleMinutes=" + staleMinutes());
            }
            return recovered;
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new DatabaseException("Failed to recover stale sync locks", ex);
        } finally {
            restore(connection, previousAutoCommit);
        }
    }

    public PageResponse<SyncRunDto> findPage(
            String search,
            OffsetDateTime from,
            OffsetDateTime to,
            String entity,
            List<String> sourceEntities,
            String status,
            String syncMode,
            String actor,
            String sort,
            String direction,
            int page,
            int size
    ) {
        int safePage = AuditListSupport.safePage(page);
        int safeSize = AuditListSupport.safeSize(size);
        if (!available()) {
            return PageResponse.of(List.of(), safePage - 1, safeSize, 0);
        }
        if (sourceEntities != null && sourceEntities.isEmpty()) {
            return PageResponse.of(List.of(), safePage - 1, safeSize, 0);
        }
        boolean joinUser = tableExists("app_user");
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (entity != null && !entity.isBlank()) {
            clauses.add("e.entity" + UNICODE + " = ?");
            params.add(entity);
        } else if (sourceEntities != null) {
            clauses.add("e.entity" + UNICODE + " IN (" + inPlaceholders(sourceEntities.size()) + ")");
            params.addAll(sourceEntities);
        }
        if (status != null && !status.isBlank()) {
            clauses.add("e.status" + UNICODE + " = ?");
            params.add(status);
        }
        if (syncMode != null && !syncMode.isBlank()) {
            if (hasSyncModeColumn()) {
                clauses.add("e.sync_mode" + UNICODE + " = ?");
                params.add(syncMode);
            } else if (!"FULL".equals(syncMode)) {
                return PageResponse.of(List.of(), safePage - 1, safeSize, 0);
            }
        }
        if (from != null) {
            clauses.add("e.started_at >= ?");
            params.add(Timestamp.from(from.toInstant()));
        }
        if (to != null) {
            clauses.add("e.started_at <= ?");
            params.add(Timestamp.from(to.toInstant()));
        }
        if (actor != null && !actor.isBlank()) {
            String like = likeContains(actor.trim());
            if (joinUser) {
                clauses.add("(e.started_by" + UNICODE + " LIKE ? ESCAPE '!' OR u.first_name LIKE ? ESCAPE '!' OR u.last_name LIKE ? ESCAPE '!' OR u.email LIKE ? ESCAPE '!')");
                params.add(like);
                params.add(like);
                params.add(like);
                params.add(like);
            } else {
                clauses.add("e.started_by" + UNICODE + " LIKE ? ESCAPE '!'");
                params.add(like);
            }
        }
        if (search != null && !search.isBlank()) {
            String like = likeContains(search.trim());
            StringBuilder searchSql = new StringBuilder(
                    "(CAST(e.id AS CHAR CHARACTER SET utf8mb4)" + UNICODE + " LIKE ? ESCAPE '!'"
                            + " OR e.entity" + UNICODE + " LIKE ? ESCAPE '!'"
                            + " OR e.status" + UNICODE + " LIKE ? ESCAPE '!'"
                            + " OR e.started_by" + UNICODE + " LIKE ? ESCAPE '!'"
            );
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            if (hasSyncModeColumn()) {
                searchSql.append(" OR e.sync_mode" + UNICODE + " LIKE ? ESCAPE '!'");
                params.add(like);
            }
            if (joinUser) {
                searchSql.append(" OR u.first_name LIKE ? ESCAPE '!' OR u.last_name LIKE ? ESCAPE '!' OR u.email LIKE ? ESCAPE '!'");
                params.add(like);
                params.add(like);
                params.add(like);
            }
            searchSql.append(")");
            clauses.add(searchSql.toString());
        }
        String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        String join = joinUser
                ? " LEFT JOIN " + qualified("app_user") + " u" + STARTED_BY_USER_ON
                : " ";
        String fromSql = selectSql() + join + where;
        String countSql = "SELECT COUNT(*) FROM " + qualified(EXECUTION_TABLE) + " e " + join + where;
        long total = scalarLong(countSql, params.toArray());
        String sql = fromSql + " ORDER BY " + AuditListSupport.orderBy(sort, direction) + " LIMIT ? OFFSET ?";
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safeSize);
        pageParams.add((safePage - 1) * safeSize);
        List<SyncRunDto> items = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, pageParams.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list audit sync executions", ex);
        }
        return PageResponse.of(items, safePage - 1, safeSize, total);
    }

    public SyncRunDto findById(String syncId) {
        if (!available() || syncId == null || syncId.isBlank()) {
            return null;
        }
        String sql = selectSql() + " WHERE e.id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSyncId(statement, 1, EXECUTION_TABLE, "id", syncId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load sync run", ex);
        }
    }

    public List<SyncRunDto> findRecent(Collection<String> entities, int limit) {
        if (!available() || entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(new LinkedHashSet<>(entities));
        values.removeIf(value -> value == null || value.isBlank());
        if (values.isEmpty()) {
            return List.of();
        }
        if (values.size() == 1) {
            return findRecent(values.get(0), limit);
        }
        int safeLimit = limit < 1 ? 1 : Math.min(limit, 100);
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
        }
        String sql = selectSql() + " WHERE e.entity IN (" + placeholders
                + ") ORDER BY e.started_at DESC, e.id DESC LIMIT " + safeLimit;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                statement.setString(i + 1, values.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<SyncRunDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(map(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load recent sync runs", ex);
        }
    }

    public List<SyncRunDto> findRecent(String entity, int limit) {
        if (!available() || entity == null) {
            return List.of();
        }
        int safeLimit = limit < 1 ? 1 : Math.min(limit, 100);
        String sql = selectSql() + " WHERE e.entity = ? ORDER BY e.started_at DESC, e.id DESC LIMIT " + safeLimit;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entity);
            try (ResultSet rs = statement.executeQuery()) {
                List<SyncRunDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(map(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load recent sync runs", ex);
        }
    }

    public SyncRunDto findLatest(String entity) {
        if (!available() || entity == null) {
            return null;
        }
        String sql = selectSql() + " WHERE e.entity = ? ORDER BY e.started_at DESC, e.id DESC LIMIT 1";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entity);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load latest sync run", ex);
        }
    }

    public List<SyncRunDto> findLatestByEntities(List<String> entities) {
        if (!available() || entities == null || entities.isEmpty()) {
            return List.of();
        }
        String sql = selectSql()
                + " WHERE e.entity IN (" + inPlaceholders(entities.size()) + ")"
                + " AND e.id = (SELECT e2.id FROM " + qualified(EXECUTION_TABLE)
                + " e2 WHERE e2.entity = e.entity ORDER BY e2.started_at DESC, e2.id DESC LIMIT 1)";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String entity : entities) {
                statement.setString(index++, entity);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<SyncRunDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(map(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load latest sync runs", ex);
        }
    }

    public SyncRunDto findLatestFinishedFull(String entity) {
        if (!available() || entity == null) {
            return null;
        }
        if (!hasSyncModeColumn() && !SyncEntityPolicy.treatsNullSyncModeAsFull(entity)) {
            return null;
        }
        String sql = selectSql()
                + " WHERE e.entity = ?"
                + fullModePredicate(entity)
                + " AND e.status NOT IN ('PENDING', 'RUNNING', 'CANCEL_REQUESTED')"
                + " ORDER BY COALESCE(e.finished_at, e.started_at) DESC, e.id DESC LIMIT 1";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entity);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load latest finished full sync run", ex);
        }
    }

    public List<SyncRunDto> findCompletedFullRuns(String entity, int limit) {
        if (!available() || entity == null) {
            return List.of();
        }
        if (!hasSyncModeColumn() && !SyncEntityPolicy.treatsNullSyncModeAsFull(entity)) {
            return List.of();
        }
        int safeLimit = limit < 1 ? 1 : Math.min(limit, 100);
        String sql = selectSql()
                + " WHERE e.entity = ?"
                + fullModePredicate(entity)
                + " AND e.status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS')"
                + " ORDER BY COALESCE(e.finished_at, e.started_at) DESC, e.id DESC LIMIT " + safeLimit;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entity);
            try (ResultSet rs = statement.executeQuery()) {
                List<SyncRunDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(map(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load completed full sync runs", ex);
        }
    }

    public SyncRunDto findLatestFull(String entity) {
        if (!available() || entity == null) {
            return null;
        }
        if (!hasSyncModeColumn() && !SyncEntityPolicy.treatsNullSyncModeAsFull(entity)) {
            return null;
        }
        String sql = selectSql()
                + " WHERE e.entity = ?"
                + fullModePredicate(entity)
                + " AND e.status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS')"
                + " ORDER BY COALESCE(e.finished_at, e.started_at) DESC, e.id DESC LIMIT 1";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entity);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load latest full sync run", ex);
        }
    }

    public List<SyncRunDto> findRunning() {
        if (!available()) {
            return List.of();
        }
        String sql = selectSql() + """
                
                INNER JOIN """ + " " + qualified(CONTROL_TABLE) + """
                 c ON e.id = c.active_sync_id
                WHERE c.active_sync_id IS NOT NULL
                ORDER BY e.started_at
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<SyncRunDto> items = new ArrayList<>();
            while (rs.next()) {
                items.add(map(rs));
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list running syncs", ex);
        }
    }

    private void ensureControlRow(Connection connection, String entity) throws SQLException {
        String sql = """
                INSERT IGNORE INTO """ + " " + qualified(CONTROL_TABLE) + """
                
                (entity, active_sync_id, locked_at, locked_by)
                VALUES (?, NULL, NULL, NULL)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entity);
            statement.executeUpdate();
        }
    }

    private void insertPending(Connection connection, SyncRunState run) throws SQLException {
        boolean withMode = hasSyncModeColumn();
        String sql = withMode
                ? """
                INSERT INTO """ + " " + qualified(EXECUTION_TABLE) + """
                
                (entity, sync_mode, status, started_at, finished_at, heartbeat_at, started_by,
                 total_expected, processed, created_count, updated_count, unchanged_count,
                 reactivated_count, marked_inactive_count, error_count, error_code, error_message)
                VALUES (?, ?, 'PENDING', NOW(6), NULL, NOW(6), ?, NULL, 0, 0, 0, 0, 0, 0, 0, NULL, NULL)
                """
                : """
                INSERT INTO """ + " " + qualified(EXECUTION_TABLE) + """
                
                (entity, status, started_at, finished_at, heartbeat_at, started_by,
                 total_expected, processed, created_count, updated_count, unchanged_count,
                 reactivated_count, marked_inactive_count, error_count, error_code, error_message)
                VALUES (?, 'PENDING', NOW(6), NULL, NOW(6), ?, NULL, 0, 0, 0, 0, 0, 0, 0, NULL, NULL)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, run.entity);
            if (withMode) {
                statement.setString(2, run.syncMode == null ? SyncMode.FULL.name() : run.syncMode.name());
                statement.setString(3, run.startedBy);
            } else {
                statement.setString(2, run.startedBy);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("sync_execution did not return a generated id");
                }
                run.syncId = String.valueOf(keys.getLong(1));
            }
        }
    }

    private int tryAcquireLock(Connection connection, String entity, String syncId, String lockedBy) throws SQLException {
        String sql = """
                UPDATE """ + " " + qualified(CONTROL_TABLE) + """
                
                SET active_sync_id = ?,
                    locked_at = NOW(6),
                    locked_by = ?""" + updatedAtTail(CONTROL_TABLE, null) + """
                
                WHERE entity = ?
                  AND active_sync_id IS NULL
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSyncId(statement, 1, CONTROL_TABLE, "active_sync_id", syncId);
            statement.setString(2, lockedBy);
            statement.setString(3, entity);
            return statement.executeUpdate();
        }
    }

    private void markRunning(Connection connection, String syncId) throws SQLException {
        String sql = """
                UPDATE """ + " " + qualified(EXECUTION_TABLE) + """
                
                SET status = 'RUNNING',
                    heartbeat_at = NOW(6)""" + updatedAtTail(EXECUTION_TABLE, null) + """
                
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSyncId(statement, 1, EXECUTION_TABLE, "id", syncId);
            statement.executeUpdate();
        }
    }

    private int recoverStale(Connection connection, String entity) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                UPDATE """).append(" ").append(qualified(CONTROL_TABLE)).append("""
                 c
                INNER JOIN """).append(" ").append(qualified(EXECUTION_TABLE)).append("""
                 e ON e.id = c.active_sync_id
                SET
                    c.active_sync_id = NULL,
                    c.locked_at = NULL,
                    c.locked_by = NULL""" + updatedAtTail(CONTROL_TABLE, "c") + """
                    ,
                    e.status = 'FAILED',
                    e.finished_at = NOW(6),
                    e.heartbeat_at = NOW(6),
                    e.error_code = ?,
                    e.error_message = CASE WHEN e.entity = 'OPENALEX' THEN ? ELSE ? END"""
                + updatedAtTail(EXECUTION_TABLE, "e") + """
                WHERE e.status IN ('PENDING', 'RUNNING')
                  AND e.heartbeat_at < DATE_SUB(NOW(6), INTERVAL ? MINUTE)
                """);
        if (entity != null) {
            sql.append(" AND c.entity = ?");
        }
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, STALE_CODE);
            statement.setString(2, staleMessage("OPENALEX"));
            statement.setString(3, staleMessage(null));
            statement.setInt(4, staleMinutes());
            if (entity != null) {
                statement.setString(5, entity);
            }
            return statement.executeUpdate();
        }
    }

    private int finishExecution(Connection connection, SyncRunState run, boolean includeCancelRequested)
            throws SQLException {
        String fromStatuses = includeCancelRequested
                ? "('PENDING', 'RUNNING', 'CANCEL_REQUESTED')"
                : "('PENDING', 'RUNNING')";
        String sql = """
                UPDATE """ + " " + qualified(EXECUTION_TABLE) + """
                
                SET status = ?,
                    finished_at = NOW(6),
                    heartbeat_at = NOW(6),
                    total_expected = ?,
                    processed = ?,
                    created_count = ?,
                    updated_count = ?,
                    unchanged_count = ?,
                    reactivated_count = ?,
                    marked_inactive_count = ?,
                    error_count = ?,
                    error_code = ?,
                    error_message = ?""" + updatedAtTail(EXECUTION_TABLE, null) + """
                
                WHERE id = ?
                  AND status IN """ + fromStatuses;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, run.status);
            bindCounters(statement, run, 2);
            bindSyncId(statement, 12, EXECUTION_TABLE, "id", run.syncId);
            return statement.executeUpdate();
        }
    }

    /**
     * Worker is gone (restart) or heartbeat expired while CANCEL_REQUESTED.
     * Finalize as CANCELLED, never FAILED. maxAgeMinutes &lt;= 0 means all.
     */
    private int recoverAbandonedCancellations(Connection connection, String entity, int maxAgeMinutes)
            throws SQLException {
        StringBuilder sql = new StringBuilder("""
                UPDATE """).append(" ").append(qualified(CONTROL_TABLE)).append("""
                 c
                INNER JOIN """).append(" ").append(qualified(EXECUTION_TABLE)).append("""
                 e ON e.id = c.active_sync_id
                SET
                    c.active_sync_id = NULL,
                    c.locked_at = NULL,
                    c.locked_by = NULL""" + updatedAtTail(CONTROL_TABLE, "c") + """
                    ,
                    e.status = ?,
                    e.finished_at = NOW(6),
                    e.heartbeat_at = NOW(6),
                    e.error_code = ?,
                    e.error_message = CASE
                        WHEN e.error_message IS NULL OR TRIM(e.error_message) = '' THEN ?
                        ELSE e.error_message
                    END"""
                + updatedAtTail(EXECUTION_TABLE, "e") + """
                WHERE e.status = ?
                """);
        if (maxAgeMinutes > 0) {
            sql.append(" AND e.heartbeat_at < DATE_SUB(NOW(6), INTERVAL ? MINUTE)");
        }
        if (entity != null) {
            sql.append(" AND c.entity = ?");
        }
        int locked;
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setString(index++, SyncCancellation.CANCELLED);
            statement.setString(index++, SyncCancellation.CANCEL_CODE);
            statement.setString(index++, SyncCancellation.cancelledMessage(null));
            statement.setString(index++, SyncCancellation.REQUESTED);
            if (maxAgeMinutes > 0) {
                statement.setInt(index++, maxAgeMinutes);
            }
            if (entity != null) {
                statement.setString(index, entity);
            }
            locked = statement.executeUpdate();
        }
        StringBuilder orphans = new StringBuilder("""
                UPDATE """).append(" ").append(qualified(EXECUTION_TABLE)).append("""
                
                SET status = ?,
                    finished_at = COALESCE(finished_at, NOW(6)),
                    heartbeat_at = NOW(6),
                    error_code = ?,
                    error_message = CASE
                        WHEN error_message IS NULL OR TRIM(error_message) = '' THEN ?
                        ELSE error_message
                    END"""
                + updatedAtTail(EXECUTION_TABLE, null) + """
                WHERE status = ?
                """);
        if (maxAgeMinutes > 0) {
            orphans.append(" AND heartbeat_at < DATE_SUB(NOW(6), INTERVAL ? MINUTE)");
        }
        if (entity != null) {
            orphans.append(" AND entity = ?");
        }
        try (PreparedStatement statement = connection.prepareStatement(orphans.toString())) {
            int index = 1;
            statement.setString(index++, SyncCancellation.CANCELLED);
            statement.setString(index++, SyncCancellation.CANCEL_CODE);
            statement.setString(index++, SyncCancellation.cancelledMessage(null));
            statement.setString(index++, SyncCancellation.REQUESTED);
            if (maxAgeMinutes > 0) {
                statement.setInt(index++, maxAgeMinutes);
            }
            if (entity != null) {
                statement.setString(index, entity);
            }
            return locked + statement.executeUpdate();
        }
    }

    private void releaseOwnLock(Connection connection, String entity, String syncId) throws SQLException {
        String sql = """
                UPDATE """ + " " + qualified(CONTROL_TABLE) + """
                
                SET active_sync_id = NULL,
                    locked_at = NULL,
                    locked_by = NULL""" + updatedAtTail(CONTROL_TABLE, null) + """
                
                WHERE entity = ?
                  AND active_sync_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entity);
            bindSyncId(statement, 2, CONTROL_TABLE, "active_sync_id", syncId);
            statement.executeUpdate();
        }
    }

    private String updatedAtTail(String table, String qualifier) {
        if (!columnExists(table, "updated_at")) {
            return "";
        }
        String column = qualifier == null || qualifier.isBlank() ? "updated_at" : qualifier + ".updated_at";
        return ", " + column + " = NOW(6)";
    }

    private ConflictException alreadyRunning(String entity) {
        SyncRunDto current = findActive(entity);
        return new ConflictException(
                "SYNC_ALREADY_RUNNING",
                "A synchronization is already running for " + entity,
                entity,
                current == null ? null : current.syncId,
                current == null ? null : current.startedAt,
                current == null ? null : current.startedBy
        );
    }

    public SyncRunDto findActive(String entity) {
        String sql = selectSql() + """
                
                INNER JOIN """ + " " + qualified(CONTROL_TABLE) + """
                 c ON c.active_sync_id = e.id
                WHERE c.entity = ?
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entity);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Unable to load active sync for entity=" + entity, ex);
            return null;
        }
    }

    private String selectSql() {
        String mode = hasSyncModeColumn() ? ", e.sync_mode" : "";
        return """
                SELECT
                    e.id, e.entity, e.status, e.started_by, e.started_at, e.finished_at, e.heartbeat_at,
                    e.total_expected, e.processed, e.created_count, e.updated_count, e.unchanged_count,
                    e.reactivated_count, e.marked_inactive_count, e.error_count, e.error_code, e.error_message"""
                + mode
                + """
                
                FROM """ + " " + qualified(EXECUTION_TABLE) + " e ";
    }

    private boolean hasSyncModeColumn() {
        return columnExists(EXECUTION_TABLE, "sync_mode");
    }

    private String fullModePredicate(String entity) {
        if (!hasSyncModeColumn()) {
            return "";
        }
        if (SyncEntityPolicy.treatsNullSyncModeAsFull(entity)) {
            return " AND (e.sync_mode = 'FULL' OR e.sync_mode IS NULL)";
        }
        return " AND e.sync_mode = 'FULL'";
    }

    private void bindCounters(PreparedStatement statement, SyncRunState run) throws SQLException {
        bindCounters(statement, run, 1);
    }

    private void bindCounters(PreparedStatement statement, SyncRunState run, int start) throws SQLException {
        if (run.totalExpected == null) {
            statement.setNull(start, Types.INTEGER);
        } else {
            statement.setInt(start, run.totalExpected);
        }
        statement.setLong(start + 1, run.processed.get());
        statement.setLong(start + 2, run.created.get());
        statement.setLong(start + 3, run.updated.get());
        statement.setLong(start + 4, run.unchanged.get());
        statement.setLong(start + 5, run.reactivated.get());
        statement.setLong(start + 6, run.markedInactive.get());
        statement.setLong(start + 7, run.errors.get());
        statement.setString(start + 8, run.errorCode);
        statement.setString(start + 9, run.message);
    }

    private static SyncRunDto map(ResultSet rs) throws SQLException {
        SyncRunDto dto = new SyncRunDto();
        dto.syncId = rs.getString("id");
        dto.entity = rs.getString("entity");
        dto.status = rs.getString("status");
        dto.startedBy = rs.getString("started_by");
        dto.startedAt = offset(rs.getTimestamp("started_at"));
        dto.finishedAt = offset(rs.getTimestamp("finished_at"));
        dto.heartbeatAt = offset(rs.getTimestamp("heartbeat_at"));
        int expected = rs.getInt("total_expected");
        dto.totalExpected = rs.wasNull() ? null : expected;
        dto.processed = rs.getLong("processed");
        dto.created = rs.getLong("created_count");
        dto.updated = rs.getLong("updated_count");
        dto.unchanged = rs.getLong("unchanged_count");
        dto.reactivated = rs.getLong("reactivated_count");
        dto.markedInactive = rs.getLong("marked_inactive_count");
        dto.errors = rs.getLong("error_count");
        dto.fetched = dto.processed + dto.errors;
        dto.errorCode = rs.getString("error_code");
        dto.message = rs.getString("error_message");
        String mode = hasColumn(rs, "sync_mode") ? rs.getString("sync_mode") : null;
        if ((mode == null || mode.isBlank()) && SyncEntityPolicy.treatsNullSyncModeAsFull(dto.entity)) {
            mode = SyncMode.FULL.name();
        }
        dto.syncMode = mode;
        return dto;
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    public static String staleMessage(String entity) {
        return "OPENALEX".equals(entity) ? OPENALEX_STALE_MESSAGE : STALE_MESSAGE;
    }

    private int staleMinutes() {
        return staleMinutes < 1 ? 15 : staleMinutes;
    }

    private static void restore(Connection connection, boolean previousAutoCommit) {
        restoreAutoCommit(connection, previousAutoCommit);
        closeQuietly(connection);
    }

    private static OffsetDateTime offset(Timestamp value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }
}
