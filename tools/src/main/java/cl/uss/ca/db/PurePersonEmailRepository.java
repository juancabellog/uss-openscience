package cl.uss.ca.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Lectura de email y gender por uuid. No realiza INSERT/UPDATE/DELETE.
 */
public class PurePersonEmailRepository {
    private static final String SQL = """
            SELECT email, gender
            FROM pure_person
            WHERE uuid = ?
            """;

    public PreparedStatement prepararConsulta(Connection connection) throws SQLException {
        return connection.prepareStatement(SQL);
    }

    public PersonaEmailBd buscarPorUuid(PreparedStatement statement, String uuid) throws SQLException {
        statement.setString(1, uuid);
        try (ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return new PersonaEmailBd(
                    trimToEmpty(rs.getString("email")),
                    trimToEmpty(rs.getString("gender"))
            );
        }
    }

    private static String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
