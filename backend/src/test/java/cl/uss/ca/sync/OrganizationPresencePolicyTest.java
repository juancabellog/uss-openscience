package cl.uss.ca.sync;

/**
 * Organizations missing-inactive contract. Not executed by the agent.
 * OrganizationPureSyncJob only calls markMissingOrganizations after canInactivate.
 */
public final class OrganizationPresencePolicyTest {

    private OrganizationPresencePolicyTest() {
    }

    public static void main(String[] args) {
        successfulFullCanInactivate();
        incompleteFullCannotInactivate();
        erroredFullCannotInactivate();
        incrementalNeverInactivates();
        organizationsAreFullOnly();
        System.out.println("OrganizationPresencePolicyTest OK");
    }

    static void successfulFullCanInactivate() {
        SyncRunState run = full("org-ok");
        run.processed.set(2);
        assertTrue(new SyncJobSupport().canInactivate(run, 2));
    }

    static void incompleteFullCannotInactivate() {
        SyncRunState run = full("org-incomplete");
        run.processed.set(1);
        assertTrue(!new SyncJobSupport().canInactivate(run, 2));
        assertEquals(SyncJobSupport.PURE_INCOMPLETE, new SyncJobSupport().classify(run, 2));
    }

    static void erroredFullCannotInactivate() {
        SyncRunState run = full("org-errors");
        run.processed.set(1);
        run.errors.set(1);
        assertTrue(!new SyncJobSupport().canInactivate(run, 2));
        assertEquals(SyncJobSupport.SYNC_PROCESSING_FAILED, new SyncJobSupport().classify(run, 2));
    }

    static void incrementalNeverInactivates() {
        SyncRunState run = full("org-inc");
        run.syncMode = SyncMode.INCREMENTAL;
        run.processed.set(2);
        assertTrue(!new SyncJobSupport().canInactivate(run, 2));
        assertTrue(!new SyncJobSupport().allowsAbsenceReconciliation(run));
    }

    static void organizationsAreFullOnly() {
        assertTrue(SyncEntityPolicy.ORGANIZATIONS.supportsFull());
        assertTrue(!SyncEntityPolicy.ORGANIZATIONS.supportsIncremental());
    }

    private static SyncRunState full(String id) {
        SyncRunState run = new SyncRunState("ORGANIZATIONS", "test");
        run.syncId = id;
        run.syncMode = SyncMode.FULL;
        return run;
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("expected true");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
