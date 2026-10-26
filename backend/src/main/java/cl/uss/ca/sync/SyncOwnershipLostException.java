package cl.uss.ca.sync;

/**
 * The worker no longer owns {@code sync_execution}: heartbeat updated 0 rows
 * because status is no longer RUNNING (stale recovery, cancel, or another finish).
 * Controlled stop. Do not log a stack trace.
 */
public class SyncOwnershipLostException extends RuntimeException {
    private final String syncId;
    private final String entity;

    public SyncOwnershipLostException(String syncId, String entity) {
        super("Execution ownership was lost");
        this.syncId = syncId;
        this.entity = entity;
    }

    public String getSyncId() {
        return syncId;
    }

    public String getEntity() {
        return entity;
    }
}
