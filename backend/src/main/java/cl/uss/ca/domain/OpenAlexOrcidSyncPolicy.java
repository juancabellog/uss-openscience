package cl.uss.ca.domain;

/**
 * Backend authority for OpenAlex ORCID comparison and PURE write eligibility.
 * STRONG is never enough to write ORCID.
 */
public final class OpenAlexOrcidSyncPolicy {
    public static final String COMPARISON_MATCH = "MATCH";
    public static final String COMPARISON_CONFLICT = "CONFLICT";
    public static final String COMPARISON_OPENALEX_ONLY = "OPENALEX_ONLY";
    public static final String COMPARISON_PURE_ONLY = "PURE_ONLY";
    public static final String COMPARISON_NONE = "NONE";

    public static final String BLOCKED_NOT_CONFIRMED = "MATCH_NOT_CONFIRMED";
    public static final String BLOCKED_REJECTED = "MATCH_REJECTED";
    public static final String BLOCKED_PURE_HAS_ORCID = "PURE_HAS_ORCID";
    public static final String BLOCKED_CONFLICT = "ORCID_CONFLICT";
    public static final String BLOCKED_MISSING = "OPENALEX_ORCID_MISSING";
    public static final String BLOCKED_INVALID = "OPENALEX_ORCID_INVALID";

    private OpenAlexOrcidSyncPolicy() {
    }

    public static Decision evaluate(String matchStatus, String pureOrcid, String openalexOrcid) {
        Decision decision = new Decision();
        decision.identityConfirmed = OpenAlexPersonMatcher.STATUS_CONFIRMED.equals(matchStatus);
        String pure = OrcidFormat.normalize(pureOrcid);
        String openalex = OrcidFormat.normalize(openalexOrcid);
        boolean purePresent = pure != null;
        boolean openalexPresent = openalex != null;
        boolean openalexValid = OrcidFormat.isValid(openalex);
        if (purePresent && openalexPresent) {
            decision.comparison = pure.equals(openalex) ? COMPARISON_MATCH : COMPARISON_CONFLICT;
        } else if (openalexPresent) {
            decision.comparison = COMPARISON_OPENALEX_ONLY;
        } else if (purePresent) {
            decision.comparison = COMPARISON_PURE_ONLY;
        } else {
            decision.comparison = COMPARISON_NONE;
        }
        if (OpenAlexPersonMatcher.STATUS_REJECTED.equals(matchStatus)) {
            decision.blockedReason = BLOCKED_REJECTED;
        } else if (!decision.identityConfirmed) {
            decision.blockedReason = BLOCKED_NOT_CONFIRMED;
        } else if (decision.comparison.equals(COMPARISON_MATCH) || decision.comparison.equals(COMPARISON_PURE_ONLY)) {
            decision.blockedReason = BLOCKED_PURE_HAS_ORCID;
        } else if (decision.comparison.equals(COMPARISON_CONFLICT)) {
            decision.blockedReason = BLOCKED_CONFLICT;
        } else if (!openalexPresent) {
            decision.blockedReason = BLOCKED_MISSING;
        } else if (!openalexValid) {
            decision.blockedReason = BLOCKED_INVALID;
        }
        decision.syncAvailable = decision.identityConfirmed
                && COMPARISON_OPENALEX_ONLY.equals(decision.comparison)
                && openalexValid;
        if (decision.syncAvailable) {
            decision.blockedReason = null;
        }
        return decision;
    }

    public static boolean authorConfirmedForOtherPerson(Long currentPersonId, Long confirmedPersonId) {
        return confirmedPersonId != null && currentPersonId != null && !confirmedPersonId.equals(currentPersonId);
    }

    public static final class Decision {
        public boolean identityConfirmed;
        public String comparison = COMPARISON_NONE;
        public boolean syncAvailable;
        public String blockedReason;
    }
}
