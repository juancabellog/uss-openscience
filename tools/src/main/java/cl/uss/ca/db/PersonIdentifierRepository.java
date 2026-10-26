package cl.uss.ca.db;

import cl.uss.ca.pure.PersonIdentifier;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Lectura de {@code pure_person.id} por uuid y escritura exclusiva en
 * {@code pure_person_identifiers}. No modifica {@code pure_person}.
 */
public class PersonIdentifierRepository {
    private static final String SELECT_PERSONA_ID_SQL = """
            SELECT id
            FROM pure_person
            WHERE uuid = ?
            """;
    private static final String DELETE_SQL = """
            DELETE FROM pure_person_identifiers
            WHERE person_id = ?
            """;
    private static final String INSERT_SQL = """
            INSERT INTO pure_person_identifiers (
                person_id,
                pure_id,
                identifier_typeDiscriminator,
                identifier_type,
                identifier_value
            )
            VALUES (?, ?, ?, ?, ?)
            """;

    private final String url;
    private final String user;
    private final String password;

    public PersonIdentifierRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection abrirConexion() throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
        return connection;
    }

    public PreparedStatement prepararSelectPersona(Connection connection) throws SQLException {
        return connection.prepareStatement(SELECT_PERSONA_ID_SQL);
    }

    public PreparedStatement prepararDelete(Connection connection) throws SQLException {
        return connection.prepareStatement(DELETE_SQL);
    }

    public PreparedStatement prepararInsert(Connection connection) throws SQLException {
        return connection.prepareStatement(INSERT_SQL);
    }

    public Long buscarIdPorUuid(PreparedStatement statement, String uuid) throws SQLException {
        statement.setString(1, uuid);
        try (ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            long id = rs.getLong("id");
            return rs.wasNull() ? null : id;
        }
    }

    public int eliminarPorPersonId(PreparedStatement statement, long personId) throws SQLException {
        statement.setLong(1, personId);
        return statement.executeUpdate();
    }

    public void insertar(PreparedStatement statement, long personId, PersonIdentifier identifier)
            throws SQLException {
        statement.setLong(1, personId);
        JdbcValues.setLong(statement, 2, identifier.getPureId());
        statement.setString(3, identifier.getTypeDiscriminator());
        setVarchar(statement, 4, identifier.getIdentifierType());
        statement.setString(5, identifier.getValue());
        statement.executeUpdate();
    }

    private static void setVarchar(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
