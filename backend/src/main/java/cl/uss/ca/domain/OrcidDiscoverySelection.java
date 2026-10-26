package cl.uss.ca.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ORCID discovery row selection: which candidate is chosen versus whether
 * the row is queued for PURE apply. Does not change STRONG vs MANUAL_REVIEW.
 */
public final class OrcidDiscoverySelection {

    public static final String ORIGIN_AUTO_STRONG = "AUTO_STRONG";
    public static final String ORIGIN_MANUAL = "MANUAL";

    public static final String DISABLED_CHOOSE_CANDIDATE = "CHOOSE_CANDIDATE";
    public static final String DISABLED_NO_CANDIDATES = "NO_CANDIDATES";
    public static final String DISABLED_ALREADY_USED = "ALREADY_USED";
    public static final String DISABLED_MULTIPLE_STRONG = "MULTIPLE_STRONG";
    public static final String DISABLED_CONFLICT = "CONFLICT";
    public static final String DISABLED_REMOTE_CHANGED = "REMOTE_CHANGED";
    public static final String DISABLED_SYNCED = "SYNCED";
    public static final String DISABLED_TOO_MANY = "TOO_MANY";

    private OrcidDiscoverySelection() {
    }

    public static boolean terminal(String personStatus) {
        return "SYNCED".equals(personStatus)
                || "SYNC_FAILED".equals(personStatus)
                || "REMOTE_CHANGED".equals(personStatus)
                || "ALREADY_USED".equals(personStatus);
    }

    public static boolean hasStoredCandidates(String personStatus, int candidateCount) {
        return !"TOO_MANY_CANDIDATES".equals(personStatus)
                && !"NO_CANDIDATE".equals(personStatus)
                && !"ERROR".equals(personStatus)
                && candidateCount > 0;
    }

    /**
     * Checkbox may be used when a unique STRONG candidate exists, or when the
     * user has already chosen a persisted candidate (MANUAL_REVIEW / CONFLICT).
     */
    public static boolean checkboxEnabled(
            String personStatus,
            Long selectedCandidateId,
            boolean uniqueValidStrong,
            int candidateCount
    ) {
        if (terminal(personStatus) || "ERROR".equals(personStatus)) {
            return selectedCandidateId != null;
        }
        if ("NO_CANDIDATE".equals(personStatus) || "TOO_MANY_CANDIDATES".equals(personStatus)) {
            return false;
        }
        if (selectedCandidateId != null) {
            return true;
        }
        if ("STRONG".equals(personStatus) && uniqueValidStrong) {
            return true;
        }
        return false;
    }

    public static String disabledReason(
            String personStatus,
            Long selectedCandidateId,
            boolean uniqueValidStrong,
            int candidateCount
    ) {
        if (checkboxEnabled(personStatus, selectedCandidateId, uniqueValidStrong, candidateCount)) {
            return null;
        }
        if ("SYNCED".equals(personStatus)) {
            return DISABLED_SYNCED;
        }
        if ("ALREADY_USED".equals(personStatus)) {
            return DISABLED_ALREADY_USED;
        }
        if ("REMOTE_CHANGED".equals(personStatus) || "SYNC_FAILED".equals(personStatus)) {
            return DISABLED_REMOTE_CHANGED;
        }
        if ("TOO_MANY_CANDIDATES".equals(personStatus)) {
            return DISABLED_TOO_MANY;
        }
        if ("NO_CANDIDATE".equals(personStatus) || candidateCount <= 0) {
            return DISABLED_NO_CANDIDATES;
        }
        if ("CONFLICT".equals(personStatus) && selectedCandidateId == null) {
            return uniqueValidStrong ? DISABLED_MULTIPLE_STRONG : DISABLED_MULTIPLE_STRONG;
        }
        if (("MANUAL_REVIEW".equals(personStatus) || "CONFLICT".equals(personStatus))
                && selectedCandidateId == null) {
            return DISABLED_CHOOSE_CANDIDATE;
        }
        return DISABLED_CHOOSE_CANDIDATE;
    }

    public static String originForStatus(String personStatus) {
        return "STRONG".equals(personStatus) ? ORIGIN_AUTO_STRONG : ORIGIN_MANUAL;
    }

    public static boolean candidateSelectable(String matchLevel, String status, Long alreadyUsedPersonId) {
        if (alreadyUsedPersonId != null || "ALREADY_USED".equals(status)) {
            return false;
        }
        return !"CONFLICT".equals(matchLevel) && !"CONFLICT".equals(status);
    }

    public static List<Long> uniquePersonIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                unique.add(id);
            }
        }
        return new ArrayList<>(unique);
    }
}
