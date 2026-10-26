package cl.uss.ca.db;

import cl.uss.ca.scopus.importer.ScopusPublicationAuthorItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ScopusPublicationAuthorRepository {
    public static final String TABLE = "scopus_publication_author";
    private static final String SELECT_SCOPUS_AUTHOR_SQL = """
            SELECT id
            FROM scopus_author
            WHERE auth_id = ?
            ORDER BY id
            """;
    private static final String DELETE_BY_PUBLICATION_SQL = """
            DELETE FROM scopus_publication_author
            WHERE scopus_publication_id = ?
            """;
    private static final String INSERT_SQL = """
            INSERT INTO scopus_publication_author (
                scopus_publication_id,
                author_position,
                auth_id,
                author_name_short,
                author_name_full,
                author_affiliations_raw,
                scopus_author_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    public PreparedStatement prepararSelectScopusAuthor(Connection connection) throws SQLException {
        return connection.prepareStatement(SELECT_SCOPUS_AUTHOR_SQL);
    }

    public PreparedStatement prepararDeletePorPublicacion(Connection connection) throws SQLException {
        return connection.prepareStatement(DELETE_BY_PUBLICATION_SQL);
    }

    public PreparedStatement prepararInsert(Connection connection) throws SQLException {
        return connection.prepareStatement(INSERT_SQL);
    }

    public List<Long> buscarIdsPorAuthId(PreparedStatement statement, String authId) throws SQLException {
        if (authId == null || authId.isBlank()) {
            return List.of();
        }
        statement.setString(1, authId);
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

    public void eliminarPorPublicacion(PreparedStatement delete, long publicationId) throws SQLException {
        delete.setLong(1, publicationId);
        delete.executeUpdate();
    }

    public void insertar(PreparedStatement insert,
                         long publicationId,
                         ScopusPublicationAuthorItem item,
                         Long scopusAuthorId) throws SQLException {
        insert.setLong(1, publicationId);
        insert.setInt(2, item.position());
        JdbcValues.setString(insert, 3, item.authId());
        JdbcValues.setString(insert, 4, item.authorNameShort());
        JdbcValues.setString(insert, 5, item.authorNameFull());
        JdbcValues.setString(insert, 6, item.authorAffiliationsRaw());
        JdbcValues.setLong(insert, 7, scopusAuthorId);
        insert.executeUpdate();
    }
}
