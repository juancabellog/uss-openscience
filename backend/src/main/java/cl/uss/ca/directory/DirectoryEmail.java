package cl.uss.ca.directory;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Email is an operational attribute, not directory identity.
 */
public final class DirectoryEmail {
    private static final Pattern SIMPLE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private DirectoryEmail() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC).trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("\\s+", "");
        return value.isEmpty() ? null : value;
    }

    public static boolean isUsable(String normalized) {
        return normalized != null && SIMPLE.matcher(normalized).matches();
    }
}
