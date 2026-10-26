package cl.uss.ca.domain;

import java.util.Locale;

public final class DoiNormalizer {
    private DoiNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        value = strip(value, "https://doi.org/");
        value = strip(value, "http://doi.org/");
        value = strip(value, "https://dx.doi.org/");
        value = strip(value, "http://dx.doi.org/");
        value = strip(value, "doi:");
        value = strip(value, "doi.org/");
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static String strip(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }
}
