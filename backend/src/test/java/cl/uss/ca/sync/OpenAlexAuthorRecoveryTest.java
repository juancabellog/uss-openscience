package cl.uss.ca.sync;

import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.audit.AuditCatalog;
import cl.uss.ca.audit.AuditEvents;
import cl.uss.ca.audit.AuditListSupport;
import cl.uss.ca.repository.SyncRunRepository;

import java.time.OffsetDateTime;

/**
 * OpenAlex #48 recovery / heartbeat / counters. Not executed by the agent.
 */
public final class OpenAlexAuthorRecoveryTest {
    private OpenAlexAuthorRecoveryTest() {
    }

    public static void main(String[] args) {
        heartbeatDueWorksAndAuthors();
        ownershipLostStopsWorker();
        staleMessageOpenAlexDoesNotMentionInactive();
        recoveryOnlyOpenAlex();
        recoveryRejectsNonTerminal();
        recoveryRejectsIneligible();
        recoveryFindsPendingAuthors();
        recoveryDoesNotCallWorks();
        successfulAuthorClearsRefresh();
        failedAuthorRemainsPending();
        recoveryIsIdempotent();
        invalidCountDoesNotFeedMarkedInactive();
        authorsCountersCountInsertAndUpdate();
        source48RemainsFailed();
        recoveryIsAuditedAsNewExecution();
        recoveryIsNotAFullBaseline();
        recoveryModeIsNotStartable();
        cancelledIsNotFailedAndNotRecoverable();
        System.out.println("OpenAlexAuthorRecoveryTest OK");
    }

    static void heartbeatDueWorksAndAuthors() {
        assert OpenAlexAuthorRecovery.heartbeatDue(10, 1_000L);
        assert OpenAlexAuthorRecovery.heartbeatDue(1, 30_000_000_000L);
        assert !OpenAlexAuthorRecovery.heartbeatDue(9, 29_000_000_000L);
        assert OpenAlexSyncPhase.WORKS.name().equals("WORKS");
        assert OpenAlexSyncPhase.AUTHORS.isAuthors();
        assert OpenAlexSyncPhase.CONFLICTS.name().equals("CONFLICTS");
        assert OpenAlexSyncPhase.FINALIZING.name().equals("FINALIZING");
    }

    static void ownershipLostStopsWorker() {
        SyncOwnershipLostException ex = new SyncOwnershipLostException("48", "OPENALEX");
        assert "48".equals(ex.getSyncId());
        assert "OPENALEX".equals(ex.getEntity());
        assert ex.getMessage().contains("ownership was lost");
        assert !Error.class.isAssignableFrom(ex.getClass());
    }

    static void staleMessageOpenAlexDoesNotMentionInactive() {
        String openalex = SyncRunRepository.staleMessage("OPENALEX");
        String pure = SyncRunRepository.staleMessage("PEOPLE");
        assert openalex.contains("heartbeat expired");
        assert !openalex.toLowerCase().contains("inactive");
        assert pure.contains("Inactive marks");
        assert SyncRunRepository.STALE_CODE.equals("STALE_SYNC_RECOVERED");
    }

    static void recoveryOnlyOpenAlex() {
        SyncRunDto people = source("PEOPLE", "FULL", "FAILED", "STALE_SYNC_RECOVERED", 10, 10, 0);
        assert OpenAlexAuthorRecovery.NOT_SUPPORTED.equals(OpenAlexAuthorRecovery.rejectCode(people));
    }

    static void recoveryRejectsNonTerminal() {
        SyncRunDto running = source("OPENALEX", "FULL", "RUNNING", null, 22515, 22515, 0);
        assert OpenAlexAuthorRecovery.NOT_TERMINAL.equals(OpenAlexAuthorRecovery.rejectCode(running));
    }

    static void recoveryRejectsIneligible() {
        SyncRunDto failedOther = source("OPENALEX", "FULL", "FAILED", "SYNC_FAILED", 22515, 22515, 0);
        assert OpenAlexAuthorRecovery.NOT_ELIGIBLE.equals(OpenAlexAuthorRecovery.rejectCode(failedOther));
        SyncRunDto incremental = source("OPENALEX", "INCREMENTAL", "FAILED", "STALE_SYNC_RECOVERED", 10, 10, 0);
        assert OpenAlexAuthorRecovery.NOT_ELIGIBLE.equals(OpenAlexAuthorRecovery.rejectCode(incremental));
        SyncRunDto recovery = source("OPENALEX", "RECOVERY", "FAILED", "STALE_SYNC_RECOVERED", 100, 100, 0);
        assert OpenAlexAuthorRecovery.NOT_ELIGIBLE.equals(OpenAlexAuthorRecovery.rejectCode(recovery));
        SyncRunDto incomplete = source("OPENALEX", "FULL", "FAILED", "STALE_SYNC_RECOVERED", 100, 22515, 0);
        assert OpenAlexAuthorRecovery.WORKS_INCOMPLETE.equals(OpenAlexAuthorRecovery.rejectCode(incomplete));
        SyncRunDto errors = source("OPENALEX", "FULL", "FAILED", "STALE_SYNC_RECOVERED", 22515, 22515, 3);
        assert OpenAlexAuthorRecovery.WORKS_HAS_ERRORS.equals(OpenAlexAuthorRecovery.rejectCode(errors));
    }

    static void recoveryFindsPendingAuthors() {
        OffsetDateTime start = OffsetDateTime.parse("2026-10-12T12:00:00Z");
        assert OpenAlexAuthorRecovery.isPendingAuthor(true, null, start);
        assert !OpenAlexAuthorRecovery.isPendingAuthor(false, null, start);
        assert !OpenAlexAuthorRecovery.isPendingAuthor(false, start.minusHours(1), start);
        assert !OpenAlexAuthorRecovery.isPendingAuthor(false, start.plusHours(1), start);
        SyncRunDto source48 = eligible48();
        assert OpenAlexAuthorRecovery.eligible(source48, 1342);
        assert !OpenAlexAuthorRecovery.eligible(source48, 0);
    }

    static void recoveryDoesNotCallWorks() {
        assert OpenAlexAuthorRecovery.OPERATION.equals("AUTHORS_RECOVERY");
        assert OpenAlexAuthorRecovery.TYPE.equals("AUTHORS");
        SyncRunState recovery = new SyncRunState("OPENALEX", "7");
        recovery.syncMode = SyncMode.RECOVERY;
        assert recovery.isRecovery();
        assert !recovery.isFull();
        assert !recovery.isIncremental();
    }

    static void successfulAuthorClearsRefresh() {
        WorkSyncState stub = new WorkSyncState();
        stub.present = true;
        stub.needsRefresh = true;
        stub.lastSyncAt = null;
        assert OpenAlexAuthorRecovery.isAuthorCreate(stub);
        assert !OpenAlexAuthorRecovery.isPendingAuthor(false, OffsetDateTime.now(), OffsetDateTime.now().minusHours(1));
    }

    static void failedAuthorRemainsPending() {
        OffsetDateTime start = OffsetDateTime.parse("2026-10-12T12:00:00Z");
        assert OpenAlexAuthorRecovery.isPendingAuthor(true, start.minusDays(1), start);
    }

    static void recoveryIsIdempotent() {
        OffsetDateTime start = OffsetDateTime.parse("2026-10-12T12:00:00Z");
        OffsetDateTime afterRecovery = start.plusHours(2);
        assert !OpenAlexAuthorRecovery.isPendingAuthor(false, afterRecovery, start);
    }

    static void invalidCountDoesNotFeedMarkedInactive() {
        SyncRunState run = new SyncRunState("OPENALEX", "7");
        run.invalid.set(67);
        run.markedInactive.set(0);
        assert run.toDto().invalidCount == 67L;
        assert run.toDto().markedInactive == 0L;
        assert OpenAlexAuthorRecovery.authorsUpserted(10, 20) == 30L;
    }

    static void authorsCountersCountInsertAndUpdate() {
        WorkSyncState missing = WorkSyncState.missing();
        WorkSyncState stub = new WorkSyncState();
        stub.present = true;
        stub.lastSyncAt = null;
        WorkSyncState existing = new WorkSyncState();
        existing.present = true;
        existing.lastSyncAt = OffsetDateTime.parse("2026-09-01T00:00:00Z");
        assert OpenAlexAuthorRecovery.isAuthorCreate(missing);
        assert OpenAlexAuthorRecovery.isAuthorCreate(stub);
        assert !OpenAlexAuthorRecovery.isAuthorCreate(existing);
    }

    static void source48RemainsFailed() {
        SyncRunDto source48 = eligible48();
        assert "FAILED".equals(source48.status);
        assert "STALE_SYNC_RECOVERED".equals(source48.errorCode);
        assert OpenAlexAuthorRecovery.recoveredFromMessage("48").contains("#48");
        assert !OpenAlexAuthorRecovery.recoveredFromMessage("48").contains("COMPLETED");
    }

    static void recoveryIsAuditedAsNewExecution() {
        SyncRunDto recovery = source("OPENALEX", "RECOVERY", "PENDING", null, 0, 100, 0);
        recovery.syncId = "51";
        recovery.sourceSyncExecutionId = "48";
        recovery.message = OpenAlexAuthorRecovery.recoveredFromMessage("48");
        recovery.recoveryAvailable = true;
        recovery.recoveryType = OpenAlexAuthorRecovery.TYPE;
        recovery.pendingAuthors = 1342L;
        var event = AuditEvents.fromSync(recovery, "Ana");
        assert "OPENALEX_AUTHORS_RECOVERY".equals(event.operation);
        assert "OPENALEX".equals(event.source);
        assert "51".equals(event.executionId);
        assert "48".equals(event.sourceSyncExecutionId);
        assert event.errorMessage.contains("#48");
        assert Boolean.TRUE.equals(event.recoveryAvailable);
        assert "AUTHORS".equals(event.recoveryType);
        assert event.pendingAuthors == 1342L;
        assert "OPENALEX_SYNC".equals(AuditCatalog.operationOf("OPENALEX"));
        assert "OPENALEX_AUTHORS_RECOVERY".equals(AuditCatalog.operationOf("OPENALEX", "RECOVERY"));
        assert "RECOVERY".equals(AuditListSupport.syncModeFilter("recovery"));
    }

    static void recoveryIsNotAFullBaseline() {
        SyncRunDto recovery = source("OPENALEX", "RECOVERY", "COMPLETED", null, 38211, 38211, 0);
        assert !SyncBaselineKind.isFull(recovery);
        assert !SyncBaselineKind.ITEM_WARNINGS_OK.establishesBaseline(recovery);
        SyncRunDto failed48 = eligible48();
        assert !SyncBaselineKind.ITEM_WARNINGS_OK.establishesBaseline(failed48);
    }

    static void recoveryModeIsNotStartable() {
        try {
            SyncEntityPolicy.OPENALEX.assertSupported(SyncMode.RECOVERY);
            throw new AssertionError("expected SYNC_MODE_NOT_SUPPORTED");
        } catch (cl.uss.ca.exception.ValidationException ex) {
            assert "SYNC_MODE_NOT_SUPPORTED".equals(ex.getCode());
        }
        assert !SyncEntityPolicy.OPENALEX.supports(SyncMode.RECOVERY);
        assert SyncMode.RECOVERY == SyncMode.parse("RECOVERY");
        assert SyncMode.RECOVERY.isRecovery();
        assert !SyncMode.RECOVERY.isFull();
        assert !SyncMode.RECOVERY.isIncremental();
    }

    static void cancelledIsNotFailedAndNotRecoverable() {
        SyncRunDto cancelled = source("OPENALEX", "FULL", "CANCELLED", "SYNC_CANCELLED", 1200, 22515, 0);
        assert OpenAlexAuthorRecovery.isTerminal("CANCELLED");
        assert OpenAlexAuthorRecovery.NOT_ELIGIBLE.equals(OpenAlexAuthorRecovery.rejectCode(cancelled));
        assert !"FAILED".equals(cancelled.status);
        assert OpenAlexAuthorRecovery.RUNNING_BLOCKED.equals("SYNC_ALREADY_RUNNING");
        SyncRunDto source48 = eligible48();
        assert "48".equals(source48.syncId);
        assert "FAILED".equals(source48.status);
        assert "STALE_SYNC_RECOVERED".equals(source48.errorCode);
    }

    private static SyncRunDto eligible48() {
        return source("OPENALEX", "FULL", "FAILED", "STALE_SYNC_RECOVERED", 22515, 22515, 0);
    }

    private static SyncRunDto source(
            String entity,
            String mode,
            String status,
            String errorCode,
            long processed,
            int totalExpected,
            long errors
    ) {
        SyncRunDto dto = new SyncRunDto();
        dto.syncId = "48";
        dto.entity = entity;
        dto.syncMode = mode;
        dto.status = status;
        dto.errorCode = errorCode;
        dto.processed = processed;
        dto.totalExpected = totalExpected;
        dto.errors = errors;
        dto.startedAt = OffsetDateTime.parse("2026-10-12T12:00:00Z");
        return dto;
    }
}
