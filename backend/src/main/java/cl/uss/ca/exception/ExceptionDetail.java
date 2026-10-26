package cl.uss.ca.exception;

import java.sql.SQLException;

public final class ExceptionDetail {
    private ExceptionDetail() {
    }

    public static String describe(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        StringBuilder out = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) {
                out.append(" | caused by ");
            }
            out.append(current.getClass().getSimpleName());
            if (current instanceof SQLException sql) {
                out.append(" SQLState=").append(sql.getSQLState());
                out.append(" vendor=").append(sql.getErrorCode());
            }
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                out.append(": ").append(current.getMessage().trim());
            }
            current = current.getCause();
            depth++;
        }
        return out.length() <= 1500 ? out.toString() : out.substring(0, 1500);
    }
}
