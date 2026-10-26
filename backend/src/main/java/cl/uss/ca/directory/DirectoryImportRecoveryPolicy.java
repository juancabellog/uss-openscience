package cl.uss.ca.directory;

/**
 * Ownership for directory imports follows the PURE sync rule: a RUNNING row is
 * abandoned only when this process no longer owns it (JVM restart, lost lock).
 * Age/heartbeat staleness alone never releases the import.
 */
public final class DirectoryImportRecoveryPolicy {
    public static final String RUNNING = "RUNNING";
    public static final String FAILED = "FAILED";
    public static final String COMPLETED = "COMPLETED";
    public static final String ABANDONED_CODE = "DIRECTORY_IMPORT_ABANDONED";
    public static final String ABANDONED_MESSAGE =
            "DIRECTORY_IMPORT_ABANDONED: the import worker was lost. "
                    + "People from this execution were not committed. History kept. No inactivation.";

    private DirectoryImportRecoveryPolicy() {
    }

    public static boolean workerOwns(boolean lockHeld, Long ownedImportId, long importId) {
        return lockHeld && ownedImportId != null && ownedImportId == importId;
    }

    public static boolean workerOwns(
            String processToken,
            String rowOwnerToken,
            boolean lockHeld,
            Long ownedImportId,
            long importId
    ) {
        if (!workerOwns(lockHeld, ownedImportId, importId)) {
            return false;
        }
        if (processToken == null || rowOwnerToken == null || rowOwnerToken.isBlank()) {
            return true;
        }
        return processToken.equals(rowOwnerToken);
    }

    public static boolean isAbandoned(String status, boolean workerOwns) {
        return RUNNING.equals(status) && !workerOwns;
    }

    public static boolean canRecover(String status, boolean workerOwns) {
        return isAbandoned(status, workerOwns);
    }

    public static boolean canStartImport(boolean lockHeld, boolean ownedRunning) {
        return !lockHeld && !ownedRunning;
    }

    public static boolean canInactivateDuringRecovery() {
        return DirectoryPresencePolicy.canInactivate(true, false, FAILED);
    }

    public static String recoveryStatus() {
        return FAILED;
    }

    public static String abandonedMessage(long importId, DirectoryImportSideEffects sideEffects) {
        DirectoryImportSideEffects effects = sideEffects == null ? DirectoryImportSideEffects.empty() : sideEffects;
        return ABANDONED_MESSAGE
                + " importId=" + importId
                + " people=" + effects.people
                + " importRows=" + effects.importRows
                + " issues=" + effects.issues
                + " emails=" + effects.emails
                + " history=" + effects.history;
    }

    public static final class DirectoryImportSideEffects {
        public long people;
        public long importRows;
        public long issues;
        public long emails;
        public long history;

        public static DirectoryImportSideEffects empty() {
            return new DirectoryImportSideEffects();
        }

        public boolean hasPartialData() {
            return people > 0 || importRows > 0 || issues > 0 || emails > 0 || history > 0;
        }
    }
}
