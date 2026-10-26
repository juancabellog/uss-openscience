package cl.uss.ca.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Actualiza únicamente pure_person.orcid. No modifica otros campos ni otras tablas.
 */
public class OrcidUpdateRepository {
    private static final String SELECT_SQL = """
            SELECT orcid
            FROM pure_person
            WHERE uuid = ?
            """;

    private static final String UPDATE_SQL = """
            UPDATE pure_person
            SET orcid = ?
            WHERE uuid = ?
            """;

    private final String url;
    private final String user;
    private final String password;

    public OrcidUpdateRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection abrirConexion() throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
        return connection;
    }

    public PreparedStatement prepararSelect(Connection connection) throws SQLException {
        return connection.prepareStatement(SELECT_SQL);
    }

    public PreparedStatement prepararUpdate(Connection connection) throws SQLException {
        return connection.prepareStatement(UPDATE_SQL);
    }

    public String buscarOrcid(PreparedStatement statement, String uuid) throws SQLException {
        statement.setString(1, uuid);
        try (ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            String orcid = rs.getString("orcid");
            if (orcid == null || orcid.isBlank()) {
                return "";
            }
            return orcid.trim();
        }
    }

    public int actualizarOrcid(PreparedStatement statement, String uuid, String orcid) throws SQLException {
        if (orcid == null) {
            statement.setNull(1, Types.VARCHAR);
        } else {
            statement.setString(1, orcid);
        }
        statement.setString(2, uuid);
        return statement.executeUpdate();
    }
}
