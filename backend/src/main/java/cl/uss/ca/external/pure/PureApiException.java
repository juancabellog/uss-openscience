package cl.uss.ca.external.pure;

public class PureApiException extends RuntimeException {
    private final int statusCode;

    public PureApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public PureApiException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isTooManyRequests() {
        return statusCode == 429;
    }
}
