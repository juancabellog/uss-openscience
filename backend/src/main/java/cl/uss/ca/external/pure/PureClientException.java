package cl.uss.ca.external.pure;

public class PureClientException extends RuntimeException {
    private final int statusCode;

    public PureClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public PureClientException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
