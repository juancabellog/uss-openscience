package cl.uss.ca.sync;

import cl.uss.ca.api.dto.SyncRunDto;

/**
 * Per-integration rule for whether a finished execution established a valid baseline.
 * Incremental never creates a baseline. A later FAILED FULL does not erase a
 * historical baseline; it only fails to create a new one.
 */
public enum SyncBaselineKind {
    /**
     * ORCID / Unpaywall / Crossref / OpenAlex: the population was walked.
     * Item-level warnings (ERROR, NOT_FOUND, optional metadata) remain retryable
     * via INCREMENTAL and do not invalidate the snapshot.
     */
    ITEM_WARNINGS_OK {
        @Override
        public boolean establishesBaseline(SyncRunDto run) {
            return isFull(run) && isFinishedOk(run);
        }
    },
    /**
     * PURE FULL: absence reconciliation (inactivate) only happens on COMPLETED.
     * COMPLETED_WITH_WARNINGS / FAILED did not produce a reliable census.
     */
    COMPLETED_FULL_ONLY {
        @Override
        public boolean establishesBaseline(SyncRunDto run) {
            return isFull(run) && run != null && "COMPLETED".equals(run.status);
        }
    };

    public abstract boolean establishesBaseline(SyncRunDto run);

    public static boolean isFull(SyncRunDto run) {
        if (run == null) {
            return false;
        }
        if (SyncMode.FULL.name().equals(run.syncMode)) {
            return true;
        }
        return (run.syncMode == null || run.syncMode.isBlank())
                && SyncEntityPolicy.treatsNullSyncModeAsFull(run.entity);
    }

    public static boolean isFinishedOk(SyncRunDto run) {
        if (run == null || run.status == null) {
            return false;
        }
        return "COMPLETED".equals(run.status) || "COMPLETED_WITH_WARNINGS".equals(run.status);
    }
}
