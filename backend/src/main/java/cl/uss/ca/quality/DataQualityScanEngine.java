package cl.uss.ca.quality;

import cl.uss.ca.exception.ErrorLog;
import cl.uss.ca.exception.ExceptionDetail;
import cl.uss.ca.quality.consistency.ConsistencyField;
import cl.uss.ca.quality.consistency.ConsistencyQualityScanner;
import cl.uss.ca.repository.DataQualityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class DataQualityScanEngine {
    private static final Logger LOG = Logger.getLogger(DataQualityScanEngine.class.getName());
    private static final int CURRENT_YEAR = Year.now().getValue();
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    DataQualityRepository repository;

    @Inject
    CrossrefQualityScanner crossrefQualityScanner;

    @Inject
    OpenAlexQualityScanner openAlexQualityScanner;

    @Inject
    ConsistencyQualityScanner consistencyQualityScanner;

    public ScanResult run(long executionId, String entityType, Long localRecordId) throws Exception {
        return run(executionId, entityType, localRecordId, null);
    }

    public ScanResult run(long executionId, String entityType, Long localRecordId, String onlyRule)
            throws Exception {
        ScanResult result = new ScanResult();
        boolean incremental = entityType != null && localRecordId != null;
        String rule = onlyRule == null || onlyRule.isBlank() ? null : onlyRule.trim();
        List<String> scanned = new ArrayList<>();
        if (!incremental || "RESEARCH_OUTPUT".equals(entityType)) {
            scanned.addAll(List.of(
                    "PURE-001", "PURE-002", "PURE-003", "PURE-004", "PURE-005",
                    "PURE-006", "PURE-007", "PURE-008", "PURE-009", "PURE-010",
                    "PURE-011", "PURE-012", "PURE-015", "PURE-017", "PURE-018",
                    "PURE-019", "PURE-020", "PURE-021", "PURE-022",
                    "CROSSREF-001", "CROSSREF-002", "CROSSREF-003", "CROSSREF-004", "CROSSREF-005",
                    "CROSSREF-006", "CROSSREF-007", "CROSSREF-008", "CROSSREF-009", "CROSSREF-010",
                    "EXT-01", "EXT-02", "EXT-03", "EXT-04", "EXT-05", "EXT-06",
                    "EXT-07", "EXT-08", "EXT-09", "EXT-10", "EXT-11", "EXT-12"
            ));
        }
        if (!incremental || "PERSON".equals(entityType)) {
            scanned.add("PURE-013");
            scanned.add("PURE-014");
            scanned.addAll(List.of("OPENALEX-001", "OPENALEX-002", "OPENALEX-003", "OPENALEX-004"));
        }
        if (rule != null) {
            scanned = scanned.stream().filter(code -> selected(rule, code)).toList();
            if (scanned.isEmpty()) {
                scanned = List.of(rule);
            }
        }
        Long outputId = "RESEARCH_OUTPUT".equals(entityType) ? localRecordId : null;
        Long personId = "PERSON".equals(entityType) ? localRecordId : null;

        runRule(executionId, "PURE-001", result, () -> scanUuidDuplicates(outputId), rule);
        runRule(executionId, "PURE-002", result, () -> scanTitles(outputId), rule);
        runRule(executionId, "PURE-003", result, () -> scanPureYear(outputId), rule);
        runRule(executionId, "PURE-004", result, () -> scanCurrentStatus(outputId), rule);
        runRule(executionId, "PURE-005", result, () -> scanPublishedYear(outputId), rule);
        runRule(executionId, "PURE-006", result, () -> scanPublishedVsAccepted(outputId), rule);
        runRule(executionId, "PURE-007", result, () -> scanElectronicVsPublished(outputId), rule);
        runRule(executionId, "PURE-008", result, () -> scanJournalUuidMissingJournal(outputId), rule);
        runRule(executionId, "PURE-009", result, () -> scanJournalMissingUuid(outputId), rule);
        runRule(executionId, "PURE-010", result, () -> scanArticleWithoutJournal(outputId), rule);
        runRule(executionId, "PURE-011", result, () -> scanAuthorNames(outputId), rule);
        runRule(executionId, "PURE-012", result, () -> scanAuthorDuplicates(outputId), rule);
        runRule(executionId, "PURE-013", result, () -> scanOrcid(personId), rule);
        runRule(executionId, "PURE-014", result, () -> scanMultipleScopus(personId), rule);
        runRule(executionId, "PURE-015", result, () -> scanContributorNames(outputId), rule);
        runRule(executionId, "PURE-017", result, () -> scanDois(outputId), rule);
        runRule(executionId, "PURE-018", result, () -> scanAbstracts(outputId), rule);
        runRule(executionId, "PURE-019", result, () -> scanPageCount(outputId), rule);
        runRule(executionId, "PURE-020", result, () -> scanPageRange(outputId), rule);
        runRule(executionId, "PURE-021", result, () -> scanEmbargo(outputId), rule);
        runRule(executionId, "PURE-022", result, () -> scanWorkflow(outputId), rule);
        if (!incremental || "RESEARCH_OUTPUT".equals(entityType)) {
            runRule(executionId, "CROSSREF-001", result, () -> crossrefQualityScanner.scan("CROSSREF-001", outputId), rule);
            runRule(executionId, "CROSSREF-002", result, () -> crossrefQualityScanner.scan("CROSSREF-002", outputId), rule);
            runRule(executionId, "CROSSREF-003", result, () -> List.of(), rule);
            runRule(executionId, "CROSSREF-004", result, () -> List.of(), rule);
            runRule(executionId, "CROSSREF-005", result, () -> List.of(), rule);
            runRule(executionId, "CROSSREF-006", result, () -> List.of(), rule);
            runRule(executionId, "CROSSREF-007", result, () -> crossrefQualityScanner.scan("CROSSREF-007", outputId), rule);
            runRule(executionId, "CROSSREF-008", result, () -> crossrefQualityScanner.scan("CROSSREF-008", outputId), rule);
            runRule(executionId, "CROSSREF-009", result, () -> crossrefQualityScanner.scan("CROSSREF-009", outputId), rule);
            runRule(executionId, "CROSSREF-010", result, () -> crossrefQualityScanner.scan("CROSSREF-010", outputId), rule);
            if (rule == null || rule.startsWith("EXT-")) {
                List<DataQualityCandidate> ext = consistencyQualityScanner.scan(outputId);
                for (ConsistencyField field : ConsistencyField.values()) {
                    String code = field.ruleCode();
                    runRule(executionId, code, result, () -> ext.stream()
                            .filter(item -> code.equals(item.ruleCode))
                            .toList(), rule);
                }
            }
        }
        if (!incremental || "PERSON".equals(entityType)) {
            runRule(executionId, "OPENALEX-001", result, () -> openAlexQualityScanner.scan("OPENALEX-001", personId), rule);
            runRule(executionId, "OPENALEX-002", result, () -> openAlexQualityScanner.scan("OPENALEX-002", personId), rule);
            runRule(executionId, "OPENALEX-003", result, () -> openAlexQualityScanner.scan("OPENALEX-003", personId), rule);
            runRule(executionId, "OPENALEX-004", result, () -> openAlexQualityScanner.scan("OPENALEX-004", personId), rule);
        }

        try {
            result.resolved = repository.resolveMissing(executionId, entityType, localRecordId, scanned);
        } catch (Exception ex) {
            result.errors++;
            result.recordFailure("AUTO_RESOLVE", ex);
            ErrorLog.unexpected(LOG, "data-quality execution=" + executionId + " auto-resolve failed", ex);
        }
        return result;
    }

    static boolean selected(String onlyRule, String ruleCode) {
        return onlyRule == null || onlyRule.isBlank() || onlyRule.equals(ruleCode);
    }

    private void runRule(
            long executionId,
            String ruleCode,
            ScanResult result,
            RuleScan scan,
            String onlyRule
    ) {
        if (!selected(onlyRule, ruleCode)) {
            return;
        }
        try {
            emit(executionId, ruleCode, result, scan.scan());
            result.successfulRules++;
        } catch (Exception ex) {
            result.errors++;
            result.recordFailure(ruleCode, ex);
            ErrorLog.unexpected(LOG, "data-quality execution=" + executionId
                    + " rule=" + ruleCode + " failed", ex);
            try {
                repository.heartbeat(executionId, result.evaluated, result.findings, result.errors);
            } catch (Exception heartbeatEx) {
                ErrorLog.unexpected(LOG, "data-quality execution=" + executionId
                        + " rule=" + ruleCode + " heartbeat failed", heartbeatEx);
            }
        }
    }

    private void emit(long executionId, String ruleCode, ScanResult result, List<DataQualityCandidate> candidates) {
        long ruleId = repository.ruleId(ruleCode);
        for (DataQualityCandidate candidate : candidates) {
            repository.upsertFinding(executionId, ruleId, candidate);
            result.findings++;
        }
        result.evaluated += Math.max(1, candidates.size());
        repository.heartbeat(executionId, result.evaluated, result.findings, result.errors);
    }

    private List<DataQualityCandidate> scanUuidDuplicates(Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title
                FROM %s o
                JOIN (
                    SELECT uuid FROM %s
                    WHERE uuid IS NOT NULL AND TRIM(uuid) <> ''
                    GROUP BY uuid
                    HAVING COUNT(*) > 1
                ) d ON d.uuid = o.uuid
                WHERE %s
                """.formatted(
                q("pure_research_output"), q("pure_research_output"),
                active("pure_research_output", "o") + idClause("o", outputId)
        );
        return repository.select(sql, rs -> {
            ObjectNode evidence = evidence("DUPLICATE_UUID");
            evidence.put("uuid", str(rs, "uuid"));
            return candidate("PURE-001", rs, "uuid", "DUPLICATE_UUID",
                    DataQualityCatalog.MODE_VIEW,
                    "UUID duplicado",
                    str(rs, "uuid"), null, evidence);
        }, params(outputId));
    }

    private List<DataQualityCandidate> scanTitles(Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title
                FROM %s o
                WHERE %s
                  AND (
                    o.title IS NULL OR TRIM(o.title) = ''
                    OR o.title LIKE ' %%' OR o.title LIKE '%% ' OR o.title LIKE '%%  %%'
                    OR o.title LIKE '%%&%%' OR o.title LIKE '%%Ã%%' OR o.title LIKE '%%<%%'
                    OR o.title LIKE CONCAT('%%', CHAR(9), '%%')
                  )
                """.formatted(q("pure_research_output"), active("pure_research_output", "o") + idClause("o", outputId));
        List<DataQualityCandidate> items = new ArrayList<>();
        for (OutputText row : repository.select(sql, this::outputText, params(outputId))) {
            items.addAll(textFindings("PURE-002", "RESEARCH_OUTPUT", row.id, row.pureId, row.uuid,
                    "title", row.title, 1, true));
        }
        return items;
    }

    private List<DataQualityCandidate> scanPureYear(Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title, o.submission_year
                FROM %s o
                WHERE %s
                  AND (o.submission_year IS NULL OR o.submission_year < 1900 OR o.submission_year > ?)
                """.formatted(q("pure_research_output"), active("pure_research_output", "o") + idClause("o", outputId));
        List<Object> params = new ArrayList<>();
        addId(params, outputId);
        params.add(CURRENT_YEAR);
        return repository.select(sql, rs -> {
            Integer year = integer(rs, "submission_year");
            String kind = year == null ? "EMPTY" : "OUT_OF_RANGE";
            ObjectNode evidence = evidence(kind);
            evidence.put("field", "SUBMISSION_YEAR");
            return candidate("PURE-003", rs, "submission_year", kind, DataQualityCatalog.MODE_MANUAL,
                    year == null ? "Año PURE vacío" : "Año PURE fuera de rango",
                    year == null ? null : String.valueOf(year), null, evidence);
        }, params.toArray());
    }

    private List<DataQualityCandidate> scanCurrentStatus(Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title,
                       s.pure_id AS status_pure_id, s.status_uri, s.status_es, s.status_en
                FROM %s o
                LEFT JOIN %s s ON s.research_output_id = o.id AND s.current_status = 1
                WHERE %s
                """.formatted(
                q("pure_research_output"), q("pure_research_output_status"),
                active("pure_research_output", "o") + idClause("o", outputId)
        );
        List<DataQualityCandidate> items = new ArrayList<>();
        for (StatusRow row : repository.select(sql, this::statusRow, params(outputId))) {
            if (blank(row.statusUri) && blank(row.statusEs) && blank(row.statusEn)) {
                ObjectNode evidence = evidence("EMPTY");
                items.add(candidate("PURE-004", row, "estado_actual", "EMPTY", DataQualityCatalog.MODE_MANUAL,
                        "Estado actual vacío", null, null, evidence));
            } else if (!knownStatus(row.statusUri, row.statusEs, row.statusEn)) {
                ObjectNode evidence = evidence("UNRECOGNIZED");
                evidence.put("statusUri", n(row.statusUri));
                items.add(candidate("PURE-004", row, "estado_actual", "UNRECOGNIZED", DataQualityCatalog.MODE_MANUAL,
                        "Estado actual no reconocido",
                        first(row.statusEs, row.statusEn, row.statusUri), null, evidence));
            }
        }
        return items;
    }

    private List<DataQualityCandidate> scanPublishedYear(Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title,
                       s.pure_id AS status_pure_id, s.status_uri, s.status_es, s.status_en, s.publication_year
                FROM %s o
                JOIN %s s ON s.research_output_id = o.id AND s.current_status = 1
                WHERE %s
                """.formatted(
                q("pure_research_output"), q("pure_research_output_status"),
                active("pure_research_output", "o") + idClause("o", outputId)
        );
        List<DataQualityCandidate> items = new ArrayList<>();
        for (StatusRow row : repository.select(sql, this::statusRow, params(outputId))) {
            if (!isPublished(row.statusUri, row.statusEs, row.statusEn)) {
                continue;
            }
            if (!DataQualityIdentifiers.yearInRange(row.year, CURRENT_YEAR)) {
                ObjectNode evidence = evidence(row.year == null ? "EMPTY" : "OUT_OF_RANGE");
                evidence.put("field", "STATUS_YEAR");
                evidence.put("childPureId", row.statusPureId == null ? 0 : row.statusPureId);
                evidence.put("contextKey", "status:" + row.statusPureId);
                items.add(candidate("PURE-005", row, "año_publicada",
                        row.year == null ? "EMPTY" : "OUT_OF_RANGE",
                        DataQualityCatalog.MODE_MANUAL,
                        "Publicación en estado Publicada sin año válido",
                        row.year == null ? null : String.valueOf(row.year), null, evidence));
            }
        }
        return items;
    }

    private List<DataQualityCandidate> scanPublishedVsAccepted(Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title,
                       pub.pure_id AS status_pure_id, pub.publication_year AS pub_year,
                       acc.publication_year AS acc_year
                FROM %s o
                JOIN %s pub ON pub.research_output_id = o.id AND %s
                JOIN %s acc ON acc.research_output_id = o.id AND %s
                WHERE %s
                  AND pub.publication_year IS NOT NULL
                  AND acc.publication_year IS NOT NULL
                  AND pub.publication_year < acc.publication_year
                """.formatted(
                q("pure_research_output"),
                q("pure_research_output_status"), publishedPredicate("pub"),
                q("pure_research_output_status"), acceptedPredicate("acc"),
                active("pure_research_output", "o") + idClause("o", outputId)
        );
        return repository.select(sql, rs -> {
            ObjectNode evidence = evidence("YEAR_ORDER");
            evidence.put("publishedYear", rs.getInt("pub_year"));
            evidence.put("acceptedYear", rs.getInt("acc_year"));
            evidence.put("childPureId", rs.getLong("status_pure_id"));
            return candidate("PURE-006", rs, "año_publicada", "YEAR_ORDER", DataQualityCatalog.MODE_MANUAL,
                    "El año de publicación es anterior al año de aceptación",
                    rs.getInt("pub_year") + " / " + rs.getInt("acc_year"), null, evidence);
        }, params(outputId));
    }

    private List<DataQualityCandidate> scanElectronicVsPublished(Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title,
                       pub.publication_year AS pub_year,
                       epub.publication_year AS epub_year,
                       epub.pure_id AS status_pure_id
                FROM %s o
                JOIN %s pub ON pub.research_output_id = o.id AND %s
                JOIN %s epub ON epub.research_output_id = o.id AND %s
                WHERE %s
                  AND pub.publication_year IS NOT NULL
                  AND epub.publication_year IS NOT NULL
                  AND epub.publication_year > pub.publication_year
                """.formatted(
                q("pure_research_output"),
                q("pure_research_output_status"), publishedPredicate("pub"),
                q("pure_research_output_status"), epubPredicate("epub"),
                active("pure_research_output", "o") + idClause("o", outputId)
        );
        return repository.select(sql, rs -> {
            ObjectNode evidence = evidence("YEAR_ORDER");
            evidence.put("publishedYear", rs.getInt("pub_year"));
            evidence.put("electronicYear", rs.getInt("epub_year"));
            return candidate("PURE-007", rs, "año_publicada_electronica", "YEAR_ORDER",
                    DataQualityCatalog.MODE_MANUAL,
                    "El año electrónico es posterior al año de publicación",
                    rs.getInt("epub_year") + " / " + rs.getInt("pub_year"), null, evidence);
        }, params(outputId));
    }

    private List<DataQualityCandidate> scanJournalUuidMissingJournal(Long outputId) {
        if (!repository.tableExistsPublic("pure_research_output_journal")
                || !repository.columnExistsPublic("pure_research_output_journal", "journal_uuid")) {
            return List.of();
        }
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title, j.journal_uuid
                FROM %s o
                JOIN %s j ON j.research_output_id = o.id
                WHERE %s
                  AND j.journal_uuid IS NOT NULL AND TRIM(j.journal_uuid) <> ''
                  AND NOT EXISTS (SELECT 1 FROM %s g WHERE g.uuid = j.journal_uuid)
                """.formatted(
                q("pure_research_output"), q("pure_research_output_journal"),
                active("pure_research_output", "o") + idClause("o", outputId),
                q("pure_journal")
        );
        return repository.select(sql, rs -> {
            ObjectNode evidence = evidence("JOURNAL_UUID_UNRESOLVED");
            evidence.put("journalUuid", str(rs, "journal_uuid"));
            return candidate("PURE-008", rs, "journal_uuid", "JOURNAL_UUID_UNRESOLVED",
                    DataQualityCatalog.MODE_VIEW, "No se pudo asociar journal_uuid",
                    str(rs, "journal_uuid"), null, evidence);
        }, params(outputId));
    }

    private List<DataQualityCandidate> scanJournalMissingUuid(Long outputId) {
        if (!repository.tableExistsPublic("pure_research_output_journal")
                || !repository.columnExistsPublic("pure_research_output_journal", "journal_uuid")) {
            return List.of();
        }
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title, j.journal_id, j.journal_uuid
                FROM %s o
                JOIN %s j ON j.research_output_id = o.id
                WHERE %s
                  AND j.journal_id IS NOT NULL
                  AND (j.journal_uuid IS NULL OR TRIM(j.journal_uuid) = '')
                """.formatted(
                q("pure_research_output"), q("pure_research_output_journal"),
                active("pure_research_output", "o") + idClause("o", outputId)
        );
        return repository.select(sql, rs -> candidate("PURE-009", rs, "journal_uuid", "MISSING_UUID",
                DataQualityCatalog.MODE_VIEW, "Revista asociada sin journal_uuid",
                null, null, evidence("MISSING_UUID")), params(outputId));
    }

    private List<DataQualityCandidate> scanArticleWithoutJournal(Long outputId) {
        String journalJoin = repository.tableExistsPublic("pure_research_output_journal")
                ? q("pure_research_output_journal")
                : null;
        if (journalJoin == null) {
            return List.of();
        }
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title, o.type_es, o.type_en, o.type_discriminator
                FROM %s o
                WHERE %s
                  AND (
                    o.type_discriminator LIKE '%%ContributionToJournal%%'
                    OR LOWER(COALESCE(o.type_es,'')) LIKE 'artículo%%'
                    OR LOWER(COALESCE(o.type_en,'')) = 'article'
                  )
                  AND NOT EXISTS (SELECT 1 FROM %s j WHERE j.research_output_id = o.id)
                """.formatted(
                q("pure_research_output"),
                active("pure_research_output", "o") + idClause("o", outputId),
                journalJoin
        );
        return repository.select(sql, rs -> {
            ObjectNode evidence = evidence("ARTICLE_WITHOUT_JOURNAL");
            evidence.put("type", first(str(rs, "type_es"), str(rs, "type_en"), str(rs, "type_discriminator")));
            return candidate("PURE-010", rs, "journal", "ARTICLE_WITHOUT_JOURNAL",
                    DataQualityCatalog.MODE_VIEW, "Artículo sin revista",
                    str(rs, "type_es"), null, evidence);
        }, params(outputId));
    }

    private List<DataQualityCandidate> scanAuthorNames(Long outputId) {
        if (!repository.tableExistsPublic("pure_research_output_contributor")) {
            return List.of();
        }
        return scanContributorText("PURE-011", true, outputId);
    }

    private List<DataQualityCandidate> scanContributorNames(Long outputId) {
        if (!repository.tableExistsPublic("pure_research_output_contributor")) {
            return List.of();
        }
        List<DataQualityCandidate> items = scanContributorText("PURE-015", false, outputId);
        items.addAll(scanContributorDuplicates("PURE-015", false, outputId));
        return items;
    }

    private List<DataQualityCandidate> scanContributorText(String ruleCode, boolean authors, Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title,
                       c.pure_id AS child_pure_id, c.first_name, c.last_name, c.type_discriminator
                FROM %s o
                JOIN %s c ON c.research_output_id = o.id
                WHERE %s
                  AND %s
                """.formatted(
                q("pure_research_output"), q("pure_research_output_contributor"),
                active("pure_research_output", "o") + idClause("o", outputId),
                authors ? authorPredicate("c") : " NOT " + authorPredicate("c")
        );
        List<DataQualityCandidate> items = new ArrayList<>();
        repository.select(sql, rs -> {
            long outputLocalId = rs.getLong("id");
            Long pureId = nullableLong(rs, "pure_id");
            String uuid = str(rs, "uuid");
            Long childId = nullableLong(rs, "child_pure_id");
            inspectName(items, ruleCode, outputLocalId, pureId, uuid, childId, "first_name",
                    str(rs, "first_name"), "CONTRIBUTOR_FIRST");
            inspectName(items, ruleCode, outputLocalId, pureId, uuid, childId, "last_name",
                    str(rs, "last_name"), "CONTRIBUTOR_LAST");
            return null;
        }, params(outputId));
        return items;
    }

    private void inspectName(
            List<DataQualityCandidate> items,
            String ruleCode,
            long outputId,
            Long pureId,
            String uuid,
            Long childId,
            String field,
            String value,
            String patchField
    ) {
        if (value == null) {
            return;
        }
        for (DataQualityCandidate candidate : textFindings(ruleCode, "RESEARCH_OUTPUT", outputId, pureId, uuid,
                field, value, 1, false)) {
            ObjectNode evidence = parseEvidence(candidate.evidenceJson);
            evidence.put("childPureId", childId == null ? 0 : childId);
            evidence.put("field", patchField);
            evidence.put("contextKey", field + ":" + childId + ":" + candidate.issueKind);
            candidate.evidenceJson = evidence.toString();
            candidate.findingKey = ruleCode + "|RESEARCH_OUTPUT|" + outputId + "|" + field + "|"
                    + childId + ":" + candidate.issueKind;
            items.add(candidate);
        }
    }

    private List<DataQualityCandidate> scanAuthorDuplicates(Long outputId) {
        return scanContributorDuplicates("PURE-012", true, outputId);
    }

    private List<DataQualityCandidate> scanContributorDuplicates(String ruleCode, boolean authors, Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, ANY_VALUE(o.title) AS title,
                       COALESCE(c.first_name, '') AS first_name,
                       COALESCE(c.last_name, '') AS last_name,
                       COALESCE(c.person_uuid, '') AS person_uuid,
                       COALESCE(c.external_person_uuid, '') AS external_person_uuid,
                       COUNT(*) AS dup_count,
                       GROUP_CONCAT(c.pure_id ORDER BY c.pure_id) AS child_ids
                FROM %s o
                JOIN %s c ON c.research_output_id = o.id
                WHERE %s
                  AND %s
                GROUP BY o.id, o.uuid, o.pure_id,
                         COALESCE(c.person_uuid, ''), COALESCE(c.external_person_uuid, ''),
                         COALESCE(c.first_name, ''), COALESCE(c.last_name, '')
                HAVING COUNT(*) > 1
                """.formatted(
                q("pure_research_output"), q("pure_research_output_contributor"),
                active("pure_research_output", "o") + idClause("o", outputId),
                authors ? authorPredicate("c") : " NOT " + authorPredicate("c")
        );
        return repository.select(sql, rs -> {
            String personUuid = str(rs, "person_uuid");
            String externalUuid = str(rs, "external_person_uuid");
            boolean exact = notBlank(personUuid) || notBlank(externalUuid);
            String childIds = str(rs, "child_ids");
            String[] parts = childIds == null ? new String[0] : childIds.split(",");
            ObjectNode evidence = evidence(exact ? "EXACT_DUPLICATE" : "AMBIGUOUS_DUPLICATE");
            ArrayNode remove = evidence.putArray("removeChildPureIds");
            Long keep = null;
            for (int i = 0; i < parts.length; i++) {
                long id = Long.parseLong(parts[i].trim());
                if (i == 0) {
                    keep = id;
                    evidence.put("keepChildPureId", id);
                } else {
                    remove.add(id);
                }
            }
            evidence.put("field", "REMOVE_CONTRIBUTOR");
            evidence.put("contextKey", "dup:" + childIds);
            String name = first(str(rs, "last_name"), "") + ", " + first(str(rs, "first_name"), "");
            return candidate(ruleCode, rs, "authors", exact ? "EXACT_DUPLICATE" : "AMBIGUOUS_DUPLICATE",
                    exact ? DataQualityCatalog.MODE_SAFE : DataQualityCatalog.MODE_MANUAL,
                    exact ? "Autores duplicados exactos en la publicación"
                            : "Posibles autores duplicados: requiere revisión manual",
                    name.trim(), keep == null ? null : String.valueOf(keep), evidence);
        }, params(outputId));
    }

    private List<DataQualityCandidate> scanOrcid(Long personId) {
        String sql = """
                SELECT p.id, p.uuid, p.pure_id, p.first_name, p.last_name, p.orcid
                FROM %s p
                WHERE %s
                  AND p.orcid IS NOT NULL AND TRIM(p.orcid) <> ''
                """.formatted(q("pure_person"), active("pure_person", "p") + idClause("p", personId));
        List<DataQualityCandidate> items = new ArrayList<>();
        repository.select(sql, rs -> {
            String orcid = str(rs, "orcid");
            if (!DataQualityIdentifiers.isValidOrcid(orcid)) {
                ObjectNode evidence = evidence("INVALID_FORMAT");
                evidence.put("field", "ORCID");
                items.add(personCandidate("PURE-013", rs, "orcid", "INVALID_FORMAT",
                        DataQualityCatalog.MODE_MANUAL, "ORCID con formato inválido", orcid, null, evidence));
            }
            return null;
        }, params(personId));
        return items;
    }

    private List<DataQualityCandidate> scanMultipleScopus(Long personId) {
        if (!repository.tableExistsPublic("pure_person_identifiers")) {
            return List.of();
        }
        String typeColumn = repository.columnExistsPublic("pure_person_identifiers", "identifier_typeDiscriminator")
                ? " OR LOWER(COALESCE(i.identifier_typeDiscriminator,'')) LIKE '%scopus%'"
                : "";
        String sql = """
                SELECT p.id, p.uuid, p.pure_id, p.first_name, p.last_name,
                       COUNT(DISTINCT TRIM(i.identifier_value)) AS scopus_count,
                       GROUP_CONCAT(DISTINCT TRIM(i.identifier_value) ORDER BY TRIM(i.identifier_value)) AS scopus_values
                FROM %s p
                JOIN %s i ON i.person_id = p.id
                WHERE %s
                  AND i.identifier_value IS NOT NULL
                  AND TRIM(i.identifier_value) <> ''
                  AND (LOWER(COALESCE(i.identifier_type,'')) LIKE '%%scopus%%'
                       %s)
                GROUP BY p.id, p.uuid, p.pure_id, p.first_name, p.last_name
                HAVING COUNT(DISTINCT TRIM(i.identifier_value)) >= 2
                """.formatted(
                q("pure_person"), q("pure_person_identifiers"),
                active("pure_person", "p") + idClause("p", personId),
                typeColumn
        );
        return repository.select(sql, rs -> {
            ObjectNode evidence = evidence("MULTIPLE_SCOPUS");
            evidence.put("values", n(str(rs, "scopus_values")));
            return personCandidate("PURE-014", rs, "scopus_author_id", "MULTIPLE_SCOPUS",
                    DataQualityCatalog.MODE_VIEW,
                    "Revisión de identidad: múltiples Scopus Author ID",
                    str(rs, "scopus_values"), null, evidence);
        }, params(personId));
    }

    private List<DataQualityCandidate> scanDois(Long outputId) {
        if (!repository.tableExistsPublic("pure_research_output_electronic_version")) {
            return List.of();
        }
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title, ev.pure_id AS child_pure_id, ev.doi
                FROM %s o
                JOIN %s ev ON ev.research_output_id = o.id
                WHERE %s
                  AND ev.doi IS NOT NULL AND TRIM(ev.doi) <> ''
                """.formatted(
                q("pure_research_output"), q("pure_research_output_electronic_version"),
                active("pure_research_output", "o") + idClause("o", outputId)
        );
        List<DataQualityCandidate> items = new ArrayList<>();
        repository.select(sql, rs -> {
            String doi = str(rs, "doi");
            if (!DataQualityIdentifiers.isValidDoi(doi)) {
                ObjectNode evidence = evidence("INVALID_FORMAT");
                evidence.put("field", "DOI");
                evidence.put("childPureId", rs.getLong("child_pure_id"));
                evidence.put("contextKey", "doi:" + rs.getLong("child_pure_id"));
                items.add(candidate("PURE-017", rs, "doi", "INVALID_FORMAT", DataQualityCatalog.MODE_MANUAL,
                        "DOI con formato inválido", doi, null, evidence));
            }
            return null;
        }, params(outputId));
        return items;
    }

    private List<DataQualityCandidate> scanAbstracts(Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title, o.abstract_es, o.abstract_en
                FROM %s o
                WHERE %s
                  AND (
                    (o.abstract_es IS NOT NULL AND TRIM(o.abstract_es) <> '')
                    OR (o.abstract_en IS NOT NULL AND TRIM(o.abstract_en) <> '')
                  )
                """.formatted(q("pure_research_output"), active("pure_research_output", "o") + idClause("o", outputId));
        List<DataQualityCandidate> items = new ArrayList<>();
        repository.select(sql, rs -> {
            addAbstract(items, rs, "abstract_es", "es_CL", str(rs, "abstract_es"));
            addAbstract(items, rs, "abstract_en", "en_US", str(rs, "abstract_en"));
            return null;
        }, params(outputId));
        return items;
    }

    private void addAbstract(List<DataQualityCandidate> items, ResultSet rs, String field, String locale, String value)
            throws SQLException {
        if (blank(value)) {
            return;
        }
        for (DataQualityCandidate candidate : textFindings("PURE-018", "RESEARCH_OUTPUT",
                rs.getLong("id"), nullableLong(rs, "pure_id"), str(rs, "uuid"),
                field, value, 40, true)) {
            ObjectNode evidence = parseEvidence(candidate.evidenceJson);
            evidence.put("field", "ABSTRACT");
            evidence.put("locale", locale);
            evidence.put("contextKey", field + ":" + candidate.issueKind);
            candidate.evidenceJson = evidence.toString();
            candidate.findingKey = "PURE-018|RESEARCH_OUTPUT|" + rs.getLong("id") + "|" + field + "|"
                    + candidate.issueKind;
            items.add(candidate);
        }
    }

    private List<DataQualityCandidate> scanPageCount(Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title, o.number_of_pages, o.pages
                FROM %s o
                WHERE %s
                  AND (
                    o.number_of_pages IS NOT NULL
                    OR (o.pages IS NOT NULL AND TRIM(o.pages) <> '' AND o.pages NOT LIKE '%%-%%')
                  )
                """.formatted(q("pure_research_output"), active("pure_research_output", "o") + idClause("o", outputId));
        List<DataQualityCandidate> items = new ArrayList<>();
        repository.select(sql, rs -> {
            Integer pages = integer(rs, "number_of_pages");
            if (pages != null && pages < 1) {
                ObjectNode evidence = evidence("INVALID_COUNT");
                evidence.put("field", "NUMBER_OF_PAGES");
                items.add(candidate("PURE-019", rs, "number_of_pages", "INVALID_COUNT",
                        DataQualityCatalog.MODE_MANUAL, "Número de páginas no interpretable",
                        String.valueOf(pages), null, evidence));
            }
            String pagesText = str(rs, "pages");
            if (pagesText != null && !pagesText.contains("-") && !pagesText.contains("–")
                    && !DataQualityIdentifiers.looksLikeArticleNumber(pagesText)
                    && !DataQualityIdentifiers.isInterpretablePageCount(pagesText)
                    && pagesText.matches(".*[A-Za-z].*")) {
                ObjectNode evidence = evidence("INVALID_COUNT");
                evidence.put("field", "PAGES");
                items.add(candidate("PURE-019", rs, "pages", "INVALID_COUNT",
                        DataQualityCatalog.MODE_MANUAL, "Número de páginas no interpretable",
                        pagesText, null, evidence));
            }
            return null;
        }, params(outputId));
        return items;
    }

    private List<DataQualityCandidate> scanPageRange(Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title, o.pages
                FROM %s o
                WHERE %s
                  AND o.pages IS NOT NULL AND TRIM(o.pages) <> ''
                  AND (o.pages LIKE '%%-%%' OR o.pages LIKE '%%–%%')
                """.formatted(q("pure_research_output"), active("pure_research_output", "o") + idClause("o", outputId));
        List<DataQualityCandidate> items = new ArrayList<>();
        repository.select(sql, rs -> {
            String pages = str(rs, "pages");
            var range = DataQualityIdentifiers.parsePageRange(pages);
            if (range != null && !range.articleNumber() && range.start() != null && range.end() != null
                    && range.end() < range.start()) {
                ObjectNode evidence = evidence("END_BEFORE_START");
                evidence.put("field", "PAGES");
                evidence.put("start", range.start());
                evidence.put("end", range.end());
                items.add(candidate("PURE-020", rs, "pages", "END_BEFORE_START",
                        DataQualityCatalog.MODE_MANUAL, "Página final menor que la inicial",
                        pages, null, evidence));
            }
            return null;
        }, params(outputId));
        return items;
    }

    private List<DataQualityCandidate> scanEmbargo(Long outputId) {
        if (!repository.tableExistsPublic("pure_research_output_electronic_version")) {
            return List.of();
        }
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title,
                       ev.pure_id AS child_pure_id, ev.access_type_es, ev.access_type_en, ev.access_type_uri
                FROM %s o
                JOIN %s ev ON ev.research_output_id = o.id
                WHERE %s
                  AND (
                    LOWER(COALESCE(ev.access_type_es,'')) LIKE '%%embargo%%'
                    OR LOWER(COALESCE(ev.access_type_en,'')) LIKE '%%embargo%%'
                    OR LOWER(COALESCE(ev.access_type_uri,'')) LIKE '%%embargo%%'
                  )
                """.formatted(
                q("pure_research_output"), q("pure_research_output_electronic_version"),
                active("pure_research_output", "o") + idClause("o", outputId)
        );
        return repository.select(sql, rs -> {
            ObjectNode evidence = evidence("EMBARGO_PERIOD_NOT_IN_REPLICA");
            evidence.put("childPureId", rs.getLong("child_pure_id"));
            evidence.put("accessType", first(str(rs, "access_type_es"), str(rs, "access_type_en")));
            evidence.put("contextKey", "embargo:" + rs.getLong("child_pure_id"));
            return candidate("PURE-021", rs, "embargoPeriod", "EMBARGO_PERIOD_NOT_IN_REPLICA",
                    DataQualityCatalog.MODE_VIEW,
                    "Archivo en embargo: el período no está en la réplica local; revíselo en PURE",
                    first(str(rs, "access_type_es"), str(rs, "access_type_en")), null, evidence);
        }, params(outputId));
    }

    private List<DataQualityCandidate> scanWorkflow(Long outputId) {
        String sql = """
                SELECT o.id, o.uuid, o.pure_id, o.title, o.workflow_step, o.workflow_es, o.workflow_en
                FROM %s o
                WHERE %s
                  AND (
                    LOWER(COALESCE(o.workflow_step,'')) IN ('forapproval','for_approval')
                    OR LOWER(COALESCE(o.workflow_es,'')) LIKE '%%para su aprobación%%'
                    OR LOWER(COALESCE(o.workflow_en,'')) LIKE '%%for approval%%'
                  )
                """.formatted(q("pure_research_output"), active("pure_research_output", "o") + idClause("o", outputId));
        return repository.select(sql, rs -> {
            ObjectNode evidence = evidence("FOR_APPROVAL");
            evidence.put("workflowStep", n(str(rs, "workflow_step")));
            return candidate("PURE-022", rs, "workflow", "FOR_APPROVAL", DataQualityCatalog.MODE_VIEW,
                    "Publicación en workflow para su aprobación",
                    first(str(rs, "workflow_es"), str(rs, "workflow_en"), str(rs, "workflow_step")),
                    null, evidence);
        }, params(outputId));
    }

    private List<DataQualityCandidate> textFindings(
            String ruleCode,
            String entityType,
            long localId,
            Long pureId,
            String uuid,
            String field,
            String value,
            int minLength,
            boolean allowEmpty
    ) {
        List<DataQualityCandidate> items = new ArrayList<>();
        if (allowEmpty && DataQualityText.isBlank(value)) {
            items.add(DataQualityCandidate.of(ruleCode, entityType, localId, pureId, uuid, field, "EMPTY",
                    DataQualityCatalog.MODE_MANUAL, emptySummary(ruleCode), null, null, evidence("EMPTY")));
            return items;
        }
        if (DataQualityText.isBlank(value)) {
            return items;
        }
        if (DataQualityText.hasRepeatableWhitespace(value)) {
            ObjectNode evidence = evidence("WHITESPACE");
            evidence.put("field", patchFieldFor(field));
            items.add(DataQualityCandidate.of(ruleCode, entityType, localId, pureId, uuid, field, "WHITESPACE",
                    DataQualityCatalog.MODE_SAFE, whitespaceSummary(ruleCode),
                    value, DataQualityText.collapseWhitespace(value), evidence));
        }
        if (DataQualityText.hasNamedOrNumericEntity(value) && !DataQualityText.looksLikeHtml(value)) {
            String decoded = DataQualityText.decodeUnequivocalEntities(value);
            ObjectNode evidence = evidence("HTML_ENTITY");
            evidence.put("field", patchFieldFor(field));
            items.add(DataQualityCandidate.of(ruleCode, entityType, localId, pureId, uuid, field, "HTML_ENTITY",
                    decoded == null ? DataQualityCatalog.MODE_MANUAL : DataQualityCatalog.MODE_SAFE,
                    "El texto contiene entidades HTML sin decodificar",
                    value, decoded, evidence));
        }
        if (DataQualityText.looksLikeHtml(value) || DataQualityText.looksLikeCodeArtifact(value)) {
            ObjectNode evidence = evidence("HTML_OR_ARTIFACT");
            items.add(DataQualityCandidate.of(ruleCode, entityType, localId, pureId, uuid, field, "HTML_OR_ARTIFACT",
                    DataQualityCatalog.MODE_MANUAL, "El texto contiene HTML o artefactos de código",
                    value, null, evidence));
        }
        if (DataQualityText.looksLikeMojibake(value)) {
            String repaired = DataQualityText.repairMojibakeIfReversible(value);
            ObjectNode evidence = evidence("MOJIBAKE");
            evidence.put("field", patchFieldFor(field));
            items.add(DataQualityCandidate.of(ruleCode, entityType, localId, pureId, uuid, field, "MOJIBAKE",
                    DataQualityCatalog.MODE_MANUAL, "Posible texto corrupto / mojibake",
                    value, repaired, evidence));
        }
        if (DataQualityText.looksTruncated(value)) {
            items.add(DataQualityCandidate.of(ruleCode, entityType, localId, pureId, uuid, field, "TRUNCATED",
                    DataQualityCatalog.MODE_MANUAL, "Posible truncamiento de texto",
                    value, null, evidence("TRUNCATED")));
        }
        if ("PURE-018".equals(ruleCode) && DataQualityText.isExtremelyShort(value, minLength)
                && items.stream().noneMatch(item -> "EMPTY".equals(item.issueKind))) {
            items.add(DataQualityCandidate.of(ruleCode, entityType, localId, pureId, uuid, field, "TOO_SHORT",
                    DataQualityCatalog.MODE_MANUAL, "El resumen es extremadamente corto",
                    value, null, evidence("TOO_SHORT")));
        }
        if ("PURE-002".equals(ruleCode) && DataQualityText.hasSuspiciousScientificTitleChar(value)) {
            items.add(DataQualityCandidate.of(ruleCode, entityType, localId, pureId, uuid, field, "SUSPICIOUS_CHAR",
                    DataQualityCatalog.MODE_MANUAL, "El título contiene un carácter sospechoso",
                    value, null, evidence("SUSPICIOUS_CHAR")));
        }
        return items;
    }

    private DataQualityCandidate candidate(
            String ruleCode,
            ResultSet rs,
            String field,
            String kind,
            String mode,
            String summary,
            String current,
            String suggested,
            ObjectNode evidence
    ) throws SQLException {
        return DataQualityCandidate.of(
                ruleCode, "RESEARCH_OUTPUT", rs.getLong("id"), nullableLong(rs, "pure_id"),
                str(rs, "uuid"), field, kind, mode, summary, current, suggested, evidence
        );
    }

    private DataQualityCandidate candidate(
            String ruleCode,
            StatusRow row,
            String field,
            String kind,
            String mode,
            String summary,
            String current,
            String suggested,
            ObjectNode evidence
    ) {
        return DataQualityCandidate.of(
                ruleCode, "RESEARCH_OUTPUT", row.id, row.pureId, row.uuid, field, kind, mode,
                summary, current, suggested, evidence
        );
    }

    private DataQualityCandidate personCandidate(
            String ruleCode,
            ResultSet rs,
            String field,
            String kind,
            String mode,
            String summary,
            String current,
            String suggested,
            ObjectNode evidence
    ) throws SQLException {
        return DataQualityCandidate.of(
                ruleCode, "PERSON", rs.getLong("id"), nullableLong(rs, "pure_id"),
                str(rs, "uuid"), field, kind, mode, summary, current, suggested, evidence
        );
    }

    private ObjectNode evidence(String kind) {
        ObjectNode node = mapper.createObjectNode();
        node.put("issueKind", kind);
        node.put("contextKey", kind);
        return node;
    }

    private ObjectNode parseEvidence(String json) {
        try {
            if (json == null || json.isBlank()) {
                return mapper.createObjectNode();
            }
            return (ObjectNode) mapper.readTree(json);
        } catch (Exception ex) {
            return mapper.createObjectNode();
        }
    }

    private OutputText outputText(ResultSet rs) throws SQLException {
        OutputText row = new OutputText();
        row.id = rs.getLong("id");
        row.uuid = str(rs, "uuid");
        row.pureId = nullableLong(rs, "pure_id");
        row.title = str(rs, "title");
        return row;
    }

    private StatusRow statusRow(ResultSet rs) throws SQLException {
        StatusRow row = new StatusRow();
        row.id = rs.getLong("id");
        row.uuid = str(rs, "uuid");
        row.pureId = nullableLong(rs, "pure_id");
        row.statusPureId = nullableLong(rs, "status_pure_id");
        row.statusUri = str(rs, "status_uri");
        row.statusEs = str(rs, "status_es");
        row.statusEn = str(rs, "status_en");
        try {
            row.year = integer(rs, "publication_year");
        } catch (SQLException ignored) {
            row.year = null;
        }
        return row;
    }

    private String q(String table) {
        return repository.qualifiedPublic(table);
    }

    private String active(String table, String alias) {
        return repository.activePredicatePublic(table, alias);
    }

    private static String idClause(String alias, Long id) {
        return id == null ? "" : " AND " + alias + ".id = ?";
    }

    private static Object[] params(Long id) {
        return id == null ? new Object[0] : new Object[]{id};
    }

    private static void addId(List<Object> params, Long id) {
        if (id != null) {
            params.add(id);
        }
    }

    private static String authorPredicate(String alias) {
        return "(LOWER(COALESCE(" + alias + ".type_discriminator,'')) LIKE '%author%' "
                + "OR LOWER(COALESCE(" + alias + ".role_en,'')) LIKE '%author%' "
                + "OR LOWER(COALESCE(" + alias + ".role_es,'')) LIKE '%autor%')";
    }

    private static String publishedPredicate(String alias) {
        return "(" + statusLike(alias, "published", "publicada") + ")";
    }

    private static String acceptedPredicate(String alias) {
        return "(" + statusLike(alias, "inpress", "aceptada")
                + " OR " + statusLike(alias, "accepted", "en prensa") + ")";
    }

    private static String epubPredicate(String alias) {
        return "(" + statusLike(alias, "epub", "electrón")
                + " OR LOWER(COALESCE(" + alias + ".status_en,'')) LIKE '%e-pub%')";
    }

    private static String statusLike(String alias, String uriToken, String esToken) {
        return "LOWER(COALESCE(" + alias + ".status_uri,'')) LIKE '%" + uriToken + "%'"
                + " OR LOWER(COALESCE(" + alias + ".status_es,'')) LIKE '%" + esToken + "%'"
                + " OR LOWER(COALESCE(" + alias + ".status_en,'')) LIKE '%" + uriToken + "%'";
    }

    private static boolean knownStatus(String uri, String es, String en) {
        String haystack = (n(uri) + " " + n(es) + " " + n(en)).toLowerCase(Locale.ROOT);
        return haystack.contains("published") || haystack.contains("publicada")
                || haystack.contains("inpress") || haystack.contains("aceptada")
                || haystack.contains("accepted") || haystack.contains("epub")
                || haystack.contains("electrón") || haystack.contains("submitted")
                || haystack.contains("enviada") || haystack.contains("unpublished")
                || haystack.contains("no publicada") || haystack.contains("inpreparation")
                || haystack.contains("preparación") || haystack.contains("in preparation");
    }

    private static boolean isPublished(String uri, String es, String en) {
        String haystack = (n(uri) + " " + n(es) + " " + n(en)).toLowerCase(Locale.ROOT);
        return haystack.contains("published") || haystack.contains("publicada");
    }

    private static String emptySummary(String ruleCode) {
        return "PURE-002".equals(ruleCode) ? "El título está vacío" : "El campo está vacío";
    }

    private static String whitespaceSummary(String ruleCode) {
        if ("PURE-002".equals(ruleCode)) {
            return "El título contiene espacios dobles";
        }
        if ("PURE-018".equals(ruleCode)) {
            return "El resumen contiene espacios dobles";
        }
        return "El texto contiene espacios dobles";
    }

    private static String patchFieldFor(String field) {
        if ("title".equals(field)) {
            return "TITLE";
        }
        if (field != null && field.startsWith("abstract")) {
            return "ABSTRACT";
        }
        return field;
    }

    private static String str(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null || value.isBlank() ? null : value;
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean notBlank(String value) {
        return !blank(value);
    }

    private static String first(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String n(String value) {
        return value == null ? "" : value;
    }

    public static final class ScanResult {
        public int evaluated;
        public int findings;
        public int resolved;
        public int errors;
        public int successfulRules;
        private final List<String> failures = new ArrayList<>();

        public void recordFailure(String ruleCode, Throwable error) {
            failures.add(ruleCode + ": " + ExceptionDetail.describe(error));
        }

        public String failureDetail() {
            if (failures.isEmpty()) {
                return null;
            }
            return String.join(" · ", failures);
        }
    }

    @FunctionalInterface
    private interface RuleScan {
        List<DataQualityCandidate> scan() throws Exception;
    }

    private static final class OutputText {
        long id;
        String uuid;
        Long pureId;
        String title;
    }

    private static final class StatusRow {
        long id;
        String uuid;
        Long pureId;
        Long statusPureId;
        String statusUri;
        String statusEs;
        String statusEn;
        Integer year;
    }
}
