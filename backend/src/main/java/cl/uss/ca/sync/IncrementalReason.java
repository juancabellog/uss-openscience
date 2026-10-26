package cl.uss.ca.sync;

/**
 * Why a record entered an INCREMENTAL population. One identity is processed once;
 * if several reasons apply, {@link #highest} wins for logs.
 */
public enum IncrementalReason {
    MANUAL_REFRESH,
    SOURCE_CHANGED,
    REMOTE_ERROR,
    PARTIAL_DATA,
    NEVER_SYNCED,
    NEW,
    STALE;

    public static IncrementalReason highest(IncrementalReason left, IncrementalReason right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.ordinal() <= right.ordinal() ? left : right;
    }
}
