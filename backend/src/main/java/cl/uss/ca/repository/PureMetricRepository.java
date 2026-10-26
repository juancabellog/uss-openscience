package cl.uss.ca.repository;

import cl.uss.ca.api.dto.MetricFetchStateDto;
import cl.uss.ca.api.dto.MetricObservationDto;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.external.pure.metrics.PureMetricFetchOutcome;
import cl.uss.ca.external.pure.metrics.PureMetricFetchResult;
import cl.uss.ca.external.pure.metrics.PureMetricObservation;
import cl.uss.ca.external.pure.metrics.PureMetricOwner;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PureMetricRepository extends JdbcRepository {

    public boolean available(PureMetricTables tables) {
        return tableExists(tables.metricTable()) && tableExists(tables.fetchTable());
    }

    public List<PureMetricOwner> listActiveOwners(PureMetricTables tables) {
        if (!tableExists(tables.ownerTable())) {
            return List.of();
        }
        boolean active = columnExists(tables.ownerTable(), "active_in_pure");
        String sql = "SELECT id, "
                + (columnExists(tables.ownerTable(), "pure_id") ? "pure_id" : "NULL AS pure_id")
                + ", uuid FROM " + qualified(tables.ownerTable())
                + " WHERE uuid IS NOT NULL AND TRIM(uuid) <> ''"
                + (active ? " AND COALESCE(active_in_pure, 1) = 1" : "")
                + " ORDER BY id";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<PureMetricOwner> owners = new ArrayList<>();
            while (rs.next()) {
                owners.add(new PureMetricOwner(
                        rs.getLong("id"),
                        nullableLong(rs, "pure_id"),
                        nullableString(rs, "uuid")
                ));
            }
            return owners;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list metric owners", ex);
        }
    }

    public List<PureMetricOwner> listIncrementalOwners(
            PureMetricTables tables,
            List<String> collections,
            int refreshAfterHours
    ) {
        if (!available(tables) || collections == null || collections.isEmpty()) {
            return listActiveOwners(tables);
        }
        boolean active = columnExists(tables.ownerTable(), "active_in_pure");
        StringBuilder missing = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < collections.size(); i++) {
            if (i > 0) {
                missing.append(" OR ");
            }
            missing.append("NOT EXISTS (SELECT 1 FROM ").append(qualified(tables.fetchTable()))
                    .append(" f0 WHERE f0.").append(tables.ownerColumn())
                    .append(" = o.id AND f0.collection_id = ?)");
            params.add(collections.get(i));
        }
        String sql = """
                SELECT o.id, %s AS pure_id, o.uuid
                FROM %s o
                WHERE o.uuid IS NOT NULL AND TRIM(o.uuid) <> ''
                  %s
                  AND (
                    %s
                    OR EXISTS (
                        SELECT 1 FROM %s f
                        WHERE f.%s = o.id
                          AND f.collection_id IN (%s)
                          AND (
                            f.last_outcome IN ('FAILED', 'INVALID')
                            OR f.last_sync_at IS NULL
                            OR f.last_sync_at < DATE_SUB(NOW(6), INTERVAL ? HOUR)
                          )
                    )
                  )
                ORDER BY o.id
                """.formatted(
                columnExists(tables.ownerTable(), "pure_id") ? "o.pure_id" : "NULL",
                qualified(tables.ownerTable()),
                active ? "AND COALESCE(o.active_in_pure, 1) = 1" : "",
                missing,
                qualified(tables.fetchTable()),
                tables.ownerColumn(),
                placeholders(collections.size())
        );
        params.addAll(collections);
        params.add(Math.max(1, refreshAfterHours));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                List<PureMetricOwner> owners = new ArrayList<>();
                while (rs.next()) {
                    owners.add(new PureMetricOwner(
                            rs.getLong("id"),
                            nullableLong(rs, "pure_id"),
                            nullableString(rs, "uuid")
                    ));
                }
                return owners;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list stale metric owners", ex);
        }
    }

    /**
     * Authoritative snapshot replace for one owner+collection. Caller must
     * only invoke this when {@link PureMetricFetchResult#replacesSnapshot()}.
     */
    public int replaceSnapshot(
            PureMetricTables tables,
            long ownerId,
            String collectionId,
            List<PureMetricObservation> observations,
            String executionId
    ) {
        Connection connection = null;
        boolean previous = true;
        try {
            connection = connection();
            previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            int deleted = deleteCollection(connection, tables, ownerId, collectionId);
            int inserted = 0;
            if (observations != null) {
                for (PureMetricObservation observation : observations) {
                    insertObservation(connection, tables, ownerId, collectionId, observation, executionId);
                    inserted++;
                }
            }
            connection.commit();
            return inserted == 0 ? deleted : inserted;
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new DatabaseException("Failed to replace metric snapshot", ex);
        } finally {
            restoreAutoCommit(connection, previous);
            closeQuietly(connection);
        }
    }

    public void saveFetch(
            PureMetricTables tables,
            long ownerId,
            String collectionId,
            PureMetricFetchResult result,
            String executionId
    ) {
        if (!available(tables)) {
            return;
        }
        String sql = """
                INSERT INTO %s (
                    %s, collection_id, last_outcome, last_http_status, last_error_code, last_error_message,
                    observation_count, last_sync_execution_id, last_sync_at, last_success_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(6), %s)
                ON DUPLICATE KEY UPDATE
                    last_outcome = VALUES(last_outcome),
                    last_http_status = VALUES(last_http_status),
                    last_error_code = VALUES(last_error_code),
                    last_error_message = VALUES(last_error_message),
                    observation_count = VALUES(observation_count),
                    last_sync_execution_id = VALUES(last_sync_execution_id),
                    last_sync_at = NOW(6),
                    last_success_at = IF(VALUES(last_outcome) IN ('SUCCESS','EMPTY'), NOW(6), last_success_at)
                """.formatted(
                qualified(tables.fetchTable()),
                tables.ownerColumn(),
                result.replacesSnapshot() ? "NOW(6)" : "NULL"
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            statement.setString(2, collectionId);
            statement.setString(3, result.outcome.name());
            if (result.httpStatus == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, result.httpStatus);
            }
            statement.setString(5, clip(result.errorCode, 64));
            statement.setString(6, clip(result.errorMessage, 500));
            statement.setInt(7, result.observations.size());
            if (executionId == null) {
                statement.setNull(8, Types.BIGINT);
            } else {
                try {
                    statement.setLong(8, Long.parseLong(executionId));
                } catch (NumberFormatException ex) {
                    statement.setNull(8, Types.BIGINT);
                }
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to persist metric fetch state", ex);
        }
    }

    public MetricFetchStateDto loadFetch(PureMetricTables tables, long ownerId, String collectionId) {
        if (!available(tables)) {
            return null;
        }
        String sql = "SELECT collection_id, last_outcome, last_http_status, last_error_code, last_error_message, "
                + "observation_count, last_sync_at, last_success_at FROM "
                + qualified(tables.fetchTable())
                + " WHERE " + tables.ownerColumn() + " = ? AND collection_id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            statement.setString(2, collectionId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapFetch(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load metric fetch state", ex);
        }
    }

    public List<MetricObservationDto> loadObservations(PureMetricTables tables, long ownerId) {
        return loadObservations(tables, ownerId, null);
    }

    public List<MetricObservationDto> loadObservations(PureMetricTables tables, long ownerId, String collectionId) {
        if (!available(tables)) {
            return List.of();
        }
        String sql = """
                SELECT collection_id, source_uri, source_term_es, source_term_en,
                       category_uri, category_term_es, category_term_en,
                       metric_year, metric_id, value_kind, integer_value, decimal_value, string_value,
                       last_synced_at
                FROM %s
                WHERE %s = ?
                %s
                ORDER BY metric_year DESC, collection_id, source_term_en, category_term_en, metric_id
                """.formatted(
                qualified(tables.metricTable()),
                tables.ownerColumn(),
                collectionId == null ? "" : "AND collection_id = ?"
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            if (collectionId != null) {
                statement.setString(2, collectionId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<MetricObservationDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapObservation(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load metric observations", ex);
        }
    }

    public MetricObservationDto latest(
            PureMetricTables tables,
            long ownerId,
            String collectionId,
            String metricId
    ) {
        if (!available(tables)) {
            return null;
        }
        String sql = """
                SELECT collection_id, source_uri, source_term_es, source_term_en,
                       category_uri, category_term_es, category_term_en,
                       metric_year, metric_id, value_kind, integer_value, decimal_value, string_value,
                       last_synced_at
                FROM %s
                WHERE %s = ? AND collection_id = ? AND metric_id = ?
                ORDER BY metric_year DESC, id DESC
                LIMIT 1
                """.formatted(qualified(tables.metricTable()), tables.ownerColumn());
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            statement.setString(2, collectionId);
            statement.setString(3, metricId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapObservation(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load latest metric", ex);
        }
    }

    private int deleteCollection(Connection connection, PureMetricTables tables, long ownerId, String collectionId)
            throws SQLException {
        String sql = "DELETE FROM " + qualified(tables.metricTable())
                + " WHERE " + tables.ownerColumn() + " = ? AND collection_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            statement.setString(2, collectionId);
            return statement.executeUpdate();
        }
    }

    private void insertObservation(
            Connection connection,
            PureMetricTables tables,
            long ownerId,
            String collectionId,
            PureMetricObservation observation,
            String executionId
    ) throws SQLException {
        String sql = """
                INSERT INTO %s (
                    %s, collection_id, source_uri, source_term_es, source_term_en,
                    category_uri, category_term_es, category_term_en,
                    metric_year, metric_id, value_kind, integer_value, decimal_value, string_value,
                    identity_key, last_sync_execution_id, last_synced_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6))
                """.formatted(qualified(tables.metricTable()), tables.ownerColumn());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            statement.setString(2, collectionId);
            statement.setString(3, observation.sourceUri);
            statement.setString(4, observation.sourceTermEs);
            statement.setString(5, observation.sourceTermEn);
            statement.setString(6, observation.categoryUri);
            statement.setString(7, observation.categoryTermEs);
            statement.setString(8, observation.categoryTermEn);
            if (observation.metricYear == null) {
                statement.setNull(9, Types.SMALLINT);
            } else {
                statement.setInt(9, observation.metricYear);
            }
            statement.setString(10, observation.metricId);
            statement.setString(11, observation.valueKind.name());
            if (observation.integerValue == null) {
                statement.setNull(12, Types.BIGINT);
            } else {
                statement.setLong(12, observation.integerValue);
            }
            if (observation.decimalValue == null) {
                statement.setNull(13, Types.DECIMAL);
            } else {
                statement.setBigDecimal(13, observation.decimalValue);
            }
            statement.setString(14, observation.stringValue);
            statement.setString(15, observation.identityKey(ownerId));
            if (executionId == null) {
                statement.setNull(16, Types.BIGINT);
            } else {
                try {
                    statement.setLong(16, Long.parseLong(executionId));
                } catch (NumberFormatException ex) {
                    statement.setNull(16, Types.BIGINT);
                }
            }
            statement.executeUpdate();
        }
    }

    static MetricObservationDto mapObservation(ResultSet rs) throws SQLException {
        MetricObservationDto dto = new MetricObservationDto();
        dto.collectionId = nullableString(rs, "collection_id");
        dto.sourceUri = nullableString(rs, "source_uri");
        dto.sourceTermEs = nullableString(rs, "source_term_es");
        dto.sourceTermEn = nullableString(rs, "source_term_en");
        dto.categoryUri = nullableString(rs, "category_uri");
        dto.categoryTermEs = nullableString(rs, "category_term_es");
        dto.categoryTermEn = nullableString(rs, "category_term_en");
        dto.metricYear = nullableInt(rs, "metric_year");
        dto.metricId = nullableString(rs, "metric_id");
        dto.valueKind = nullableString(rs, "value_kind");
        dto.integerValue = nullableLong(rs, "integer_value");
        dto.decimalValue = nullableBigDecimal(rs, "decimal_value");
        dto.stringValue = nullableString(rs, "string_value");
        Timestamp synced = rs.getTimestamp("last_synced_at");
        dto.lastSyncedAt = synced == null ? null : synced.toInstant().toString();
        return dto;
    }

    static MetricFetchStateDto mapFetch(ResultSet rs) throws SQLException {
        MetricFetchStateDto dto = new MetricFetchStateDto();
        dto.collectionId = nullableString(rs, "collection_id");
        dto.lastOutcome = nullableString(rs, "last_outcome");
        dto.lastHttpStatus = nullableInt(rs, "last_http_status");
        dto.lastErrorCode = nullableString(rs, "last_error_code");
        dto.lastErrorMessage = nullableString(rs, "last_error_message");
        dto.observationCount = nullableInt(rs, "observation_count");
        Timestamp synced = rs.getTimestamp("last_sync_at");
        dto.lastSyncedAt = synced == null ? null : synced.toInstant().toString();
        Timestamp success = rs.getTimestamp("last_success_at");
        dto.lastSuccessAt = success == null ? null : success.toInstant().toString();
        return dto;
    }

    public static BigDecimal numericValue(MetricObservationDto dto) {
        if (dto == null) {
            return null;
        }
        if (dto.decimalValue != null) {
            return dto.decimalValue;
        }
        if (dto.integerValue != null) {
            return BigDecimal.valueOf(dto.integerValue);
        }
        return null;
    }

    public static Integer integerValue(MetricObservationDto dto) {
        if (dto == null) {
            return null;
        }
        if (dto.integerValue != null) {
            return dto.integerValue.intValue();
        }
        if (dto.decimalValue != null) {
            return dto.decimalValue.intValue();
        }
        return null;
    }

    private static BigDecimal nullableBigDecimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return rs.wasNull() ? null : value;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String placeholders(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('?');
        }
        return builder.toString();
    }
}
