package cl.uss.ca.external.crossref;

public final class CrossrefStatus {
    public static final String FOUND = "FOUND";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String OTHER_AGENCY = "OTHER_AGENCY";
    public static final String INVALID_DOI = "INVALID_DOI";
    public static final String ERROR = "ERROR";
    public static final String DISABLED = "DISABLED";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    public static final String COMPARE_MATCH = "MATCH";
    public static final String COMPARE_DIFFERENCE = "DIFFERENCE";
    public static final String COMPARE_NOT_APPLICABLE = "NOT_APPLICABLE";

    private CrossrefStatus() {
    }

    public static boolean remoteOutcome(String status) {
        return FOUND.equals(status) || NOT_FOUND.equals(status) || OTHER_AGENCY.equals(status);
    }

    public static boolean skipRemote(String status) {
        return INVALID_DOI.equals(status) || DISABLED.equals(status);
    }
}
