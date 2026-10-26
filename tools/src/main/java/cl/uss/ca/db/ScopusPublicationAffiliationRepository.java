package cl.uss.ca.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ScopusPublicationAffiliationRepository {
    public static final String TABLE = "scopus_publication_affiliation";

    private static final String DELETE_BY_PUBLICATION_SQL = """
            DELETE FROM scopus_publication_affiliation
            WHERE scopus_publication_id = ?
            """;
    private static final String INSERT_SQL = """
            INSERT INTO scopus_publication_affiliation (
                scopus_publication_id,
                affiliation_position,
                affiliation_text
            ) VALUES (?, ?, ?)
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

    public void insertar(PreparedStatement insert, long publicationId, int position, String text)
            throws SQLException {
        insert.setLong(1, publicationId);
        insert.setInt(2, position);
        insert.setString(3, text);
        insert.executeUpdate();
    }
}
