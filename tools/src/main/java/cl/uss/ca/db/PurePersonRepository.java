package cl.uss.ca.db;

import cl.uss.ca.pure.AcademicTitle;
import cl.uss.ca.pure.PurePerson;
import cl.uss.ca.pure.StaffAssociation;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class PurePersonRepository {
    private static final String UPSERT_PERSONA_SQL = """
            INSERT INTO pure_person (
                pure_id, uuid, first_name, last_name, orcid, orcid_authenticated,
                email, fecha_ingreso, cargo, titulo, fecha_creacion_pure, fecha_modificacion_pure
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                fecha_creacion_pure = VALUES(fecha_creacion_pure),
                fecha_modificacion_pure = VALUES(fecha_modificacion_pure)
            """;

    private static final String SELECT_PERSONA_ID_SQL = "SELECT id FROM pure_person WHERE uuid = ?";
    private static final String DELETE_ASOCIACIONES_SQL = "DELETE FROM pure_person_organization WHERE person_id = ?";
    private static final String DELETE_TITULOS_SQL = "DELETE FROM pure_person_title WHERE person_id = ?";

    private static final String INSERT_ASOCIACION_SQL = """
            INSERT INTO pure_person_organization (
                person_id, pure_id, organization_uuid, employment_type, email,
                fecha_inicio, fecha_termino, primary_association, contract_type,
                fte, job_description, job_title, staff_type
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_TITULO_SQL = """
            INSERT INTO pure_person_title (
                person_id, pure_id, title_type, title_value
            ) VALUES (?, ?, ?, ?)
            """;

    private final String url;
    private final String user;
    private final String password;
    private final DataSource dataSource;

    public PurePersonRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.dataSource = null;
    }

    public PurePersonRepository(DataSource dataSource) {
        this.url = null;
        this.user = null;
        this.password = null;
        this.dataSource = dataSource;
    }

    public Connection abrirConexion() throws SQLException {
        Connection connection = dataSource != null
                ? dataSource.getConnection()
                : DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
        return connection;
    }

    public PersistenciaResumen guardarTodas(List<PurePerson> persons) throws SQLException {
        PersistenciaResumen resumen = new PersistenciaResumen();
        try (Connection connection = abrirConexion()) {
            connection.setAutoCommit(false);
            int procesadas = 0;
            for (PurePerson person : persons) {
                try {
                    int[] relaciones = guardarPersonaCompleta(connection, person);
                    connection.commit();
                    resumen.registrarPersona(relaciones[0], relaciones[1]);
                    procesadas++;
                    if (procesadas % 100 == 0) {
                        System.out.println("Persistidas " + procesadas + " personas...");
                    }
                } catch (SQLException ex) {
                    connection.rollback();
                    throw new SQLException("Error persistiendo persona uuid=" + person.getUuid()
                            + ". Se revirtió la transacción de esa persona.", ex);
                }
            }
        }
        return resumen;
    }

    public int upsertAll(List<PurePerson> persons) throws SQLException {
        return guardarTodas(persons).getPersonas();
    }

    public boolean existsByUuid(Connection connection, String uuid) throws SQLException {
        return buscarIdPorUuid(connection, uuid) != null;
    }

    public int[] persistOne(Connection connection, PurePerson person) throws SQLException {
        return guardarPersonaCompleta(connection, person);
    }

    int[] guardarPersonaCompleta(Connection connection, PurePerson person) throws SQLException {
        Long personId = guardarPersona(connection, person);
        int asociaciones = guardarAsociaciones(connection, personId, person.getAsociaciones());
        int titulos = guardarTitulos(connection, personId, person.getTitulos());
        return new int[] {asociaciones, titulos};
    }

    Long guardarPersona(Connection connection, PurePerson person) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                UPSERT_PERSONA_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bindPersona(statement, person);
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
        return buscarIdPorUuid(connection, person.getUuid());
    }

    int guardarAsociaciones(Connection connection, Long personId,
                            List<StaffAssociation> associations) throws SQLException {
        eliminarRelaciones(connection, DELETE_ASOCIACIONES_SQL, personId);
        if (associations == null || associations.isEmpty()) {
            return 0;
        }
        int guardadas = 0;
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ASOCIACION_SQL)) {
            for (StaffAssociation asociacion : associations) {
                if (asociacion == null) {
                    continue;
                }
                if (asociacion.getPureId() == null) {
                    System.err.println("WARN Asociación de person_id=" + personId + " sin pureId");
                }
                if (asociacion.getOrganizationUuid() == null) {
                    System.err.println("WARN Asociación Pure "
                            + valorODesconocido(asociacion.getPureId()) + " sin organization.uuid");
                }
                statement.setLong(1, personId);
                setLong(statement, 2, asociacion.getPureId());
                statement.setString(3, asociacion.getOrganizationUuid());
                statement.setString(4, asociacion.getEmploymentType());
                statement.setString(5, asociacion.getEmail());
                setDate(statement, 6, asociacion.getStartDate());
                setDate(statement, 7, asociacion.getEndDate());
                statement.setBoolean(8, asociacion.isPrimaryAssociation());
                statement.setString(9, asociacion.getContractType());
                setBigDecimal(statement, 10, asociacion.getFte());
                statement.setString(11, asociacion.getJobDescription());
                statement.setString(12, asociacion.getJobTitle());
                statement.setString(13, asociacion.getStaffType());
                statement.addBatch();
                guardadas++;
            }
            statement.executeBatch();
        }
        return guardadas;
    }

    int guardarTitulos(Connection connection, Long personId,
                       List<AcademicTitle> titles) throws SQLException {
        eliminarRelaciones(connection, DELETE_TITULOS_SQL, personId);
        if (titles == null || titles.isEmpty()) {
            return 0;
        }
        int guardados = 0;
        try (PreparedStatement statement = connection.prepareStatement(INSERT_TITULO_SQL)) {
            for (AcademicTitle titulo : titles) {
                if (titulo == null) {
                    continue;
                }
                if (titulo.getPureId() == null) {
                    System.err.println("WARN Título de person_id=" + personId + " sin pureId");
                }
                if (titulo.getValue() == null) {
                    System.err.println("WARN Título Pure "
                            + valorODesconocido(titulo.getPureId()) + " sin valor");
                    continue;
                }
                statement.setLong(1, personId);
                setLong(statement, 2, titulo.getPureId());
                statement.setString(3, titulo.getType());
                statement.setString(4, titulo.getValue());
                statement.addBatch();
                guardados++;
            }
            statement.executeBatch();
        }
        return guardados;
    }

    private static void eliminarRelaciones(Connection connection, String sql, Long personId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            statement.executeUpdate();
        }
    }

    private static Long buscarIdPorUuid(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PERSONA_ID_SQL)) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("id");
                }
            }
        }
        throw new SQLException("No se pudo resolver el id interno de pure_person uuid=" + uuid);
    }

    private static void bindPersona(PreparedStatement statement, PurePerson person) throws SQLException {
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
        setTimestamp(statement, 11, person.getFechaCreacionPure());
        setTimestamp(statement, 12, person.getFechaModificacionPure());
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

    private static String valorODesconocido(Long pureId) {
        return pureId == null ? "?" : String.valueOf(pureId);
    }
}
