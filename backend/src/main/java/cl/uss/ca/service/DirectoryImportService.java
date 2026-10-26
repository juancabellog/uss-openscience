package cl.uss.ca.service;

import cl.uss.ca.api.dto.DirectoryImportResultDto;
import cl.uss.ca.api.dto.DirectoryImportSummaryDto;
import cl.uss.ca.api.dto.DirectoryIssueDto;
import cl.uss.ca.directory.DirectoryCatalog;
import cl.uss.ca.directory.DirectoryExcelParser;
import cl.uss.ca.directory.DirectoryExcelParser.DirectoryParseResult;
import cl.uss.ca.directory.DirectoryFileStore;
import cl.uss.ca.directory.DirectoryImportPlan;
import cl.uss.ca.directory.DirectoryImportPlanner;
import cl.uss.ca.directory.DirectoryImportRecoveryPolicy;
import cl.uss.ca.directory.DirectoryImportRecoveryPolicy.DirectoryImportSideEffects;
import cl.uss.ca.directory.DirectoryIncomingRow;
import cl.uss.ca.directory.DirectoryIssue;
import cl.uss.ca.directory.DirectoryPlannedRow;
import cl.uss.ca.directory.DirectoryPresencePolicy;
import cl.uss.ca.directory.DirectoryReconcileDecision;
import cl.uss.ca.directory.DirectoryReconciler;
import cl.uss.ca.directory.DirectoryRowMapper;
import cl.uss.ca.directory.DirectoryRutCrypto;
import cl.uss.ca.exception.ConflictException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.DirectoryRepository;
import cl.uss.ca.repository.DirectoryRepository.DirectoryImportCounts;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class DirectoryImportService {
    private static final Logger LOG = Logger.getLogger(DirectoryImportService.class.getName());
    private static final int ISSUE_PREVIEW = 100;
    private static final int HEARTBEAT_EVERY = 50;
    private final ReentrantLock lock = new ReentrantLock();
    private final String processToken = UUID.randomUUID().toString();
    private volatile Long ownedImportId;
    private volatile int processedCount;
    private volatile int expectedCount;

    @Inject
    DirectoryExcelParser parser;

    @Inject
    DirectoryRowMapper rowMapper;

    @Inject
    DirectoryImportPlanner planner;

    @Inject
    DirectoryRepository directoryRepository;

    @Inject
    DirectoryRutCrypto crypto;

    @Inject
    DirectoryFileStore fileStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    void onApplicationStart(@Observes @Initialized(ApplicationScoped.class) Object start) {
        recoverOnStartup();
    }

    @PostConstruct
    void recoverOnStartup() {
        try {
            if (!directoryRepository.available()) {
                LOG.info("directory import recovery skipped: tables not available");
                return;
            }
            int running = directoryRepository.findRunningImports().size();
            int recovered = recoverAbandonedImports();
            LOG.info("directory import recovery running=" + running
                    + " recovered=" + recovered
                    + " processToken=" + processToken);
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "directory import recovery failed", ex);
        }
    }

    public DirectoryImportResultDto dryRun(byte[] bytes, String filename) {
        crypto.requireConfigured();
        DirectoryPrepared prepared = prepare(bytes, filename);
        return toDto(true, null, filename, prepared.parse, prepared.plan, 0);
    }

    public DirectoryImportResultDto importFile(byte[] bytes, String filename, String importedBy) {
        crypto.requireConfigured();
        if (!lock.tryLock()) {
            throw runningConflict();
        }
        Long importId = null;
        boolean completed = false;
        boolean previousAutoCommit = true;
        Connection persistConnection = null;
        try {
            if (directoryRepository.hasRunningImport()) {
                throw runningConflict();
            }
            DirectoryPrepared prepared = prepare(bytes, filename);
            if (prepared.parse.rows().isEmpty()) {
                throw new ValidationException("DIRECTORY_FILE_EMPTY", "The directory file has no data rows");
            }
            String publicId = UUID.randomUUID().toString();
            String storageKey = fileStore.save(bytes, filename);
            String sha = fileStore.sha256(bytes);
            expectedCount = prepared.parse.rows().size();
            processedCount = 0;
            importId = directoryRepository.insertImportCommitted(
                    publicId,
                    filename == null ? "directory.xlsx" : filename,
                    storageKey,
                    sha,
                    bytes.length,
                    prepared.parse.sheetName(),
                    String.join(",", prepared.parse.headers()),
                    importedBy == null ? "system" : importedBy,
                    processToken,
                    expectedCount
            );
            ownedImportId = importId;
            LOG.info("directory import RUNNING id=" + importId
                    + " publicId=" + publicId
                    + " expected=" + expectedCount);
            persistConnection = directoryRepository.openConnection();
            previousAutoCommit = directoryRepository.beginWork(persistConnection);
            persistPlan(persistConnection, importId, prepared.plan);
            int inactivated = 0;
            if (DirectoryPresencePolicy.canInactivate(prepared.plan.censusReliable, false, "COMPLETED")) {
                inactivated = directoryRepository.markMissing(persistConnection, importId);
            }
            DirectoryImportCounts counts = countsOf(prepared.parse.rows().size(), prepared.plan, inactivated);
            directoryRepository.finishImport(persistConnection, importId, "COMPLETED", counts, null);
            persistConnection.commit();
            completed = true;
            LOG.info("directory import completed publicId=" + publicId
                    + " created=" + counts.created
                    + " updated=" + counts.updated
                    + " unchanged=" + counts.unchanged
                    + " issues=" + counts.issues
                    + " inactivated=" + inactivated
                    + " censusReliable=" + prepared.plan.censusReliable);
            return toDto(false, publicId, filename, prepared.parse, prepared.plan, inactivated);
        } catch (ConflictException | ValidationException ex) {
            DirectoryRepository.rollback(persistConnection);
            failImport(importId, completed, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            DirectoryRepository.rollback(persistConnection);
            failImport(importId, completed, "Directory import failed: " + ex.getClass().getSimpleName());
            LOG.warning("directory import failed: " + ex.getClass().getSimpleName());
            throw new ValidationException("DIRECTORY_IMPORT_FAILED", "Directory import failed");
        } finally {
            DirectoryRepository.restore(persistConnection, previousAutoCommit);
            DirectoryRepository.close(persistConnection);
            ownedImportId = null;
            processedCount = 0;
            expectedCount = 0;
            lock.unlock();
        }
    }

    public int recoverAbandonedImports() {
        int recovered = 0;
        for (DirectoryImportSummaryDto running : directoryRepository.findRunningImports()) {
            if (recoverIfAbandoned(running.id) != null) {
                recovered++;
            }
        }
        return recovered;
    }

    public DirectoryImportSummaryDto recoverImport(long importId) {
        DirectoryImportSummaryDto current = directoryRepository.findImport(importId);
        DirectoryImportSummaryDto recovered = recoverIfAbandoned(importId);
        if (recovered != null) {
            return recovered;
        }
        if (DirectoryImportRecoveryPolicy.RUNNING.equals(current.status)
                && DirectoryImportRecoveryPolicy.workerOwns(lock.isLocked(), ownedImportId, importId)) {
            throw new ConflictException(
                    "DIRECTORY_IMPORT_RUNNING",
                    "A directory import is already running",
                    "DIRECTORY",
                    String.valueOf(importId),
                    null,
                    current.importedBy
            );
        }
        return decorate(current);
    }

    public List<DirectoryImportSummaryDto> decorate(List<DirectoryImportSummaryDto> items) {
        if (items == null) {
            return List.of();
        }
        for (DirectoryImportSummaryDto item : items) {
            decorate(item);
        }
        return items;
    }

    public DirectoryImportSummaryDto decorate(DirectoryImportSummaryDto item) {
        if (item == null || item.id == null) {
            return item;
        }
        boolean owned = DirectoryImportRecoveryPolicy.workerOwns(lock.isLocked(), ownedImportId, item.id);
        item.owned = owned;
        item.interrupted = DirectoryImportRecoveryPolicy.isAbandoned(item.status, owned);
        if (owned) {
            item.processedCount = Math.max(item.processedCount, processedCount);
            item.expectedCount = Math.max(item.expectedCount, expectedCount);
        }
        return item;
    }

    private DirectoryImportSummaryDto recoverIfAbandoned(long importId) {
        DirectoryImportSummaryDto current;
        try {
            current = directoryRepository.findImport(importId);
        } catch (NotFoundException ex) {
            return null;
        }
        boolean owned = DirectoryImportRecoveryPolicy.workerOwns(
                processToken,
                null,
                lock.isLocked(),
                ownedImportId,
                importId
        );
        if (!DirectoryImportRecoveryPolicy.canRecover(current.status, owned)) {
            return null;
        }
        DirectoryImportSideEffects effects = directoryRepository.countSideEffects(importId);
        String message = DirectoryImportRecoveryPolicy.abandonedMessage(importId, effects);
        boolean marked = directoryRepository.finishImportCommitted(
                importId,
                DirectoryImportRecoveryPolicy.recoveryStatus(),
                new DirectoryImportCounts(),
                message
        );
        if (!marked) {
            return decorate(directoryRepository.findImport(importId));
        }
        LOG.warning("directory import abandoned recovered id=" + importId
                + " people=" + effects.people
                + " importRows=" + effects.importRows
                + " issues=" + effects.issues
                + " inactivation=false");
        return decorate(directoryRepository.findImport(importId));
    }

    private void failImport(Long importId, boolean completed, String errorMessage) {
        if (importId == null || completed) {
            return;
        }
        try {
            boolean marked = directoryRepository.finishImportCommitted(
                    importId,
                    DirectoryImportRecoveryPolicy.FAILED,
                    new DirectoryImportCounts(),
                    errorMessage == null ? "Directory import failed" : errorMessage
            );
            if (marked) {
                LOG.warning("directory import marked FAILED id=" + importId + " reason=" + errorMessage);
            } else {
                LOG.warning("directory import FAILED update matched 0 rows id=" + importId);
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "directory import status could not be marked FAILED id=" + importId, ex);
        }
    }

    private ConflictException runningConflict() {
        return new ConflictException("DIRECTORY_IMPORT_RUNNING", "A directory import is already running");
    }

    private DirectoryPrepared prepare(byte[] bytes, String filename) {
        if (filename != null && !(filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls"))) {
            throw new ValidationException("DIRECTORY_FILE_INVALID", "Only Excel files are accepted");
        }
        DirectoryParseResult parsed = parser.parse(bytes);
        List<DirectoryIssue> preIssues = new ArrayList<>();
        for (int i = 0; i < parsed.unmappedHeaders().size(); i++) {
            preIssues.add(new DirectoryIssue(1, "UNMAPPED_HEADER", null, null));
        }
        List<DirectoryIncomingRow> incoming = rowMapper.map(parsed.rows(), preIssues);
        DirectoryCatalog catalog = directoryRepository.available()
                ? directoryRepository.loadCatalog()
                : new DirectoryCatalog();
        DirectoryImportPlan plan = planner.plan(incoming, catalog, preIssues);
        plan.unmappedHeaders = parsed.unmappedHeaders();
        plan.sheetName = parsed.sheetName();
        return new DirectoryPrepared(parsed, plan);
    }

    private void persistPlan(Connection connection, long importId, DirectoryImportPlan plan) throws Exception {
        int index = 0;
        for (DirectoryPlannedRow planned : plan.rows) {
            requireOwnership(importId, index, plan.rows.size());
            Long personId = planned.existing == null ? null : planned.existing.id;
            byte[] encrypted = planned.incoming.rutNormalized == null ? null : crypto.encrypt(planned.incoming.rutNormalized);
            if (planned.decision == DirectoryReconcileDecision.CREATE) {
                personId = directoryRepository.insertPerson(connection, planned.incoming, encrypted, importId);
                upsertEmails(connection, personId, planned.incoming, importId);
                directoryRepository.insertHistory(connection, personId, importId, snapshotJson(planned.incoming), planned.incoming.snapshotHash);
            } else if (planned.decision == DirectoryReconcileDecision.UPDATE
                    || planned.decision == DirectoryReconcileDecision.REACTIVATE) {
                directoryRepository.updatePerson(
                        connection,
                        personId,
                        planned.incoming,
                        encrypted,
                        importId,
                        planned.decision == DirectoryReconcileDecision.REACTIVATE
                );
                upsertEmails(connection, personId, planned.incoming, importId);
                directoryRepository.insertHistory(connection, personId, importId, snapshotJson(planned.incoming), planned.incoming.snapshotHash);
            } else if (planned.decision == DirectoryReconcileDecision.UNCHANGED && personId != null) {
                directoryRepository.touchUnchanged(connection, personId, importId);
            }
            directoryRepository.insertImportRow(connection, importId, planned, personId, encrypted);
            index++;
            processedCount = index;
        }
        requireOwnership(importId, index, plan.rows.size());
        for (DirectoryIssue issue : plan.issues) {
            if ("UNMAPPED_HEADER".equals(issue.code)) {
                continue;
            }
            directoryRepository.insertIssue(connection, importId, issue);
        }
    }

    private void requireOwnership(long importId, int processed, int expected) {
        if (processed > 0 && processed % HEARTBEAT_EVERY != 0 && processed < expected) {
            return;
        }
        if (!directoryRepository.heartbeat(importId, processToken, processed, expected)) {
            throw new ValidationException(
                    DirectoryImportRecoveryPolicy.ABANDONED_CODE,
                    DirectoryImportRecoveryPolicy.ABANDONED_MESSAGE
            );
        }
    }

    private void upsertEmails(Connection connection, long personId, DirectoryIncomingRow row, long importId)
            throws Exception {
        if (row.emailNormalized != null) {
            directoryRepository.upsertPrimaryEmail(connection, personId, row.emailNormalized, row.emailNormalized, importId);
        }
        String alternate = DirectoryReconciler.alternateEmpresaEmail(row.empresaRaw, row.emailNormalized);
        if (alternate != null) {
            directoryRepository.upsertAlternateEmail(connection, personId, row.empresaRaw, alternate, importId);
        }
    }

    private String snapshotJson(DirectoryIncomingRow row) throws Exception {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("givenName", row.givenName);
        snapshot.put("middleName", row.middleName);
        snapshot.put("familyName", row.familyName);
        snapshot.put("secondFamilyName", row.secondFamilyName);
        snapshot.put("campusName", row.campusName);
        snapshot.put("faculty", row.facultyRaw);
        snapshot.put("positionTitle", row.positionTitle);
        snapshot.put("email", row.emailNormalized);
        snapshot.put("hash", row.snapshotHash);
        return objectMapper.writeValueAsString(snapshot);
    }

    private static DirectoryImportCounts countsOf(int rows, DirectoryImportPlan plan, int inactivated) {
        DirectoryImportCounts counts = new DirectoryImportCounts();
        counts.rowCount = rows;
        counts.created = plan.created;
        counts.updated = plan.updated;
        counts.unchanged = plan.unchanged;
        counts.reactivated = plan.reactivated;
        counts.rejected = plan.rejected;
        counts.issues = (int) plan.issues.stream().filter(issue -> !"UNMAPPED_HEADER".equals(issue.code)).count();
        counts.inactivated = inactivated;
        return counts;
    }

    private static DirectoryImportResultDto toDto(
            boolean dryRun,
            String publicId,
            String filename,
            DirectoryParseResult parsed,
            DirectoryImportPlan plan,
            int inactivated
    ) {
        DirectoryImportResultDto dto = new DirectoryImportResultDto();
        dto.dryRun = dryRun;
        dto.importPublicId = publicId;
        dto.filename = filename;
        dto.sheetName = parsed.sheetName();
        dto.status = dryRun ? "DRY_RUN" : "COMPLETED";
        dto.rowCount = parsed.rows().size();
        dto.created = plan.created;
        dto.updated = plan.updated;
        dto.unchanged = plan.unchanged;
        dto.reactivated = plan.reactivated;
        dto.rejected = plan.rejected;
        dto.wouldInactivate = plan.wouldInactivate;
        dto.inactivated = inactivated;
        dto.issueCount = (int) plan.issues.stream().filter(issue -> !"UNMAPPED_HEADER".equals(issue.code)).count();
        dto.censusReliable = plan.censusReliable;
        dto.unmappedHeaders = plan.unmappedHeaders;
        int shown = 0;
        for (DirectoryIssue issue : plan.issues) {
            if ("UNMAPPED_HEADER".equals(issue.code)) {
                continue;
            }
            if (shown++ >= ISSUE_PREVIEW) {
                break;
            }
            DirectoryIssueDto item = new DirectoryIssueDto();
            item.sourceRowNumber = issue.sourceRowNumber;
            item.code = issue.code;
            item.leftPersonId = issue.leftPersonId;
            item.rightPersonId = issue.rightPersonId;
            dto.issues.add(item);
        }
        return dto;
    }

    private record DirectoryPrepared(DirectoryParseResult parse, DirectoryImportPlan plan) {
    }
}
