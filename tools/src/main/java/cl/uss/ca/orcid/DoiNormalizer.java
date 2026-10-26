package cl.uss.ca.orcid;

import java.util.Locale;

public final class DoiNormalizer {
    private DoiNormalizer() {
    }

    public static String normalizarDoi(String doi) {
        if (doi == null) {
            return null;
        }
        String value = doi.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        value = quitarPrefijo(value, "https://doi.org/");
        value = quitarPrefijo(value, "http://doi.org/");
        value = quitarPrefijo(value, "https://dx.doi.org/");
        value = quitarPrefijo(value, "http://dx.doi.org/");
        value = quitarPrefijo(value, "doi.org/");
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static String quitarPrefijo(String value, String prefijo) {
        if (value.startsWith(prefijo)) {
            return value.substring(prefijo.length());
        }
        return value;
    }
}
