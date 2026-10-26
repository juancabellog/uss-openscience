package cl.uss.ca.domain;

import cl.uss.ca.external.dspace.DspaceAccessClassifier;
import cl.uss.ca.quality.consistency.AccessMapper;
import cl.uss.ca.quality.consistency.AccessSemantic;

import java.util.List;
import java.util.Locale;

/**
 * Canonical access comparison for Gestionar DSpace vs PURE electronic versions.
 * Does not change EXT-09 / AccessMapper.fromDspace.
 */
public final class DspaceAccessComparison {
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
    public static final String RESTRICTED = "RESTRICTED";
    public static final String EMBARGOED = "EMBARGOED";
    public static final String METADATA_ONLY = "METADATA_ONLY";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String CONSISTENT = "CONSISTENT";
    public static final String DIFFERENCE = "DIFFERENCE";
    public static final String NOT_EVALUABLE = "NOT_EVALUABLE";

    private DspaceAccessComparison() {
    }

    public static String fromDspace(String accessStatus) {
        String status = DspaceAccessClassifier.normalizeStatus(accessStatus);
        if (status == null) {
            return UNKNOWN;
        }
        return switch (status) {
            case DspaceAccessClassifier.OPEN_ACCESS -> OPEN;
            case DspaceAccessClassifier.METADATA_ONLY -> METADATA_ONLY;
            case DspaceAccessClassifier.RESTRICTED -> RESTRICTED;
            case DspaceAccessClassifier.EMBARGO -> EMBARGOED;
            default -> UNKNOWN;
        };
    }

    public static String fromPure(String accessType) {
        AccessSemantic semantic = AccessMapper.fromPure(accessType);
        return switch (semantic) {
            case OPEN -> OPEN;
            case CLOSED -> CLOSED;
            case UNKNOWN -> UNKNOWN;
        };
    }

    public static String fromPureValues(List<String> accessTypes) {
        if (accessTypes == null || accessTypes.isEmpty()) {
            return UNKNOWN;
        }
        boolean closed = false;
        for (String value : accessTypes) {
            String canonical = fromPure(value);
            if (OPEN.equals(canonical)) {
                return OPEN;
            }
            if (CLOSED.equals(canonical)) {
                closed = true;
            }
        }
        return closed ? CLOSED : UNKNOWN;
    }

    public static String compare(String dspaceCanonical, String pureCanonical, boolean linked) {
        if (!linked) {
            return NOT_EVALUABLE;
        }
        if (blankOrUnknown(dspaceCanonical) || blankOrUnknown(pureCanonical)) {
            return NOT_EVALUABLE;
        }
        return collapse(dspaceCanonical).equals(collapse(pureCanonical)) ? CONSISTENT : DIFFERENCE;
    }

    public static String collapse(String canonical) {
        if (RESTRICTED.equals(canonical) || EMBARGOED.equals(canonical) || CLOSED.equals(canonical)) {
            return CLOSED;
        }
        return canonical;
    }

    public static String firstRaw(List<String> values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public static List<String> splitBundle(String bundle) {
        if (bundle == null || bundle.isBlank()) {
            return List.of();
        }
        return List.of(bundle.split("\\n"));
    }

    public static boolean isPublicDownload(boolean publicDownload, String accessStatus) {
        if (publicDownload) {
            return true;
        }
        return DspaceAccessClassifier.OPEN_ACCESS.equals(DspaceAccessClassifier.normalizeStatus(accessStatus));
    }

    private static boolean blankOrUnknown(String canonical) {
        return canonical == null || canonical.isBlank() || UNKNOWN.equals(canonical);
    }
}
