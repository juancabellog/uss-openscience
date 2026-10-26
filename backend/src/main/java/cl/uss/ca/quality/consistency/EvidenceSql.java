package cl.uss.ca.quality.consistency;

import cl.uss.ca.quality.DataQualityIdentifiers;
import cl.uss.ca.repository.DataQualityRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class EvidenceSql {
    static final int CHUNK = 200;

    private EvidenceSql() {
    }

    static List<List<String>> chunks(Collection<String> values) {
        List<List<String>> chunks = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            return chunks;
        }
        List<String> current = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            current.add(value);
            if (current.size() >= CHUNK) {
                chunks.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    static OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : OffsetDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    static AuthorEvidence author(String fullName, String orcid, String scopusId, String openAlexId, Integer position) {
        AuthorEvidence author = new AuthorEvidence();
        author.fullName = fullName;
        author.orcid = DataQualityIdentifiers.normalizeOrcid(orcid);
        author.scopusAuthorId = blank(scopusId) ? null : scopusId.trim();
        author.openAlexAuthorId = blank(openAlexId) ? null : openAlexId.trim();
        author.position = position;
        return author;
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static String str(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static Boolean bool(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    static boolean table(DataQualityRepository repository, String name) {
        try {
            return repository.tableExistsPublic(name);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
