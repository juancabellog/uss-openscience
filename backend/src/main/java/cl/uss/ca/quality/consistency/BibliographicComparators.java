package cl.uss.ca.quality.consistency;

import cl.uss.ca.quality.CrossrefComparison;
import cl.uss.ca.quality.DataQualityIdentifiers;
import cl.uss.ca.quality.DataQualityText;

import java.util.List;
import java.util.Locale;

public final class BibliographicComparators {
    private BibliographicComparators() {
    }

    public static ComparisonResult title(String pure, String external) {
        if (blank(pure) || blank(external)) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        return CrossrefComparison.titlesDiffer(pure, external)
                ? ComparisonResult.DIFFERENCE_DETECTED
                : ComparisonResult.CONSISTENT;
    }

    public static ComparisonResult year(Integer pure, Integer external) {
        if (pure == null || external == null) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        return pure.equals(external) ? ComparisonResult.CONSISTENT : ComparisonResult.DIFFERENCE_DETECTED;
    }

    public static ComparisonResult journal(String pure, String external) {
        if (blank(pure) || blank(external)) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        return CrossrefComparison.containersDiffer(pure, external)
                ? ComparisonResult.DIFFERENCE_DETECTED
                : ComparisonResult.CONSISTENT;
    }

    public static ComparisonResult issn(List<String> pure, List<String> external) {
        if (empty(pure) || empty(external)) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        return IssnNormalizer.anyMatch(pure, external)
                ? ComparisonResult.CONSISTENT
                : ComparisonResult.DIFFERENCE_DETECTED;
    }

    public static ComparisonResult exactText(String pure, String external) {
        String left = normalizeExact(pure);
        String right = normalizeExact(external);
        if (left.isEmpty() || right.isEmpty()) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        return left.equals(right) ? ComparisonResult.CONSISTENT : ComparisonResult.DIFFERENCE_DETECTED;
    }

    public static ComparisonResult pages(String purePages, String externalPages, String externalArticleNumber) {
        LocatorKind pureKind = locatorKind(purePages, null);
        LocatorKind externalKind = locatorKind(externalPages, externalArticleNumber);
        if (pureKind == LocatorKind.UNKNOWN || externalKind == LocatorKind.UNKNOWN) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        if (pureKind != externalKind) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        if (pureKind == LocatorKind.ARTICLE_NUMBER) {
            return exactText(purePages, firstNonBlank(externalArticleNumber, externalPages));
        }
        return exactText(purePages, externalPages);
    }

    public static ComparisonResult publisher(String pure, String external) {
        return exactText(pure, external);
    }

    public static ComparisonResult access(AccessSemantic pure, AccessSemantic external) {
        if (pure == null || external == null
                || pure == AccessSemantic.UNKNOWN
                || external == AccessSemantic.UNKNOWN) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        return pure == external ? ComparisonResult.CONSISTENT : ComparisonResult.DIFFERENCE_DETECTED;
    }

    public static ComparisonResult license(String pureRaw, String externalRaw) {
        String left = LicenseNormalizer.normalize(pureRaw);
        String right = LicenseNormalizer.normalize(externalRaw);
        if (left == null || right == null) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        return left.equals(right) ? ComparisonResult.CONSISTENT : ComparisonResult.DIFFERENCE_DETECTED;
    }

    public static ComparisonResult awards(List<String> pure, List<String> external) {
        if (empty(pure) || empty(external)) {
            return ComparisonResult.NOT_COMPARABLE;
        }
        for (String award : pure) {
            String normalized = normalizeExact(award);
            if (normalized.isEmpty()) {
                continue;
            }
            for (String other : external) {
                if (normalized.equals(normalizeExact(other))) {
                    return ComparisonResult.CONSISTENT;
                }
            }
        }
        return ComparisonResult.DIFFERENCE_DETECTED;
    }

    public static LocatorKind locatorKind(String pages, String articleNumber) {
        if (!blank(articleNumber) && blank(pages)) {
            return LocatorKind.ARTICLE_NUMBER;
        }
        if (DataQualityIdentifiers.looksLikeArticleNumber(pages)) {
            return LocatorKind.ARTICLE_NUMBER;
        }
        if (!blank(pages) && DataQualityIdentifiers.parsePageRange(pages) != null
                && !DataQualityIdentifiers.parsePageRange(pages).articleNumber()) {
            return LocatorKind.PAGES;
        }
        if (!blank(pages)) {
            return LocatorKind.UNKNOWN;
        }
        return LocatorKind.UNKNOWN;
    }

    public static String normalizeExact(String value) {
        if (value == null) {
            return "";
        }
        String collapsed = DataQualityText.collapseWhitespace(value);
        if (collapsed == null) {
            return "";
        }
        return java.text.Normalizer.normalize(collapsed, java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    public static String display(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(" | ", values);
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static boolean empty(List<String> values) {
        if (values == null || values.isEmpty()) {
            return true;
        }
        for (String value : values) {
            if (!blank(value)) {
                return false;
            }
        }
        return true;
    }

    private static String firstNonBlank(String left, String right) {
        return blank(left) ? right : left;
    }
}
