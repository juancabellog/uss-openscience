package cl.uss.ca.quality;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detección de calidad de texto. La corrección nunca se aplica aquí: solo propone.
 */
public final class DataQualityText {
    private static final Pattern REPEATED_SPACE = Pattern.compile("[\\u00A0\\u2007\\u202F ]{2,}");
    private static final Pattern ANY_SPACE = Pattern.compile("[\\u00A0\\u2007\\u202F\\t\\r\\n ]+");
    private static final Pattern NAMED_ENTITY = Pattern.compile("&([a-zA-Z]{2,12});");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]{1,6});");
    private static final Pattern HTML_TAG = Pattern.compile("(?i)</?[a-z][^>]*>");
    private static final Pattern CODE_ARTIFACT = Pattern.compile(
            "(?i)(<script|</script|href\\s*=|src\\s*=|\\{\\{|null\\b|undefined\\b|&nbsp;|<div|<span|<p>)"
    );
    private static final Pattern MOJIBAKE_MARK = Pattern.compile("Ã.|Â.|â€.|â„¢|Ã±|Ã¡|Ã©|Ã­|Ã³|Ãº");
    private static final Map<String, String> NAMED = namedEntities();

    private DataQualityText() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String collapseWhitespace(String value) {
        if (value == null) {
            return null;
        }
        String normalized = ANY_SPACE.matcher(value).replaceAll(" ").strip();
        return normalized.isEmpty() ? "" : normalized;
    }

    public static boolean hasRepeatableWhitespace(String value) {
        if (value == null) {
            return false;
        }
        if (!value.equals(value.strip())) {
            return true;
        }
        return REPEATED_SPACE.matcher(value).find() || value.indexOf('\t') >= 0;
    }

    public static boolean looksLikeHtml(String value) {
        return value != null && HTML_TAG.matcher(value).find();
    }

    public static boolean hasNamedOrNumericEntity(String value) {
        if (value == null) {
            return false;
        }
        return NAMED_ENTITY.matcher(value).find() || NUMERIC_ENTITY.matcher(value).find();
    }

    public static String decodeUnequivocalEntities(String value) {
        if (value == null || !hasNamedOrNumericEntity(value)) {
            return value;
        }
        String decoded = decodeNumeric(decodeNamed(value));
        if (hasNamedOrNumericEntity(decoded) || looksLikeHtml(decoded)) {
            return null;
        }
        return decoded;
    }

    public static boolean looksLikeMojibake(String value) {
        return value != null && MOJIBAKE_MARK.matcher(value).find();
    }

    public static String repairMojibakeIfReversible(String value) {
        if (!looksLikeMojibake(value)) {
            return null;
        }
        try {
            byte[] latin1 = value.getBytes(Charset.forName("ISO-8859-1"));
            String repaired = new String(latin1, StandardCharsets.UTF_8);
            if (repaired.equals(value) || looksLikeMojibake(repaired) || repaired.indexOf('\uFFFD') >= 0) {
                return null;
            }
            if (repaired.isBlank() || !isMostlyPrintable(repaired)) {
                return null;
            }
            return repaired;
        } catch (Exception ex) {
            return null;
        }
    }

    public static boolean looksLikeCodeArtifact(String value) {
        return value != null && CODE_ARTIFACT.matcher(value).find();
    }

    public static boolean looksTruncated(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.strip();
        return trimmed.endsWith("...") || trimmed.endsWith("…") || trimmed.endsWith("\uFFFD");
    }

    public static boolean isExtremelyShort(String value, int minChars) {
        if (value == null) {
            return true;
        }
        return collapseWhitespace(value).length() < minChars;
    }

    public static boolean hasSuspiciousScientificTitleChar(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 32 && ch != '\t') {
                return true;
            }
            if (ch == '\uFFFD' || Character.getType(ch) == Character.PRIVATE_USE) {
                return true;
            }
        }
        return false;
    }

    public static String highlightChange(String before, String after) {
        if (before == null) {
            return after;
        }
        if (after == null) {
            return before;
        }
        return after;
    }

    private static String decodeNamed(String value) {
        Matcher matcher = NAMED_ENTITY.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = NAMED.get(name);
            if (replacement == null) {
                replacement = NAMED.get(name.toLowerCase(Locale.ROOT));
            }
            if (replacement == null) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String decodeNumeric(String value) {
        Matcher matcher = NUMERIC_ENTITY.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String raw = matcher.group(1);
            try {
                int codePoint = raw.startsWith("x") || raw.startsWith("X")
                        ? Integer.parseInt(raw.substring(1), 16)
                        : Integer.parseInt(raw);
                if (codePoint <= 0 || codePoint > Character.MAX_CODE_POINT) {
                    matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
                } else {
                    matcher.appendReplacement(out, Matcher.quoteReplacement(Character.toString(codePoint)));
                }
            } catch (NumberFormatException ex) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static boolean isMostlyPrintable(String value) {
        int printable = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isISOControl(ch) || ch == '\n' || ch == '\t') {
                printable++;
            }
        }
        return printable * 10 >= value.length() * 8;
    }

    private static Map<String, String> namedEntities() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("nbsp", " ");
        map.put("amp", "&");
        map.put("lt", "<");
        map.put("gt", ">");
        map.put("quot", "\"");
        map.put("apos", "'");
        map.put("aacute", "á");
        map.put("eacute", "é");
        map.put("iacute", "í");
        map.put("oacute", "ó");
        map.put("uacute", "ú");
        map.put("Aacute", "Á");
        map.put("Eacute", "É");
        map.put("Iacute", "Í");
        map.put("Oacute", "Ó");
        map.put("Uacute", "Ú");
        map.put("ntilde", "ñ");
        map.put("Ntilde", "Ñ");
        map.put("uuml", "ü");
        map.put("Uuml", "Ü");
        map.put("auml", "ä");
        map.put("ouml", "ö");
        map.put("ccedil", "ç");
        map.put("Ccedil", "Ç");
        map.put("agrave", "à");
        map.put("egrave", "è");
        map.put("igrave", "ì");
        map.put("ograve", "ò");
        map.put("ugrave", "ù");
        map.put("acirc", "â");
        map.put("ecirc", "ê");
        map.put("ocirc", "ô");
        map.put("ldquo", "“");
        map.put("rdquo", "”");
        map.put("lsquo", "‘");
        map.put("rsquo", "’");
        map.put("ndash", "–");
        map.put("mdash", "—");
        return map;
    }
}
