package cl.uss.ca.domain;

import cl.uss.ca.external.openalex.OpenAlexWorkRecord;
import cl.uss.ca.quality.DataQualityIdentifiers;

/**
 * OpenAlex Work identity is {@code openalex_work_id} after a successful fetch.
 * The PURE lookup DOI is a discovery key, not the Work primary key.
 */
public final class OpenAlexWorkIdentity {
    public static final String DOI_OA_ID_CONFLICT = "OPENALEX_DOI_OA_ID_CONFLICT";

    private OpenAlexWorkIdentity() {
    }

    public static String canonicalNormalizedDoi(String lookupNormalized, OpenAlexWorkRecord work) {
        String fromWork = DoiNormalizer.normalize(work == null ? null : work.doi);
        if (fromWork != null && DataQualityIdentifiers.isValidDoi(fromWork)) {
            return fromWork.toLowerCase(java.util.Locale.ROOT);
        }
        if (lookupNormalized == null || lookupNormalized.isBlank()) {
            return null;
        }
        return lookupNormalized.toLowerCase(java.util.Locale.ROOT);
    }

    public static String displayDoi(String lookupDisplay, OpenAlexWorkRecord work) {
        if (work != null && work.doi != null && !work.doi.isBlank()) {
            return work.doi;
        }
        return lookupDisplay;
    }

    public static Decision decide(
            Snapshot byOpenAlexId,
            Snapshot byCanonicalDoi,
            Snapshot byLookupDoi,
            String incomingOpenAlexId
    ) {
        if (incomingOpenAlexId != null && byOpenAlexId != null) {
            return Decision.reuse(byOpenAlexId, false);
        }
        Snapshot existing = firstNonNull(byCanonicalDoi, byLookupDoi);
        if (existing == null) {
            return Decision.insert();
        }
        if (incomingOpenAlexId != null
                && existing.openalexWorkId != null
                && !incomingOpenAlexId.equals(existing.openalexWorkId)) {
            return Decision.conflict(existing);
        }
        return Decision.reuse(existing, incomingOpenAlexId != null && existing.openalexWorkId == null);
    }

    private static Snapshot firstNonNull(Snapshot left, Snapshot right) {
        if (left != null) {
            return left;
        }
        return right;
    }

    public static final class Snapshot {
        public long id;
        public String openalexWorkId;
        public String normalizedDoi;

        public static Snapshot of(long id, String openalexWorkId, String normalizedDoi) {
            Snapshot snapshot = new Snapshot();
            snapshot.id = id;
            snapshot.openalexWorkId = openalexWorkId;
            snapshot.normalizedDoi = normalizedDoi;
            return snapshot;
        }
    }

    public static final class Decision {
        public enum Action {
            INSERT,
            REUSE,
            CONFLICT
        }

        public final Action action;
        public final Snapshot existing;
        public final boolean assignOpenAlexId;

        private Decision(Action action, Snapshot existing, boolean assignOpenAlexId) {
            this.action = action;
            this.existing = existing;
            this.assignOpenAlexId = assignOpenAlexId;
        }

        static Decision insert() {
            return new Decision(Action.INSERT, null, false);
        }

        static Decision reuse(Snapshot existing, boolean assignOpenAlexId) {
            return new Decision(Action.REUSE, existing, assignOpenAlexId);
        }

        static Decision conflict(Snapshot existing) {
            return new Decision(Action.CONFLICT, existing, false);
        }
    }
}
