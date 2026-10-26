package cl.uss.ca.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class OrcidFormat {
    private static final Pattern PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]$");

    private OrcidFormat() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://orcid.org/")) {
            value = value.substring("https://orcid.org/".length());
        } else if (lower.startsWith("http://orcid.org/")) {
            value = value.substring("http://orcid.org/".length());
        }
        value = value.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String normalized) {
        return normalized != null && PATTERN.matcher(normalized).matches();
    }

    public static String normalizeValidOrNull(String raw) {
        String normalized = normalize(raw);
        return isValid(normalized) ? normalized : null;
    }
}
