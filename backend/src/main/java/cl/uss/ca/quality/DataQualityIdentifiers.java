package cl.uss.ca.quality;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class DataQualityIdentifiers {
    private static final Pattern ORCID = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]$");
    private static final Pattern DOI = Pattern.compile("^10\\.\\d{4,9}/[-._;()/:A-Z0-9]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOI_PREFIX = Pattern.compile("^(https?://)?(dx\\.)?doi\\.org/", Pattern.CASE_INSENSITIVE);

    private DataQualityIdentifiers() {
    }

    public static String normalizeOrcid(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        trimmed = trimmed.replaceFirst("(?i)^https?://orcid\\.org/", "");
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    public static boolean isValidOrcid(String value) {
        String normalized = normalizeOrcid(value);
        return normalized != null && ORCID.matcher(normalized).matches();
    }

    public static List<String> uniqueNormalizedOrcids(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalizeOrcid(value);
            if (normalized != null) {
                unique.add(normalized);
            }
        }
        return unique.isEmpty() ? List.of() : List.copyOf(unique);
    }

    public static String normalizeDoi(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        trimmed = DOI_PREFIX.matcher(trimmed).replaceFirst("");
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean isValidDoi(String value) {
        String normalized = normalizeDoi(value);
        return normalized != null && DOI.matcher(normalized).matches();
    }

    public static PageRange parsePageRange(String pages) {
        if (pages == null || pages.isBlank()) {
            return null;
        }
        String trimmed = pages.strip();
        if (looksLikeArticleNumber(trimmed)) {
            return PageRange.articleNumber(trimmed);
        }
        String[] parts = trimmed.split("\\s*[-–—]\\s*");
        if (parts.length != 2) {
            return null;
        }
        Integer start = parsePositiveInt(parts[0]);
        Integer end = parsePositiveInt(parts[1]);
        if (start == null || end == null) {
            return null;
        }
        return new PageRange(trimmed, start, end, false);
    }

    public static boolean looksLikeArticleNumber(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.strip();
        return trimmed.matches("(?i)e\\d+") || trimmed.matches("(?i)art\\.?\\s*\\d+");
    }

    public static boolean isInterpretablePageCount(String value) {
        return parsePositiveInt(value) != null;
    }

    public static Integer parsePositiveInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.strip().replace(" ", "");
        if (!digits.matches("\\d{1,6}")) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(digits);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static boolean yearInRange(Integer year, int currentYear) {
        return year != null && year >= 1900 && year <= currentYear;
    }

    public record PageRange(String raw, Integer start, Integer end, boolean articleNumber) {
        static PageRange articleNumber(String raw) {
            return new PageRange(raw, null, null, true);
        }
    }
}
