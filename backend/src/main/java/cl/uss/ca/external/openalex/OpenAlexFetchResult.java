package cl.uss.ca.external.openalex;

public class OpenAlexFetchResult {
    public String status;
    public int httpStatus;
    public String errorCode;
    public String errorMessage;
    public OpenAlexWorkRecord work;
    public OpenAlexAuthorRecord author;

    public static OpenAlexFetchResult found(int httpStatus, OpenAlexWorkRecord work) {
        OpenAlexFetchResult result = new OpenAlexFetchResult();
        result.status = OpenAlexStatus.FOUND;
        result.httpStatus = httpStatus;
        result.work = work;
        return result;
    }

    public static OpenAlexFetchResult authorFound(int httpStatus, OpenAlexAuthorRecord author) {
        OpenAlexFetchResult result = new OpenAlexFetchResult();
        result.status = OpenAlexStatus.FOUND;
        result.httpStatus = httpStatus;
        result.author = author;
        return result;
    }

    public static OpenAlexFetchResult notFound(int httpStatus) {
        return failure(OpenAlexStatus.NOT_FOUND, httpStatus, "OPENALEX_NOT_FOUND", "OpenAlex did not find the DOI");
    }

    public static OpenAlexFetchResult invalid(String message) {
        return failure(OpenAlexStatus.INVALID_DOI, 0, "OPENALEX_INVALID_DOI", message);
    }

    public static OpenAlexFetchResult failure(String status, int httpStatus, String code, String message) {
        OpenAlexFetchResult result = new OpenAlexFetchResult();
        result.status = status;
        result.httpStatus = httpStatus;
        result.errorCode = code;
        result.errorMessage = message;
        return result;
    }

    public boolean found() {
        return OpenAlexStatus.FOUND.equals(status);
    }

    public boolean retryable() {
        return OpenAlexStatus.ERROR.equals(status) || OpenAlexStatus.RATE_LIMITED.equals(status);
    }
}
