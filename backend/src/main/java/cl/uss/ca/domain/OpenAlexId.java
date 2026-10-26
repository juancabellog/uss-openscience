package cl.uss.ca.domain;

import java.util.Locale;

/**
 * Canonical OpenAlex entity IDs: {@code W...}, {@code A...}, {@code I...}, {@code T...}.
 */
public final class OpenAlexId {
    private OpenAlexId() {
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
        int slash = lower.lastIndexOf('/');
        if (slash >= 0 && slash < value.length() - 1) {
            value = value.substring(slash + 1);
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    public static String url(String id) {
        String normalized = normalize(id);
        return normalized == null ? null : "https://openalex.org/" + normalized;
    }
}
