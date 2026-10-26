package cl.uss.ca.external.orcid;

import cl.uss.ca.domain.OrcidFormat;
import cl.uss.ca.exception.ValidationException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class HttpOrcidClient implements OrcidClient {
    private static final Logger LOG = Logger.getLogger(HttpOrcidClient.class.getName());
    private static final int MAX_RETRIES = 2;

    @Inject
    @ConfigProperty(name = "orcid.base.url")
    Optional<String> baseUrlConfig;

    @Inject
    @ConfigProperty(name = "orcid.api.url")
    Optional<String> legacyApiUrl;

    @Inject
    @ConfigProperty(name = "orcid.search.base.url")
    Optional<String> searchBaseUrl;

    @Inject
    @ConfigProperty(name = "orcid.search.token")
    Optional<String> searchToken;

    @Inject
    @ConfigProperty(name = "orcid.user.agent", defaultValue = "OPENSCIENCE-USS (Universidad San Sebastian)")
    String userAgent;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Override
    public void assertConfigured() {
        if (!isConfigured()) {
            throw new ValidationException(
                    "ORCID_NOT_CONFIGURED",
                    "ORCID is not configured. Set ORCID_BASE_URL in the OpenLiberty environment and restart."
            );
        }
    }

    @Override
    public boolean isConfigured() {
        return baseUrl() != null
                || firstNonBlank(searchBaseUrl == null ? null : searchBaseUrl.orElse(null), null) != null;
    }

    @Override
    public List<OrcidWorkGroupRecord> fetchWorks(String orcid) {
        assertConfigured();
        String normalized = requireOrcid(orcid);
        HttpResponse<String> response = send(publicGet(worksPath(normalized)));
        throwIfUnsuccessful(response, "ORCID_NOT_FOUND", "ORCID was not found");
        try {
            return OrcidWorksMapper.parseWorks(normalized, objectMapper.readTree(response.body()), objectMapper);
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException("ORCID_PARSE_ERROR", "ORCID payload could not be parsed");
        }
    }

    @Override
    public OrcidSearchPage searchByName(String givenName, String familyName) {
        assertConfigured();
        if (givenName == null || givenName.isBlank() || familyName == null || familyName.isBlank()) {
            throw new ValidationException("ORCID_INVALID", "ORCID search requires given and family name");
        }
        String query = "given-names:\"" + givenName.trim() + "\" AND family-name:\"" + familyName.trim() + "\"";
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(publicGetAbsolute(searchBase() + "/search/?q=" + encoded));
        throwIfUnsuccessful(response, "ORCID_NOT_FOUND", "ORCID search was not found");
        try {
            return parseSearch(objectMapper.readTree(response.body()));
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException("ORCID_PARSE_ERROR", "ORCID search payload could not be parsed");
        }
    }

    @Override
    public OrcidWorkRecord fetchWorkDetail(String orcid, long putCode) {
        assertConfigured();
        String normalized = requireOrcid(orcid);
        HttpResponse<String> response = send(publicGet(workDetailPath(normalized, putCode)));
        throwIfUnsuccessful(response, "ORCID_WORK_NOT_FOUND", "ORCID work was not found");
        try {
            return OrcidWorksMapper.mapDetail(normalized, objectMapper.readTree(response.body()), objectMapper);
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException("ORCID_PARSE_ERROR", "ORCID work detail could not be parsed");
        }
    }

    @Override
    public List<OrcidEmploymentRecord> fetchEmployments(String orcid) {
        assertConfigured();
        String normalized = requireOrcid(orcid);
        HttpResponse<String> response = send(publicGet(employmentsPath(normalized)));
        if (response.statusCode() == 404) {
            return List.of();
        }
        throwIfUnsuccessful(response, "ORCID_NOT_FOUND", "ORCID was not found");
        try {
            return OrcidEmploymentsMapper.parse(normalized, objectMapper.readTree(response.body()));
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException("ORCID_PARSE_ERROR", "ORCID employment payload could not be parsed");
        }
    }

    static String worksPath(String orcid) {
        return orcid + "/works";
    }

    static String employmentsPath(String orcid) {
        return orcid + "/employments";
    }

    static String workDetailPath(String orcid, long putCode) {
        return orcid + "/work/" + putCode;
    }

    private static String requireOrcid(String orcid) {
        if (orcid == null || orcid.isBlank()) {
            throw new ValidationException("ORCID_INVALID", "ORCID is empty");
        }
        String normalized = OrcidFormat.normalizeValidOrNull(orcid);
        if (normalized == null) {
            throw new ValidationException("ORCID_INVALID", "ORCID format is invalid");
        }
        return normalized;
    }

    private HttpRequest publicGet(String path) {
        return publicGetAbsolute(baseUrl() + "/" + path);
    }

    private HttpRequest publicGetAbsolute(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(20))
                .GET();
        String token = searchToken == null ? null : searchToken.orElse(null);
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder.build();
    }

    private static OrcidSearchPage parseSearch(JsonNode root) {
        OrcidSearchPage page = new OrcidSearchPage();
        if (root == null || root.isMissingNode() || root.isNull()) {
            return page;
        }
        page.numFound = root.path("num-found").asLong(0);
        JsonNode results = root.path("result");
        if (!results.isArray()) {
            return page;
        }
        for (JsonNode item : results) {
            String orcid = text(item.path("orcid-identifier").path("path"));
            if (orcid == null) {
                orcid = text(item.path("orcid-identifier").path("uri"));
            }
            String normalized = OrcidFormat.normalizeValidOrNull(orcid);
            if (normalized != null) {
                page.orcids.add(normalized);
            }
        }
        return page;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private HttpResponse<String> send(HttpRequest request) {
        HttpResponse<String> response = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ValidationException("ORCID_INTERRUPTED", "ORCID request was interrupted");
            } catch (IOException ex) {
                if (attempt == MAX_RETRIES) {
                    throw new ValidationException("ORCID_TEMPORARY_ERROR", "ORCID request failed", ex);
                }
                sleep(backoffMs(attempt, null), "timeout/I/O");
                continue;
            }
            int status = response.statusCode();
            if (status == 429) {
                String retryAfter = response.headers().firstValue("Retry-After").orElse("none");
                LOG.warning("ORCID request failed: status=429 retry-after=" + retryAfter);
                if (attempt == MAX_RETRIES) {
                    return response;
                }
                sleep(backoffMs(attempt, response), "HTTP 429");
                continue;
            }
            if (!retryable(status) || attempt == MAX_RETRIES) {
                return response;
            }
            LOG.warning("ORCID request failed: status=" + status);
            sleep(backoffMs(attempt, response), "HTTP " + status);
        }
        return response;
    }

    private void throwIfUnsuccessful(HttpResponse<String> response, String notFoundCode, String notFoundMessage) {
        int status = response.statusCode();
        if (status == 404) {
            throw new ValidationException(notFoundCode, notFoundMessage);
        }
        if (status == 429) {
            throw new ValidationException("ORCID_RATE_LIMITED", "ORCID rate limit exceeded");
        }
        if (status >= 500) {
            throw new ValidationException("ORCID_TEMPORARY_ERROR", "ORCID HTTP " + status);
        }
        if (status < 200 || status >= 300 || response.body() == null) {
            throw new ValidationException("ORCID_HTTP_ERROR", "ORCID HTTP " + status);
        }
    }

    private static boolean retryable(int status) {
        return status == 500 || status == 502 || status == 503 || status == 504;
    }

    private long backoffMs(int attempt, HttpResponse<String> response) {
        if (response != null) {
            Optional<String> retryAfter = response.headers().firstValue("Retry-After");
            if (retryAfter.isPresent()) {
                try {
                    return Math.max(250L, Long.parseLong(retryAfter.get().trim()) * 1000L);
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        return 500L * (attempt + 1);
    }

    private static void sleep(long millis, String reason) {
        LOG.warning("ORCID retry after " + reason + " in " + millis + " ms");
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ValidationException("ORCID_INTERRUPTED", "ORCID retry was interrupted");
        }
    }

    private String searchBase() {
        String value = firstNonBlank(
                searchBaseUrl == null ? null : searchBaseUrl.orElse(null),
                baseUrl()
        );
        if (value == null) {
            throw new ValidationException(
                    "ORCID_NOT_CONFIGURED",
                    "ORCID is not configured. Set ORCID_SEARCH_BASE_URL or ORCID_BASE_URL and restart."
            );
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String baseUrl() {
        String value = firstNonBlank(
                baseUrlConfig == null ? null : baseUrlConfig.orElse(null),
                legacyApiUrl == null ? null : legacyApiUrl.orElse(null)
        );
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }
}
