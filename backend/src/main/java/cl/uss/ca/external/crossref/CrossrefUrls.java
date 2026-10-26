package cl.uss.ca.external.crossref;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class CrossrefUrls {
    private CrossrefUrls() {
    }

    public static String trimSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String work(String apiUrl, String doi, String mailto) {
        return trimSlash(apiUrl) + "/works/" + encodeDoi(doi) + polite(mailto);
    }

    public static String agency(String apiUrl, String doi, String mailto) {
        return trimSlash(apiUrl) + "/works/" + encodeDoi(doi) + "/agency" + polite(mailto);
    }

    public static String journal(String apiUrl, String issn, String mailto) {
        return trimSlash(apiUrl) + "/journals/" + encodeDoi(issn) + polite(mailto);
    }

    public static String journalWorks(String apiUrl, String issn, String mailto) {
        return trimSlash(apiUrl) + "/journals/" + encodeDoi(issn) + "/works" + polite(mailto);
    }

    public static String searchWorks(String apiUrl, String bibliographic, String mailto) {
        return trimSlash(apiUrl) + "/works?query.bibliographic="
                + URLEncoder.encode(bibliographic == null ? "" : bibliographic, StandardCharsets.UTF_8)
                + "&rows=5"
                + (mailto == null || mailto.isBlank() ? "" : "&mailto=" + encode(mailto));
    }

    private static String polite(String mailto) {
        if (mailto == null || mailto.isBlank()) {
            return "";
        }
        return "?mailto=" + encode(mailto);
    }

    private static String encodeDoi(String doi) {
        return encode(doi).replace("+", "%20");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
