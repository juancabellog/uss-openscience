package cl.uss.ca.external.unpaywall;

public class UnpaywallFetchResult {
    public static final String FOUND = "FOUND";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String INVALID = "INVALID";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String ERROR = "ERROR";

    public String outcome;
    public int httpStatus;
    public String errorCode;
    public String errorMessage;
    public UnpaywallWorkRecord work;

    public static UnpaywallFetchResult found(int httpStatus, UnpaywallWorkRecord work) {
        UnpaywallFetchResult result = new UnpaywallFetchResult();
        result.outcome = FOUND;
        result.httpStatus = httpStatus;
        result.work = work;
        return result;
    }

    public static UnpaywallFetchResult notFound(int httpStatus) {
        UnpaywallFetchResult result = new UnpaywallFetchResult();
        result.outcome = NOT_FOUND;
        result.httpStatus = httpStatus;
        result.errorCode = "UNPAYWALL_NOT_FOUND";
        result.errorMessage = "Unpaywall did not find the DOI";
        return result;
    }

    public static UnpaywallFetchResult failure(String outcome, int httpStatus, String code, String message) {
        UnpaywallFetchResult result = new UnpaywallFetchResult();
        result.outcome = outcome;
        result.httpStatus = httpStatus;
        result.errorCode = code;
        result.errorMessage = message;
        return result;
    }

    public boolean found() {
        return FOUND.equals(outcome);
    }

    public boolean notFound() {
        return NOT_FOUND.equals(outcome);
    }
}
