package cl.uss.ca.exception;

public class ForbiddenException extends RuntimeException {
    private final String code;

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
