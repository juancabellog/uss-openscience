package cl.uss.ca.external.openalex;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class OpenAlexUrls {
    public static final String WORK_SELECT = String.join(",",
            "id",
            "doi",
            "display_name",
            "publication_year",
            "publication_date",
            "type",
            "cited_by_count",
            "referenced_works_count",
            "language",
            "primary_location",
            "primary_topic",
            "topics",
            "authorships",
            "open_access",
            "updated_date"
    );
    public static final String AUTHOR_SELECT = String.join(",",
            "id",
            "display_name",
            "orcid",
            "works_count",
            "cited_by_count",
            "updated_date"
    );

    private OpenAlexUrls() {
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

    public static String workByDoi(String apiUrl, String doi, String apiKey, String mailto) {
        return trimSlash(apiUrl) + "/works/doi:" + encode(doi) + query(apiKey, mailto, WORK_SELECT);
    }

    public static String author(String apiUrl, String authorId, String apiKey, String mailto) {
        return trimSlash(apiUrl) + "/authors/" + encode(authorId) + query(apiKey, mailto, AUTHOR_SELECT);
    }

    private static String query(String apiKey, String mailto, String select) {
        StringBuilder builder = new StringBuilder("?select=").append(encode(select));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.append("&api_key=").append(encode(apiKey.trim()));
        }
        if (mailto != null && !mailto.isBlank()) {
            builder.append("&mailto=").append(encode(mailto.trim()));
        }
        return builder.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
