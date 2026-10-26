package cl.uss.ca.db;

import cl.uss.ca.scopus.importer.ScopusPublicationCsvRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ScopusPublicationRepository {
    public static final String TABLE = "scopus_publication";

    private static final String SELECT_BY_EID_SQL = """
            SELECT id
            FROM scopus_publication
            WHERE eid = ?
            ORDER BY id
            """;
    private static final String SELECT_BY_DOI_SQL = """
            SELECT id
            FROM scopus_publication
            WHERE doi_normalized = ?
            ORDER BY id
            """;
    private static final String INSERT_SQL = """
            INSERT INTO scopus_publication (
                eid, doi, doi_normalized, title, publication_year, source_title,
                volume, issue, article_number, page_start, page_end, cited_by,
                link, issn, isbn, coden, pubmed_id, document_type, publication_stage,
                open_access, source, authors_raw, author_full_names_raw, author_ids_raw,
                affiliations_raw, authors_with_affiliations_raw, author_keywords_raw,
                index_keywords_raw, funding_details, funding_texts, `abstract`,
                correspondence_address, editors, source_file
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """;
    private static final String UPDATE_SQL = """
            UPDATE scopus_publication
            SET eid = ?,
                doi = ?,
                doi_normalized = ?,
                title = ?,
                publication_year = ?,
                source_title = ?,
                volume = ?,
                issue = ?,
                article_number = ?,
                page_start = ?,
                page_end = ?,
                cited_by = ?,
                link = ?,
                issn = ?,
                isbn = ?,
                coden = ?,
                pubmed_id = ?,
                document_type = ?,
                publication_stage = ?,
                open_access = ?,
                source = ?,
                authors_raw = ?,
                author_full_names_raw = ?,
                author_ids_raw = ?,
                affiliations_raw = ?,
                authors_with_affiliations_raw = ?,
                author_keywords_raw = ?,
                index_keywords_raw = ?,
                funding_details = ?,
                funding_texts = ?,
                `abstract` = ?,
                correspondence_address = ?,
                editors = ?,
                source_file = ?
            WHERE id = ?
            """;

    public PreparedStatement prepararSelectPorEid(Connection connection) throws SQLException {
        return connection.prepareStatement(SELECT_BY_EID_SQL);
    }

    public PreparedStatement prepararSelectPorDoi(Connection connection) throws SQLException {
        return connection.prepareStatement(SELECT_BY_DOI_SQL);
    }

    public PreparedStatement prepararInsert(Connection connection) throws SQLException {
        return connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
    }

    public PreparedStatement prepararUpdate(Connection connection) throws SQLException {
        return connection.prepareStatement(UPDATE_SQL);
    }

    public List<Long> buscarIdsPorEid(PreparedStatement statement, String eid) throws SQLException {
        if (eid == null || eid.isBlank()) {
            return List.of();
        }
        statement.setString(1, eid);
        return leerIds(statement);
    }

    public List<Long> buscarIdsPorDoiNormalizado(PreparedStatement statement, String doiNormalized)
            throws SQLException {
        if (doiNormalized == null || doiNormalized.isBlank()) {
            return List.of();
        }
        statement.setString(1, doiNormalized);
        return leerIds(statement);
    }

    public long insertar(PreparedStatement insert, ScopusPublicationCsvRow fila, String sourceFile)
            throws SQLException {
        bind(insert, fila, sourceFile, null);
        insert.executeUpdate();
        try (ResultSet keys = insert.getGeneratedKeys()) {
            if (keys.next()) {
                long id = keys.getLong(1);
                if (id > 0) {
                    return id;
                }
            }
        }
        throw new SQLException("No se pudo obtener scopus_publication.id generado para EID="
                + fila.eid());
    }

    public void actualizar(PreparedStatement update, long id, ScopusPublicationCsvRow fila, String sourceFile)
            throws SQLException {
        bind(update, fila, sourceFile, id);
        update.executeUpdate();
    }

    private static void bind(PreparedStatement statement, ScopusPublicationCsvRow fila,
                             String sourceFile, Long id) throws SQLException {
        JdbcValues.setString(statement, 1, fila.eid());
        JdbcValues.setString(statement, 2, fila.doi());
        JdbcValues.setString(statement, 3, fila.doiNormalized());
        JdbcValues.setString(statement, 4, fila.title());
        JdbcValues.setInt(statement, 5, fila.publicationYear());
        JdbcValues.setString(statement, 6, fila.sourceTitle());
        JdbcValues.setString(statement, 7, fila.volume());
        JdbcValues.setString(statement, 8, fila.issue());
        JdbcValues.setString(statement, 9, fila.articleNumber());
        JdbcValues.setString(statement, 10, fila.pageStart());
        JdbcValues.setString(statement, 11, fila.pageEnd());
        JdbcValues.setInt(statement, 12, fila.citedBy());
        JdbcValues.setString(statement, 13, fila.link());
        JdbcValues.setString(statement, 14, fila.issn());
        JdbcValues.setString(statement, 15, fila.isbn());
        JdbcValues.setString(statement, 16, fila.coden());
        JdbcValues.setString(statement, 17, fila.pubmedId());
        JdbcValues.setString(statement, 18, fila.documentType());
        JdbcValues.setString(statement, 19, fila.publicationStage());
        JdbcValues.setString(statement, 20, fila.openAccess());
        JdbcValues.setString(statement, 21, fila.source());
        JdbcValues.setString(statement, 22, fila.authorsRaw());
        JdbcValues.setString(statement, 23, fila.authorFullNamesRaw());
        JdbcValues.setString(statement, 24, fila.authorIdsRaw());
        JdbcValues.setString(statement, 25, fila.affiliationsRaw());
        JdbcValues.setString(statement, 26, fila.authorsWithAffiliationsRaw());
        JdbcValues.setString(statement, 27, fila.authorKeywordsRaw());
        JdbcValues.setString(statement, 28, fila.indexKeywordsRaw());
        JdbcValues.setString(statement, 29, fila.fundingDetails());
        JdbcValues.setString(statement, 30, fila.fundingTexts());
        JdbcValues.setString(statement, 31, fila.abstractText());
        JdbcValues.setString(statement, 32, fila.correspondenceAddress());
        JdbcValues.setString(statement, 33, fila.editors());
        JdbcValues.setString(statement, 34, sourceFile);
        if (id != null) {
            statement.setLong(35, id);
        }
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
}
