package cl.uss.ca.directory;

public final class DirectoryPresencePolicyTest {
    private DirectoryPresencePolicyTest() {
    }

    public static void main(String[] args) {
        completedReliableCensusCanInactivate();
        dryRunNeverInactivates();
        failedImportNeverInactivates();
        unreliableCensusNeverInactivates();
        System.out.println("DirectoryPresencePolicyTest OK");
    }

    static void completedReliableCensusCanInactivate() {
        assertEquals(true, DirectoryPresencePolicy.canInactivate(true, false, "COMPLETED"));
    }

    static void dryRunNeverInactivates() {
        assertEquals(false, DirectoryPresencePolicy.canInactivate(true, true, "DRY_RUN"));
        assertEquals(false, DirectoryPresencePolicy.canInactivate(true, true, "COMPLETED"));
    }

    static void failedImportNeverInactivates() {
        assertEquals(false, DirectoryPresencePolicy.canInactivate(true, false, "FAILED"));
        assertEquals(false, DirectoryPresencePolicy.canInactivate(true, false, "CANCELLED"));
        assertEquals(false, DirectoryPresencePolicy.canInactivate(true, false, "RUNNING"));
    }

    static void unreliableCensusNeverInactivates() {
        assertEquals(false, DirectoryPresencePolicy.canInactivate(false, false, "COMPLETED"));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
