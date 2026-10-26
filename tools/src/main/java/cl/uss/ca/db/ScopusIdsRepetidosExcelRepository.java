package cl.uss.ca.db;

import cl.uss.ca.scopus.ScopusDuplicadoRow;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Solo lectura. Reporta parejas de personas que comparten un Scopus ID.
 */
public class ScopusIdsRepetidosExcelRepository {
    static final String TIPO_SCOPUS = "Scopus ID";

    private static final String SELECT_PAREJAS_SQL = """
            WITH scopus_limpio AS (
                SELECT DISTINCT
                    person_id,
                    TRIM(identifier_value) AS identifier_value
                FROM pure_person_identifiers
                WHERE identifier_type = ?
                  AND identifier_value IS NOT NULL
                  AND TRIM(identifier_value) <> ''
            ),
            scopus_por_persona AS (
                SELECT
                    person_id,
                    COUNT(DISTINCT identifier_value) AS cantidad_scopus
                FROM scopus_limpio
                GROUP BY person_id
            ),
            scopus_repetidos AS (
                SELECT
                    identifier_value
                FROM scopus_limpio
                GROUP BY identifier_value
                HAVING COUNT(DISTINCT person_id) > 1
            )
            SELECT
                p1.id AS idPerson,
                p1.uuid AS uuid,
                p1.first_name AS first_name,
                p1.last_name AS last_name,
                c1.cantidad_scopus AS cantidadScopus1,
                p2.id AS idPerson2,
                p2.uuid AS uuid2,
                p2.first_name AS first_name2,
                p2.last_name AS last_name2,
                c2.cantidad_scopus AS cantidadScopus2,
                i1.identifier_value AS scopus_repetido
            FROM scopus_limpio i1
            JOIN scopus_limpio i2
                ON i1.identifier_value = i2.identifier_value
               AND i1.person_id < i2.person_id
            JOIN scopus_repetidos sr
                ON sr.identifier_value = i1.identifier_value
            JOIN pure_person p1
                ON p1.id = i1.person_id
            JOIN pure_person p2
                ON p2.id = i2.person_id
            JOIN scopus_por_persona c1
                ON c1.person_id = p1.id
            JOIN scopus_por_persona c2
                ON c2.person_id = p2.id
            ORDER BY
                i1.identifier_value,
                p1.id,
                p2.id
            """;

    private final String url;
    private final String user;
    private final String password;

    public ScopusIdsRepetidosExcelRepository(String url, String user, String password) {
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

    public List<ScopusDuplicadoRow> buscarParejas(Connection connection) throws SQLException {
        List<ScopusDuplicadoRow> filas = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PAREJAS_SQL)) {
            statement.setString(1, TIPO_SCOPUS);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    filas.add(new ScopusDuplicadoRow(
                            rs.getLong("idPerson"),
                            nuloAVacio(rs.getString("uuid")),
                            nombreCompleto(rs.getString("first_name"), rs.getString("last_name")),
                            rs.getInt("cantidadScopus1"),
                            rs.getLong("idPerson2"),
                            nuloAVacio(rs.getString("uuid2")),
                            nombreCompleto(rs.getString("first_name2"), rs.getString("last_name2")),
                            rs.getInt("cantidadScopus2"),
                            nuloAVacio(rs.getString("scopus_repetido"))
                    ));
                }
            }
        }
        return filas;
    }

    static String nombreCompleto(String firstName, String lastName) {
        String first = trimToNull(firstName);
        String last = trimToNull(lastName);
        if (first != null && last != null) {
            return (first + " " + last).trim();
        }
        if (first != null) {
            return first;
        }
        if (last != null) {
            return last;
        }
        return "";
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
}
