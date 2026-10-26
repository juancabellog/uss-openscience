package cl.uss.ca.repository;

import cl.uss.ca.domain.OrcidFirstSeen;
import cl.uss.ca.external.pure.AcademicTitle;
import cl.uss.ca.external.pure.PersonIdentifier;
import cl.uss.ca.external.pure.PurePerson;
import cl.uss.ca.external.pure.StaffAssociation;
import cl.uss.ca.sync.diff.AggregateOutcome;
import cl.uss.ca.sync.diff.PersonSnapshot;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PersonSyncRepository extends JdbcRepository {
    private String upsertPersonaSql() {
        return """
                INSERT INTO """ + " " + qualified("pure_person") + """
                 (
                    pure_id, uuid, first_name, last_name, orcid, orcid_authenticated,
                    email, fecha_ingreso, cargo, titulo, created_by, modified_by,
                    fecha_creacion_pure, fecha_modificacion_pure
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    id = LAST_INSERT_ID(id),
                    pure_id = VALUES(pure_id),
                    first_name = VALUES(first_name),
                    last_name = VALUES(last_name),
                    orcid = VALUES(orcid),
                    orcid_authenticated = VALUES(orcid_authenticated),
                    email = VALUES(email),
                    fecha_ingreso = VALUES(fecha_ingreso),
                    cargo = VALUES(cargo),
                    titulo = VALUES(titulo),
                    created_by = VALUES(created_by),
                    modified_by = VALUES(modified_by),
                    fecha_creacion_pure = VALUES(fecha_creacion_pure),
                    fecha_modificacion_pure = VALUES(fecha_modificacion_pure)
                """;
    }

    private String selectPersonaIdSql() {
        return "SELECT id FROM " + qualified("pure_person") + " WHERE uuid = ?";
    }

    private String deleteAsociacionesSql() {
        return "DELETE FROM " + qualified("pure_person_organization") + " WHERE person_id = ?";
    }

    private String deleteTitulosSql() {
        return "DELETE FROM " + qualified("pure_person_title") + " WHERE person_id = ?";
    }

    private String insertAsociacionSql() {
        return """
                INSERT INTO """ + " " + qualified("pure_person_organization") + """
                 (
                    person_id, pure_id, organization_uuid, employment_type, email,
                    fecha_inicio, fecha_termino, primary_association, contract_type,
                    fte, job_description, job_title, staff_type
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
    }

    private String insertTituloSql() {
        return """
                INSERT INTO """ + " " + qualified("pure_person_title") + """
                 (
                    person_id, pure_id, title_type, title_value
                ) VALUES (?, ?, ?, ?)
                """;
    }

    private String deleteIdentifiersSql() {
        return "DELETE FROM " + qualified("pure_person_identifiers") + " WHERE person_id = ?";
    }

    private String insertIdentifierSql() {
        return """
                INSERT INTO """ + " " + qualified("pure_person_identifiers") + """
                 (
                    person_id, pure_id, identifier_typeDiscriminator, identifier_type, identifier_value
                ) VALUES (?, ?, ?, ?, ?)
                """;
    }

    public boolean identifiersAvailable() {
        return tableExists("pure_person_identifiers");
    }

    public Connection begin() throws SQLException {
        Connection connection = connection();
        connection.setAutoCommit(false);
        return connection;
    }

    public boolean existsByUuid(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(selectPersonaIdSql())) {
            statement.setString(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Map<String, PersonSnapshot> loadSnapshots(Connection connection, Collection<String> uuids)
            throws SQLException {
        Map<String, PersonSnapshot> byUuid = new LinkedHashMap<>();
        if (uuids == null || uuids.isEmpty()) {
            return byUuid;
        }
        List<String> values = new ArrayList<>(uuids);
        String sql = """
                SELECT id, uuid, pure_id, first_name, last_name, orcid, orcid_authenticated, email,
                       fecha_ingreso, cargo, titulo, created_by, modified_by,
                       fecha_creacion_pure, fecha_modificacion_pure
                """ + (columnExists("pure_person", "active_in_pure") ? ", active_in_pure" : "")
                + (columnExists("pure_person", "orcid_first_seen_at")
                ? ", orcid_first_seen_at, orcid_first_seen_source"
                : "") + """
                
                FROM """ + " " + qualified("pure_person") + """
                
                WHERE uuid IN (""" + inPlaceholders(values.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                statement.setString(i + 1, values.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    PersonSnapshot snapshot = mapPerson(rs);
                    byUuid.put(snapshot.uuid, snapshot);
                }
            }
        }
        if (byUuid.isEmpty()) {
            return byUuid;
        }
        List<Long> ids = byUuid.values().stream().map(item -> item.id).toList();
        if (tableExists("pure_person_organization")) {
            loadAssociations(connection, ids, byUuid);
        }
        if (tableExists("pure_person_title")) {
            loadTitles(connection, ids, byUuid);
        }
        if (tableExists("pure_person_identifiers")) {
            loadIdentifiers(connection, ids, byUuid);
        }
        return byUuid;
    }

    public Long apply(Connection connection, PurePerson person, AggregateOutcome outcome, boolean persistIdentifiers)
            throws SQLException {
        Long personId = outcome.localId;
        if (outcome.persistParent() || personId == null) {
            personId = upsertPerson(connection, person);
        }
        if (outcome.persistChildren()) {
            replaceAssociations(connection, personId, person.getAsociaciones());
            replaceTitles(connection, personId, person.getTitulos());
            if (persistIdentifiers) {
                replaceIdentifiers(connection, personId, person.getIdentifiers());
            }
        }
        return personId;
    }

    /**
     * Local observation stamp. Never part of the PURE UPSERT list, so FULL/UNCHANGED
     * cannot reset it. Call even when the parent row is unchanged so existing ORCID
     * rows receive a one-time BASELINE.
     */
    public void stampOrcidFirstSeen(
            Connection connection,
            Long personId,
            String previousOrcid,
            OffsetDateTime previousSeen,
            String incomingOrcid
    ) throws SQLException {
        if (personId == null || !columnExists("pure_person", "orcid_first_seen_at")) {
            return;
        }
        String source = OrcidFirstSeen.sourceToStamp(previousOrcid, previousSeen, incomingOrcid);
        if (source == null) {
            return;
        }
        String sql = """
                UPDATE """ + " " + qualified("pure_person") + """
                
                SET orcid_first_seen_at = NOW(6),
                    orcid_first_seen_source = ?
                WHERE id = ?
                  AND orcid_first_seen_at IS NULL
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source);
            statement.setLong(2, personId);
            statement.executeUpdate();
        }
    }

    public void upsert(Connection connection, PurePerson person) throws SQLException {
        Long personId = upsertPerson(connection, person);
        replaceAssociations(connection, personId, person.getAsociaciones());
        replaceTitles(connection, personId, person.getTitulos());
        if (tableExists("pure_person_identifiers")) {
            replaceIdentifiers(connection, personId, person.getIdentifiers());
        }
    }

    private Long upsertPerson(Connection connection, PurePerson person) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                upsertPersonaSql(), Statement.RETURN_GENERATED_KEYS)) {
            setLong(statement, 1, person.getPureId());
            statement.setString(2, person.getUuid());
            statement.setString(3, person.getFirstName());
            statement.setString(4, person.getLastName());
            statement.setString(5, person.getOrcid());
            statement.setBoolean(6, person.isOrcidAuthenticated());
            statement.setString(7, person.getEmail());
            setDate(statement, 8, person.getFechaIngreso());
            statement.setString(9, person.getCargo());
            statement.setString(10, person.getTitulo());
            statement.setString(11, person.getCreatedBy());
            statement.setString(12, person.getModifiedBy());
            setTimestamp(statement, 13, person.getFechaCreacionPure());
            setTimestamp(statement, 14, person.getFechaModificacionPure());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    if (id > 0) {
                        return id;
                    }
                }
            }
        }
        return requireIdByUuid(connection, person.getUuid());
    }

    private void replaceAssociations(Connection connection, Long personId, List<StaffAssociation> associations)
            throws SQLException {
        deleteByPerson(connection, deleteAsociacionesSql(), personId);
        if (associations == null || associations.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(insertAsociacionSql())) {
            for (StaffAssociation association : associations) {
                if (association == null) {
                    continue;
                }
                statement.setLong(1, personId);
                setLong(statement, 2, association.getPureId());
                statement.setString(3, association.getOrganizationUuid());
                statement.setString(4, association.getEmploymentType());
                statement.setString(5, association.getEmail());
                setDate(statement, 6, association.getStartDate());
                setDate(statement, 7, association.getEndDate());
                statement.setBoolean(8, association.isPrimaryAssociation());
                statement.setString(9, association.getContractType());
                setBigDecimal(statement, 10, association.getFte());
                statement.setString(11, association.getJobDescription());
                statement.setString(12, association.getJobTitle());
                statement.setString(13, association.getStaffType());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceTitles(Connection connection, Long personId, List<AcademicTitle> titles)
            throws SQLException {
        deleteByPerson(connection, deleteTitulosSql(), personId);
        if (titles == null || titles.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(insertTituloSql())) {
            for (AcademicTitle title : titles) {
                if (title == null || title.getValue() == null) {
                    continue;
                }
                statement.setLong(1, personId);
                setLong(statement, 2, title.getPureId());
                statement.setString(3, title.getType());
                statement.setString(4, title.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceIdentifiers(Connection connection, Long personId, List<PersonIdentifier> identifiers)
            throws SQLException {
        deleteByPerson(connection, deleteIdentifiersSql(), personId);
        if (identifiers == null || identifiers.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(insertIdentifierSql())) {
            for (PersonIdentifier identifier : identifiers) {
                if (identifier == null || identifier.getValue() == null) {
                    continue;
                }
                statement.setLong(1, personId);
                setLong(statement, 2, identifier.getPureId());
                statement.setString(3, identifier.getTypeDiscriminator());
                statement.setString(4, identifier.getIdentifierType());
                statement.setString(5, identifier.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void loadAssociations(Connection connection, List<Long> ids, Map<String, PersonSnapshot> byUuid)
            throws SQLException {
        Map<Long, PersonSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT person_id, pure_id, organization_uuid, employment_type, email,
                       fecha_inicio, fecha_termino, primary_association, contract_type,
                       fte, job_description, job_title, staff_type
                FROM """ + " " + qualified("pure_person_organization") + """
                
                WHERE person_id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    PersonSnapshot snapshot = byId.get(rs.getLong("person_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    StaffAssociation association = new StaffAssociation();
                    association.setPureId(nullableLong(rs, "pure_id"));
                    association.setOrganizationUuid(rs.getString("organization_uuid"));
                    association.setEmploymentType(rs.getString("employment_type"));
                    association.setEmail(rs.getString("email"));
                    Date start = rs.getDate("fecha_inicio");
                    Date end = rs.getDate("fecha_termino");
                    association.setStartDate(start == null ? null : start.toLocalDate());
                    association.setEndDate(end == null ? null : end.toLocalDate());
                    association.setPrimaryAssociation(rs.getBoolean("primary_association"));
                    association.setContractType(rs.getString("contract_type"));
                    association.setFte(rs.getBigDecimal("fte"));
                    association.setJobDescription(rs.getString("job_description"));
                    association.setJobTitle(rs.getString("job_title"));
                    association.setStaffType(rs.getString("staff_type"));
                    snapshot.asociaciones.add(association);
                }
            }
        }
    }

    private void loadTitles(Connection connection, List<Long> ids, Map<String, PersonSnapshot> byUuid)
            throws SQLException {
        Map<Long, PersonSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT person_id, pure_id, title_type, title_value
                FROM """ + " " + qualified("pure_person_title") + """
                
                WHERE person_id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    PersonSnapshot snapshot = byId.get(rs.getLong("person_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    AcademicTitle title = new AcademicTitle();
                    title.setPureId(nullableLong(rs, "pure_id"));
                    title.setType(rs.getString("title_type"));
                    title.setValue(rs.getString("title_value"));
                    snapshot.titulos.add(title);
                }
            }
        }
    }

    private void loadIdentifiers(Connection connection, List<Long> ids, Map<String, PersonSnapshot> byUuid)
            throws SQLException {
        Map<Long, PersonSnapshot> byId = byId(byUuid);
        String sql = """
                SELECT person_id, pure_id, identifier_typeDiscriminator, identifier_type, identifier_value
                FROM """ + " " + qualified("pure_person_identifiers") + """
                
                WHERE person_id IN (""" + inPlaceholders(ids.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLongs(statement, ids);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    PersonSnapshot snapshot = byId.get(rs.getLong("person_id"));
                    if (snapshot == null) {
                        continue;
                    }
                    PersonIdentifier identifier = new PersonIdentifier();
                    identifier.setPureId(nullableLong(rs, "pure_id"));
                    identifier.setTypeDiscriminator(rs.getString("identifier_typeDiscriminator"));
                    identifier.setIdentifierType(rs.getString("identifier_type"));
                    identifier.setValue(rs.getString("identifier_value"));
                    snapshot.identifiers.add(identifier);
                }
            }
        }
    }

    private PersonSnapshot mapPerson(ResultSet rs) throws SQLException {
        PersonSnapshot snapshot = new PersonSnapshot();
        snapshot.id = rs.getLong("id");
        snapshot.uuid = rs.getString("uuid");
        snapshot.pureId = nullableLong(rs, "pure_id");
        snapshot.firstName = rs.getString("first_name");
        snapshot.lastName = rs.getString("last_name");
        snapshot.orcid = rs.getString("orcid");
        snapshot.orcidAuthenticated = rs.getBoolean("orcid_authenticated");
        snapshot.email = rs.getString("email");
        Date ingreso = rs.getDate("fecha_ingreso");
        snapshot.fechaIngreso = ingreso == null ? null : ingreso.toLocalDate();
        snapshot.cargo = rs.getString("cargo");
        snapshot.titulo = rs.getString("titulo");
        snapshot.createdBy = nullableString(rs, "created_by");
        snapshot.modifiedBy = nullableString(rs, "modified_by");
        snapshot.fechaCreacionPure = offset(rs.getTimestamp("fecha_creacion_pure"));
        snapshot.fechaModificacionPure = offset(rs.getTimestamp("fecha_modificacion_pure"));
        if (columnExists("pure_person", "active_in_pure")) {
            boolean active = rs.getBoolean("active_in_pure");
            snapshot.activeInPure = rs.wasNull() ? Boolean.TRUE : active;
        } else {
            snapshot.activeInPure = Boolean.TRUE;
        }
        if (columnExists("pure_person", "orcid_first_seen_at")) {
            snapshot.orcidFirstSeenAt = offset(rs.getTimestamp("orcid_first_seen_at"));
            snapshot.orcidFirstSeenSource = nullableString(rs, "orcid_first_seen_source");
        }
        return snapshot;
    }

    private static Map<Long, PersonSnapshot> byId(Map<String, PersonSnapshot> byUuid) {
        Map<Long, PersonSnapshot> byId = new LinkedHashMap<>();
        for (PersonSnapshot snapshot : byUuid.values()) {
            byId.put(snapshot.id, snapshot);
        }
        return byId;
    }

    private static void bindLongs(PreparedStatement statement, List<Long> ids) throws SQLException {
        for (int i = 0; i < ids.size(); i++) {
            statement.setLong(i + 1, ids.get(i));
        }
    }

    private static OffsetDateTime offset(Timestamp value) {
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private static void deleteByPerson(Connection connection, String sql, Long personId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            statement.executeUpdate();
        }
    }

    private Long requireIdByUuid(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(selectPersonaIdSql())) {
            statement.setString(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        throw new SQLException("Unable to resolve pure_person id for uuid=" + uuid);
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void setBigDecimal(PreparedStatement statement, int index, java.math.BigDecimal value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static void setDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(value));
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
}
