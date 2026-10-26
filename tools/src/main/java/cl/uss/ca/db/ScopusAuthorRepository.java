package cl.uss.ca.db;

import cl.uss.ca.scopus.ScopusAffiliation;
import cl.uss.ca.scopus.ScopusAuthorCsvRecord;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Escritura exclusiva en {@code scopus_author}, {@code scopus_areas}
 * y {@code scopus_author_affiliation}.
 * No elimina afiliaciones anteriores: cada importación solo agrega o confirma
 * la afiliación del archivo actual.
 */
public class ScopusAuthorRepository {
    static final int AFFILIATION_NAME_MAX_LENGTH = 255;
    static final int AFFILIATION_ID_MAX_LENGTH = 30;
    private static final String INSERT_AUTOR_SQL = """
            INSERT INTO scopus_author (
                auth_id,
                author_name,
                number_documents,
                orcid
            )
            VALUES (?, ?, ?, ?)
            """;
    private static final String SELECT_ID_BY_AUTH_ID_SQL = """
            SELECT id
            FROM scopus_author
            WHERE auth_id = ?
            ORDER BY id
            """;
    private static final String SELECT_ID_BY_NAME_SQL = """
            SELECT id
            FROM scopus_author
            WHERE REGEXP_REPLACE(TRIM(author_name), '[[:space:]]+', ' ') = ?
            ORDER BY id
            """;
    private static final String INSERT_AREA_SQL = """
            INSERT INTO scopus_areas (
                scopus_author_id,
                area
            )
            VALUES (?, ?)
            """;
    private static final String UPSERT_AFFILIATION_SQL = """
            INSERT INTO scopus_author_affiliation (
                scopus_author_id,
                affiliation_name,
                affiliation_id
            )
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                affiliation_name = ?
            """;

    private final String url;
    private final String user;
    private final String password;

    public ScopusAuthorRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection abrirConexion() throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
        return connection;
    }

    public PreparedStatement prepararInsertAutor(Connection connection) throws SQLException {
        return connection.prepareStatement(INSERT_AUTOR_SQL, Statement.RETURN_GENERATED_KEYS);
    }

    public PreparedStatement prepararSelectIdPorAuthId(Connection connection) throws SQLException {
        return connection.prepareStatement(SELECT_ID_BY_AUTH_ID_SQL);
    }

    public PreparedStatement prepararSelectIdPorNombre(Connection connection) throws SQLException {
        return connection.prepareStatement(SELECT_ID_BY_NAME_SQL);
    }

    public PreparedStatement prepararInsertArea(Connection connection) throws SQLException {
        return connection.prepareStatement(INSERT_AREA_SQL);
    }

    public PreparedStatement prepararUpsertAfiliacion(Connection connection) throws SQLException {
        return connection.prepareStatement(UPSERT_AFFILIATION_SQL);
    }

    public List<Long> buscarIdsPorAuthId(PreparedStatement statement, String authId) throws SQLException {
        if (authId == null || authId.isBlank()) {
            return List.of();
        }
        statement.setString(1, authId);
        return leerIds(statement);
    }

    public List<Long> buscarIdsPorNombre(PreparedStatement statement, String authorName) throws SQLException {
        String normalizado = normalizarAuthorName(authorName);
        if (normalizado == null) {
            return List.of();
        }
        statement.setString(1, normalizado);
        return leerIds(statement);
    }

    public long insertarAutor(PreparedStatement insert, ScopusAuthorCsvRecord registro) throws SQLException {
        if (registro.authId() == null || registro.authId().isBlank()) {
            insert.setNull(1, Types.VARCHAR);
        } else {
            insert.setString(1, registro.authId());
        }
        insert.setString(2, registro.authorName());
        JdbcValues.setInt(insert, 3, registro.numberDocuments());
        if (registro.orcid() == null) {
            insert.setNull(4, Types.VARCHAR);
        } else {
            insert.setString(4, registro.orcid());
        }
        insert.executeUpdate();
        try (ResultSet keys = insert.getGeneratedKeys()) {
            if (keys.next()) {
                long id = keys.getLong(1);
                if (id > 0) {
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo obtener scopus_author.id generado para author_name="
                + registro.authorName());
    }

    /**
     * Trim y colapsa espacios internos. No elimina acentos ni puntuación;
     * la comparación case-insensitive queda a cargo de la collation MySQL.
     */
    public static String normalizarAuthorName(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.trim().replaceAll("\\s+", " ");
        return normalizado.isEmpty() ? null : normalizado;
    }

    private static List<Long> leerIds(PreparedStatement statement) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                if (!rs.wasNull()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    public void insertarArea(PreparedStatement statement, long scopusAuthorId, String area)
            throws SQLException {
        statement.setLong(1, scopusAuthorId);
        statement.setString(2, area);
        statement.executeUpdate();
    }

    /**
     * Inserta la afiliación del archivo actual o actualiza el nombre si ya existe
     * {@code (scopus_author_id, affiliation_id)}. No borra otras afiliaciones.
     *
     * @return {@code true} si se insertó una fila nueva
     */
    public boolean upsertAfiliacion(PreparedStatement statement,
                                    long scopusAuthorId,
                                    ScopusAffiliation affiliation) throws SQLException {
        if (affiliation == null) {
            throw new SQLException("Afiliación no determinada para scopus_author.id=" + scopusAuthorId);
        }
        String affiliationName = truncar(affiliation.affiliationName(), AFFILIATION_NAME_MAX_LENGTH);
        String affiliationId = truncar(affiliation.affiliationId(), AFFILIATION_ID_MAX_LENGTH);
        if (affiliationName == null || affiliationName.isBlank()
                || affiliationId == null || affiliationId.isBlank()) {
            throw new SQLException("Afiliación inválida para scopus_author.id=" + scopusAuthorId);
        }
        statement.setLong(1, scopusAuthorId);
        statement.setString(2, affiliationName);
        statement.setString(3, affiliationId);
        statement.setString(4, affiliationName);
        int affected = statement.executeUpdate();
        return affected == 1;
    }

    private static String truncar(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
