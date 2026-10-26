package cl.uss.ca.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Solo lectura. Detecta el mismo Scopus ID repetido en la misma persona.
 * Nunca hace UPDATE/DELETE sobre pure_person ni pure_person_identifiers.
 * Un candidato es (persona + identifier_value) con COUNT(*) > 1;
 * no implica eliminar otros Scopus ID distintos de esa persona.
 */
public class ScopusIdsInternosDuplicadosRepository {
    static final String TIPO_SCOPUS = "Scopus ID";

    private static final String SELECT_CANDIDATOS_SQL = """
            SELECT
                p.id AS person_id,
                p.uuid,
                CONCAT_WS(' ', p.first_name, p.last_name) AS name,
                i.identifier_value,
                i.n
            FROM (
                SELECT
                    person_id,
                    identifier_value,
                    COUNT(*) AS n
                FROM pure_person_identifiers
                WHERE identifier_type = ?
                  AND identifier_value IS NOT NULL
                  AND TRIM(identifier_value) <> ''
                GROUP BY person_id, identifier_value
                HAVING COUNT(*) > 1
            ) i
            JOIN pure_person p
                ON i.person_id = p.id
            ORDER BY p.id, i.identifier_value
            """;

    private final String url;
    private final String user;
    private final String password;

    public ScopusIdsInternosDuplicadosRepository(String url, String user, String password) {
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

    public List<CandidatoScopusInterno> buscarCandidatos(Connection connection) throws SQLException {
        List<CandidatoScopusInterno> candidatos = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_CANDIDATOS_SQL)) {
            statement.setString(1, TIPO_SCOPUS);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String uuid = nuloAVacio(rs.getString("uuid"));
                    String nombre = nuloAVacio(rs.getString("name"));
                    String scopusId = nuloAVacio(rs.getString("identifier_value"));
                    if (uuid.isEmpty() || scopusId.isEmpty()) {
                        continue;
                    }
                    candidatos.add(new CandidatoScopusInterno(
                            rs.getLong("person_id"),
                            uuid,
                            nombre,
                            scopusId,
                            rs.getInt("n")
                    ));
                }
            }
        }
        return candidatos;
    }

    private static String nuloAVacio(String value) {
        return value == null ? "" : value.trim();
    }

    public record CandidatoScopusInterno(
            long personId,
            String uuid,
            String nombre,
            String scopusId,
            int ocurrenciasBd
    ) {
    }
}
