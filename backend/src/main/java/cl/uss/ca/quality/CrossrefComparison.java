package cl.uss.ca.quality;

import cl.uss.ca.domain.AuthorNameMatcher;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * External consistency helpers. Threshold documented: Jaccard of title tokens &lt; 0.55
 * after Unicode/whitespace/entity/punctuation normalization is a significant difference.
 */
public final class CrossrefComparison {
    public static final double TITLE_JACCARD_THRESHOLD = 0.55;
    private static final Pattern PUNCT = Pattern.compile("[\\p{Punct}¿¡«»]+");

    private CrossrefComparison() {
    }

    public static String normalizeComparable(String value) {
        if (value == null) {
            return "";
        }
        String decoded = DataQualityText.decodeUnequivocalEntities(value);
        String working = decoded == null ? value : decoded;
        working = DataQualityText.collapseWhitespace(working);
        if (working == null) {
            return "";
        }
        working = Normalizer.normalize(working, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        working = PUNCT.matcher(working).replaceAll(" ");
        return DataQualityText.collapseWhitespace(working);
    }

    public static boolean titlesDiffer(String pureTitle, String crossrefTitle) {
        String left = normalizeComparable(pureTitle);
        String right = normalizeComparable(crossrefTitle);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (left.equals(right)) {
            return false;
        }
        return jaccard(tokens(left), tokens(right)) < TITLE_JACCARD_THRESHOLD;
    }

    public static boolean containersDiffer(String pureJournal, String crossrefContainer) {
        String left = normalizeComparable(pureJournal);
        String right = normalizeComparable(crossrefContainer);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        return !left.equals(right) && jaccard(tokens(left), tokens(right)) < 0.6;
    }

    public static boolean authorsDiffer(List<String> pureNames, List<String> crossrefNames) {
        if (pureNames == null || crossrefNames == null || pureNames.isEmpty() || crossrefNames.isEmpty()) {
            return false;
        }
        Set<String> left = new HashSet<>();
        for (String name : pureNames) {
            String normalized = normalizeComparable(AuthorNameMatcher.displayName(name, null));
            if (normalized.isBlank()) {
                normalized = normalizeComparable(name);
            }
            if (!normalized.isBlank()) {
                left.add(normalized);
            }
        }
        Set<String> right = new HashSet<>();
        for (String name : crossrefNames) {
            String normalized = normalizeComparable(name);
            if (!normalized.isBlank()) {
                right.add(normalized);
            }
        }
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        int overlap = 0;
        for (String name : left) {
            if (right.contains(name) || containsLoose(right, name)) {
                overlap++;
            }
        }
        return overlap == 0 || overlap < Math.min(left.size(), right.size()) / 2.0;
    }

    private static boolean containsLoose(Set<String> haystack, String needle) {
        for (String item : haystack) {
            if (item.contains(needle) || needle.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> tokens(String value) {
        Set<String> tokens = new HashSet<>();
        for (String part : value.split(" ")) {
            if (part.length() > 1) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1;
        }
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        Set<String> inter = new HashSet<>(left);
        inter.retainAll(right);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }
}
