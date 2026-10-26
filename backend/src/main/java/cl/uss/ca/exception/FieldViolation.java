package cl.uss.ca.exception;

public record FieldViolation(String field, String code, String message) {
}
