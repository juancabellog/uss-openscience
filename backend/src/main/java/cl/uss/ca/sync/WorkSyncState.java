package cl.uss.ca.sync;

import java.time.OffsetDateTime;

/**
 * Compact local state used to decide whether a DOI/person is an INCREMENTAL candidate
 * without an extra remote call.
 */
public class WorkSyncState {
    public boolean present;
    public OffsetDateTime lastSyncAt;
    public String status;
    public boolean needsRefresh;
    public boolean mapped;

    public static WorkSyncState missing() {
        WorkSyncState state = new WorkSyncState();
        state.present = false;
        return state;
    }
}
