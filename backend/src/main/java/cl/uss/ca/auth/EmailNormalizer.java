package cl.uss.ca.auth;

import java.util.Locale;

public final class EmailNormalizer {
    private EmailNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    public static boolean looksLikeEmail(String raw) {
        String value = normalize(raw);
        return value != null && value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    }
}
