package cl.uss.ca.sync;

import cl.uss.ca.api.dto.SyncRunDto;

import java.time.OffsetDateTime;

/**
 * Preconditions for an explicit OpenAlex AUTHORS recovery.
 * Does not treat RECOVERY as FULL and does not invent pending authors.
 */
public final class OpenAlexAuthorRecovery {
    public static final String OPERATION = "AUTHORS_RECOVERY";
    public static final String TYPE = "AUTHORS";
    public static final String NOT_SUPPORTED = "OPENALEX_RECOVERY_NOT_SUPPORTED";
    public static final String NOT_TERMINAL = "OPENALEX_RECOVERY_NOT_TERMINAL";
    public static final String NOT_ELIGIBLE = "OPENALEX_RECOVERY_NOT_ELIGIBLE";
    public static final String WORKS_INCOMPLETE = "OPENALEX_RECOVERY_WORKS_INCOMPLETE";
    public static final String WORKS_HAS_ERRORS = "OPENALEX_RECOVERY_WORKS_HAS_ERRORS";
    public static final String NO_PENDING = "OPENALEX_RECOVERY_NO_PENDING_AUTHORS";
    public static final String RUNNING_BLOCKED = "SYNC_ALREADY_RUNNING";

    private OpenAlexAuthorRecovery() {
    }

    public static boolean isTerminal(String status) {
        return "FAILED".equals(status)
                || "COMPLETED".equals(status)
                || "COMPLETED_WITH_WARNINGS".equals(status)
                || SyncCancellation.CANCELLED.equals(status);
    }

    public static boolean isOpenAlex(SyncRunDto source) {
        return source != null && "OPENALEX".equals(source.entity);
    }

    public static boolean isFullWorksSource(SyncRunDto source) {
        return source != null && SyncBaselineKind.isFull(source);
    }

    /**
     * Structural eligibility without counting authors.
     * Recovery is only offered for a stale OpenAlex FULL whose Works phase
     * finished (processed == total_expected) with no recorded item errors.
     */
    public static String rejectCode(SyncRunDto source) {
        if (source == null) {
            return "SYNC_NOT_FOUND";
        }
        if (!isOpenAlex(source)) {
            return NOT_SUPPORTED;
        }
        if (!isTerminal(source.status)) {
            return NOT_TERMINAL;
        }
        if (!"STALE_SYNC_RECOVERED".equals(source.errorCode) || !"FAILED".equals(source.status)) {
            return NOT_ELIGIBLE;
        }
        if (!isFullWorksSource(source) || SyncMode.RECOVERY.name().equals(source.syncMode)) {
            return NOT_ELIGIBLE;
        }
        if (source.totalExpected == null || source.processed != source.totalExpected.longValue()) {
            return WORKS_INCOMPLETE;
        }
        if (source.errors > 0) {
            return WORKS_HAS_ERRORS;
        }
        return null;
    }

    public static String rejectMessage(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "SYNC_NOT_FOUND" -> "Synchronization was not found";
            case NOT_SUPPORTED -> "Author recovery is only available for OpenAlex executions";
            case NOT_TERMINAL -> "Author recovery requires a finished OpenAlex execution";
            case NOT_ELIGIBLE ->
                    "Author recovery is only available for an OpenAlex FULL execution recovered as STALE_SYNC_RECOVERED";
            case WORKS_INCOMPLETE ->
                    "Author recovery requires the Works phase to have processed every expected DOI";
            case WORKS_HAS_ERRORS ->
                    "Author recovery requires the Works phase to have finished without recorded errors";
            case NO_PENDING -> "There are no pending OpenAlex authors (needs_refresh) to recover";
            default -> "OpenAlex author recovery is not available for this execution";
        };
    }

    public static boolean eligible(SyncRunDto source, long pendingAuthors) {
        return rejectCode(source) == null && pendingAuthors > 0;
    }

    /**
     * Provenance of the recovery event. Does not mean every pending author
     * was created by that execution.
     */
    public static String recoveredFromMessage(String sourceSyncId) {
        return "Recovered pending OpenAlex authors. Triggered from OpenAlex FULL #" + sourceSyncId;
    }

    /**
     * A stub is pending iff {@code needs_refresh} is set.
     * Does not use last_sync_execution_id or created_at.
     */
    public static boolean isPendingAuthor(
            boolean needsRefresh,
            OffsetDateTime lastSyncAt,
            OffsetDateTime sourceStartedAt
    ) {
        return needsRefresh;
    }

    public static boolean isAuthorCreate(WorkSyncState state) {
        return state == null || !state.present || state.lastSyncAt == null;
    }

    public static boolean heartbeatDue(int authorsSinceHeartbeat, long nanosSinceHeartbeat) {
        return authorsSinceHeartbeat >= 10 || nanosSinceHeartbeat >= 30_000_000_000L;
    }

    public static long authorsUpserted(long created, long updated) {
        return created + updated;
    }
}
