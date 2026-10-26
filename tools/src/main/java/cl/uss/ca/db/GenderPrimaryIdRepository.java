package cl.uss.ca.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Actualiza únicamente gender y primaryId de pure_person. Solo lectura/escritura de esas columnas.
 */
public class GenderPrimaryIdRepository {
    private static final String UPDATE_SQL = """
            UPDATE pure_person
            SET gender = ?,
                `primaryId` = ?
            WHERE uuid = ?
            """;

    private final String url;
    private final String user;
    private final String password;

    public GenderPrimaryIdRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection abrirConexion() throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
        return connection;
    }

    public PreparedStatement prepararUpdate(Connection connection) throws SQLException {
        return connection.prepareStatement(UPDATE_SQL);
    }

    public int actualizar(PreparedStatement statement, String uuid, String gender, String primaryId)
            throws SQLException {
        setVarchar(statement, 1, gender);
        setVarchar(statement, 2, primaryId);
        statement.setString(3, uuid);
        return statement.executeUpdate();
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
