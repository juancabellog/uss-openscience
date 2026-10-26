package cl.uss.ca.external.openalex;

public final class OpenAlexStatus {
    public static final String FOUND = "FOUND";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String INVALID_DOI = "INVALID_DOI";
    public static final String ERROR = "ERROR";
    public static final String DISABLED = "DISABLED";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    private OpenAlexStatus() {
    }

    public static boolean remoteOutcome(String status) {
        return FOUND.equals(status) || NOT_FOUND.equals(status);
    }

    public static boolean skipRemote(String status) {
        return INVALID_DOI.equals(status) || DISABLED.equals(status);
    }
}
