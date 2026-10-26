package cl.uss.ca.sync;

import cl.uss.ca.external.crossref.CrossrefStatus;
import cl.uss.ca.external.openalex.OpenAlexStatus;
import cl.uss.ca.external.unpaywall.UnpaywallFetchResult;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Shared INCREMENTAL inclusion rules for DOI enrichments (Crossref / Unpaywall).
 * Terminal functional outcomes are not treated as errors; they may still be STALE.
 */
public final class DoiIncrementalClassifier {
    public static final Set<String> CROSSREF_RETRYABLE = Set.of(
            CrossrefStatus.ERROR,
            CrossrefStatus.RATE_LIMITED
    );
    public static final Set<String> CROSSREF_STALE_OK = Set.of(
            CrossrefStatus.FOUND,
            CrossrefStatus.NOT_FOUND,
            CrossrefStatus.OTHER_AGENCY
    );
    public static final Set<String> CROSSREF_TERMINAL = Set.of(
            CrossrefStatus.INVALID_DOI,
            CrossrefStatus.DISABLED
    );
    public static final Set<String> UNPAYWALL_RETRYABLE = Set.of(
            UnpaywallFetchResult.ERROR
    );
    public static final Set<String> UNPAYWALL_STALE_OK = Set.of(
            UnpaywallFetchResult.FOUND,
            UnpaywallFetchResult.NOT_FOUND
    );
    public static final Set<String> OPENALEX_RETRYABLE = Set.of(
            OpenAlexStatus.ERROR,
            OpenAlexStatus.RATE_LIMITED
    );
    public static final Set<String> OPENALEX_STALE_OK = Set.of(
            OpenAlexStatus.FOUND,
            OpenAlexStatus.NOT_FOUND
    );
    public static final Set<String> OPENALEX_TERMINAL = Set.of(
            OpenAlexStatus.INVALID_DOI,
            OpenAlexStatus.DISABLED
    );

    private DoiIncrementalClassifier() {
    }

    public static IncrementalReason classifyCrossref(WorkSyncState state, int refreshDays, OffsetDateTime now) {
        return classify(state, refreshDays, now, CROSSREF_RETRYABLE, CROSSREF_STALE_OK, CROSSREF_TERMINAL);
    }

    public static IncrementalReason classifyUnpaywall(WorkSyncState state, int refreshDays, OffsetDateTime now) {
        return classify(state, refreshDays, now, UNPAYWALL_RETRYABLE, UNPAYWALL_STALE_OK, Set.of());
    }

    public static IncrementalReason classifyOpenAlex(WorkSyncState state, int refreshDays, OffsetDateTime now) {
        return classify(state, refreshDays, now, OPENALEX_RETRYABLE, OPENALEX_STALE_OK, OPENALEX_TERMINAL);
    }

    public static IncrementalReason classify(
            WorkSyncState state,
            int refreshDays,
            OffsetDateTime now,
            Set<String> retryable,
            Set<String> staleOk,
            Set<String> terminal
    ) {
        if (state == null || !state.present) {
            return IncrementalReason.NEW;
        }
        IncrementalReason reason = null;
        if (state.needsRefresh) {
            reason = IncrementalReason.highest(reason, IncrementalReason.MANUAL_REFRESH);
        }
        if (state.lastSyncAt == null) {
            reason = IncrementalReason.highest(reason, IncrementalReason.NEVER_SYNCED);
        }
        String status = state.status == null ? "" : state.status.trim().toUpperCase();
        if (retryable.contains(status)) {
            reason = IncrementalReason.highest(reason, IncrementalReason.REMOTE_ERROR);
        }
        if (!state.mapped && !terminal.contains(status)) {
            reason = IncrementalReason.highest(reason, IncrementalReason.SOURCE_CHANGED);
        }
        if (refreshDays > 0
                && state.lastSyncAt != null
                && staleOk.contains(status)
                && !state.lastSyncAt.isAfter(now.minusDays(refreshDays))) {
            reason = IncrementalReason.highest(reason, IncrementalReason.STALE);
        }
        return reason;
    }
}
