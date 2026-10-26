package cl.uss.ca.service;

import cl.uss.ca.api.dto.DspaceStatsDto;
import cl.uss.ca.audit.AuditCatalog;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.dspace.DspaceInventoryState;
import cl.uss.ca.sync.SyncEntityPolicy;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncMode;
import cl.uss.ca.sync.SyncRunState;

/**
 * BITSTREAM_BACKFILL contracts. Not executed by the agent.
 */
public final class DspaceBitstreamBackfillTest {
    private DspaceBitstreamBackfillTest() {
    }

    public static void main(String[] args) {
        recoveryIsNotFullOrIncremental();
        dspacePolicyDoesNotExposeRecovery();
        auditUsesDedicatedOperation();
        onlyConfirmedInventoryMayReplace();
        suspectWarningWhenAccessHasFilesButCountIsZero();
        mismatchWarningWhenCardsDoNotAddUp();
        recoveryDoesNotMarkMissing();
        System.out.println("DspaceBitstreamBackfillTest OK");
    }

    static void recoveryIsNotFullOrIncremental() {
        assertEquals(SyncMode.RECOVERY, SyncMode.parse("RECOVERY"));
        assertTrue(SyncMode.RECOVERY.isRecovery());
        assertTrue(!SyncMode.RECOVERY.isFull());
        assertTrue(!SyncMode.RECOVERY.isIncremental());
        assertEquals(DspaceSyncJob.STAGE_BITSTREAMS, "BITSTREAMS");
        assertEquals(DspaceSyncJob.STAGE_BITSTREAM_BACKFILL, "BITSTREAM_BACKFILL");
        assertEquals(SyncJobSupport.STAGE_DSPACE_BITSTREAMS, "BITSTREAM_BACKFILL");
    }

    static void dspacePolicyDoesNotExposeRecovery() {
        assertTrue(SyncEntityPolicy.DSPACE.supports(SyncMode.FULL));
        assertTrue(SyncEntityPolicy.DSPACE.supports(SyncMode.INCREMENTAL));
        assertTrue(!SyncEntityPolicy.DSPACE.supports(SyncMode.RECOVERY));
        try {
            SyncEntityPolicy.DSPACE.assertSupported(SyncMode.RECOVERY);
            throw new AssertionError("RECOVERY must not be a public DSpace census mode");
        } catch (ValidationException ex) {
            assertEquals("SYNC_MODE_NOT_SUPPORTED", ex.getCode());
        }
    }

    static void auditUsesDedicatedOperation() {
        assertEquals("DSPACE_BITSTREAM_BACKFILL", AuditCatalog.operationOf("DSPACE", "RECOVERY"));
        assertEquals("DSPACE_SYNC", AuditCatalog.operationOf("DSPACE", "FULL"));
        assertEquals("DSPACE_SYNC", AuditCatalog.operationOf("DSPACE", "INCREMENTAL"));
    }

    static void onlyConfirmedInventoryMayReplace() {
        assertTrue(DspaceInventoryState.CONFIRMED.mayReplaceBitstreams());
        assertTrue(!DspaceInventoryState.FETCH_FAILED.mayReplaceBitstreams());
        assertTrue(!DspaceInventoryState.NOT_LOADED.mayReplaceBitstreams());
    }

    static void suspectWarningWhenAccessHasFilesButCountIsZero() {
        DspaceStatsDto snapshot = new DspaceStatsDto();
        snapshot.items = 10;
        snapshot.withFile = 0;
        snapshot.withoutFile = 10;
        snapshot.openAccess = 8;
        snapshot.restricted = 1;
        snapshot.embargo = 1;
        snapshot.bitstreamCount = 0;
        assertTrue(!DspaceSyncJob.withFileWithoutFileMismatch(snapshot));
        assertTrue(DspaceSyncJob.isSuspectEmptyInventory(snapshot));
    }

    static void mismatchWarningWhenCardsDoNotAddUp() {
        DspaceStatsDto snapshot = new DspaceStatsDto();
        snapshot.items = 10;
        snapshot.withFile = 3;
        snapshot.withoutFile = 6;
        snapshot.bitstreamCount = 3;
        assertTrue(DspaceSyncJob.withFileWithoutFileMismatch(snapshot));
    }

    static void recoveryDoesNotMarkMissing() {
        SyncRunState recovery = new SyncRunState("DSPACE", "test");
        recovery.syncMode = SyncMode.RECOVERY;
        SyncJobSupport support = new SyncJobSupport();
        assertTrue(!support.allowsAbsenceReconciliation(recovery));
        SyncRunState full = new SyncRunState("DSPACE", "test");
        full.syncMode = SyncMode.FULL;
        assertTrue(support.allowsAbsenceReconciliation(full));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("expected true");
        }
    }
}
