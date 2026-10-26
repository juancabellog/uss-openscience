package cl.uss.ca.quality.consistency;

import java.util.Locale;

public final class LicenseNormalizer {
    private LicenseNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.contains("creativecommons.org/licenses/by-nc-nd")) {
            return "CC-BY-NC-ND";
        }
        if (value.contains("creativecommons.org/licenses/by-nc-sa")) {
            return "CC-BY-NC-SA";
        }
        if (value.contains("creativecommons.org/licenses/by-nc")) {
            return "CC-BY-NC";
        }
        if (value.contains("creativecommons.org/licenses/by-sa")) {
            return "CC-BY-SA";
        }
        if (value.contains("creativecommons.org/licenses/by-nd")) {
            return "CC-BY-ND";
        }
        if (value.contains("creativecommons.org/licenses/by/") || value.contains("creativecommons.org/licenses/by ")) {
            return "CC-BY";
        }
        if (value.contains("creativecommons.org/publicdomain/zero") || value.contains("cc0")) {
            return "CC0";
        }
        String compact = value.replace('_', '-').replace(' ', '-').toUpperCase(Locale.ROOT);
        if (compact.startsWith("CC-BY") || compact.equals("CC0") || compact.startsWith("CC-BY-")) {
            return compact;
        }
        if ("CC BY".equalsIgnoreCase(raw.trim()) || "CC-BY".equalsIgnoreCase(raw.trim())) {
            return "CC-BY";
        }
        return compact;
    }
}
