package cl.uss.ca.exception;

import cl.uss.ca.audit.AuditSanitizer;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Technical ERROR boundary. Passes the Throwable to JUL so OpenLiberty
 * prints the full stack. Does not stringify stack traces.
 */
public final class ErrorLog {
    private ErrorLog() {
    }

    public static void unexpected(Logger logger, String context, Throwable error) {
        if (logger == null) {
            return;
        }
        logger.log(Level.SEVERE, AuditSanitizer.redact(context), error);
    }
}
