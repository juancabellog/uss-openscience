package cl.uss.ca.quality.consistency;

import java.util.Locale;

public final class IssnNormalizer {
    private IssnNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String compact = raw.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
        if (compact.length() != 8) {
            return compact.isEmpty() ? null : compact;
        }
        return compact;
    }

    public static boolean anyMatch(Iterable<String> left, Iterable<String> right) {
        if (left == null || right == null) {
            return false;
        }
        for (String a : left) {
            String na = normalize(a);
            if (na == null) {
                continue;
            }
            for (String b : right) {
                if (na.equals(normalize(b))) {
                    return true;
                }
            }
        }
        return false;
    }
}
