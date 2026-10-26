package cl.uss.ca.external.crossref;

import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ExceptionDetail;

import java.sql.SQLException;

/**
 * CROSSREF_UPSERT diagnostics. Not executed by the agent.
 */
public final class CrossrefUpsertErrorTest {
    private CrossrefUpsertErrorTest() {
    }

    public static void main(String[] args) {
        SQLException mysql = new SQLException(
                "Data truncation: Data too long for column 'name' at row 1", "22001", 1406);
        SQLException located = new SQLException(
                "Failed writing `uss-ca`.`crossref_author_affiliation`.`name` length=2341: "
                        + mysql.getMessage(),
                mysql.getSQLState(),
                mysql.getErrorCode(),
                mysql
        );
        DatabaseException wrapped = new DatabaseException(
                "Failed to reconcile Crossref work doi=10.1056/nejmoa2102968 "
                        + "(children: crossref_author, crossref_author_affiliation, "
                        + "crossref_issn, crossref_funder, crossref_license, crossref_relation)",
                located
        );
        String described = ExceptionDetail.describe(wrapped);
        assert described.contains("10.1056/nejmoa2102968");
        assert described.contains("crossref_author_affiliation");
        assert described.contains("`name`");
        assert described.contains("length=2341");
        assert described.contains("Data truncation");
        System.out.println("CrossrefUpsertErrorTest OK");
    }
}
