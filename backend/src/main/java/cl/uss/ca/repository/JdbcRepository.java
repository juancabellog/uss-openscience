package cl.uss.ca.repository;

import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ValidationException;
import jakarta.annotation.Resource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class JdbcRepository {
    private static final Logger LOG = Logger.getLogger(JdbcRepository.class.getName());
    static final String SCOPUS_IDENTIFIER_TYPE = "Scopus ID";
    private static final String DATABASE_CATALOG = "uss-ca";
    private static final Set<String> KNOWN_TABLES = Set.of(
            "pure_person",
            "pure_person_identifiers",
            "pure_person_organization",
            "pure_organization",
            "uss_directory_import",
            "uss_directory_import_row",
            "uss_directory_import_issue",
            "uss_directory_person",
            "uss_directory_person_email",
            "uss_directory_person_history",
            "uss_directory_person_pure_link",
            "event",
            "event_organizing_unit",
            "event_participant",
            "event_registration",
            "event_registration_status_history",
            "event_attendance",
            "event_document",
            "event_generated_artifact",
            "event_attendance_import",
            "event_attendance_import_row",
            "event_generated_artifact_row",
            "event_attendance_history",
            "event_email_challenge",
            "event_verified_session",
            "pure_person_title",
            "pure_journal",
            "pure_journal_title",
            "pure_journal_issn",
            "pure_journal_identifier",
            "pure_journal_keyword_group",
            "pure_journal_classification",
            "pure_journal_keyword",
            "pure_journal_metric",
            "pure_journal_metric_fetch",
            "pure_project",
            "pure_project_participant",
            "pure_project_participant_organization",
            "pure_project_organization",
            "pure_project_identifier",
            "pure_project_description",
            "pure_project_nature_type",
            "pure_project_pretty_url_identifier",
            "pure_research_output",
            "pure_research_output_contributor",
            "pure_research_output_contributor_organization",
            "pure_research_output_electronic_version",
            "pure_research_output_external_organization",
            "pure_research_output_identifier",
            "pure_research_output_journal",
            "pure_research_output_keyword",
            "pure_research_output_keyword_group",
            "pure_research_output_link",
            "pure_research_output_status",
            "pure_research_output_metric",
            "pure_research_output_metric_fetch",
            "pure_person_metric",
            "pure_person_metric_fetch",
            "scopus_author",
            "scopus_publication",
            "scopus_publication_author",
            "openscience_sync_run",
            "sync_execution",
            "sync_control",
            "sync_change",
            "sync_error",
            "pure_correction_audit",
            "orcid_person_sync",
            "orcid_work_group",
            "orcid_work_group_external_id",
            "orcid_work",
            "orcid_work_external_id",
            "orcid_work_contributor",
            "orcid_employment",
            "orcid_discovery_execution",
            "orcid_discovery_person",
            "orcid_candidate",
            "orcid_candidate_evidence",
            "data_quality_rule",
            "data_quality_execution",
            "data_quality_finding",
            "data_quality_finding_evidence",
            "unpaywall_work",
            "unpaywall_oa_location",
            "unpaywall_research_output",
            "unpaywall_sync_stats",
            "crossref_work",
            "crossref_author",
            "crossref_author_affiliation",
            "crossref_issn",
            "crossref_funder",
            "crossref_license",
            "crossref_relation",
            "crossref_research_output",
            "crossref_sync_stats",
            "crossref_doi_candidate",
            "openalex_work",
            "openalex_author",
            "openalex_authorship",
            "openalex_authorship_institution",
            "openalex_work_topic",
            "openalex_research_output",
            "openalex_person_match",
            "openalex_person_match_work",
            "openalex_person_match_work_evidence",
            "openalex_sync_stats",
            "dspace_community",
            "dspace_collection",
            "dspace_item",
            "dspace_item_metadata",
            "dspace_bitstream",
            "dspace_item_collection",
            "dspace_item_match",
            "dspace_sync_checkpoint",
            "dspace_sync_stats",
            "app_module",
            "app_permission",
            "app_role",
            "app_user",
            "app_user_role",
            "app_role_permission",
            "app_account_token",
            "app_session",
            "app_security_event"
    );
    private final ConcurrentHashMap<String, Boolean> presentTables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> presentColumns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ColumnProfile> columnProfiles = new ConcurrentHashMap<>();

    @Resource(lookup = "jdbc/openscience", shareable = false)
    DataSource dataSource;

    protected Connection connection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource jdbc/openscience is not bound");
        }
        return dataSource.getConnection();
    }

    protected static void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // keep the original error; a dead socket cannot roll back
        }
    }

    protected static void restoreAutoCommit(Connection connection, boolean previous) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(previous);
        } catch (SQLException ignored) {
            // fatal connections must still leave the try-with-resources close path
        }
    }

    protected static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // already closed or fatal
        }
    }

    protected static String qualified(String table) {
        if (!KNOWN_TABLES.contains(table)) {
            throw new ValidationException("INVALID_TABLE", "Unknown table");
        }
        return "`" + DATABASE_CATALOG + "`.`" + table + "`";
    }

    protected ColumnProfile columnProfile(String table, String column) {
        String key = table + "." + column;
        ColumnProfile cached = columnProfiles.get(key);
        if (cached != null) {
            return cached;
        }
        ColumnProfile profile = probeColumnProfile(table, column);
        columnProfiles.put(key, profile);
        return profile;
    }

    private ColumnProfile probeColumnProfile(String table, String column) {
        if (column == null || !column.matches("[a-zA-Z0-9_]+")) {
            throw new ValidationException("INVALID_COLUMN", "Unknown column");
        }
        String sql = "SELECT `" + column + "` FROM " + qualified(table) + " WHERE 1=0";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            var meta = rs.getMetaData();
            return new ColumnProfile(meta.getColumnType(1), meta.isAutoIncrement(1), meta.getPrecision(1));
        } catch (SQLException ex) {
            return ColumnProfile.unknown();
        }
    }

    protected void bindSyncId(PreparedStatement statement, int index, String table, String column, String syncId)
            throws SQLException {
        ColumnProfile profile = columnProfile(table, column);
        if (profile.numeric) {
            Long parsed = parseNumericId(syncId);
            if (parsed == null) {
                statement.setNull(index, Types.BIGINT);
            } else {
                statement.setLong(index, parsed);
            }
            return;
        }
        statement.setString(index, syncId);
    }

    protected boolean columnExists(String table, String column) {
        if (column == null || !column.matches("[a-zA-Z0-9_]+")) {
            throw new ValidationException("INVALID_COLUMN", "Unknown column");
        }
        String key = table + "." + column;
        Boolean cached = presentColumns.get(key);
        if (cached != null) {
            return cached;
        }
        boolean present = probeColumn(table, column);
        presentColumns.put(key, present);
        return present;
    }

    private boolean probeColumn(String table, String column) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT `" + column + "` FROM " + qualified(table) + " WHERE 1=0")) {
            statement.executeQuery();
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    protected boolean tableExists(String table) {
        if (!KNOWN_TABLES.contains(table)) {
            throw new ValidationException("INVALID_TABLE", "Unknown table");
        }
        if (Boolean.TRUE.equals(presentTables.get(table))) {
            return true;
        }
        boolean present = probeTable(table);
        if (present) {
            presentTables.put(table, Boolean.TRUE);
        }
        return present;
    }

    private boolean probeTable(String table) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM " + qualified(table) + " WHERE 1=0")) {
            statement.executeQuery();
            return true;
        } catch (SQLException ex) {
            LOG.warning("Unable to probe table " + table + ": " + ex.getMessage());
            return false;
        }
    }

    protected long scalarLong(String sql, Object... params) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Query failed: " + ex.getMessage(), ex);
        }
    }

    protected static void bind(PreparedStatement statement, Object... params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    protected static String nullableString(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    protected static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    protected static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    protected static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    protected String activeInPurePredicate(String table, String alias) {
        if (!columnExists(table, "active_in_pure")) {
            return " 1=1";
        }
        return " COALESCE(" + alias + ".active_in_pure, 1) = 1";
    }

    /**
     * One publication year per research output. Year lives on
     * {@code pure_research_output_status}, not on the parent row.
     */
    protected String purePublicationYearDerivedSql() {
        if (!tableExists("pure_research_output_status")) {
            return "SELECT CAST(NULL AS UNSIGNED) AS research_output_id, CAST(NULL AS SIGNED) AS publication_year "
                    + "FROM " + qualified("pure_research_output") + " WHERE 1=0";
        }
        String status = qualified("pure_research_output_status");
        if (columnExists("pure_research_output_status", "current_status")) {
            return """
                    SELECT ranked.research_output_id, ranked.publication_year
                    FROM (
                        SELECT
                            s.research_output_id,
                            s.publication_year,
                            ROW_NUMBER() OVER (
                                PARTITION BY s.research_output_id
                                ORDER BY
                                    CASE WHEN s.current_status = 1 THEN 0 ELSE 1 END,
                                    CASE WHEN s.current_status = 1 THEN s.id ELSE 2147483647 END,
                                    s.publication_year DESC,
                                    s.id
                            ) AS rn
                        FROM %s s
                        WHERE s.publication_year IS NOT NULL
                    ) ranked
                    WHERE ranked.rn = 1
                    """.formatted(status);
        }
        return """
                SELECT s.research_output_id, MAX(s.publication_year) AS publication_year
                FROM %s s
                WHERE s.publication_year IS NOT NULL
                GROUP BY s.research_output_id
                """.formatted(status);
    }

    protected static String inPlaceholders(int size) {
        if (size < 1) {
            throw new ValidationException("INVALID_IN_CLAUSE", "IN clause requires at least one value");
        }
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    protected static String likeContains(String value) {
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    protected static Long parseNumericId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.matches("\\d{1,18}")) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    protected static String orderByNullsLast(String expression, boolean descending) {
        return "(" + expression + " IS NULL), " + expression + (descending ? " DESC" : " ASC");
    }

    protected static String stripKnownPrefix(String raw, String... prefixes) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase();
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix.toLowerCase())) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return trimmed;
    }

    protected static final class ColumnProfile {
        final boolean numeric;
        final boolean autoIncrement;
        final int precision;

        ColumnProfile(int jdbcType, boolean autoIncrement, int precision) {
            this.numeric = jdbcType == Types.INTEGER
                    || jdbcType == Types.BIGINT
                    || jdbcType == Types.SMALLINT
                    || jdbcType == Types.TINYINT;
            this.autoIncrement = autoIncrement;
            this.precision = precision;
        }

        static ColumnProfile unknown() {
            return new ColumnProfile(Types.VARCHAR, false, 36);
        }
    }
}
