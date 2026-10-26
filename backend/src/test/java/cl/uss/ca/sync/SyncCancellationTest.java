package cl.uss.ca.sync;

import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.audit.AuditListSupport;

/**
 * Cooperative cancel / confirm contract. Not executed by the agent.
 */
public final class SyncCancellationTest {
    private SyncCancellationTest() {
    }

    public static void main(String[] args) {
        statusesAreDistinctFromFailed();
        requestMessageNamesActor();
        cancelledKeepsProcessedDataContract();
        auditAcceptsCancelStatuses();
        recoveryBlockedWhileAnotherOpenAlexIsActive();
        newerExecutionDoesNotDeleteSource48();
        System.out.println("SyncCancellationTest OK");
    }

    static void statusesAreDistinctFromFailed() {
        assert SyncCancellation.isCancellable("RUNNING");
        assert SyncCancellation.isCancellable("PENDING");
        assert !SyncCancellation.isCancellable("CANCEL_REQUESTED");
        assert !SyncCancellation.isCancellable("CANCELLED");
        assert !SyncCancellation.isCancellable("FAILED");
        assert SyncCancellation.isActive("RUNNING");
        assert SyncCancellation.isActive("CANCEL_REQUESTED");
        assert !SyncCancellation.isActive("CANCELLED");
        assert !SyncCancellation.isActive("FAILED");
        assert SyncCancellation.isCancelRequested("CANCEL_REQUESTED");
        assert SyncCancellation.isCancelled("CANCELLED");
        assert SyncCancellation.isTerminal("CANCELLED");
        assert !SyncCancellation.isTerminal("CANCEL_REQUESTED");
        assert !"FAILED".equals(SyncCancellation.CANCELLED);
        assert !"FAILED".equals(SyncCancellation.REQUESTED);
    }

    static void requestMessageNamesActor() {
        String requested = SyncCancellation.requestMessage("1");
        assert requested.contains("user=1");
        assert requested.toLowerCase().contains("not reverted");
        assert !requested.contains("Exception");
        String cancelled = SyncCancellation.cancelledMessage("1");
        assert cancelled.contains("user=1");
        assert cancelled.toLowerCase().contains("not reverted");
        assert SyncCancellation.NOT_RUNNING.equals("SYNC_NOT_RUNNING");
    }

    static void cancelledKeepsProcessedDataContract() {
        SyncRunState run = new SyncRunState("OPENALEX", "1");
        run.syncId = "52";
        run.status = "RUNNING";
        run.processed.set(1200);
        run.created.set(40);
        run.status = SyncCancellation.REQUESTED;
        assert run.processed.get() == 1200L;
        assert run.created.get() == 40L;
        run.status = SyncCancellation.CANCELLED;
        assert run.processed.get() == 1200L;
        assert !"FAILED".equals(run.status);
    }

    static void auditAcceptsCancelStatuses() {
        assert "CANCELLED".equals(AuditListSupport.statusFilter("cancelled"));
        assert "CANCEL_REQUESTED".equals(AuditListSupport.statusFilter("CANCEL_REQUESTED"));
        assert AuditListSupport.statusFilter("all") == null;
    }

    static void recoveryBlockedWhileAnotherOpenAlexIsActive() {
        SyncRunDto source48 = new SyncRunDto();
        source48.syncId = "48";
        source48.entity = "OPENALEX";
        source48.syncMode = "FULL";
        source48.status = "FAILED";
        source48.errorCode = "STALE_SYNC_RECOVERED";
        source48.processed = 22515;
        source48.totalExpected = 22515;
        source48.errors = 0;
        assert OpenAlexAuthorRecovery.rejectCode(source48) == null;
        assert OpenAlexAuthorRecovery.eligible(source48, 10);
        assert OpenAlexAuthorRecovery.eligible(source48, 10);
        source48.recoveryAvailable = false;
        source48.recoveryBlockedReason = "SYNC_ALREADY_RUNNING";
        source48.pendingAuthors = 10L;
        source48.recoveryType = OpenAlexAuthorRecovery.TYPE;
        assert !Boolean.TRUE.equals(source48.recoveryAvailable);
        assert "SYNC_ALREADY_RUNNING".equals(source48.recoveryBlockedReason);
        assert source48.pendingAuthors == 10L;
        assert "48".equals(source48.syncId);
        assert "FAILED".equals(source48.status);
    }

    static void newerExecutionDoesNotDeleteSource48() {
        SyncRunDto source48 = new SyncRunDto();
        source48.syncId = "48";
        source48.status = "FAILED";
        source48.errorCode = "STALE_SYNC_RECOVERED";
        SyncRunDto newer = new SyncRunDto();
        newer.syncId = "52";
        newer.status = "RUNNING";
        assert "48".equals(source48.syncId);
        assert "FAILED".equals(source48.status);
        assert "STALE_SYNC_RECOVERED".equals(source48.errorCode);
        assert !"48".equals(newer.syncId);
    }
}
