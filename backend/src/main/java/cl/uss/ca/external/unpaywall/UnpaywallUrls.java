package cl.uss.ca.external.unpaywall;

import cl.uss.ca.domain.DoiNormalizer;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class UnpaywallUrls {
    private UnpaywallUrls() {
    }

    public static URI doiRequest(String apiUrl, String doi, String email) {
        String base = trimSlash(apiUrl);
        String normalized = DoiNormalizer.normalize(doi);
        if (normalized == null) {
            throw new IllegalArgumentException("DOI is empty");
        }
        String encodedDoi = URLEncoder.encode(normalized, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedEmail = URLEncoder.encode(email.trim(), StandardCharsets.UTF_8);
        return URI.create(base + "/" + encodedDoi + "?email=" + encodedEmail);
    }

    static String trimSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
