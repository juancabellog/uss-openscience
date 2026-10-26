package cl.uss.ca.repository;

import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.external.orcid.OrcidEmploymentRecord;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class OrcidEmploymentRepository extends JdbcRepository {

    public boolean available() {
        return tableExists("orcid_employment");
    }

    public void replaceForPerson(long personId, String orcid, List<OrcidEmploymentRecord> incoming) {
        if (!available()) {
            return;
        }
        Timestamp retrievedAt = Timestamp.from(Instant.now());
        try (Connection connection = connection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM " + qualified("orcid_employment") + " WHERE person_id = ?")) {
                    delete.setLong(1, personId);
                    delete.executeUpdate();
                }
                if (incoming != null && !incoming.isEmpty()) {
                    String sql = """
                            INSERT INTO %s (
                                person_id, orcid, put_code,
                                organization_name, organization_city, organization_region, organization_country,
                                organization_identifier, organization_identifier_type,
                                start_year, start_month, start_day,
                                end_year, end_month, end_day,
                                current_affiliation, visibility, retrieved_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """.formatted(qualified("orcid_employment"));
                    try (PreparedStatement insert = connection.prepareStatement(sql)) {
                        for (OrcidEmploymentRecord record : incoming) {
                            if (record == null || record.putCode == null) {
                                continue;
                            }
                            int index = 1;
                            insert.setLong(index++, personId);
                            insert.setString(index++, orcid);
                            insert.setLong(index++, record.putCode);
                            setNullableString(insert, index++, record.organizationName);
                            setNullableString(insert, index++, record.organizationCity);
                            setNullableString(insert, index++, record.organizationRegion);
                            setNullableString(insert, index++, record.organizationCountry);
                            setNullableString(insert, index++, record.organizationIdentifier);
                            setNullableString(insert, index++, record.organizationIdentifierType);
                            setNullableInt(insert, index++, record.startYear);
                            setNullableInt(insert, index++, record.startMonth);
                            setNullableInt(insert, index++, record.startDay);
                            setNullableInt(insert, index++, record.endYear);
                            setNullableInt(insert, index++, record.endMonth);
                            setNullableInt(insert, index++, record.endDay);
                            insert.setInt(index++, record.currentAffiliation ? 1 : 0);
                            setNullableString(insert, index++, record.visibility);
                            insert.setTimestamp(index, retrievedAt);
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previous);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to persist ORCID employment", ex);
        }
    }

    public List<StoredEmployment> listForActivePersons() {
        if (!available()) {
            return List.of();
        }
        String sql = """
                SELECT e.person_id, e.orcid, e.put_code, e.organization_name,
                       e.organization_identifier, e.organization_identifier_type,
                       e.current_affiliation
                FROM %s e
                JOIN %s p ON p.id = e.person_id
                WHERE %s
                ORDER BY e.person_id, e.put_code
                """.formatted(
                qualified("orcid_employment"),
                qualified("pure_person"),
                activeInPurePredicate("pure_person", "p")
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<StoredEmployment> items = new ArrayList<>();
            while (rs.next()) {
                StoredEmployment row = new StoredEmployment();
                row.personId = rs.getLong("person_id");
                row.orcid = nullableString(rs, "orcid");
                row.putCode = nullableLong(rs, "put_code");
                row.organizationName = nullableString(rs, "organization_name");
                row.organizationIdentifier = nullableString(rs, "organization_identifier");
                row.organizationIdentifierType = nullableString(rs, "organization_identifier_type");
                row.currentAffiliation = rs.getBoolean("current_affiliation");
                items.add(row);
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list ORCID employment", ex);
        }
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.trim());
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    public static class StoredEmployment {
        public long personId;
        public String orcid;
        public Long putCode;
        public String organizationName;
        public String organizationIdentifier;
        public String organizationIdentifierType;
        public boolean currentAffiliation;
    }
}
