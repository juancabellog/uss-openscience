package cl.uss.ca.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Solo lectura de {@code pure_person_identifiers} y {@code pure_person}.
 * No realiza INSERT, UPDATE ni DELETE.
 */
public class ScopusIdDuplicadoRepository {
    static final String TIPO_SCOPUS = "Scopus ID";

    private static final String SELECT_SCOPUS_REPETIDOS_SQL = """
            SELECT
                identifier_value
            FROM pure_person_identifiers
            WHERE identifier_type = ?
              AND identifier_value IS NOT NULL
              AND TRIM(identifier_value) <> ''
            GROUP BY identifier_value
            HAVING COUNT(DISTINCT person_id) > 1
            ORDER BY identifier_value
            """;

    private static final String SELECT_PERSONAS_CON_SCOPUS_REPETIDO_SQL = """
            SELECT
                p.id AS person_id,
                p.uuid,
                p.first_name,
                p.last_name,
                TRIM(i.identifier_value) AS identifier_value
            FROM pure_person_identifiers i
            JOIN pure_person p
                ON p.id = i.person_id
            WHERE i.identifier_type = ?
              AND i.identifier_value IS NOT NULL
              AND TRIM(i.identifier_value) <> ''
              AND i.person_id IN (
                    SELECT person_id
                    FROM pure_person_identifiers
                    WHERE identifier_type = ?
                      AND identifier_value IS NOT NULL
                      AND TRIM(identifier_value) <> ''
                      AND identifier_value IN (
                            SELECT identifier_value
                            FROM pure_person_identifiers
                            WHERE identifier_type = ?
                              AND identifier_value IS NOT NULL
                              AND TRIM(identifier_value) <> ''
                            GROUP BY identifier_value
                            HAVING COUNT(DISTINCT person_id) > 1
                      )
              )
            ORDER BY p.id, identifier_value
            """;

    private final String url;
    private final String user;
    private final String password;

    public ScopusIdDuplicadoRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection abrirConexion() throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        connection.setReadOnly(true);
        connection.setAutoCommit(true);
        return connection;
    }

    public List<String> buscarScopusIdsRepetidos(Connection connection) throws SQLException {
        List<String> valores = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SCOPUS_REPETIDOS_SQL)) {
            statement.setString(1, TIPO_SCOPUS);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String valor = trimToNull(rs.getString("identifier_value"));
                    if (valor != null) {
                        valores.add(valor);
                    }
                }
            }
        }
        return valores;
    }

    public List<ScopusPersonIdentifierRow> buscarIdentificadoresDePersonasInvolucradas(Connection connection)
            throws SQLException {
        List<ScopusPersonIdentifierRow> filas = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PERSONAS_CON_SCOPUS_REPETIDO_SQL)) {
            statement.setString(1, TIPO_SCOPUS);
            statement.setString(2, TIPO_SCOPUS);
            statement.setString(3, TIPO_SCOPUS);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String valor = trimToNull(rs.getString("identifier_value"));
                    if (valor == null) {
                        continue;
                    }
                    filas.add(new ScopusPersonIdentifierRow(
                            rs.getLong("person_id"),
                            nuloAVacio(rs.getString("uuid")),
                            nuloAVacio(rs.getString("first_name")),
                            nuloAVacio(rs.getString("last_name")),
                            valor
                    ));
                }
            }
        }
        return filas;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nuloAVacio(String value) {
        return value == null ? "" : value.trim();
    }

    public record ScopusPersonIdentifierRow(
            long personId,
            String uuid,
            String firstName,
            String lastName,
            String identifierValue
    ) {
    }
}
