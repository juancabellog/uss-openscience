package cl.uss.ca.audit;

import java.util.regex.Pattern;

/**
 * Output-only redaction for audit payloads. Does not mutate persisted rows.
 */
public final class AuditSanitizer {
    static final String REDACTED = "[REDACTED]";

    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)([?&](?:token|api[_-]?key|apikey|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|passwd|authorization|auth|secret|key)=)[^&\\s#]*"
    );
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[a-z0-9._\\-+/=]+");
    private static final Pattern BASIC = Pattern.compile("(?i)(basic\\s+)[a-z0-9+/=]+");
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)(\"(?:password|passwd|pwd|api[_-]?key|apikey|access[_-]?token|refresh[_-]?token|client[_-]?secret|secret|authorization|private[_-]?key|connection[_-]?string)\"\\s*:\\s*\")(.*?)(\")"
    );
    private static final Pattern ASSIGNED_SECRET = Pattern.compile(
            "(?i)((?:password|passwd|pwd|api[_-]?key|apikey|access[_-]?token|refresh[_-]?token|client[_-]?secret|secret|authorization|private[_-]?key)\\s*[=:]\\s*)([^\\s,;\"']+)"
    );
    private static final Pattern JDBC_PASSWORD = Pattern.compile(
            "(?i)((?:jdbc:[^\\s]*|mysql:[^\\s]*)[^\\s]*[?&]password=)[^&\\s]*"
    );

    private AuditSanitizer() {
    }

    public static String redact(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String out = JDBC_PASSWORD.matcher(value).replaceAll("$1" + REDACTED);
        out = SENSITIVE_QUERY.matcher(out).replaceAll("$1" + REDACTED);
        out = BEARER.matcher(out).replaceAll("$1" + REDACTED);
        out = BASIC.matcher(out).replaceAll("$1" + REDACTED);
        out = JSON_SECRET.matcher(out).replaceAll("$1" + REDACTED + "$3");
        out = ASSIGNED_SECRET.matcher(out).replaceAll("$1" + REDACTED);
        return out;
    }
}
