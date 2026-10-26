package cl.uss.ca.service;

import cl.uss.ca.api.dto.DataQualityApplyItemDto;
import cl.uss.ca.api.dto.DataQualityApplyRequestDto;
import cl.uss.ca.api.dto.DataQualityApplyResultDto;
import cl.uss.ca.api.dto.DataQualityExecutionDto;
import cl.uss.ca.api.dto.DataQualityFindingDto;
import cl.uss.ca.api.dto.DataQualityRuleDto;
import cl.uss.ca.api.dto.DataQualityStatsDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.api.dto.PureCorrectionResultDto;
import cl.uss.ca.auth.AppModule;
import cl.uss.ca.auth.AppPermission;
import cl.uss.ca.exception.ConflictException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.quality.DataQualityCatalog;
import cl.uss.ca.quality.DataQualityIdentifiers;
import cl.uss.ca.quality.DataQualityPatch;
import cl.uss.ca.quality.DataQualityText;
import cl.uss.ca.repository.DataQualityRepository;
import cl.uss.ca.repository.DataQualityRepository.FindingQuery;
import cl.uss.ca.security.CurrentUser;
import cl.uss.ca.security.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class DataQualityService {
    private static final Logger LOG = Logger.getLogger(DataQualityService.class.getName());

    @Resource
    ManagedExecutorService executor;

    @Inject
    DataQualityRepository repository;

    @Inject
    DataQualityJob job;

    @Inject
    PureCorrectionService correctionService;

    @Inject
    ExcelExportService excelExportService;

    @Inject
    CurrentUserService currentUserService;

    private final ObjectMapper mapper = new ObjectMapper();

    public DataQualityStatsDto stats(FindingQuery query) {
        assertAvailable();
        repository.ensureCatalog();
        return repository.stats(query);
    }

    public PageResponse<DataQualityFindingDto> findings(FindingQuery query) {
        assertAvailable();
        return repository.listFindings(query);
    }

    public DataQualityFindingDto finding(long id) {
        assertAvailable();
        return repository.requireFinding(id);
    }

    public List<DataQualityRuleDto> rules() {
        assertAvailable();
        repository.ensureCatalog();
        return repository.listRules();
    }

    public List<DataQualityExecutionDto> history() {
        assertAvailable();
        return repository.history(20);
    }

    public DataQualityExecutionDto latest() {
        assertAvailable();
        return repository.findLatest();
    }

    public DataQualityExecutionDto getExecution(long id) {
        assertAvailable();
        return repository.findExecution(id);
    }

    public DataQualityExecutionDto startManual() {
        return start("MANUAL", "FULL", null, null, null, null);
    }

    public DataQualityExecutionDto startRule(String ruleCode) {
        DataQualityCatalog.RuleDef rule = requireRule(ruleCode);
        return start("MANUAL", "RULE", rule.entityType(), null, null, rule.code());
    }

    public void startAfterSuccessfulSync(String entity, String syncId, String startedBy) {
        if (!"PEOPLE".equals(entity) && !"RESEARCH_OUTPUT".equals(entity)
                && !"CROSSREF".equals(entity) && !"OPENALEX".equals(entity)
                && !"UNPAYWALL".equals(entity) && !"ORCID".equals(entity)
                && !"DSPACE".equals(entity)) {
            return;
        }
        try {
            if (!repository.available() || repository.hasRunning()) {
                return;
            }
            repository.ensureCatalog();
            Long syncExecutionId = parseSyncId(syncId);
            int total = repository.countOutputs() + repository.countPeople();
            long executionId = repository.createExecution(
                    "AFTER_SYNC", "FULL", null, null, syncExecutionId, startedBy, total
            );
            executor.execute(() -> job.run(executionId, null, null));
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Unable to start data-quality validation after sync entity=" + entity, ex);
        }
    }

    public DataQualityExecutionDto startIncremental(String entityType, long localRecordId, String triggerType) {
        return start(triggerType, "RECORD", entityType, localRecordId, null, null);
    }

    public DataQualityApplyResultDto apply(long findingId, DataQualityApplyRequestDto body) {
        assertCanCorrect();
        DataQualityFindingDto finding = repository.requireFinding(findingId);
        DataQualityApplyItemDto item = applyOne(finding, body == null ? null : body.newValue,
                body != null && Boolean.TRUE.equals(body.applySuggestion));
        DataQualityApplyResultDto result = new DataQualityApplyResultDto();
        result.items.add(item);
        tally(result, item);
        return result;
    }

    public DataQualityApplyResultDto applyBulk(List<Long> findingIds) {
        assertCanCorrect();
        if (findingIds == null || findingIds.isEmpty()) {
            throw new ValidationException("QUALITY_BULK_EMPTY", "Select at least one finding.");
        }
        List<DataQualityFindingDto> findings = new ArrayList<>();
        for (Long id : findingIds) {
            findings.add(repository.requireFinding(id));
        }
        assertCompatibleBulk(findings);
        DataQualityApplyResultDto result = new DataQualityApplyResultDto();
        for (DataQualityFindingDto finding : findings) {
            DataQualityApplyItemDto item = applyOne(finding, null, true);
            result.items.add(item);
            tally(result, item);
        }
        return result;
    }

    public DataQualityFindingDto markNotApplicable(long findingId, String justification) {
        assertCanCorrect();
        if (justification == null || justification.isBlank()) {
            throw new ValidationException("QUALITY_JUSTIFICATION_REQUIRED", "A justification is required.");
        }
        repository.markNotApplicable(findingId, currentUserService.current().id, justification.trim());
        return repository.requireFinding(findingId);
    }

    public DataQualityFindingDto markInReview(long findingId) {
        assertCanCorrect();
        repository.markInReview(findingId, currentUserService.current().id);
        return repository.requireFinding(findingId);
    }

    public PureCorrectionResultDto retryLocal(long findingId) {
        assertCanCorrect();
        DataQualityFindingDto finding = repository.requireFinding(findingId);
        String entity = "PERSON".equals(finding.entityType) ? "PEOPLE" : "RESEARCH_OUTPUT";
        PureCorrectionResultDto dto = correctionService.reconcileLocalRecord(entity, finding.localRecordId);
        revalidate(finding);
        return dto;
    }

    public Response export(FindingQuery query) {
        assertAvailable();
        List<DataQualityFindingDto> rows = repository.exportFindings(query);
        if (rows.isEmpty()) {
            throw new ValidationException("EXPORT_EMPTY", "There are no findings to export.");
        }
        return excelExportService.exportDataQuality(rows);
    }

    private DataQualityExecutionDto start(
            String triggerType,
            String scope,
            String entityType,
            Long localRecordId,
            Long syncExecutionId,
            String onlyRule
    ) {
        assertCanRun();
        assertAvailable();
        repository.ensureCatalog();
        if (repository.hasRunning()) {
            throw new ConflictException("DATA_QUALITY_RUNNING", "A data-quality validation is already running");
        }
        int total = totalRecords(scope, entityType);
        long executionId = repository.createExecution(
                triggerType, scope, entityType, localRecordId, syncExecutionId,
                currentUserService.current().id, total
        );
        String scanEntity = "RECORD".equals(scope) ? entityType : null;
        Long scanRecord = "RECORD".equals(scope) ? localRecordId : null;
        executor.execute(() -> job.run(executionId, scanEntity, scanRecord, onlyRule));
        return repository.findExecution(executionId);
    }

    private int totalRecords(String scope, String entityType) {
        if ("RECORD".equals(scope)) {
            return 1;
        }
        if ("PERSON".equals(entityType)) {
            return repository.countPeople();
        }
        if ("RESEARCH_OUTPUT".equals(entityType)) {
            return repository.countOutputs();
        }
        return repository.countOutputs() + repository.countPeople();
    }

    private static DataQualityCatalog.RuleDef requireRule(String ruleCode) {
        String code = ruleCode == null ? "" : ruleCode.trim();
        if (code.isEmpty()) {
            throw new ValidationException("DATA_QUALITY_RULE_REQUIRED", "A rule code is required.");
        }
        try {
            return DataQualityCatalog.require(code);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("DATA_QUALITY_RULE_NOT_FOUND", "Unknown data-quality rule: " + code);
        }
    }

    private DataQualityApplyItemDto applyOne(DataQualityFindingDto finding, String providedValue, boolean applySuggestion) {
        DataQualityApplyItemDto item = new DataQualityApplyItemDto();
        item.findingId = finding.id;
        item.ruleCode = finding.ruleCode;
        item.recordLabel = finding.recordLabel;
        item.currentValue = finding.currentValue;
        try {
            if ("VIEW_ONLY".equals(finding.correctionMode) && !"EXACT_DUPLICATE".equals(finding.issueKind)) {
                throw new ValidationException("QUALITY_VIEW_ONLY", "This finding cannot be corrected from this screen.");
            }
            String newValue = resolveNewValue(finding, providedValue, applySuggestion);
            validateNewValue(finding, newValue);
            DataQualityPatch patch = toPatch(finding, newValue);
            PureCorrectionResultDto remote = correctionService.applyDataQuality(patch);
            item.status = remote.status;
            item.message = remote.message;
            item.suggestedValue = newValue;
            if (PureCorrectionService.STATUS_ORCID_UPDATED.equals(remote.status)
                    || "UPDATED".equals(remote.status)
                    || PureCorrectionService.STATUS_REMOVED.equals(remote.status)) {
                revalidate(finding);
            } else if (PureCorrectionService.STATUS_LOCAL_FAILED.equals(remote.status)) {
                item.message = "PURE fue actualizado, pero la copia local quedó pendiente.";
            } else if (PureCorrectionService.STATUS_REMOTE_CHANGED.equals(remote.status)) {
                item.message = "El registro cambió en PURE. Revise nuevamente.";
            }
            return item;
        } catch (ConflictException | ValidationException ex) {
            item.status = ex instanceof ConflictException
                    ? ((ConflictException) ex).getCode()
                    : ((ValidationException) ex).getCode();
            item.message = ex.getMessage();
            return item;
        } catch (RuntimeException ex) {
            item.status = "FAILED";
            item.message = ex.getMessage();
            return item;
        }
    }

    private void revalidate(DataQualityFindingDto finding) {
        try {
            startIncremental(finding.entityType, finding.localRecordId, "AFTER_CORRECTION");
        } catch (ConflictException ex) {
            LOG.log(Level.INFO, "Incremental validation skipped because another run is active", ex);
        }
    }

    private String resolveNewValue(DataQualityFindingDto finding, String providedValue, boolean applySuggestion) {
        if ("EXACT_DUPLICATE".equals(finding.issueKind)) {
            return finding.suggestedValue;
        }
        if (providedValue != null && !providedValue.isBlank()) {
            return providedValue;
        }
        if (applySuggestion && finding.suggestedValue != null) {
            return finding.suggestedValue;
        }
        throw new ValidationException("QUALITY_VALUE_REQUIRED", "A new value is required.");
    }

    private void validateNewValue(DataQualityFindingDto finding, String newValue) {
        if ("EXACT_DUPLICATE".equals(finding.issueKind)) {
            return;
        }
        if (newValue == null) {
            throw new ValidationException("QUALITY_VALUE_REQUIRED", "A new value is required.");
        }
        switch (finding.ruleCode) {
            case "PURE-013" -> {
                if (!DataQualityIdentifiers.isValidOrcid(newValue)) {
                    throw new ValidationException("ORCID_INVALID", "The ORCID format is invalid.");
                }
            }
            case "PURE-017" -> {
                if (!DataQualityIdentifiers.isValidDoi(newValue)) {
                    throw new ValidationException("DOI_INVALID", "The DOI format is invalid.");
                }
            }
            case "PURE-003", "PURE-005" -> {
                Integer year = DataQualityIdentifiers.parsePositiveInt(newValue);
                if (!DataQualityIdentifiers.yearInRange(year, Year.now().getValue())) {
                    throw new ValidationException("YEAR_INVALID", "The year must be between 1900 and the current year.");
                }
            }
            case "PURE-019" -> {
                if (!DataQualityIdentifiers.isInterpretablePageCount(newValue)) {
                    throw new ValidationException("PAGES_INVALID", "The page count is not interpretable.");
                }
            }
            case "PURE-002", "PURE-011", "PURE-015", "PURE-018" -> {
                if (DataQualityText.isBlank(newValue)) {
                    throw new ValidationException("TEXT_EMPTY", "The value cannot be empty.");
                }
            }
            default -> {
            }
        }
    }

    private DataQualityPatch toPatch(DataQualityFindingDto finding, String newValue) {
        JsonNode evidence = readEvidence(finding.evidenceJson);
        DataQualityPatch patch = new DataQualityPatch();
        patch.entity = "PERSON".equals(finding.entityType) ? "PEOPLE" : "RESEARCH_OUTPUT";
        patch.recordUuid = finding.recordUuid;
        patch.localRecordId = finding.localRecordId;
        patch.ruleCode = finding.ruleCode;
        patch.findingId = finding.id;
        patch.issueKind = finding.issueKind;
        patch.expectedValue = finding.currentValue;
        patch.newValue = newValue;
        patch.locale = text(evidence, "locale");
        patch.childPureId = longValue(evidence, "childPureId");
        patch.keepChildPureId = longValue(evidence, "keepChildPureId");
        if (evidence != null && evidence.has("removeChildPureIds") && evidence.get("removeChildPureIds").isArray()) {
            evidence.get("removeChildPureIds").forEach(node -> patch.removeChildPureIds.add(node.asLong()));
        }
        String field = text(evidence, "field");
        if (field == null) {
            field = switch (finding.ruleCode) {
                case "PURE-002" -> "TITLE";
                case "PURE-003" -> "SUBMISSION_YEAR";
                case "PURE-013" -> "ORCID";
                case "PURE-017" -> "DOI";
                case "PURE-018" -> "ABSTRACT";
                case "PURE-019" -> "NUMBER_OF_PAGES";
                case "PURE-020" -> "PAGES";
                case "PURE-012", "PURE-015" -> "EXACT_DUPLICATE".equals(finding.issueKind) ? "REMOVE_CONTRIBUTOR" : "CONTRIBUTOR_FIRST";
                default -> finding.fieldName;
            };
        }
        patch.field = field;
        return patch;
    }

    private JsonNode readEvidence(String json) {
        try {
            if (json == null || json.isBlank()) {
                return mapper.createObjectNode();
            }
            return mapper.readTree(json);
        } catch (Exception ex) {
            return mapper.createObjectNode();
        }
    }

    private static void assertCompatibleBulk(List<DataQualityFindingDto> findings) {
        String rule = findings.get(0).ruleCode;
        String mode = findings.get(0).correctionMode;
        String kind = findings.get(0).issueKind;
        for (DataQualityFindingDto finding : findings) {
            if (!rule.equals(finding.ruleCode) || !mode.equals(finding.correctionMode) || !kind.equals(finding.issueKind)) {
                throw new ValidationException("QUALITY_BULK_MIXED", "Bulk actions must share the same rule and correction type.");
            }
            if (!DataQualityCatalog.MODE_SAFE.equals(finding.correctionMode) || finding.suggestedValue == null) {
                throw new ValidationException("QUALITY_BULK_UNSAFE", "Bulk apply is only available for safe suggestions.");
            }
        }
    }

    private static void tally(DataQualityApplyResultDto result, DataQualityApplyItemDto item) {
        if ("UPDATED".equals(item.status) || PureCorrectionService.STATUS_ORCID_UPDATED.equals(item.status)
                || PureCorrectionService.STATUS_REMOVED.equals(item.status)
                || PureCorrectionService.STATUS_ALREADY_REMOVED.equals(item.status)) {
            result.applied++;
        } else if (PureCorrectionService.STATUS_REMOTE_CHANGED.equals(item.status)) {
            result.remoteChanged++;
            result.reviewRequired++;
        } else if (PureCorrectionService.STATUS_LOCAL_FAILED.equals(item.status)) {
            result.localFailed++;
        } else {
            result.failed++;
        }
    }

    private void assertAvailable() {
        if (!repository.available()) {
            throw new ValidationException("DATA_QUALITY_TABLES_UNAVAILABLE", "Data quality tables are not available");
        }
    }

    private void assertCanRun() {
        assertQualityAdmin(
                "DATA_QUALITY_FORBIDDEN",
                "Running data-quality validation requires DATA_QUALITY ADMINISTER"
        );
    }

    private void assertCanCorrect() {
        assertQualityAdmin(
                "CORRECTION_FORBIDDEN",
                "Correcting PURE requires DATA_QUALITY ADMINISTER"
        );
    }

    private void assertQualityAdmin(String code, String message) {
        CurrentUser user = currentUserService.current();
        if (!user.has(AppModule.DATA_QUALITY, AppPermission.ADMINISTER)) {
            throw new ValidationException(code, message);
        }
    }

    private static Long parseSyncId(String syncId) {
        if (syncId == null || !syncId.matches("\\d{1,18}")) {
            return null;
        }
        return Long.parseLong(syncId);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static Long longValue(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        long value = node.get(field).asLong();
        return value == 0L ? null : value;
    }
}
