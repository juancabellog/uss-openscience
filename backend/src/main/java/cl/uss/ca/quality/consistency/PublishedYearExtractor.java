package cl.uss.ca.quality.consistency;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps only publication-like date strings to a year. Callers must not pass
 * created/deposited/indexed timestamps.
 */
public final class PublishedYearExtractor {
    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");

    private PublishedYearExtractor() {
    }

    public static Integer from(String... publishedValues) {
        if (publishedValues == null) {
            return null;
        }
        for (String value : publishedValues) {
            Integer year = fromOne(value);
            if (year != null) {
                return year;
            }
        }
        return null;
    }

    public static Integer fromOne(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = YEAR.matcher(value.trim());
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group());
    }
}
