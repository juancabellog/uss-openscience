package cl.uss.ca.sync;

/**
 * Cooperative cancellation. The worker stops on the next ownership/heartbeat
 * check. Already persisted records are kept. FAILED is reserved for errors.
 */
public final class SyncCancellation {
    public static final String REQUESTED = "CANCEL_REQUESTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String REQUEST_CODE = "SYNC_CANCEL_REQUESTED";
    public static final String CANCEL_CODE = "SYNC_CANCELLED";
    public static final String NOT_RUNNING = "SYNC_NOT_RUNNING";

    private SyncCancellation() {
    }

    public static boolean isActive(String status) {
        return "PENDING".equals(status)
                || "RUNNING".equals(status)
                || REQUESTED.equals(status);
    }

    public static boolean isCancellable(String status) {
        return "PENDING".equals(status) || "RUNNING".equals(status);
    }

    public static boolean isCancelRequested(String status) {
        return REQUESTED.equals(status);
    }

    public static boolean isCancelled(String status) {
        return CANCELLED.equals(status);
    }

    public static boolean isTerminal(String status) {
        return "FAILED".equals(status)
                || "COMPLETED".equals(status)
                || "COMPLETED_WITH_WARNINGS".equals(status)
                || CANCELLED.equals(status);
    }

    public static String requestMessage(String userId) {
        return "Cancellation requested by user=" + actor(userId)
                + ". Records already processed were not reverted.";
    }

    public static String cancelledMessage(String userId) {
        return "Cancelled by user=" + actor(userId)
                + ". Records already processed were not reverted.";
    }

    public static String notRunningMessage() {
        return "Synchronization is not running";
    }

    private static String actor(String userId) {
        return userId == null || userId.isBlank() ? "?" : userId.trim();
    }
}
