package cl.uss.ca.repository;

import cl.uss.ca.exception.DatabaseException;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ResearcherIdentityRepository extends JdbcRepository {

    public long countActivePersons() {
        return scalarLong(
                "SELECT COUNT(*) FROM " + qualified("pure_person") + " p WHERE "
                        + activeInPurePredicate("pure_person", "p")
        );
    }

    public long countActivePersonsWithScopus() {
        if (!tableExists("pure_person_identifiers")) {
            return 0L;
        }
        return scalarLong("""
                SELECT COUNT(DISTINCT p.id)
                FROM %s p
                WHERE %s
                  AND EXISTS (
                    SELECT 1
                    FROM %s i
                    WHERE i.person_id = p.id
                      AND i.identifier_type = ?
                      AND i.identifier_value IS NOT NULL
                      AND TRIM(i.identifier_value) <> ''
                  )
                """.formatted(
                qualified("pure_person"),
                activeInPurePredicate("pure_person", "p"),
                qualified("pure_person_identifiers")
        ), SCOPUS_IDENTIFIER_TYPE);
    }

    public long countActivePersonsWithMultipleDistinctScopus() {
        if (!tableExists("pure_person_identifiers")) {
            return 0L;
        }
        return scalarLong("""
                SELECT COUNT(*) FROM (
                    SELECT i.person_id
                    FROM %s p
                    JOIN %s i ON i.person_id = p.id
                    WHERE %s
                      AND i.identifier_type = ?
                      AND i.identifier_value IS NOT NULL
                      AND TRIM(i.identifier_value) <> ''
                    GROUP BY i.person_id
                    HAVING COUNT(DISTINCT TRIM(i.identifier_value)) >= 2
                ) t
                """.formatted(
                qualified("pure_person"),
                qualified("pure_person_identifiers"),
                activeInPurePredicate("pure_person", "p")
        ), SCOPUS_IDENTIFIER_TYPE);
    }

    public List<SignatureContributionRow> listSignatureContributions() {
        if (!tableExists("pure_research_output_contributor") || !tableExists("pure_research_output")) {
            return List.of();
        }
        String sql = """
                SELECT p.id AS person_id, p.pure_id, p.first_name, p.last_name, p.cargo, p.orcid,
                       c.research_output_id, c.first_name AS signature_first, c.last_name AS signature_last
                FROM %s p
                JOIN %s c ON c.person_uuid = p.uuid
                JOIN %s o ON o.id = c.research_output_id
                WHERE %s
                  AND %s
                  AND p.uuid IS NOT NULL
                  AND TRIM(p.uuid) <> ''
                """.formatted(
                qualified("pure_person"),
                qualified("pure_research_output_contributor"),
                qualified("pure_research_output"),
                activeInPurePredicate("pure_person", "p"),
                activeInPurePredicate("pure_research_output", "o")
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<SignatureContributionRow> items = new ArrayList<>();
            while (rs.next()) {
                SignatureContributionRow row = new SignatureContributionRow();
                row.personId = rs.getLong("person_id");
                row.pureId = nullableLong(rs, "pure_id");
                row.firstName = nullableString(rs, "first_name");
                row.lastName = nullableString(rs, "last_name");
                row.position = nullableString(rs, "cargo");
                row.orcid = nullableString(rs, "orcid");
                row.researchOutputId = rs.getLong("research_output_id");
                row.signatureFirst = nullableString(rs, "signature_first");
                row.signatureLast = nullableString(rs, "signature_last");
                items.add(row);
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list signature contributions", ex);
        }
    }

    public List<PersonIdentityRow> listActivePersons() {
        String sql = """
                SELECT p.id, p.pure_id, p.first_name, p.last_name, p.cargo, p.orcid
                FROM %s p
                WHERE %s
                """.formatted(
                qualified("pure_person"),
                activeInPurePredicate("pure_person", "p")
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<PersonIdentityRow> items = new ArrayList<>();
            while (rs.next()) {
                PersonIdentityRow row = new PersonIdentityRow();
                row.personId = rs.getLong("id");
                row.pureId = nullableLong(rs, "pure_id");
                row.firstName = nullableString(rs, "first_name");
                row.lastName = nullableString(rs, "last_name");
                row.position = nullableString(rs, "cargo");
                row.orcid = nullableString(rs, "orcid");
                items.add(row);
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list active persons", ex);
        }
    }

    public static class SignatureContributionRow {
        public long personId;
        public Long pureId;
        public String firstName;
        public String lastName;
        public String position;
        public String orcid;
        public long researchOutputId;
        public String signatureFirst;
        public String signatureLast;
    }

    public static class PersonIdentityRow {
        public long personId;
        public Long pureId;
        public String firstName;
        public String lastName;
        public String position;
        public String orcid;
    }
}
