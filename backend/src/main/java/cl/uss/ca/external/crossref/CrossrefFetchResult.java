package cl.uss.ca.external.crossref;

public class CrossrefFetchResult {
    public String status;
    public int httpStatus;
    public String errorCode;
    public String errorMessage;
    public CrossrefWorkRecord work;

    public static CrossrefFetchResult found(int httpStatus, CrossrefWorkRecord work) {
        CrossrefFetchResult result = new CrossrefFetchResult();
        result.status = CrossrefStatus.FOUND;
        result.httpStatus = httpStatus;
        result.work = work;
        return result;
    }

    public static CrossrefFetchResult otherAgency(int httpStatus, String agencyId, String agencyLabel) {
        CrossrefFetchResult result = new CrossrefFetchResult();
        result.status = CrossrefStatus.OTHER_AGENCY;
        result.httpStatus = httpStatus;
        result.work = new CrossrefWorkRecord();
        result.work.registrationAgencyId = agencyId;
        result.work.registrationAgency = agencyLabel;
        return result;
    }

    public static CrossrefFetchResult notFound(int httpStatus) {
        return failure(CrossrefStatus.NOT_FOUND, httpStatus, "CROSSREF_NOT_FOUND", "Crossref did not find the DOI");
    }

    public static CrossrefFetchResult invalid(String message) {
        return failure(CrossrefStatus.INVALID_DOI, 0, "CROSSREF_INVALID_DOI", message);
    }

    public static CrossrefFetchResult failure(String status, int httpStatus, String code, String message) {
        CrossrefFetchResult result = new CrossrefFetchResult();
        result.status = status;
        result.httpStatus = httpStatus;
        result.errorCode = code;
        result.errorMessage = message;
        return result;
    }

    public boolean found() {
        return CrossrefStatus.FOUND.equals(status);
    }

    public boolean retryable() {
        return CrossrefStatus.ERROR.equals(status) || CrossrefStatus.RATE_LIMITED.equals(status);
    }
}
