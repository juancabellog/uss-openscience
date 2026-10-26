package cl.uss.ca.events;

import java.util.Locale;

public final class EventLanguage {
    public static final String ES = "es";
    public static final String EN = "en";
    public static final String ES_CL = "es-CL";
    public static final String EN_US = "en-US";

    private EventLanguage() {
    }

    public static String fromRequest(String locale, String language) {
        return normalize(firstNonBlank(locale, language));
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return ES;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if ("en".equals(value) || value.startsWith("en-")) {
            return EN;
        }
        if ("es".equals(value) || value.startsWith("es-")) {
            return ES;
        }
        return ES;
    }

    public static String localeTag(String raw) {
        return isEnglish(raw) ? EN_US : ES_CL;
    }

    public static boolean isEnglish(String raw) {
        return EN.equals(normalize(raw));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
