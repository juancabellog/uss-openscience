package cl.uss.ca.exception;

import java.util.ArrayList;
import java.util.List;

public class ValidationException extends RuntimeException {
    private final String code;
    private final String field;
    private final List<FieldViolation> violations;

    public ValidationException(String code, String message) {
        this(code, message, null, List.of(), null);
    }

    public ValidationException(String code, String message, Throwable cause) {
        this(code, message, null, List.of(), cause);
    }

    public ValidationException(List<FieldViolation> violations) {
        this(firstCode(violations), firstMessage(violations), firstField(violations), copy(violations), null);
    }

    private ValidationException(
            String code,
            String message,
            String field,
            List<FieldViolation> violations,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.field = field;
        this.violations = violations;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }

    private static String firstCode(List<FieldViolation> violations) {
        return violations == null || violations.isEmpty() ? "VALIDATION_ERROR" : violations.get(0).code();
    }

    private static String firstMessage(List<FieldViolation> violations) {
        return violations == null || violations.isEmpty() ? "Invalid request" : violations.get(0).message();
    }

    private static String firstField(List<FieldViolation> violations) {
        return violations == null || violations.isEmpty() ? null : violations.get(0).field();
    }

    private static List<FieldViolation> copy(List<FieldViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(violations));
    }
}
