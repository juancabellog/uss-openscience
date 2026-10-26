package cl.uss.ca.repository;

import cl.uss.ca.external.pure.model.PureOrganization;
import cl.uss.ca.sync.diff.AggregateOutcome;
import cl.uss.ca.sync.diff.OrganizationSnapshot;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OrganizationSyncRepository extends JdbcRepository {

    public Connection begin() throws SQLException {
        Connection connection = connection();
        connection.setAutoCommit(false);
        return connection;
    }

    public Map<String, OrganizationSnapshot> loadSnapshots(Connection connection, Collection<String> uuids)
            throws SQLException {
        Map<String, OrganizationSnapshot> byUuid = new LinkedHashMap<>();
        if (uuids == null || uuids.isEmpty() || !tableExists("pure_organization")) {
            return byUuid;
        }
        List<String> values = new ArrayList<>(uuids);
        String sql = """
                SELECT id, uuid, pure_id, type_discriminator, name_es, name_en,
                       created_by, created_date_pure, modified_by, modified_date_pure,
                       pure_version, system_name, type_uri, type_en, type_es, parent_uuid
                """ + (columnExists("pure_organization", "active_in_pure") ? ", active_in_pure" : "") + """
                
                FROM """ + " " + qualified("pure_organization") + """
                
                WHERE uuid IN (""" + inPlaceholders(values.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                statement.setString(i + 1, values.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrganizationSnapshot snapshot = mapParent(rs);
                    byUuid.put(snapshot.uuid, snapshot);
                }
            }
        } catch (SQLException ex) {
            if (isUnknownColumn(ex)) {
                return byUuid;
            }
            throw ex;
        }
        return byUuid;
    }

    public Long apply(Connection connection, PureOrganization organization, AggregateOutcome outcome)
            throws SQLException {
        Long organizationId = outcome.localId;
        if (outcome.persistParent() || organizationId == null) {
            organizationId = upsert(connection, organization);
        }
        return organizationId;
    }

    private Long upsert(Connection connection, PureOrganization organization) throws SQLException {
        String sql = """
                INSERT INTO """ + " " + qualified("pure_organization") + """
                 (
                    uuid, pure_id, type_discriminator, name_es, name_en,
                    created_by, created_date_pure, modified_by, modified_date_pure, pure_version,
                    system_name, type_uri, type_en, type_es, parent_uuid
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    id = LAST_INSERT_ID(id),
                    pure_id = VALUES(pure_id),
                    type_discriminator = VALUES(type_discriminator),
                    name_es = VALUES(name_es),
                    name_en = VALUES(name_en),
                    created_by = VALUES(created_by),
                    created_date_pure = VALUES(created_date_pure),
                    modified_by = VALUES(modified_by),
                    modified_date_pure = VALUES(modified_date_pure),
                    pure_version = VALUES(pure_version),
                    system_name = VALUES(system_name),
                    type_uri = VALUES(type_uri),
                    type_en = VALUES(type_en),
                    type_es = VALUES(type_es),
                    parent_uuid = VALUES(parent_uuid)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, organization.getUuid());
            setLong(statement, 2, organization.getPureId());
            statement.setString(3, organization.getTypeDiscriminator());
            statement.setString(4, organization.getNameEs());
            statement.setString(5, organization.getNameEn());
            statement.setString(6, organization.getCreatedBy());
            setTimestamp(statement, 7, organization.getCreatedDatePure());
            statement.setString(8, organization.getModifiedBy());
            setTimestamp(statement, 9, organization.getModifiedDatePure());
            statement.setString(10, organization.getVersion());
            statement.setString(11, organization.getSystemName());
            statement.setString(12, organization.getTypeUri());
            statement.setString(13, organization.getTypeEn());
            statement.setString(14, organization.getTypeEs());
            statement.setString(15, organization.getParentUuid());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next() && keys.getLong(1) > 0) {
                    return keys.getLong(1);
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM " + qualified("pure_organization") + " WHERE uuid = ?")) {
            statement.setString(1, organization.getUuid());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Unable to persist organization uuid=" + organization.getUuid());
    }

    private static OrganizationSnapshot mapParent(ResultSet rs) throws SQLException {
        OrganizationSnapshot snapshot = new OrganizationSnapshot();
        snapshot.id = rs.getLong("id");
        snapshot.uuid = rs.getString("uuid");
        snapshot.pureId = nullableLong(rs, "pure_id");
        snapshot.typeDiscriminator = nullableString(rs, "type_discriminator");
        snapshot.nameEs = nullableString(rs, "name_es");
        snapshot.nameEn = nullableString(rs, "name_en");
        snapshot.createdBy = nullableString(rs, "created_by");
        snapshot.createdDatePure = offset(rs.getTimestamp("created_date_pure"));
        snapshot.modifiedBy = nullableString(rs, "modified_by");
        snapshot.modifiedDatePure = offset(rs.getTimestamp("modified_date_pure"));
        snapshot.version = nullableString(rs, "pure_version");
        snapshot.systemName = nullableString(rs, "system_name");
        snapshot.typeUri = nullableString(rs, "type_uri");
        snapshot.typeEn = nullableString(rs, "type_en");
        snapshot.typeEs = nullableString(rs, "type_es");
        snapshot.parentUuid = nullableString(rs, "parent_uuid");
        if (hasColumn(rs, "active_in_pure")) {
            snapshot.activeInPure = nullableBoolean(rs, "active_in_pure");
        }
        return snapshot;
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void setTimestamp(PreparedStatement statement, int index, OffsetDateTime value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value.toInstant()));
        }
    }

    private static OffsetDateTime offset(Timestamp value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private static boolean isUnknownColumn(SQLException ex) {
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unknown column");
    }
}
