package cl.uss.ca.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lectura de uuid y DOI de una persona. No realiza INSERT/UPDATE/DELETE.
 */
public class OrcidPersonaDoiRepository {
    private static final String SQL = """
            SELECT DISTINCT
                p.uuid,
                ev.doi
            FROM pure_person p
            LEFT JOIN pure_research_output_contributor c
                ON c.person_uuid = p.uuid
            LEFT JOIN pure_research_output_electronic_version ev
                ON ev.research_output_id = c.research_output_id
            WHERE p.id = ?
            """;

    private final String url;
    private final String user;
    private final String password;

    public OrcidPersonaDoiRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public PersonaDoisBd buscarPorPersonId(long personId) throws SQLException {
        String uuid = null;
        Set<String> dois = new LinkedHashSet<>();
        try (Connection connection = DriverManager.getConnection(url, user, password);
             PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setLong(1, personId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (uuid == null) {
                        uuid = trimToNull(rs.getString("uuid"));
                    }
                    String doi = trimToNull(rs.getString("doi"));
                    if (doi != null) {
                        dois.add(doi);
                    }
                }
            }
        }
        if (uuid == null) {
            return PersonaDoisBd.noEncontrada();
        }
        return new PersonaDoisBd(uuid, dois);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
