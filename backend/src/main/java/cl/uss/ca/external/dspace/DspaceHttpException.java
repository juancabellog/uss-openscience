package cl.uss.ca.external.dspace;

import cl.uss.ca.exception.ValidationException;

public class DspaceHttpException extends ValidationException {
    public final int httpStatus;
    public final String url;

    public DspaceHttpException(String code, String message, int httpStatus, String url) {
        super(code, message);
        this.httpStatus = httpStatus;
        this.url = url;
    }
}
