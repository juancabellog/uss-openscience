package cl.uss.ca.repository;

import cl.uss.ca.api.dto.ResearchOutputContributorDto;
import cl.uss.ca.api.dto.ResearchOutputDetailDto;
import cl.uss.ca.api.dto.ResearchOutputDoiVersionsDto;
import cl.uss.ca.api.dto.ResearchOutputElectronicVersionDto;
import cl.uss.ca.api.dto.ResearchOutputFiltersDto;
import cl.uss.ca.api.dto.ResearchOutputIdentifierDto;
import cl.uss.ca.api.dto.ResearchOutputJournalDto;
import cl.uss.ca.api.dto.ResearchOutputKeywordDto;
import cl.uss.ca.api.dto.ResearchOutputKeywordGroupDto;
import cl.uss.ca.api.dto.ResearchOutputLinkDto;
import cl.uss.ca.api.dto.ResearchOutputStatusDto;
import cl.uss.ca.api.dto.ResearchOutputStatsDto;
import cl.uss.ca.api.dto.ResearchOutputSummaryDto;
import cl.uss.ca.api.dto.ResearchOutputTypeDto;
import cl.uss.ca.api.dto.ResearchOutputWorkflowOptionDto;
import cl.uss.ca.exception.DatabaseException;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ResearchOutputRepository extends JdbcRepository {
    private static final Logger LOG = Logger.getLogger(ResearchOutputRepository.class.getName());
    private static final String DOI_ELECTRONIC_VERSION = "DoiElectronicVersion";

    public long count(ResearchOutputQuery query) {
        long started = System.nanoTime();
        SqlParts parts = buildWhere(query);
        String sql = "SELECT COUNT(*) FROM " + qualified("pure_research_output") + " o" + parts.where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count research outputs", ex);
        } finally {
            logTiming("count", started);
        }
    }

    public List<ResearchOutputSummaryDto> find(ResearchOutputQuery query) {
        long started = System.nanoTime();
        SqlParts parts = buildWhere(query);
        String sortKey = sortKey(query);
        boolean paginate = query.size() > 0;
        boolean needsDoiCount = "doiVersionCount".equals(sortKey)
                || "MULTIPLE_DOI_VERSIONS".equalsIgnoreCase(query.doiStatus());
        boolean needsContributors = "contributorsCount".equals(sortKey);
        boolean needsDoi = "doi".equals(sortKey);
        boolean needsJournal = "journal".equals(sortKey);
        String extraSelect = "";
        String extraJoins = "";
        if (needsDoiCount && tableExists("pure_research_output_electronic_version")) {
            extraSelect += "COALESCE(dc.doi_version_count, 0) AS doi_version_count, ";
            extraJoins += " LEFT JOIN (" + doiVersionAggSql() + ") dc ON dc.research_output_id = o.id";
        }
        if (needsContributors && tableExists("pure_research_output_contributor")) {
            extraSelect += "COALESCE(cc.contributor_count, 0) AS contributor_count, ";
            extraJoins += " LEFT JOIN (" + contributorAggSql() + ") cc ON cc.research_output_id = o.id";
        }
        if (needsDoi) {
            extraSelect += doiExpr("o") + " AS doi, ";
        }
        if (needsJournal) {
            extraSelect += journalTitleExpr("o") + " AS journal, ";
        }
        if (tableExists("pure_research_output_metric")) {
            extraSelect += "fw.fwci AS fwci, fw.fwci_year AS fwci_year, ";
            extraJoins += " " + PureMetricSql.fwciJoin(qualified("pure_research_output_metric"));
        }
        String inner = """
                SELECT
                    o.id,
                    o.pure_id,
                    o.uuid,
                    o.title,
                    y.publication_year AS year,
                    COALESCE(NULLIF(TRIM(o.type_en), ''), NULLIF(TRIM(o.type_es), '')) AS type,
                    o.workflow_step,
                    o.workflow_es,
                    o.workflow_en,
                    %s
                    %s
                    %s
                FROM %s o
                LEFT JOIN (%s) y ON y.research_output_id = o.id
                %s
                %s
                %s
                """.formatted(
                extraSelect,
                auditSelect("o"),
                activeInPureSelect("o"),
                qualified("pure_research_output"),
                yearDerivedSql(),
                extraJoins,
                parts.where,
                orderBy(query)
        );
        if (paginate) {
            inner += " LIMIT ? OFFSET ?";
        }
        String sql = """
                SELECT
                    page.id,
                    page.pure_id,
                    page.uuid,
                    page.title,
                    page.year,
                    page.type,
                    page.workflow_step,
                    page.workflow_es,
                    page.workflow_en,
                    page.created_by,
                    page.modified_by,
                    %s AS doi,
                    %s AS journal,
                    %s AS journal_uuid,
                    %s AS contributor_count,
                    %s AS doi_version_count,
                    page.active_in_pure,
                    %s AS fwci,
                    %s AS fwci_year
                FROM (%s) page
                """.formatted(
                needsDoi ? "page.doi" : doiExpr("page"),
                needsJournal ? "page.journal" : journalTitleExpr("page"),
                journalUuidExpr("page"),
                needsContributors ? "page.contributor_count" : contributorCountExpr("page"),
                needsDoiCount ? "page.doi_version_count" : doiVersionCountExpr("page"),
                tableExists("pure_research_output_metric") ? "page.fwci" : "CAST(NULL AS DECIMAL(18,6))",
                tableExists("pure_research_output_metric") ? "page.fwci_year" : "CAST(NULL AS SIGNED)",
                inner
        );
        List<Object> params = new ArrayList<>(parts.params);
        if (paginate) {
            params.add(query.size());
            params.add((long) query.page() * query.size());
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                List<ResearchOutputSummaryDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapSummary(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list research outputs", ex);
        } finally {
            logTiming("find", started);
        }
    }

    public ResearchOutputDetailDto findById(long id) {
        String sql = """
                SELECT
                    o.id,
                    o.pure_id,
                    o.uuid,
                    o.title,
                    %s AS year,
                    COALESCE(NULLIF(TRIM(o.type_en), ''), NULLIF(TRIM(o.type_es), '')) AS type,
                    o.type_es,
                    o.type_en,
                    o.submission_year,
                    o.volume,
                    o.journal_number,
                    o.pages,
                    o.number_of_pages,
                    o.workflow_step,
                    o.workflow_es,
                    o.workflow_en,
                    %s
                    %s
                    %s AS doi,
                    %s AS journal,
                    %s AS contributor_count,
                    %s AS doi_version_count,
                    %s
                FROM %s o
                WHERE o.id = ?
                """.formatted(
                publicationYearExpr("o"),
                visibilitySelect("o"),
                auditSelect("o"),
                doiExpr("o"),
                journalTitleExpr("o"),
                contributorCountExpr("o"),
                doiVersionCountExpr("o"),
                activeInPureSelect("o"),
                qualified("pure_research_output")
        );
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ResearchOutputDetailDto detail = new ResearchOutputDetailDto();
                copySummary(mapSummary(rs), detail);
                detail.typeEs = nullableString(rs, "type_es");
                detail.typeEn = nullableString(rs, "type_en");
                detail.submissionYear = nullableInt(rs, "submission_year");
                detail.volume = nullableString(rs, "volume");
                detail.issue = nullableString(rs, "journal_number");
                detail.pages = nullableString(rs, "pages");
                detail.numberOfPages = nullableInt(rs, "number_of_pages");
                detail.journalDetail = findJournal(connection, id);
                detail.publicationStatuses = findStatuses(connection, id);
                detail.identifiers = findIdentifiers(connection, id);
                detail.electronicVersions = findElectronicVersions(connection, id);
                attachDoiVersionMetrics(detail);
                detail.contributors = findContributors(connection, id);
                detail.keywordGroups = findKeywordGroups(connection, id);
                detail.links = findLinks(connection, id);
                detail.externalOrganizationUuids = findExternalOrganizationUuids(connection, id);
                return detail;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load research output", ex);
        }
    }

    public ResearchOutputDoiVersionsDto findDoiVersions(long researchOutputId) {
        if (!researchOutputExists(researchOutputId)) {
            return null;
        }
        ResearchOutputDoiVersionsDto dto = new ResearchOutputDoiVersionsDto();
        dto.researchOutputId = researchOutputId;
        dto.items = findDoiElectronicVersions(researchOutputId);
        dto.count = dto.items.size();
        dto.distinctCount = countDistinctDois(dto.items);
        return dto;
    }

    public ResearchOutputFiltersDto filters() {
        ResearchOutputFiltersDto filters = new ResearchOutputFiltersDto();
        filters.types = findTypes();
        filters.years = findYears();
        filters.workflows = findWorkflows();
        return filters;
    }

    public List<ResearchOutputTypeDto> findTypes() {
        String sql = """
                SELECT DISTINCT
                    NULLIF(TRIM(type_en), '') AS type_en,
                    NULLIF(TRIM(type_es), '') AS type_es
                FROM """ + " " + qualified("pure_research_output") + """
                
                WHERE (type_en IS NOT NULL AND TRIM(type_en) <> '')
                   OR (type_es IS NOT NULL AND TRIM(type_es) <> '')
                ORDER BY type_en, type_es
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<ResearchOutputTypeDto> items = new ArrayList<>();
            while (rs.next()) {
                ResearchOutputTypeDto dto = new ResearchOutputTypeDto();
                dto.typeEn = nullableString(rs, "type_en");
                dto.typeEs = nullableString(rs, "type_es");
                dto.code = dto.typeEn != null ? dto.typeEn : dto.typeEs;
                if (dto.code != null) {
                    items.add(dto);
                }
            }
            return items;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list research output types", ex);
        }
    }

    public List<Integer> findYears() {
        if (!tableExists("pure_research_output_status")) {
            return List.of();
        }
        String sql = """
                SELECT DISTINCT publication_year
                FROM """ + " " + qualified("pure_research_output_status") + """
                
                WHERE publication_year IS NOT NULL
                ORDER BY publication_year DESC
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<Integer> years = new ArrayList<>();
            while (rs.next()) {
                Integer year = nullableInt(rs, "publication_year");
                if (year != null) {
                    years.add(year);
                }
            }
            return years;
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list research output years", ex);
        }
    }

    private SqlParts buildWhere(ResearchOutputQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (query.search() != null && !query.search().isBlank()) {
            String raw = query.search().trim();
            String like = likeContains(raw);
            where.append(" AND (o.title LIKE ? ESCAPE '!' OR o.uuid = ?");
            params.add(like);
            params.add(raw);
            Long pureId = parseNumericId(raw);
            if (pureId != null) {
                where.append(" OR o.pure_id = ?");
                params.add(pureId);
            }
            appendDoiSearch(where, params, raw, like);
            appendJournalSearch(where, params, like);
            where.append(")");
        }
        if (query.type() != null && !query.type().isBlank()) {
            where.append(" AND (TRIM(o.type_en) = ? OR TRIM(o.type_es) = ?)");
            params.add(query.type().trim());
            params.add(query.type().trim());
        }
        if (query.year() != null) {
            if (tableExists("pure_research_output_status")) {
                where.append(" AND o.id IN (SELECT yf.research_output_id FROM (")
                        .append(yearDerivedSql())
                        .append(") yf WHERE yf.publication_year = ?)");
            } else {
                where.append(" AND ").append(publicationYearExpr("o")).append(" = ?");
            }
            params.add(query.year());
        }
        appendDoiStatus(where, query);
        if (query.workflowStep() != null && !query.workflowStep().isBlank()) {
            where.append(" AND o.workflow_step = ?");
            params.add(query.workflowStep().trim());
        }
        if (query.activeInPure() != null && columnExists("pure_research_output", "active_in_pure")) {
            where.append(" AND COALESCE(o.active_in_pure, 1) = ?");
            params.add(Boolean.TRUE.equals(query.activeInPure()) ? 1 : 0);
        }
        if ((query.fwciFrom() != null || query.fwciTo() != null) && tableExists("pure_research_output_metric")) {
            where.append("""
                     AND EXISTS (
                        SELECT 1 FROM %s m
                        WHERE m.research_output_id = o.id
                          AND m.collection_id = 'sciVal'
                          AND m.metric_id = 'fwci'
                    """.formatted(qualified("pure_research_output_metric")));
            if (query.fwciFrom() != null) {
                where.append(" AND COALESCE(m.decimal_value, m.integer_value) >= ?");
                params.add(query.fwciFrom());
            }
            if (query.fwciTo() != null) {
                where.append(" AND COALESCE(m.decimal_value, m.integer_value) <= ?");
                params.add(query.fwciTo());
            }
            where.append(")");
        }
        return new SqlParts(where.toString(), params);
    }

    private void appendDoiStatus(StringBuilder where, ResearchOutputQuery query) {
        String status = query.doiStatus() == null ? "" : query.doiStatus().trim().toUpperCase(Locale.ROOT);
        if ("WITH_DOI".equals(status) || "WITH".equals(status)) {
            where.append(" AND (").append(validDoiExistsPredicate("o")).append(")");
            return;
        }
        if ("WITHOUT_DOI".equals(status) || "WITHOUT".equals(status)) {
            where.append(" AND NOT (").append(validDoiExistsPredicate("o")).append(")");
            return;
        }
        if ("MULTIPLE_DOI_VERSIONS".equals(status)) {
            where.append(" AND (").append(multipleDoiVersionsPredicate("o")).append(")");
            return;
        }
        if ("SHARED_DOI".equals(status)) {
            where.append(" AND (").append(sharedDoiPredicate("o", query.activeInPure())).append(")");
            return;
        }
        if (Boolean.TRUE.equals(query.hasDoi())) {
            where.append(" AND (").append(validDoiExistsPredicate("o")).append(")");
        } else if (Boolean.FALSE.equals(query.hasDoi())) {
            where.append(" AND NOT (").append(validDoiExistsPredicate("o")).append(")");
        }
    }

    private String validDoiExistsPredicate(String alias) {
        if (!tableExists("pure_research_output_electronic_version")) {
            return "1=0";
        }
        return """
                EXISTS (
                    SELECT 1
                    FROM %s ev
                    WHERE ev.research_output_id = %s.id
                      AND ev.type_discriminator = '%s'
                      AND ev.doi IS NOT NULL
                      AND TRIM(ev.doi) <> ''
                )
                """.formatted(qualified("pure_research_output_electronic_version"), alias, DOI_ELECTRONIC_VERSION);
    }

    private String multipleDoiVersionsPredicate(String alias) {
        if (!tableExists("pure_research_output_electronic_version")) {
            return "1=0";
        }
        return alias + ".id IN (SELECT y.research_output_id FROM "
                + qualified("pure_research_output_electronic_version")
                + " y WHERE y.type_discriminator = '" + DOI_ELECTRONIC_VERSION + "'"
                + " GROUP BY y.research_output_id HAVING COUNT(*) > 1)";
    }

    private String sharedDoiPredicate(String alias, Boolean activeInPure) {
        if (!tableExists("pure_research_output_electronic_version")) {
            return "1=0";
        }
        String ev = qualified("pure_research_output_electronic_version");
        String parent = qualified("pure_research_output");
        String groupedNorm = normalizedDoiSql("dup.doi");
        String evNorm = normalizedDoiSql("ev.doi");
        return """
                EXISTS (
                    SELECT 1
                    FROM %s ev
                    JOIN (
                        SELECT %s AS doi_norm
                        FROM %s dup
                        JOIN %s parent ON parent.id = dup.research_output_id
                        WHERE dup.type_discriminator = '%s'
                          AND dup.doi IS NOT NULL
                          AND TRIM(dup.doi) <> ''
                          AND %s
                        GROUP BY %s
                        HAVING COUNT(DISTINCT dup.research_output_id) > 1
                    ) shared ON shared.doi_norm = %s
                    WHERE ev.research_output_id = %s.id
                      AND ev.type_discriminator = '%s'
                      AND ev.doi IS NOT NULL
                      AND TRIM(ev.doi) <> ''
                )
                """.formatted(
                ev,
                groupedNorm,
                ev,
                parent,
                DOI_ELECTRONIC_VERSION,
                activeInPureSql("parent", activeInPure),
                groupedNorm,
                evNorm,
                alias,
                DOI_ELECTRONIC_VERSION
        );
    }

    private String activeInPureSql(String alias, Boolean activeInPure) {
        if (activeInPure == null || !columnExists("pure_research_output", "active_in_pure")) {
            return "1=1";
        }
        return "COALESCE(" + alias + ".active_in_pure, 1) = " + (Boolean.TRUE.equals(activeInPure) ? "1" : "0");
    }

    private static String normalizedDoiSql(String column) {
        return """
                LOWER(TRIM(
                    CASE
                        WHEN LOWER(TRIM(%1$s)) LIKE 'https://dx.doi.org/%%' THEN SUBSTRING(TRIM(%1$s), 20)
                        WHEN LOWER(TRIM(%1$s)) LIKE 'http://dx.doi.org/%%' THEN SUBSTRING(TRIM(%1$s), 19)
                        WHEN LOWER(TRIM(%1$s)) LIKE 'https://doi.org/%%' THEN SUBSTRING(TRIM(%1$s), 17)
                        WHEN LOWER(TRIM(%1$s)) LIKE 'http://doi.org/%%' THEN SUBSTRING(TRIM(%1$s), 16)
                        WHEN LOWER(TRIM(%1$s)) LIKE 'doi:%%' THEN SUBSTRING(TRIM(%1$s), 5)
                        ELSE TRIM(%1$s)
                    END
                ))
                """.formatted(column);
    }

    public ResearchOutputStatsDto stats(Boolean activeInPure) {
        return stats(new ResearchOutputQuery(
                null, null, null, null, null, null, activeInPure, null, null, "year", "desc", 0, -1
        ));
    }

    public ResearchOutputStatsDto stats(ResearchOutputQuery query) {
        long started = System.nanoTime();
        ResearchOutputStatsDto dto = new ResearchOutputStatsDto();
        ResearchOutputQuery safe = query == null
                ? new ResearchOutputQuery(null, null, null, null, null, null, Boolean.TRUE, null, null, "year", "desc", 0, -1)
                : query;
        loadDoiUniverseStats(dto, safe);
        dto.withFwci = countWithFwci(safe);
        logTiming("stats", started);
        return dto;
    }

    private void loadDoiUniverseStats(ResearchOutputStatsDto dto, ResearchOutputQuery query) {
        if (!tableExists("pure_research_output")) {
            return;
        }
        SqlParts parts = buildWhere(query);
        String evJoin = "";
        String evSelect = """
                0 AS with_doi,
                COUNT(*) AS without_doi,
                0 AS multiple_doi,
                0 AS empty_doi_versions,
                0 AS shared_doi
                """;
        if (tableExists("pure_research_output_electronic_version")) {
            evJoin = " LEFT JOIN (" + doiVersionAggSql() + ") ev ON ev.research_output_id = o.id";
            evSelect = """
                    COALESCE(SUM(CASE WHEN ev.valid_doi_count > 0 THEN 1 ELSE 0 END), 0) AS with_doi,
                    COALESCE(SUM(CASE WHEN ev.valid_doi_count IS NULL OR ev.valid_doi_count = 0 THEN 1 ELSE 0 END), 0) AS without_doi,
                    COALESCE(SUM(CASE WHEN ev.doi_version_count > 1 THEN 1 ELSE 0 END), 0) AS multiple_doi,
                    0 AS empty_doi_versions,
                    COALESCE(SUM(CASE WHEN (
                    """
                    + sharedDoiPredicate("o", query.activeInPure())
                    + """
                    ) THEN 1 ELSE 0 END), 0) AS shared_doi
                    """;
        }
        String sql = "SELECT COUNT(*) AS total, "
                + evSelect
                + " FROM " + qualified("pure_research_output") + " o "
                + evJoin
                + " "
                + parts.where;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                dto.total = rs.getLong("total");
                dto.withDoi = rs.getLong("with_doi");
                dto.withoutDoi = rs.getLong("without_doi");
                dto.withMultipleDoiVersions = rs.getLong("multiple_doi");
                dto.doiElectronicVersionsWithoutDoi = rs.getLong("empty_doi_versions");
                dto.withSharedDoi = rs.getLong("shared_doi");
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load research output stats", ex);
        }
    }

    private long countWithFwci(ResearchOutputQuery query) {
        if (!tableExists("pure_research_output_metric")) {
            return 0L;
        }
        SqlParts parts = buildWhere(query);
        String sql = "SELECT COUNT(*) FROM " + qualified("pure_research_output") + " o" + parts.where
                + " AND EXISTS (SELECT 1 FROM " + qualified("pure_research_output_metric")
                + " m WHERE m.research_output_id = o.id AND m.collection_id = 'sciVal' AND m.metric_id = 'fwci')";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parts.params.toArray());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to count research outputs with FWCI", ex);
        }
    }

    private static ResearchOutputQuery statusQuery(String doiStatus, Boolean activeInPure) {
        return new ResearchOutputQuery(
                null, null, null, null, doiStatus, null, activeInPure, null, null, "year", "desc", 0, -1
        );
    }

    private long countSharedDoiValues(Boolean activeInPure) {
        if (!tableExists("pure_research_output_electronic_version")) {
            return 0L;
        }
        String ev = qualified("pure_research_output_electronic_version");
        String parent = qualified("pure_research_output");
        String norm = normalizedDoiSql("dup.doi");
        String sql = """
                SELECT COUNT(*)
                FROM (
                    SELECT 1
                    FROM %s dup
                    JOIN %s parent ON parent.id = dup.research_output_id
                    WHERE dup.type_discriminator = '%s'
                      AND dup.doi IS NOT NULL
                      AND TRIM(dup.doi) <> ''
                      AND %s
                    GROUP BY %s
                    HAVING COUNT(DISTINCT dup.research_output_id) > 1
                ) shared
                """.formatted(
                ev,
                parent,
                DOI_ELECTRONIC_VERSION,
                activeInPureSql("parent", activeInPure),
                norm
        );
        return scalarLong(sql);
    }

    private long countDoiElectronicVersionsWithoutDoi(Boolean activeInPure) {
        if (!tableExists("pure_research_output_electronic_version")) {
            return 0L;
        }
        String ev = qualified("pure_research_output_electronic_version");
        String parent = qualified("pure_research_output");
        String sql = """
                SELECT COUNT(y.id)
                FROM %s y
                JOIN %s o ON o.id = y.research_output_id
                WHERE y.type_discriminator = '%s'
                  AND (y.doi IS NULL OR TRIM(y.doi) = '')
                  AND %s
                """.formatted(
                ev,
                parent,
                DOI_ELECTRONIC_VERSION,
                activeInPureSql("o", activeInPure)
        );
        return scalarLong(sql);
    }

    private void appendDoiSearch(StringBuilder where, List<Object> params, String raw, String like) {
        String normalizedDoi = stripKnownPrefix(raw, "https://doi.org/", "http://doi.org/", "https://dx.doi.org/", "doi:");
        String doiLike = likeContains(normalizedDoi);
        if (tableExists("pure_research_output_electronic_version")) {
            where.append("""
                     OR EXISTS (
                        SELECT 1
                        FROM """ + " " + qualified("pure_research_output_electronic_version") + """
                         ev
                        WHERE ev.research_output_id = o.id
                          AND (
                              ev.doi LIKE ? ESCAPE '!'
                              OR ev.doi LIKE ? ESCAPE '!'
                              OR ev.doi = ?
                          )
                     )
                    """);
            params.add(like);
            params.add(doiLike);
            params.add(normalizedDoi);
        }
        if (tableExists("pure_research_output_identifier")) {
            where.append("""
                     OR EXISTS (
                        SELECT 1
                        FROM """ + " " + qualified("pure_research_output_identifier") + """
                         i
                        WHERE i.research_output_id = o.id
                          AND (
                              i.value LIKE ? ESCAPE '!'
                              OR i.value LIKE ? ESCAPE '!'
                              OR i.value = ?
                          )
                          AND (
                              LOWER(i.id_source) LIKE '%doi%'
                              OR LOWER(i.type_discriminator) LIKE '%doi%'
                          )
                     )
                    """);
            params.add(like);
            params.add(doiLike);
            params.add(normalizedDoi);
        }
    }

    private void appendJournalSearch(StringBuilder where, List<Object> params, String like) {
        if (!tableExists("pure_research_output_journal") || !tableExists("pure_journal")) {
            return;
        }
        where.append("""
                 OR EXISTS (
                    SELECT 1
                    FROM """ + " " + qualified("pure_research_output_journal") + """
                     rj
                    LEFT JOIN """ + " " + qualified("pure_journal") + """
                     j ON j.id = rj.journal_id
                    LEFT JOIN """ + " " + (tableExists("pure_journal_title")
                        ? qualified("pure_journal_title")
                        : qualified("pure_journal")) + """
                     jt ON """ + (tableExists("pure_journal_title") ? "jt.journal_id = j.id" : "1=0") + """
                    
                    WHERE rj.research_output_id = o.id
                      AND (
                            COALESCE(jt.title, j.title) LIKE ? ESCAPE '!'
                         OR """ + (columnExists("pure_research_output_journal", "journal_uuid")
                        ? "rj.journal_uuid LIKE ? ESCAPE '!'"
                        : "1=0") + """
                      )
                 )
                """);
        params.add(like);
        if (columnExists("pure_research_output_journal", "journal_uuid")) {
            params.add(like);
        }
    }

    private static String sortKey(ResearchOutputQuery query) {
        return query.sort() == null ? "" : query.sort().trim();
    }

    private void logTiming(String stage, long startedNanos) {
        if (!LOG.isLoggable(Level.FINE)) {
            return;
        }
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        LOG.fine("researchOutput." + stage + " " + elapsedMs + " ms");
    }

    private String yearDerivedSql() {
        return purePublicationYearDerivedSql();
    }

    private String doiVersionAggSql() {
        return """
                SELECT
                    y.research_output_id,
                    COUNT(y.id) AS doi_version_count,
                    SUM(CASE WHEN y.doi IS NOT NULL AND TRIM(y.doi) <> '' THEN 1 ELSE 0 END) AS valid_doi_count
                FROM %s y
                WHERE y.type_discriminator = '%s'
                GROUP BY y.research_output_id
                """.formatted(qualified("pure_research_output_electronic_version"), DOI_ELECTRONIC_VERSION);
    }

    private String contributorAggSql() {
        return """
                SELECT c.research_output_id, COUNT(c.id) AS contributor_count
                FROM %s c
                GROUP BY c.research_output_id
                """.formatted(qualified("pure_research_output_contributor"));
    }

    private String publicationYearExpr(String alias) {
        if (!tableExists("pure_research_output_status")) {
            return "NULL";
        }
        String status = qualified("pure_research_output_status");
        return """
                COALESCE(
                    (
                        SELECT s.publication_year
                        FROM %s s
                        WHERE s.research_output_id = %s.id
                          AND s.current_status = 1
                          AND s.publication_year IS NOT NULL
                        ORDER BY s.id
                        LIMIT 1
                    ),
                    (
                        SELECT s.publication_year
                        FROM %s s
                        WHERE s.research_output_id = %s.id
                          AND s.publication_year IS NOT NULL
                        ORDER BY s.publication_year DESC, s.id
                        LIMIT 1
                    )
                )
                """.formatted(status, alias, status, alias);
    }

    private String doiExpr(String alias) {
        if (!tableExists("pure_research_output_electronic_version")) {
            return "NULL";
        }
        return """
                (
                    SELECT ev.doi
                    FROM %s ev
                    WHERE ev.research_output_id = %s.id
                      AND ev.type_discriminator = '%s'
                      AND ev.doi IS NOT NULL
                      AND TRIM(ev.doi) <> ''
                    ORDER BY ev.id
                    LIMIT 1
                )
                """.formatted(
                qualified("pure_research_output_electronic_version"),
                alias,
                DOI_ELECTRONIC_VERSION
        );
    }

    private String journalTitleExpr(String alias) {
        if (!tableExists("pure_research_output_journal") || !tableExists("pure_journal")) {
            return "NULL";
        }
        String titleSource = tableExists("pure_journal_title")
                ? """
                    COALESCE(
                        (
                            SELECT t.title
                            FROM %s t
                            WHERE t.journal_id = j.id
                              AND t.title IS NOT NULL
                              AND TRIM(t.title) <> ''
                            ORDER BY t.id
                            LIMIT 1
                        ),
                        j.title
                    )
                    """.formatted(qualified("pure_journal_title"))
                : "j.title";
        return """
                (
                    SELECT %s
                    FROM %s rj
                    LEFT JOIN %s j ON j.id = rj.journal_id
                        %s
                    WHERE rj.research_output_id = %s.id
                    ORDER BY rj.id
                    LIMIT 1
                )
                """.formatted(
                titleSource,
                qualified("pure_research_output_journal"),
                qualified("pure_journal"),
                columnExists("pure_research_output_journal", "journal_uuid")
                        ? "OR (rj.journal_uuid IS NOT NULL AND j.uuid = rj.journal_uuid)"
                        : "",
                alias
        );
    }

    private String journalUuidExpr(String alias) {
        if (!tableExists("pure_research_output_journal")) {
            return "NULL";
        }
        if (columnExists("pure_research_output_journal", "journal_uuid")) {
            return """
                    (
                        SELECT COALESCE(rj.journal_uuid, j.uuid)
                        FROM %s rj
                        LEFT JOIN %s j ON j.id = rj.journal_id
                        WHERE rj.research_output_id = %s.id
                        ORDER BY rj.id
                        LIMIT 1
                    )
                    """.formatted(
                    qualified("pure_research_output_journal"),
                    qualified("pure_journal"),
                    alias
            );
        }
        return """
                (
                    SELECT j.uuid
                    FROM %s rj
                    JOIN %s j ON j.id = rj.journal_id
                    WHERE rj.research_output_id = %s.id
                    ORDER BY rj.id
                    LIMIT 1
                )
                """.formatted(
                qualified("pure_research_output_journal"),
                qualified("pure_journal"),
                alias
        );
    }

    private String contributorCountExpr(String alias) {
        if (!tableExists("pure_research_output_contributor")) {
            return "NULL";
        }
        return """
                (
                    SELECT COUNT(c.id)
                    FROM %s c
                    WHERE c.research_output_id = %s.id
                )
                """.formatted(qualified("pure_research_output_contributor"), alias);
    }

    private String doiVersionCountExpr(String alias) {
        if (!tableExists("pure_research_output_electronic_version")) {
            return "0";
        }
        return """
                (
                    SELECT COUNT(y.id)
                    FROM %s y
                    WHERE y.research_output_id = %s.id
                      AND y.type_discriminator = '%s'
                )
                """.formatted(qualified("pure_research_output_electronic_version"), alias, DOI_ELECTRONIC_VERSION);
    }

    private static String orderBy(ResearchOutputQuery query) {
        boolean descending = !"asc".equalsIgnoreCase(query.direction());
        String key = query.sort() == null ? "" : query.sort().trim();
        if (key.isEmpty() && "MULTIPLE_DOI_VERSIONS".equalsIgnoreCase(query.doiStatus())) {
            return " ORDER BY " + orderByNullsLast("doi_version_count", true)
                    + ", " + orderByNullsLast("year", true)
                    + ", o.title ASC, o.id ASC";
        }
        return switch (key) {
            case "pureId" -> " ORDER BY " + orderByNullsLast("o.pure_id", descending) + ", o.id ASC";
            case "title" -> " ORDER BY " + orderByNullsLast("o.title", descending) + ", o.id ASC";
            case "type" -> " ORDER BY " + orderByNullsLast("type", descending) + ", o.title ASC, o.id ASC";
            case "doi" -> " ORDER BY " + orderByNullsLast("doi", descending) + ", o.title ASC, o.id ASC";
            case "doiVersionCount" -> " ORDER BY " + orderByNullsLast("doi_version_count", descending)
                    + ", " + orderByNullsLast("year", true)
                    + ", o.title ASC, o.id ASC";
            case "journal" -> " ORDER BY " + orderByNullsLast("journal", descending) + ", o.title ASC, o.id ASC";
            case "contributorsCount" -> " ORDER BY " + orderByNullsLast("contributor_count", descending)
                    + ", o.title ASC, o.id ASC";
            case "workflow" -> " ORDER BY " + orderByNullsLast("o.workflow_step", descending)
                    + ", o.title ASC, o.id ASC";
            case "createdBy" -> " ORDER BY " + orderByNullsLast("o.created_by", descending)
                    + ", o.title ASC, o.id ASC";
            case "modifiedBy" -> " ORDER BY " + orderByNullsLast("o.modified_by", descending)
                    + ", o.title ASC, o.id ASC";
            case "fwci" -> " ORDER BY " + orderByNullsLast("fwci", descending)
                    + ", o.title ASC, o.id ASC";
            default -> " ORDER BY " + orderByNullsLast("year", descending)
                    + ", o.title ASC, o.id ASC";
        };
    }

    private ResearchOutputJournalDto findJournal(Connection connection, long researchOutputId) throws SQLException {
        if (!tableExists("pure_research_output_journal") || !tableExists("pure_journal")) {
            return null;
        }
        boolean hasUuid = columnExists("pure_research_output_journal", "journal_uuid");
        String titleSource = tableExists("pure_journal_title")
                ? """
                    COALESCE((
                        SELECT t.title FROM %s t
                        WHERE t.journal_id = j.id AND t.title IS NOT NULL AND TRIM(t.title) <> ''
                        ORDER BY t.id LIMIT 1
                    ), j.title)
                    """.formatted(qualified("pure_journal_title"))
                : "j.title";
        String issnSource = tableExists("pure_journal_issn")
                ? """
                    COALESCE((
                        SELECT i.issn FROM %s i
                        WHERE i.journal_id = j.id AND i.issn IS NOT NULL AND TRIM(i.issn) <> ''
                        ORDER BY i.id LIMIT 1
                    ), j.issn)
                    """.formatted(qualified("pure_journal_issn"))
                : "j.issn";
        String sql = "SELECT "
                + (hasUuid ? "rj.journal_uuid, " : "")
                + "j.uuid, "
                + titleSource
                + " AS title, "
                + issnSource
                + " AS issn, j.id AS catalog_id FROM "
                + qualified("pure_research_output_journal")
                + " rj LEFT JOIN "
                + qualified("pure_journal")
                + " j ON j.id = rj.journal_id"
                + (hasUuid ? " OR (rj.journal_uuid IS NOT NULL AND j.uuid = rj.journal_uuid)" : "")
                + " WHERE rj.research_output_id = ? ORDER BY rj.id LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ResearchOutputJournalDto dto = new ResearchOutputJournalDto();
                String relationUuid = hasUuid ? nullableString(rs, "journal_uuid") : null;
                dto.uuid = firstNonBlank(relationUuid, nullableString(rs, "uuid"));
                dto.title = nullableString(rs, "title");
                dto.issn = nullableString(rs, "issn");
                dto.synced = nullableLong(rs, "catalog_id") != null;
                return dto;
            }
        }
    }

    private List<ResearchOutputStatusDto> findStatuses(Connection connection, long researchOutputId)
            throws SQLException {
        if (!tableExists("pure_research_output_status")) {
            return List.of();
        }
        String sql = """
                SELECT current_status, status_es, status_en, publication_year, publication_month, publication_day
                FROM """ + " " + qualified("pure_research_output_status") + """
                
                WHERE research_output_id = ?
                ORDER BY current_status DESC, id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                List<ResearchOutputStatusDto> items = new ArrayList<>();
                while (rs.next()) {
                    ResearchOutputStatusDto dto = new ResearchOutputStatusDto();
                    dto.current = nullableBoolean(rs, "current_status");
                    dto.statusEs = nullableString(rs, "status_es");
                    dto.statusEn = nullableString(rs, "status_en");
                    dto.publicationYear = nullableInt(rs, "publication_year");
                    dto.publicationMonth = nullableInt(rs, "publication_month");
                    dto.publicationDay = nullableInt(rs, "publication_day");
                    items.add(dto);
                }
                return items;
            }
        }
    }

    private List<ResearchOutputIdentifierDto> findIdentifiers(Connection connection, long researchOutputId)
            throws SQLException {
        if (!tableExists("pure_research_output_identifier")) {
            return List.of();
        }
        String sql = """
                SELECT type_discriminator, id_source, value
                FROM """ + " " + qualified("pure_research_output_identifier") + """
                
                WHERE research_output_id = ?
                ORDER BY id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                List<ResearchOutputIdentifierDto> items = new ArrayList<>();
                while (rs.next()) {
                    ResearchOutputIdentifierDto dto = new ResearchOutputIdentifierDto();
                    dto.typeDiscriminator = nullableString(rs, "type_discriminator");
                    dto.idSource = nullableString(rs, "id_source");
                    dto.value = nullableString(rs, "value");
                    items.add(dto);
                }
                return items;
            }
        }
    }

    private boolean researchOutputExists(long researchOutputId) {
        String sql = "SELECT 1 FROM " + qualified("pure_research_output") + " o WHERE o.id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to check research output", ex);
        }
    }

    private List<ResearchOutputElectronicVersionDto> findDoiElectronicVersions(long researchOutputId) {
        if (!tableExists("pure_research_output_electronic_version")) {
            return List.of();
        }
        String sql = """
                SELECT id, pure_id, type_discriminator, doi, access_type_es, access_type_en, version_type_es, version_type_en
                FROM """ + " " + qualified("pure_research_output_electronic_version") + """
                
                WHERE research_output_id = ?
                  AND type_discriminator = ?
                ORDER BY id
                """;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            statement.setString(2, DOI_ELECTRONIC_VERSION);
            try (ResultSet rs = statement.executeQuery()) {
                List<ResearchOutputElectronicVersionDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapElectronicVersion(rs));
                }
                return items;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to list DOI electronic versions", ex);
        }
    }

    private static int countDistinctDois(List<ResearchOutputElectronicVersionDto> items) {
        Set<String> distinct = new LinkedHashSet<>();
        for (ResearchOutputElectronicVersionDto item : items) {
            String normalized = normalizeDoiValue(item == null ? null : item.doi);
            if (normalized != null) {
                distinct.add(normalized);
            }
        }
        return distinct.size();
    }

    private List<ResearchOutputElectronicVersionDto> findElectronicVersions(
            Connection connection, long researchOutputId) throws SQLException {
        if (!tableExists("pure_research_output_electronic_version")) {
            return List.of();
        }
        String sql = """
                SELECT id, pure_id, type_discriminator, doi, access_type_es, access_type_en, version_type_es, version_type_en
                FROM """ + " " + qualified("pure_research_output_electronic_version") + """
                
                WHERE research_output_id = ?
                ORDER BY id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                List<ResearchOutputElectronicVersionDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapElectronicVersion(rs));
                }
                return items;
            }
        }
    }

    private static ResearchOutputElectronicVersionDto mapElectronicVersion(ResultSet rs) throws SQLException {
        ResearchOutputElectronicVersionDto dto = new ResearchOutputElectronicVersionDto();
        dto.id = nullableLong(rs, "id");
        dto.pureId = nullableLong(rs, "pure_id");
        dto.typeDiscriminator = nullableString(rs, "type_discriminator");
        dto.doi = nullableString(rs, "doi");
        dto.accessTypeEs = nullableString(rs, "access_type_es");
        dto.accessTypeEn = nullableString(rs, "access_type_en");
        dto.versionTypeEs = nullableString(rs, "version_type_es");
        dto.versionTypeEn = nullableString(rs, "version_type_en");
        return dto;
    }

    private static void attachDoiVersionMetrics(ResearchOutputDetailDto detail) {
        int versionCount = 0;
        Set<String> distinctDois = new LinkedHashSet<>();
        for (ResearchOutputElectronicVersionDto version : detail.electronicVersions) {
            if (version == null || !DOI_ELECTRONIC_VERSION.equals(version.typeDiscriminator)) {
                continue;
            }
            versionCount++;
            String normalized = normalizeDoiValue(version.doi);
            if (normalized != null) {
                distinctDois.add(normalized);
            }
        }
        detail.doiElectronicVersionCount = versionCount;
        detail.distinctDoiCount = distinctDois.size();
    }

    private static String normalizeDoiValue(String doi) {
        if (doi == null || doi.isBlank()) {
            return null;
        }
        String stripped = stripKnownPrefix(
                doi.trim(),
                "https://dx.doi.org/",
                "http://dx.doi.org/",
                "https://doi.org/",
                "http://doi.org/",
                "doi:"
        );
        if (stripped == null || stripped.isBlank()) {
            return null;
        }
        return stripped.toLowerCase(Locale.ROOT).trim();
    }

    private List<ResearchOutputContributorDto> findContributors(Connection connection, long researchOutputId)
            throws SQLException {
        if (!tableExists("pure_research_output_contributor")) {
            return List.of();
        }
        boolean persons = tableExists("pure_person");
        String personJoin = persons
                ? " LEFT JOIN " + qualified("pure_person") + " p ON p.uuid = c.person_uuid"
                : "";
        String personCols = persons
                ? ", p.id AS person_id, p.first_name AS person_first_name, p.last_name AS person_last_name"
                : ", NULL AS person_id, NULL AS person_first_name, NULL AS person_last_name";
        String sql = """
                SELECT
                    c.id,
                    c.first_name,
                    c.last_name,
                    c.role_es,
                    c.role_en,
                    c.person_uuid,
                    c.type_discriminator,
                    c.corresponding_author
                """ + personCols + """
                
                FROM """ + " " + qualified("pure_research_output_contributor") + " c"
                + personJoin + """
                
                WHERE c.research_output_id = ?
                ORDER BY c.id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                List<ResearchOutputContributorDto> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapContributor(rs));
                }
                attachContributorOrganizations(connection, items);
                return items;
            }
        }
    }

    private void attachContributorOrganizations(Connection connection, List<ResearchOutputContributorDto> contributors)
            throws SQLException {
        if (contributors.isEmpty() || !tableExists("pure_research_output_contributor_organization")) {
            return;
        }
        Map<Long, ResearchOutputContributorDto> byId = new LinkedHashMap<>();
        for (ResearchOutputContributorDto contributor : contributors) {
            if (contributor.id != null) {
                byId.put(contributor.id, contributor);
            }
        }
        if (byId.isEmpty()) {
            return;
        }
        String placeholders = String.join(", ", byId.keySet().stream().map(id -> "?").toList());
        String sql = """
                SELECT contributor_id, organization_uuid
                FROM """ + " " + qualified("pure_research_output_contributor_organization") + """
                
                WHERE contributor_id IN (""" + placeholders + ") ORDER BY organization_uuid";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Long contributorId : byId.keySet()) {
                statement.setLong(index++, contributorId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ResearchOutputContributorDto contributor = byId.get(rs.getLong("contributor_id"));
                    String uuid = nullableString(rs, "organization_uuid");
                    if (contributor != null && uuid != null) {
                        contributor.organizationUuids.add(uuid);
                    }
                }
            }
        }
    }

    private List<ResearchOutputKeywordGroupDto> findKeywordGroups(Connection connection, long researchOutputId)
            throws SQLException {
        if (!tableExists("pure_research_output_keyword_group")) {
            return List.of();
        }
        String sql = """
                SELECT id, logical_name, name_es, name_en
                FROM """ + " " + qualified("pure_research_output_keyword_group") + """
                
                WHERE research_output_id = ?
                ORDER BY id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                Map<Long, ResearchOutputKeywordGroupDto> groups = new LinkedHashMap<>();
                while (rs.next()) {
                    ResearchOutputKeywordGroupDto dto = new ResearchOutputKeywordGroupDto();
                    dto.nameEs = nullableString(rs, "name_es");
                    dto.nameEn = nullableString(rs, "name_en");
                    dto.name = firstNonBlank(dto.nameEn, firstNonBlank(dto.nameEs, nullableString(rs, "logical_name")));
                    groups.put(rs.getLong("id"), dto);
                }
                if (tableExists("pure_research_output_keyword")) {
                    loadKeywords(connection, groups);
                }
                return new ArrayList<>(groups.values());
            }
        }
    }

    private void loadKeywords(Connection connection, Map<Long, ResearchOutputKeywordGroupDto> groups)
            throws SQLException {
        if (groups.isEmpty()) {
            return;
        }
        String placeholders = String.join(", ", groups.keySet().stream().map(id -> "?").toList());
        String sql = """
                SELECT keyword_group_id, keyword_type, keyword_es, keyword_en, locale, free_keyword
                FROM """ + " " + qualified("pure_research_output_keyword") + """
                
                WHERE keyword_group_id IN (""" + placeholders + ") ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Long groupId : groups.keySet()) {
                statement.setLong(index++, groupId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ResearchOutputKeywordGroupDto group = groups.get(rs.getLong("keyword_group_id"));
                    if (group == null) {
                        continue;
                    }
                    ResearchOutputKeywordDto keyword = new ResearchOutputKeywordDto();
                    keyword.type = nullableString(rs, "keyword_type");
                    keyword.locale = nullableString(rs, "locale");
                    keyword.value = firstNonBlank(
                            nullableString(rs, "free_keyword"),
                            firstNonBlank(nullableString(rs, "keyword_en"), nullableString(rs, "keyword_es"))
                    );
                    if (keyword.value != null) {
                        group.keywords.add(keyword);
                    }
                }
            }
        }
    }

    private List<ResearchOutputLinkDto> findLinks(Connection connection, long researchOutputId) throws SQLException {
        if (!tableExists("pure_research_output_link")) {
            return List.of();
        }
        String sql = """
                SELECT url, description_es, description_en, link_type_es, link_type_en
                FROM """ + " " + qualified("pure_research_output_link") + """
                
                WHERE research_output_id = ?
                ORDER BY id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                List<ResearchOutputLinkDto> items = new ArrayList<>();
                while (rs.next()) {
                    ResearchOutputLinkDto dto = new ResearchOutputLinkDto();
                    dto.url = nullableString(rs, "url");
                    dto.descriptionEs = nullableString(rs, "description_es");
                    dto.descriptionEn = nullableString(rs, "description_en");
                    dto.linkTypeEs = nullableString(rs, "link_type_es");
                    dto.linkTypeEn = nullableString(rs, "link_type_en");
                    items.add(dto);
                }
                return items;
            }
        }
    }

    private List<String> findExternalOrganizationUuids(Connection connection, long researchOutputId)
            throws SQLException {
        if (!tableExists("pure_research_output_external_organization")) {
            return List.of();
        }
        String sql = """
                SELECT organization_uuid
                FROM """ + " " + qualified("pure_research_output_external_organization") + """
                
                WHERE research_output_id = ?
                ORDER BY organization_uuid
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, researchOutputId);
            try (ResultSet rs = statement.executeQuery()) {
                List<String> items = new ArrayList<>();
                while (rs.next()) {
                    String uuid = nullableString(rs, "organization_uuid");
                    if (uuid != null) {
                        items.add(uuid);
                    }
                }
                return items;
            }
        }
    }

    private static ResearchOutputContributorDto mapContributor(ResultSet rs) throws SQLException {
        ResearchOutputContributorDto dto = new ResearchOutputContributorDto();
        dto.id = rs.getLong("id");
        dto.personUuid = nullableString(rs, "person_uuid");
        Long personId = nullableLong(rs, "person_id");
        dto.personId = personId;
        String contributorFirst = nullableString(rs, "first_name");
        String contributorLast = nullableString(rs, "last_name");
        String personFirst = nullableString(rs, "person_first_name");
        String personLast = nullableString(rs, "person_last_name");
        dto.firstName = personFirst != null ? personFirst : contributorFirst;
        dto.lastName = personLast != null ? personLast : contributorLast;
        dto.fullName = fullName(dto.firstName, dto.lastName);
        dto.roleEs = nullableString(rs, "role_es");
        dto.roleEn = nullableString(rs, "role_en");
        dto.role = firstNonBlank(dto.roleEn, dto.roleEs);
        String discriminator = nullableString(rs, "type_discriminator");
        dto.external = dto.personUuid == null
                || (discriminator != null && discriminator.toLowerCase().contains("external"));
        dto.correspondingAuthor = nullableBoolean(rs, "corresponding_author");
        return dto;
    }

    private static ResearchOutputSummaryDto mapSummary(ResultSet rs) throws SQLException {
        ResearchOutputSummaryDto dto = new ResearchOutputSummaryDto();
        dto.id = rs.getLong("id");
        dto.pureId = nullableLong(rs, "pure_id");
        dto.uuid = nullableString(rs, "uuid");
        dto.title = nullableString(rs, "title");
        dto.year = nullableInt(rs, "year");
        dto.type = nullableString(rs, "type");
        dto.doi = nullableString(rs, "doi");
        dto.journal = nullableString(rs, "journal");
        dto.journalUuid = hasColumn(rs, "journal_uuid") ? nullableString(rs, "journal_uuid") : null;
        dto.contributorCount = nullableInt(rs, "contributor_count");
        dto.doiVersionCount = nullableInt(rs, "doi_version_count");
        dto.activeInPure = nullableBoolean(rs, "active_in_pure");
        dto.workflowStep = hasColumn(rs, "workflow_step") ? nullableString(rs, "workflow_step") : null;
        dto.workflowEs = hasColumn(rs, "workflow_es") ? nullableString(rs, "workflow_es") : null;
        dto.workflowEn = hasColumn(rs, "workflow_en") ? nullableString(rs, "workflow_en") : null;
        dto.visibilityKey = hasColumn(rs, "visibility_key") ? nullableString(rs, "visibility_key") : null;
        dto.visibilityEs = hasColumn(rs, "visibility_es") ? nullableString(rs, "visibility_es") : null;
        dto.visibilityEn = hasColumn(rs, "visibility_en") ? nullableString(rs, "visibility_en") : null;
        dto.createdBy = hasColumn(rs, "created_by") ? nullableString(rs, "created_by") : null;
        dto.modifiedBy = hasColumn(rs, "modified_by") ? nullableString(rs, "modified_by") : null;
        if (hasColumn(rs, "fwci")) {
            dto.fwci = rs.getBigDecimal("fwci");
            if (rs.wasNull()) {
                dto.fwci = null;
            }
            dto.fwciYear = nullableInt(rs, "fwci_year");
        }
        return dto;
    }

    private static void copySummary(ResearchOutputSummaryDto source, ResearchOutputDetailDto target) {
        target.id = source.id;
        target.pureId = source.pureId;
        target.uuid = source.uuid;
        target.title = source.title;
        target.year = source.year;
        target.type = source.type;
        target.doi = source.doi;
        target.journal = source.journal;
        target.contributorCount = source.contributorCount;
        target.doiVersionCount = source.doiVersionCount;
        target.activeInPure = source.activeInPure;
        target.workflowStep = source.workflowStep;
        target.workflowEs = source.workflowEs;
        target.workflowEn = source.workflowEn;
        target.visibilityKey = source.visibilityKey;
        target.visibilityEs = source.visibilityEs;
        target.visibilityEn = source.visibilityEn;
        target.createdBy = source.createdBy;
        target.modifiedBy = source.modifiedBy;
        target.fwci = source.fwci;
        target.fwciYear = source.fwciYear;
    }

    private String auditSelect(String alias) {
        if (!columnExists("pure_research_output", "created_by")) {
            return "CAST(NULL AS CHAR) AS created_by, CAST(NULL AS CHAR) AS modified_by,";
        }
        return alias + ".created_by, " + alias + ".modified_by,";
    }

    private String visibilitySelect(String alias) {
        if (!columnExists("pure_research_output", "visibility_key")) {
            return "CAST(NULL AS CHAR) AS visibility_key, CAST(NULL AS CHAR) AS visibility_es, CAST(NULL AS CHAR) AS visibility_en,";
        }
        return alias + ".visibility_key, " + alias + ".visibility_es, " + alias + ".visibility_en,";
    }

    private List<ResearchOutputWorkflowOptionDto> findWorkflows() {
        List<ResearchOutputWorkflowOptionDto> items = new ArrayList<>();
        if (!columnExists("pure_research_output", "workflow_step")) {
            return items;
        }
        String sql = """
                SELECT DISTINCT workflow_step, workflow_en, workflow_es
                FROM %s
                WHERE workflow_step IS NOT NULL AND TRIM(workflow_step) <> ''
                ORDER BY workflow_step
                """.formatted(qualified("pure_research_output"));
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                ResearchOutputWorkflowOptionDto option = new ResearchOutputWorkflowOptionDto();
                option.step = rs.getString("workflow_step");
                option.labelEn = rs.getString("workflow_en");
                option.labelEs = rs.getString("workflow_es");
                items.add(option);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to load research output workflows", ex);
        }
        return items;
    }

    private String activeInPureSelect(String alias) {
        if (!columnExists("pure_research_output", "active_in_pure")) {
            return "1 AS active_in_pure";
        }
        return "COALESCE(" + alias + ".active_in_pure, 1) AS active_in_pure";
    }

    private static String fullName(String first, String last) {
        if (first == null && last == null) {
            return null;
        }
        if (first == null) {
            return last;
        }
        if (last == null) {
            return first;
        }
        return first + " " + last;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    public record ResearchOutputQuery(
            String search,
            String type,
            Integer year,
            Boolean hasDoi,
            String doiStatus,
            String workflowStep,
            Boolean activeInPure,
            java.math.BigDecimal fwciFrom,
            java.math.BigDecimal fwciTo,
            String sort,
            String direction,
            int page,
            int size
    ) {
    }

    private record SqlParts(String where, List<Object> params) {
    }
}
