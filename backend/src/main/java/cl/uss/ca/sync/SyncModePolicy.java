package cl.uss.ca.sync;

import cl.uss.ca.api.dto.SyncModePolicyDto;
import cl.uss.ca.api.dto.SyncRunDto;
import cl.uss.ca.exception.ValidationException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure FULL/INCREMENTAL recommendation. No persistence, no entity-name if/else.
 * Dual-mode entities always expose both modes; baseline only changes the
 * recommended default. Each {@link SyncEntityPolicy} supplies {@link SyncBaselineKind}.
 */
public final class SyncModePolicy {
    private SyncModePolicy() {
    }

    public static SyncModePolicyDto evaluate(
            SyncEntityPolicy entity,
            SyncRunDto latest,
            List<SyncRunDto> completedFullRuns,
            SyncRunDto lastFinishedFull
    ) {
        SyncRunDto baseline = firstEstablishing(entity, completedFullRuns);
        boolean hasValidBaseline = baseline != null;
        List<String> supported = names(entity.supportedModes());
        List<String> available = new ArrayList<>();
        if (entity.supportsFull()) {
            available.add(SyncMode.FULL.name());
        }
        if (entity.supportsIncremental() && hasValidBaseline) {
            available.add(SyncMode.INCREMENTAL.name());
        }
        SyncMode recommended = recommend(entity, latest, hasValidBaseline);
        if (!available.contains(recommended.name()) && !available.isEmpty()) {
            recommended = SyncMode.parse(available.get(0));
        }
        SyncModePolicyDto dto = new SyncModePolicyDto();
        dto.entity = entity.entity();
        dto.supportedSyncModes = supported;
        dto.availableSyncModes = List.copyOf(available);
        dto.hasValidBaseline = hasValidBaseline;
        dto.recommendedSyncMode = recommended.name();
        dto.lastSuccessfulFullSyncAt = timestamp(baseline);
        dto.lastFullSyncAt = timestamp(lastFinishedFull != null ? lastFinishedFull : first(completedFullRuns));
        dto.lastExecution = latest;
        return dto;
    }

    public static SyncMode resolveStart(SyncEntityPolicy entity, String raw, SyncModePolicyDto policy) {
        SyncMode parsed = SyncMode.parse(raw);
        SyncMode mode = parsed == null
                ? SyncMode.parse(policy == null ? null : policy.recommendedSyncMode)
                : parsed;
        if (mode == null) {
            mode = SyncMode.FULL;
        }
        entity.assertSupported(mode);
        if (mode.isIncremental() && (policy == null || !policy.hasValidBaseline)) {
            throw new ValidationException(
                    "SYNC_BASELINE_REQUIRED",
                    entity.entity() + " requires a valid FULL baseline before INCREMENTAL"
            );
        }
        return mode;
    }

    static SyncMode recommend(SyncEntityPolicy entity, SyncRunDto latest, boolean hasValidBaseline) {
        if (!entity.supportsIncremental()) {
            return SyncMode.FULL;
        }
        if (!hasValidBaseline) {
            return SyncMode.FULL;
        }
        if (latest != null && SyncBaselineKind.isFull(latest)) {
            if ("FAILED".equals(latest.status)
                    || SyncCancellation.CANCELLED.equals(latest.status)
                    || SyncCancellation.REQUESTED.equals(latest.status)) {
                return SyncMode.FULL;
            }
            if ("COMPLETED_WITH_WARNINGS".equals(latest.status)
                    && !entity.baselineKind().establishesBaseline(latest)) {
                return SyncMode.FULL;
            }
        }
        return SyncMode.INCREMENTAL;
    }

    private static SyncRunDto firstEstablishing(SyncEntityPolicy entity, List<SyncRunDto> completedFullRuns) {
        if (completedFullRuns == null) {
            return null;
        }
        for (SyncRunDto run : completedFullRuns) {
            if (entity.baselineKind().establishesBaseline(run)) {
                return run;
            }
        }
        return null;
    }

    private static SyncRunDto first(List<SyncRunDto> runs) {
        return runs == null || runs.isEmpty() ? null : runs.get(0);
    }

    private static OffsetDateTime timestamp(SyncRunDto run) {
        if (run == null) {
            return null;
        }
        return run.finishedAt != null ? run.finishedAt : run.startedAt;
    }

    private static List<String> names(List<SyncMode> modes) {
        List<String> names = new ArrayList<>();
        for (SyncMode mode : modes) {
            names.add(mode.name());
        }
        return List.copyOf(names);
    }
}
