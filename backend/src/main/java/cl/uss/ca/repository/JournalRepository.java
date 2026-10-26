package cl.uss.ca.repository;

import cl.uss.ca.api.dto.JournalClassificationItemDto;
import cl.uss.ca.api.dto.JournalDetailDto;
import cl.uss.ca.api.dto.JournalFiltersDto;
import cl.uss.ca.api.dto.JournalIdentifierItemDto;
import cl.uss.ca.api.dto.JournalIssnItemDto;
import cl.uss.ca.api.dto.JournalIssnsDto;
import cl.uss.ca.api.dto.JournalKeywordGroupDto;
import cl.uss.ca.api.dto.JournalStatsDto;
import cl.uss.ca.api.dto.JournalSummaryDto;
import cl.uss.ca.api.dto.JournalTitleItemDto;
import cl.uss.ca.api.dto.JournalTypeOptionDto;
import cl.uss.ca.api.dto.JournalWorkflowOptionDto;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class JournalRepository extends JdbcRepository {

    public record JournalQuery(
            String search,
            String typeUri,
            String workflowStep,
            String issnCount,
            String publications,
            String citescore,
            String quartile,
            Boolean activeInPure,
            String sort,
            String direction,
            int page,
            int size
    ) {
    }

    public long count(JournalQuery query) {
        SqlParts parts = buildWhere(query);
        String sql = "SELECT COUNT(*) FROM " + qualified("pure_journal") + " j" + parts.where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count journals", ex);
        }
    }

    public List<JournalSummaryDto> find(JournalQuery query) {
        SqlParts parts = buildWhere(query);
        String sql = """
                SELECT
                    j.id, j.pure_id, j.uuid,
                    %s AS title,
                    %s AS type,
                    j.type_es, j.type_en,
                    j.workflow_step, j.workflow_description_es, j.workflow_description_en,
                    j.created_by, j.modified_by,
                    %s,
                    %s,
                    %s,
                    %s,
                    %s
                FROM %s j
                %s
                %s
                %s
                %s
                """.formatted(
                displayTitleExpr(),
                displayTypeExpr(),
                issnListSelect(),
                firstScopusSelect(),
                publicationCountSelect(),
                activeInPureSelect("j"),
                journalMetricSelect(),
                qualified("pure_journal"),
                publicationCountJoin(),
                journalMetricJoin(),
                parts.where,
                orderBy(query.sort(), query.direction())
        );
        List<Object> params = new ArrayList<>(parts.params);
        if (query.size() > 0) {
            sql += " LIMIT ? OFFSET ?";
            params.add(query.size());
            params.add((long) query.page() * query.size());
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                List<JournalSummaryDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapSummary(connection, rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list journals", ex);
        }
    }

    public JournalDetailDto findById(long id) {
        String sql = """
                SELECT j.*, %s AS title, %s AS type, %s, %s
                FROM %s j
                WHERE j.id = ?
                """.formatted(
                displayTitleExpr(),
                displayTypeExpr(),
                publicationCountCorrelatedSelect(),
                activeInPureSelect("j"),
                qualified("pure_journal")
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("JOURNAL_NOT_FOUND", "Journal was not found");
                }
                JournalDetailDto dto = new JournalDetailDto();
                copySummary(mapSummary(connection, rs), dto);
                dto.publisherUuid = nullableString(rs, "publisher_uuid");
                dto.publisherSystemName = nullableString(rs, "publisher_system_name");
                dto.createdBy = nullableString(rs, "created_by");
                dto.createdDatePure = timestamp(rs, "created_date_pure");
                dto.modifiedBy = nullableString(rs, "modified_by");
                dto.modifiedDatePure = timestamp(rs, "modified_date_pure");
                dto.version = nullableString(rs, "pure_version");
                dto.systemName = nullableString(rs, "system_name");
                dto.titles = loadTitles(connection, id);
                dto.identifiers = loadIdentifiers(connection, id);
                dto.keywordGroups = loadKeywordGroups(connection, id);
                return dto;
            }
        } catch (NotFoundException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load journal", ex);
        }
    }

    public JournalIssnsDto issns(long journalId) {
        JournalRef journal = requireJournal(journalId);
        JournalIssnsDto dto = new JournalIssnsDto();
        dto.journalId = journal.id;
        dto.journalPureId = journal.pureId;
        dto.journalUuid = journal.uuid;
        dto.items = loadIssns(journalId);
        dto.count = dto.items.size();
        return dto;
    }

    public JournalStatsDto stats() {
        return stats(new JournalQuery(null, null, null, null, null, null, null, true, "title", "ASC", 0, 0));
    }

    public JournalStatsDto stats(JournalQuery query) {
        JournalQuery safe = query == null
                ? new JournalQuery(null, null, null, null, null, null, null, true, "title", "ASC", 0, 0)
                : query;
        SqlParts parts = buildWhere(safe);
        JournalStatsDto dto = new JournalStatsDto();
        String issnCountExpr = tableExists("pure_journal_issn")
                ? "(SELECT COUNT(*) FROM " + qualified("pure_journal_issn") + " i WHERE i.journal_id = j.id)"
                : "NULL";
        String scopusExistsExpr = tableExists("pure_journal_identifier")
                ? """
                        EXISTS (
                            SELECT 1 FROM %s i
                            WHERE i.journal_id = j.id
                              AND (
                                  LOWER(COALESCE(i.type_en, '')) LIKE '%%scopus%%'
                                  OR LOWER(COALESCE(i.id_source, '')) LIKE '%%scopus%%'
                              )
                        )
                        """.formatted(qualified("pure_journal_identifier"))
                : "0";
        String withoutPublicationsExpr = canCountPublications()
                ? "CASE WHEN NOT EXISTS (" + associatedPublicationExistsSql() + ") THEN 1 ELSE 0 END"
                : "0";
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(CASE WHEN " + issnCountExpr + " = 0 THEN 1 ELSE 0 END), 0) AS without_issn, "
                + "COALESCE(SUM(CASE WHEN " + issnCountExpr + " = 1 THEN 1 ELSE 0 END), 0) AS one_issn, "
                + "COALESCE(SUM(CASE WHEN " + issnCountExpr + " >= 2 THEN 1 ELSE 0 END), 0) AS two_or_more_issn, "
                + "COALESCE(SUM(CASE WHEN " + scopusExistsExpr + " THEN 1 ELSE 0 END), 0) AS with_scopus, "
                + "COALESCE(SUM(" + withoutPublicationsExpr + "), 0) AS without_publications, "
                + "COALESCE(SUM(" + withCiteScoreExpr() + "), 0) AS with_citescore, "
                + "COALESCE(SUM(" + withQuartileExpr() + "), 0) AS with_quartile "
                + "FROM " + qualified("pure_journal") + " j "
                + parts.where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    dto.total = rs.getLong("total");
                    dto.withoutIssn = rs.getLong("without_issn");
                    dto.oneIssn = rs.getLong("one_issn");
                    dto.twoOrMoreIssn = rs.getLong("two_or_more_issn");
                    dto.withScopusId = rs.getLong("with_scopus");
                    dto.withoutPublications = rs.getLong("without_publications");
                    dto.withCiteScore = hasColumn(rs, "with_citescore") ? rs.getLong("with_citescore") : 0L;
                    dto.withQuartile = hasColumn(rs, "with_quartile") ? rs.getLong("with_quartile") : 0L;
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load journal stats", ex);
        }
        if (tableExists("pure_research_output_journal") && columnExists("pure_research_output_journal", "journal_uuid")) {
            dto.unresolvedResearchOutputRefs = scalar("""
                    SELECT COUNT(DISTINCT rj.journal_uuid)
                    FROM %s rj
                    LEFT JOIN %s j ON j.uuid = rj.journal_uuid
                    WHERE rj.journal_uuid IS NOT NULL AND j.id IS NULL
                    """.formatted(qualified("pure_research_output_journal"), qualified("pure_journal")));
        }
        return dto;
    }

    public JournalFiltersDto filters() {
        JournalFiltersDto dto = new JournalFiltersDto();
        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT DISTINCT type_uri, type_en, type_es
                    FROM """ + " " + qualified("pure_journal") + """
                    
                    WHERE type_uri IS NOT NULL
                    ORDER BY type_en, type_es
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JournalTypeOptionDto option = new JournalTypeOptionDto();
                    option.uri = rs.getString("type_uri");
                    option.typeEn = rs.getString("type_en");
                    option.typeEs = rs.getString("type_es");
                    dto.types.add(option);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT DISTINCT workflow_step, workflow_description_en, workflow_description_es
                    FROM """ + " " + qualified("pure_journal") + """
                    
                    WHERE workflow_step IS NOT NULL
                    ORDER BY workflow_step
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JournalWorkflowOptionDto option = new JournalWorkflowOptionDto();
                    option.step = rs.getString("workflow_step");
                    option.labelEn = rs.getString("workflow_description_en");
                    option.labelEs = rs.getString("workflow_description_es");
                    dto.workflows.add(option);
                }
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load journal filters", ex);
        }
        return dto;
    }

    private JournalSummaryDto mapSummary(Connection connection, ResultSet rs) throws SQLException {
        JournalSummaryDto dto = new JournalSummaryDto();
        dto.id = rs.getLong("id");
        dto.pureId = nullableLong(rs, "pure_id");
        dto.uuid = nullableString(rs, "uuid");
        dto.title = nullableString(rs, "title");
        dto.type = nullableString(rs, "type");
        dto.typeEs = nullableString(rs, "type_es");
        dto.typeEn = nullableString(rs, "type_en");
        dto.workflowStep = nullableString(rs, "workflow_step");
        dto.workflowEs = nullableString(rs, "workflow_description_es");
        dto.workflowEn = nullableString(rs, "workflow_description_en");
        dto.createdBy = hasColumn(rs, "created_by") ? nullableString(rs, "created_by") : null;
        dto.modifiedBy = hasColumn(rs, "modified_by") ? nullableString(rs, "modified_by") : null;
        dto.activeInPure = columnExists("pure_journal", "active_in_pure")
                ? nullableBoolean(rs, "active_in_pure")
                : Boolean.TRUE;
        dto.publicationCount = hasColumn(rs, "publication_count") ? rs.getInt("publication_count") : 0;
        if (hasColumn(rs, "issn_count")) {
            dto.issnCount = rs.getInt("issn_count");
            String firstIssn = nullableString(rs, "first_issn");
            if (firstIssn != null && dto.issnCount != null && dto.issnCount == 1) {
                dto.issns.add(firstIssn);
            }
        } else {
            List<JournalIssnItemDto> issns = loadIssns(connection, dto.id);
            dto.issnCount = issns.size();
            for (JournalIssnItemDto issn : issns) {
                if (issn.issn != null) {
                    dto.issns.add(issn.issn);
                }
            }
        }
        dto.scopusId = hasColumn(rs, "first_scopus")
                ? nullableString(rs, "first_scopus")
                : firstScopus(connection, dto.id);
        if (hasColumn(rs, "citescore")) {
            dto.citeScore = rs.getBigDecimal("citescore");
            if (rs.wasNull()) {
                dto.citeScore = null;
            }
            dto.citeScoreYear = nullableInt(rs, "citescore_year");
        }
        if (hasColumn(rs, "quartiles_compact")) {
            dto.quartilesCompact = nullableString(rs, "quartiles_compact");
            dto.quartileYear = nullableInt(rs, "quartile_year");
            dto.quartileCount = nullableInt(rs, "quartile_count");
        }
        return dto;
    }

    private JournalRef requireJournal(long journalId) {
        String sql = "SELECT id, pure_id, uuid FROM " + qualified("pure_journal") + " WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, journalId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("JOURNAL_NOT_FOUND", "Journal was not found");
                }
                return new JournalRef(rs.getLong("id"), nullableLong(rs, "pure_id"), nullableString(rs, "uuid"));
            }
        } catch (NotFoundException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load journal", ex);
        }
    }

    private List<JournalIssnItemDto> loadIssns(long journalId) {
        try (Connection connection = connection()) {
            return loadIssns(connection, journalId);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load ISSNs", ex);
        }
    }

    private List<JournalIssnItemDto> loadIssns(Connection connection, long journalId) throws SQLException {
        if (!tableExists("pure_journal_issn")) {
            return List.of();
        }
        String sql = "SELECT id, pure_id, issn FROM " + qualified("pure_journal_issn")
                + " WHERE journal_id = ? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, journalId);
            try (ResultSet rs = statement.executeQuery()) {
                List<JournalIssnItemDto> items = new ArrayList<>();
                while (rs.next()) {
                    JournalIssnItemDto item = new JournalIssnItemDto();
                    item.id = rs.getLong("id");
                    item.pureId = nullableLong(rs, "pure_id");
                    item.issn = rs.getString("issn");
                    items.add(item);
                }
                return items;
            }
        }
    }

    private List<JournalTitleItemDto> loadTitles(Connection connection, long journalId) throws SQLException {
        if (!tableExists("pure_journal_title")) {
            return List.of();
        }
        String sql = "SELECT pure_id, title FROM " + qualified("pure_journal_title") + " WHERE journal_id = ? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, journalId);
            try (ResultSet rs = statement.executeQuery()) {
                List<JournalTitleItemDto> items = new ArrayList<>();
                while (rs.next()) {
                    JournalTitleItemDto item = new JournalTitleItemDto();
                    item.pureId = nullableLong(rs, "pure_id");
                    item.title = rs.getString("title");
                    items.add(item);
                }
                return items;
            }
        }
    }

    private List<JournalIdentifierItemDto> loadIdentifiers(Connection connection, long journalId) throws SQLException {
        if (!tableExists("pure_journal_identifier")) {
            return List.of();
        }
        String sql = """
                SELECT pure_id, type_discriminator, id_source, identifier_value, type_uri, type_en, type_es
                FROM """ + " " + qualified("pure_journal_identifier") + " WHERE journal_id = ? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, journalId);
            try (ResultSet rs = statement.executeQuery()) {
                List<JournalIdentifierItemDto> items = new ArrayList<>();
                while (rs.next()) {
                    JournalIdentifierItemDto item = new JournalIdentifierItemDto();
                    item.pureId = nullableLong(rs, "pure_id");
                    item.typeDiscriminator = rs.getString("type_discriminator");
                    item.idSource = rs.getString("id_source");
                    item.value = rs.getString("identifier_value");
                    item.typeUri = rs.getString("type_uri");
                    item.typeEn = rs.getString("type_en");
                    item.typeEs = rs.getString("type_es");
                    items.add(item);
                }
                return items;
            }
        }
    }

    private List<JournalKeywordGroupDto> loadKeywordGroups(Connection connection, long journalId) throws SQLException {
        if (!tableExists("pure_journal_keyword_group")) {
            return List.of();
        }
        String sql = """
                SELECT id, pure_id, type_discriminator, logical_name, name_en, name_es
                FROM """ + " " + qualified("pure_journal_keyword_group") + " WHERE journal_id = ? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, journalId);
            try (ResultSet rs = statement.executeQuery()) {
                List<JournalKeywordGroupDto> groups = new ArrayList<>();
                while (rs.next()) {
                    JournalKeywordGroupDto group = new JournalKeywordGroupDto();
                    long groupId = rs.getLong("id");
                    group.pureId = nullableLong(rs, "pure_id");
                    group.typeDiscriminator = rs.getString("type_discriminator");
                    group.logicalName = rs.getString("logical_name");
                    group.nameEn = rs.getString("name_en");
                    group.nameEs = rs.getString("name_es");
                    group.classifications = loadClassifications(connection, groupId);
                    groups.add(group);
                }
                return groups;
            }
        }
    }

    private List<JournalClassificationItemDto> loadClassifications(Connection connection, long groupId)
            throws SQLException {
        if (!tableExists("pure_journal_classification")) {
            return List.of();
        }
        String sql = "SELECT uri, term_en, term_es FROM " + qualified("pure_journal_classification")
                + " WHERE keyword_group_id = ? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, groupId);
            try (ResultSet rs = statement.executeQuery()) {
                List<JournalClassificationItemDto> items = new ArrayList<>();
                while (rs.next()) {
                    JournalClassificationItemDto item = new JournalClassificationItemDto();
                    item.uri = rs.getString("uri");
                    item.termEn = rs.getString("term_en");
                    item.termEs = rs.getString("term_es");
                    items.add(item);
                }
                return items;
            }
        }
    }

    private String firstScopus(Connection connection, long journalId) throws SQLException {
        if (!tableExists("pure_journal_identifier")) {
            return null;
        }
        String sql = """
                SELECT identifier_value
                FROM """ + " " + qualified("pure_journal_identifier") + """
                
                WHERE journal_id = ?
                  AND (
                    LOWER(COALESCE(type_en, '')) LIKE '%scopus%'
                    OR LOWER(COALESCE(id_source, '')) LIKE '%scopus%'
                  )
                ORDER BY id
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, journalId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? nullableString(rs, "identifier_value") : null;
            }
        }
    }

    private SqlParts buildWhere(JournalQuery query) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE ");
        where.append(activeInPurePredicate("pure_journal", "j"));
        if (query.search() != null && !query.search().isBlank()) {
            String raw = query.search().trim();
            String like = likeContains(raw);
            String digits = raw.replace("-", "").replace(" ", "");
            Long pureId = parseNumericId(raw);
            where.append(" AND (");
            where.append(" j.uuid LIKE ? ESCAPE '!'");
            params.add(like);
            if (pureId != null) {
                where.append(" OR j.pure_id = ?");
                params.add(pureId);
            }
            where.append(" OR ").append(displayTitleExpr()).append(" LIKE ? ESCAPE '!'");
            params.add(like);
            if (tableExists("pure_journal_issn")) {
                where.append("""
                         OR EXISTS (
                            SELECT 1 FROM """ + " " + qualified("pure_journal_issn") + """
                             i WHERE i.journal_id = j.id
                              AND (i.issn LIKE ? ESCAPE '!'
                                OR REPLACE(REPLACE(i.issn, '-', ''), ' ', '') LIKE ? ESCAPE '!')
                         )
                        """);
                params.add(like);
                params.add(likeContains(digits));
            }
            if (tableExists("pure_journal_identifier")) {
                where.append("""
                         OR EXISTS (
                            SELECT 1 FROM """ + " " + qualified("pure_journal_identifier") + """
                             d WHERE d.journal_id = j.id AND d.identifier_value LIKE ? ESCAPE '!'
                         )
                        """);
                params.add(like);
            }
            where.append(")");
        }
        if (query.typeUri() != null && !query.typeUri().isBlank()) {
            where.append(" AND j.type_uri = ?");
            params.add(query.typeUri().trim());
        }
        if (query.workflowStep() != null && !query.workflowStep().isBlank()) {
            where.append(" AND j.workflow_step = ?");
            params.add(query.workflowStep().trim());
        }
        if (query.issnCount() != null && tableExists("pure_journal_issn")) {
            String countSql = "(SELECT COUNT(*) FROM " + qualified("pure_journal_issn") + " i WHERE i.journal_id = j.id)";
            switch (query.issnCount()) {
                case "0" -> where.append(" AND ").append(countSql).append(" = 0");
                case "1" -> where.append(" AND ").append(countSql).append(" = 1");
                case "2+" -> where.append(" AND ").append(countSql).append(" >= 2");
                default -> {
                }
            }
        }
        if (query.publications() != null && canCountPublications()) {
            String exists = associatedPublicationExistsSql();
            if ("WITH_PUBLICATIONS".equals(query.publications())) {
                where.append(" AND EXISTS (").append(exists).append(")");
            } else if ("WITHOUT_PUBLICATIONS".equals(query.publications())) {
                where.append(" AND NOT EXISTS (").append(exists).append(")");
            }
        }
        if (query.citescore() != null && tableExists("pure_journal_metric")) {
            String exists = "EXISTS (SELECT 1 FROM " + qualified("pure_journal_metric")
                    + " m WHERE m.journal_id = j.id AND m.collection_id = 'scopusJournal'"
                    + " AND m.metric_id = 'rating_citescore')";
            if ("WITH".equalsIgnoreCase(query.citescore()) || "WITH_CITESCORE".equalsIgnoreCase(query.citescore())) {
                where.append(" AND ").append(exists);
            } else if ("WITHOUT".equalsIgnoreCase(query.citescore()) || "WITHOUT_CITESCORE".equalsIgnoreCase(query.citescore())) {
                where.append(" AND NOT ").append(exists);
            }
        }
        if (query.quartile() != null && tableExists("pure_journal_metric")) {
            Integer q = parseQuartile(query.quartile());
            if (q != null) {
                where.append(" AND ").append(PureMetricSql.latestQuartileExists(qualified("pure_journal_metric"), "j", q));
            }
        }
        return new SqlParts(where.toString(), params);
    }

    private String orderBy(String sort, String direction) {
        boolean desc = "DESC".equalsIgnoreCase(direction);
        String order = desc ? " DESC" : " ASC";
        String field = sort == null ? "title" : sort.toLowerCase(Locale.ROOT);
        return switch (field) {
            case "pureid" -> " ORDER BY j.pure_id" + order + ", j.id ASC";
            case "workflow" -> " ORDER BY j.workflow_step" + order + ", j.id ASC";
            case "type" -> " ORDER BY type" + order + ", j.id ASC";
            case "publicationcount" -> " ORDER BY publication_count" + order + ", title ASC, j.id ASC";
            case "issn" -> " ORDER BY " + orderByNullsLast("first_issn", desc) + ", title ASC, j.id ASC";
            case "scopusid" -> " ORDER BY " + orderByNullsLast("first_scopus", desc) + ", title ASC, j.id ASC";
            case "citescore" -> " ORDER BY " + orderByNullsLast("citescore", desc) + ", title ASC, j.id ASC";
            case "quartile" -> " ORDER BY " + orderByNullsLast("quartile_year", desc) + ", title ASC, j.id ASC";
            case "createdby" -> " ORDER BY " + orderByNullsLast("j.created_by", desc) + ", title ASC, j.id ASC";
            case "modifiedby" -> " ORDER BY " + orderByNullsLast("j.modified_by", desc) + ", title ASC, j.id ASC";
            default -> " ORDER BY title" + order + ", j.id ASC";
        };
    }

    private String activeInPureSelect(String alias) {
        if (!columnExists("pure_journal", "active_in_pure")) {
            return "CAST(1 AS UNSIGNED) AS active_in_pure";
        }
        return alias + ".active_in_pure";
    }

    private String displayTitleExpr() {
        if (tableExists("pure_journal_title")) {
            return """
                    COALESCE((
                        SELECT t.title FROM %s t
                        WHERE t.journal_id = j.id AND t.title IS NOT NULL AND TRIM(t.title) <> ''
                        ORDER BY t.id LIMIT 1
                    ), j.title)
                    """.formatted(qualified("pure_journal_title"));
        }
        return "j.title";
    }

    private static String displayTypeExpr() {
        return "COALESCE(NULLIF(TRIM(j.type_en), ''), NULLIF(TRIM(j.type_es), ''))";
    }

    private String issnListSelect() {
        if (!tableExists("pure_journal_issn")) {
            return "0 AS issn_count, CAST(NULL AS CHAR) AS first_issn";
        }
        return "(SELECT COUNT(*) FROM " + qualified("pure_journal_issn") + " i WHERE i.journal_id = j.id) AS issn_count, "
                + "(SELECT i.issn FROM " + qualified("pure_journal_issn")
                + " i WHERE i.journal_id = j.id ORDER BY i.id LIMIT 1) AS first_issn";
    }

    private String firstScopusSelect() {
        if (!tableExists("pure_journal_identifier")) {
            return "CAST(NULL AS CHAR) AS first_scopus";
        }
        return "(SELECT i.identifier_value FROM " + qualified("pure_journal_identifier")
                + " i WHERE i.journal_id = j.id"
                + " AND (LOWER(COALESCE(i.type_en, '')) LIKE '%scopus%'"
                + " OR LOWER(COALESCE(i.id_source, '')) LIKE '%scopus%')"
                + " ORDER BY i.id LIMIT 1) AS first_scopus";
    }

    private boolean canCountPublications() {
        return tableExists("pure_research_output_journal") && tableExists("pure_research_output");
    }

    private String publicationCountSelect() {
        return canCountPublications()
                ? "COALESCE(pc.publication_count, 0) AS publication_count"
                : "0 AS publication_count";
    }

    private String publicationCountJoin() {
        return canCountPublications()
                ? " LEFT JOIN (" + publicationCountAggregateSql() + ") pc ON pc.journal_id = j.id"
                : "";
    }

    private String journalMetricSelect() {
        if (!tableExists("pure_journal_metric")) {
            return "CAST(NULL AS DECIMAL(18,6)) AS citescore, CAST(NULL AS SIGNED) AS citescore_year, "
                    + "CAST(NULL AS CHAR) AS quartiles_compact, CAST(NULL AS SIGNED) AS quartile_year, "
                    + "CAST(NULL AS SIGNED) AS quartile_count";
        }
        return "cs.citescore, cs.citescore_year, qs.quartiles_compact, qs.quartile_year, qs.quartile_count";
    }

    private String journalMetricJoin() {
        if (!tableExists("pure_journal_metric")) {
            return "";
        }
        return PureMetricSql.journalCiteScoreJoin(qualified("pure_journal_metric"))
                + " "
                + PureMetricSql.journalQuartileJoin(qualified("pure_journal_metric"));
    }

    private String withCiteScoreExpr() {
        if (!tableExists("pure_journal_metric")) {
            return "0";
        }
        return "CASE WHEN EXISTS (SELECT 1 FROM " + qualified("pure_journal_metric")
                + " m WHERE m.journal_id = j.id AND m.collection_id = 'scopusJournal'"
                + " AND m.metric_id = 'rating_citescore') THEN 1 ELSE 0 END";
    }

    private String withQuartileExpr() {
        if (!tableExists("pure_journal_metric")) {
            return "0";
        }
        return "CASE WHEN EXISTS (SELECT 1 FROM " + qualified("pure_journal_metric")
                + " m WHERE m.journal_id = j.id AND m.collection_id = 'scopusCitescore'"
                + " AND m.metric_id = 'quartile') THEN 1 ELSE 0 END";
    }

    private static Integer parseQuartile(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT).replace("Q", "");
        return switch (value) {
            case "1" -> 1;
            case "2" -> 2;
            case "3" -> 3;
            case "4" -> 4;
            default -> null;
        };
    }

    private String publicationCountCorrelatedSelect() {
        if (!canCountPublications()) {
            return "0 AS publication_count";
        }
        return "(SELECT COUNT(DISTINCT ro.id) FROM " + qualified("pure_research_output_journal") + " rj"
                + " JOIN " + qualified("pure_research_output") + " ro ON ro.id = rj.research_output_id"
                + activeResearchOutputPredicate("ro")
                + " WHERE " + journalRelationPredicate("j", "rj") + ") AS publication_count";
    }

    private String publicationCountAggregateSql() {
        String relation = qualified("pure_research_output_journal");
        String output = qualified("pure_research_output");
        String active = activeResearchOutputPredicate("ro");
        String byId = "SELECT rj.journal_id AS journal_id, rj.research_output_id"
                + " FROM " + relation + " rj"
                + " JOIN " + output + " ro ON ro.id = rj.research_output_id" + active
                + " WHERE rj.journal_id IS NOT NULL";
        if (!columnExists("pure_research_output_journal", "journal_uuid")) {
            return "SELECT matched.journal_id, COUNT(DISTINCT matched.research_output_id) AS publication_count"
                    + " FROM (" + byId + ") matched GROUP BY matched.journal_id";
        }
        String byUuid = "SELECT j2.id AS journal_id, rj.research_output_id"
                + " FROM " + relation + " rj"
                + " JOIN " + output + " ro ON ro.id = rj.research_output_id" + active
                + " JOIN " + qualified("pure_journal") + " j2 ON j2.uuid = rj.journal_uuid"
                + " WHERE rj.journal_id IS NULL AND rj.journal_uuid IS NOT NULL";
        return "SELECT matched.journal_id, COUNT(DISTINCT matched.research_output_id) AS publication_count"
                + " FROM (" + byId + " UNION ALL " + byUuid + ") matched GROUP BY matched.journal_id";
    }

    private String associatedPublicationExistsSql() {
        return "SELECT 1 FROM " + qualified("pure_research_output_journal") + " rj"
                + " JOIN " + qualified("pure_research_output") + " ro ON ro.id = rj.research_output_id"
                + activeResearchOutputPredicate("ro")
                + " WHERE " + journalRelationPredicate("j", "rj");
    }

    private String journalRelationPredicate(String journalAlias, String relationAlias) {
        if (columnExists("pure_research_output_journal", "journal_uuid")) {
            return "(" + relationAlias + ".journal_id = " + journalAlias + ".id"
                    + " OR (" + relationAlias + ".journal_id IS NULL AND "
                    + relationAlias + ".journal_uuid IS NOT NULL AND "
                    + relationAlias + ".journal_uuid = " + journalAlias + ".uuid))";
        }
        return relationAlias + ".journal_id = " + journalAlias + ".id";
    }

    private String activeResearchOutputPredicate(String alias) {
        if (!columnExists("pure_research_output", "active_in_pure")) {
            return "";
        }
        return " AND COALESCE(" + alias + ".active_in_pure, 1) = 1";
    }

    private long scalar(String sql) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed journal stats query", ex);
        }
    }

    private static void copySummary(JournalSummaryDto source, JournalDetailDto target) {
        target.id = source.id;
        target.pureId = source.pureId;
        target.uuid = source.uuid;
        target.title = source.title;
        target.issns = source.issns;
        target.issnCount = source.issnCount;
        target.publicationCount = source.publicationCount;
        target.scopusId = source.scopusId;
        target.type = source.type;
        target.typeEs = source.typeEs;
        target.typeEn = source.typeEn;
        target.workflowStep = source.workflowStep;
        target.workflowEs = source.workflowEs;
        target.workflowEn = source.workflowEn;
        target.createdBy = source.createdBy;
        target.modifiedBy = source.modifiedBy;
        target.activeInPure = source.activeInPure;
        target.citeScore = source.citeScore;
        target.citeScoreYear = source.citeScoreYear;
        target.quartilesCompact = source.quartilesCompact;
        target.quartileYear = source.quartileYear;
        target.quartileCount = source.quartileCount;
    }

    private static String timestamp(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if (column.equalsIgnoreCase(meta.getColumnLabel(i))) {
                    return true;
                }
            }
            return false;
        } catch (SQLException ex) {
            return false;
        }
    }

    private record JournalRef(long id, Long pureId, String uuid) {
    }

    private record SqlParts(String where, List<Object> params) {
    }
}
