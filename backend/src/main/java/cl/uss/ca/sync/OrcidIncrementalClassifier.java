package cl.uss.ca.sync;

import java.time.OffsetDateTime;
import java.util.Set;

public final class OrcidIncrementalClassifier {
    public static final Set<String> RETRYABLE = Set.of(
            "FAILED",
            "ERROR",
            "TIMEOUT",
            "RATE_LIMIT",
            "RATE_LIMITED",
            "REMOTE_FAILURE",
            "REMOTE_ERROR"
    );
    public static final Set<String> PARTIAL = Set.of("COMPLETED_WITH_WARNINGS", "PARTIAL");

    private OrcidIncrementalClassifier() {
    }

    public static IncrementalReason classify(
            boolean hasSyncRow,
            String storedOrcid,
            String currentOrcid,
            OffsetDateTime lastSyncAt,
            String lastSyncStatus,
            boolean pending,
            int refreshDays,
            OffsetDateTime now
    ) {
        IncrementalReason reason = null;
        if (pending) {
            reason = IncrementalReason.highest(reason, IncrementalReason.MANUAL_REFRESH);
        }
        if (storedOrcid != null && currentOrcid != null && !storedOrcid.equalsIgnoreCase(currentOrcid)) {
            reason = IncrementalReason.highest(reason, IncrementalReason.SOURCE_CHANGED);
        }
        String status = lastSyncStatus == null ? "" : lastSyncStatus.trim().toUpperCase();
        if (RETRYABLE.contains(status)) {
            reason = IncrementalReason.highest(reason, IncrementalReason.REMOTE_ERROR);
        }
        if (PARTIAL.contains(status)) {
            reason = IncrementalReason.highest(reason, IncrementalReason.PARTIAL_DATA);
        }
        if (!hasSyncRow) {
            reason = IncrementalReason.highest(reason, IncrementalReason.NEW);
        } else if (lastSyncAt == null) {
            reason = IncrementalReason.highest(reason, IncrementalReason.NEVER_SYNCED);
        }
        if (refreshDays > 0
                && lastSyncAt != null
                && !RETRYABLE.contains(status)
                && !lastSyncAt.isAfter(now.minusDays(refreshDays))) {
            reason = IncrementalReason.highest(reason, IncrementalReason.STALE);
        }
        return reason;
    }
}
