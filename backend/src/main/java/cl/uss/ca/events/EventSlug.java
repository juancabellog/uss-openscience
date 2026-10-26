package cl.uss.ca.events;

import java.text.Normalizer;
import java.util.Locale;

public final class EventSlug {
    private EventSlug() {
    }

    public static String fromTitle(String title) {
        String base = sanitize(title);
        return base.isEmpty() ? "evento" : base;
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 72) {
            slug = slug.substring(0, 72).replaceAll("-+$", "");
        }
        return slug;
    }

    public static boolean isValid(String slug) {
        return slug != null && slug.matches("^[a-z0-9][a-z0-9-]{0,71}$");
    }

    public static String withSuffix(String base, int attempt) {
        String root = sanitize(base);
        if (root.isEmpty()) {
            root = "evento";
        }
        if (attempt <= 1) {
            return root;
        }
        String suffix = "-" + attempt;
        int maxRoot = Math.max(1, 72 - suffix.length());
        if (root.length() > maxRoot) {
            root = root.substring(0, maxRoot).replaceAll("-+$", "");
        }
        return root + suffix;
    }
}
