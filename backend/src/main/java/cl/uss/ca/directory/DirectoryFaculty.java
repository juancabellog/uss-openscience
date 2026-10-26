package cl.uss.ca.directory;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Faculty/VR labels in the current matrix have a catalog prefix such as
 * {@code c. Facultad de Medicina}. The raw value is retained; the label drops
 * only that leading letter-dot prefix.
 */
public final class DirectoryFaculty {
    private static final Pattern PREFIX = Pattern.compile("^[a-zA-Z]\\.\\s+");

    private DirectoryFaculty() {
    }

    public static String normalizeRaw(String raw) {
        if (raw == null) {
            return null;
        }
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC).trim();
        return value.isEmpty() ? null : value;
    }

    public static String label(String raw) {
        String value = normalizeRaw(raw);
        if (value == null) {
            return null;
        }
        return PREFIX.matcher(value).replaceFirst("").trim();
    }
}
