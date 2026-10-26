package cl.uss.ca.directory;

/**
 * People are inactivated only after a completed, reliable FULL census.
 * Dry-run, failed, cancelled or conflicted files never change presence.
 */
public final class DirectoryPresencePolicy {
    private DirectoryPresencePolicy() {
    }

    public static boolean canInactivate(boolean censusReliable, boolean dryRun, String status) {
        return !dryRun && censusReliable && "COMPLETED".equals(status);
    }
}
