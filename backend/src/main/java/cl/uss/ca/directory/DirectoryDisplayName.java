package cl.uss.ca.directory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public final class DirectoryDisplayName {
    private DirectoryDisplayName() {
    }

    public static String of(String given, String middle, String family, String secondFamily) {
        List<String> parts = new ArrayList<>();
        add(parts, given);
        add(parts, middle);
        add(parts, family);
        add(parts, secondFamily);
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static void add(List<String> parts, String raw) {
        if (raw == null) {
            return;
        }
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC).trim();
        if (!value.isEmpty()) {
            parts.add(value);
        }
    }
}
