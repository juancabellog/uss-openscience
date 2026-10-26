package cl.uss.ca.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ScopusPublicationKeywordRepository {
    public static final String TABLE = "scopus_publication_keyword";
    public static final String TYPE_AUTHOR = "AUTHOR";
    public static final String TYPE_INDEX = "INDEX";

    private static final String DELETE_BY_PUBLICATION_SQL = """
            DELETE FROM scopus_publication_keyword
            WHERE scopus_publication_id = ?
            """;
    private static final String INSERT_SQL = """
            INSERT INTO scopus_publication_keyword (
                scopus_publication_id,
                keyword_type,
                keyword_position,
                keyword_text
            ) VALUES (?, ?, ?, ?)
            """;

    public PreparedStatement prepararDeletePorPublicacion(Connection connection) throws SQLException {
        return connection.prepareStatement(DELETE_BY_PUBLICATION_SQL);
    }

    public PreparedStatement prepararInsert(Connection connection) throws SQLException {
        return connection.prepareStatement(INSERT_SQL);
    }

    public void eliminarPorPublicacion(PreparedStatement delete, long publicationId) throws SQLException {
        delete.setLong(1, publicationId);
        delete.executeUpdate();
    }

    public void insertar(PreparedStatement insert,
                         long publicationId,
                         String keywordType,
                         int position,
                         String text) throws SQLException {
        insert.setLong(1, publicationId);
        insert.setString(2, keywordType);
        insert.setInt(3, position);
        insert.setString(4, text);
        insert.executeUpdate();
    }
}
