package cl.uss.ca.external.openalex;

import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.domain.OpenAlexId;
import cl.uss.ca.exception.ErrorLog;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.quality.DataQualityIdentifiers;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class HttpOpenAlexClient implements OpenAlexClient {
    private static final Logger LOG = Logger.getLogger(HttpOpenAlexClient.class.getName());

    @Inject
    @ConfigProperty(name = "openalex.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    @ConfigProperty(name = "openalex.api.url", defaultValue = "https://api.openalex.org")
    Optional<String> apiUrl;

    @Inject
    @ConfigProperty(name = "openalex.api.key")
    Optional<String> apiKey;

    @Inject
    @ConfigProperty(name = "openalex.mailto")
    Optional<String> mailto;

    @Inject
    @ConfigProperty(name = "openalex.user.agent", defaultValue = "OPENSCIENCE-USS (Universidad San Sebastian)")
    String userAgent;

    @Inject
    @ConfigProperty(name = "openalex.connectTimeoutSeconds", defaultValue = "10")
    int connectTimeoutSeconds;

    @Inject
    @ConfigProperty(name = "openalex.readTimeoutSeconds", defaultValue = "20")
    int readTimeoutSeconds;

    @Inject
    @ConfigProperty(name = "openalex.retryMaxAttempts", defaultValue = "2")
    int retryMaxAttempts;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void assertConfigured() {
        if (!isEnabled()) {
            throw new ValidationException("OPENALEX_DISABLED", "OpenAlex is disabled. Set OPENALEX_ENABLED=true.");
        }
        if (!isConfigured()) {
            throw new ValidationException(
                    "OPENALEX_NOT_CONFIGURED",
                    "OpenAlex is not configured. Set OPENALEX_API_URL and restart."
            );
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isConfigured() {
        return isEnabled() && OpenAlexUrls.trimSlash(apiUrlValue()) != null;
    }

    @Override
    public OpenAlexFetchResult fetchWork(String doi) {
        assertConfigured();
        String normalized = DoiNormalizer.normalize(doi);
        if (normalized == null || !DataQualityIdentifiers.isValidDoi(normalized)) {
            return OpenAlexFetchResult.invalid("DOI format is invalid");
        }
        long started = System.currentTimeMillis();
        HttpResponse<String> response = send(
                request(URI.create(OpenAlexUrls.workByDoi(apiUrlValue(), normalized, apiKeyValue(), mailtoValue()))),
                normalized,
                "works"
        );
        int status = response.statusCode();
        LOG.info("OpenAlex doi=" + normalized + " endpoint=works status=" + status
                + " durationMs=" + (System.currentTimeMillis() - started));
        logRateLimit(response);
        if (status == 200) {
            return parseWork(normalized, response);
        }
        if (status == 404) {
            return OpenAlexFetchResult.notFound(404);
        }
        return httpFailure(status);
    }

    @Override
    public OpenAlexFetchResult fetchAuthor(String openAlexAuthorId) {
        assertConfigured();
        String id = OpenAlexId.normalize(openAlexAuthorId);
        if (id == null) {
            return OpenAlexFetchResult.failure(
                    OpenAlexStatus.ERROR, 0, "OPENALEX_INVALID_AUTHOR", "OpenAlex author id is blank"
            );
        }
        HttpResponse<String> response = send(
                request(URI.create(OpenAlexUrls.author(apiUrlValue(), id, apiKeyValue(), mailtoValue()))),
                id,
                "authors"
        );
        int status = response.statusCode();
        LOG.info("OpenAlex author=" + id + " endpoint=authors status=" + status);
        logRateLimit(response);
        if (status == 200) {
            return parseAuthor(id, response);
        }
        if (status == 404) {
            return OpenAlexFetchResult.failure(
                    OpenAlexStatus.NOT_FOUND, 404, "OPENALEX_AUTHOR_NOT_FOUND", "OpenAlex author was not found"
            );
        }
        return httpFailure(status);
    }

    private OpenAlexFetchResult parseWork(String normalized, HttpResponse<String> response) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            OpenAlexWorkRecord work = OpenAlexMapper.parseWork(root);
            OpenAlexRawJson.apply(work, response.body());
            if (work.doi == null) {
                work.doi = normalized;
            }
            return OpenAlexFetchResult.found(200, work);
        } catch (Exception ex) {
            LOG.warning("OpenAlex doi=" + normalized + " parse error");
            return OpenAlexFetchResult.failure(
                    OpenAlexStatus.ERROR, 200, "OPENALEX_PARSE_ERROR", "OpenAlex payload could not be parsed"
            );
        }
    }

    private OpenAlexFetchResult parseAuthor(String id, HttpResponse<String> response) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            OpenAlexAuthorRecord author = OpenAlexMapper.parseAuthor(root);
            if (author.openalexAuthorId == null) {
                author.openalexAuthorId = id;
            }
            return OpenAlexFetchResult.authorFound(200, author);
        } catch (Exception ex) {
            LOG.warning("OpenAlex author=" + id + " parse error");
            return OpenAlexFetchResult.failure(
                    OpenAlexStatus.ERROR, 200, "OPENALEX_PARSE_ERROR", "OpenAlex author payload could not be parsed"
            );
        }
    }

    private OpenAlexFetchResult httpFailure(int status) {
        if (status == 429) {
            return OpenAlexFetchResult.failure(
                    OpenAlexStatus.RATE_LIMITED, status, "OPENALEX_RATE_LIMITED", "OpenAlex rate limit exceeded"
            );
        }
        if (status >= 500) {
            return OpenAlexFetchResult.failure(
                    OpenAlexStatus.ERROR, status, "OPENALEX_TEMPORARY_ERROR", "OpenAlex HTTP " + status
            );
        }
        if (status == 0) {
            return OpenAlexFetchResult.failure(
                    OpenAlexStatus.ERROR, 0, "OPENALEX_TIMEOUT", "OpenAlex request timed out"
            );
        }
        return OpenAlexFetchResult.failure(
                OpenAlexStatus.ERROR, status, "OPENALEX_HTTP_ERROR", "OpenAlex HTTP " + status
        );
    }

    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .header("User-Agent", userAgentValue())
                .timeout(Duration.ofSeconds(Math.max(5, readTimeoutSeconds)))
                .GET()
                .build();
    }

    private HttpResponse<String> send(HttpRequest request, String key, String endpoint) {
        HttpResponse<String> response = null;
        int max = Math.max(0, retryMaxAttempts);
        for (int attempt = 0; attempt <= max; attempt++) {
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ValidationException("OPENALEX_INTERRUPTED", "OpenAlex request was interrupted");
            } catch (IOException ex) {
                LOG.warning("OpenAlex key=" + key + " endpoint=" + endpoint + " timeout/I/O attempt=" + attempt);
                if (attempt == max) {
                    ErrorLog.unexpected(LOG, "OpenAlex key=" + key + " endpoint=" + endpoint
                            + " failed after " + (attempt + 1) + " attempts", ex);
                    return timeoutResponse();
                }
                sleep(backoffMs(attempt, null), key, "timeout/I/O");
                continue;
            }
            int status = response.statusCode();
            if (status == 429) {
                String retryAfter = response.headers().firstValue("Retry-After").orElse("none");
                LOG.warning("OpenAlex key=" + key + " status=429 retry-after=" + retryAfter);
                if (attempt == max) {
                    return response;
                }
                sleep(backoffMs(attempt, response), key, "HTTP 429");
                continue;
            }
            if (!OpenAlexHttpRetry.shouldRetry(status, attempt, max)) {
                return response;
            }
            LOG.warning("OpenAlex key=" + key + " endpoint=" + endpoint + " status=" + status);
            sleep(backoffMs(attempt, response), key, "HTTP " + status);
        }
        return response;
    }

    private void logRateLimit(HttpResponse<String> response) {
        if (response == null) {
            return;
        }
        Optional<String> remaining = response.headers().firstValue("X-RateLimit-Remaining");
        if (remaining.isEmpty()) {
            return;
        }
        try {
            int value = Integer.parseInt(remaining.get().trim());
            if (value <= 100) {
                String reset = response.headers().firstValue("X-RateLimit-Reset").orElse("?");
                LOG.info("OpenAlex rate-limit remaining=" + value + " reset=" + reset);
            }
        } catch (NumberFormatException ignored) {
            // ignore malformed headers
        }
    }

    private long backoffMs(int attempt, HttpResponse<String> response) {
        if (response != null) {
            Optional<String> retryAfter = response.headers().firstValue("Retry-After");
            if (retryAfter.isPresent()) {
                try {
                    return Math.min(60_000L, Math.max(250L, Long.parseLong(retryAfter.get().trim()) * 1000L));
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        return OpenAlexHttpRetry.backoffMs(attempt);
    }

    private static void sleep(long millis, String key, String reason) {
        LOG.warning("OpenAlex key=" + key + " retry after " + reason + " in " + millis + " ms");
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ValidationException("OPENALEX_INTERRUPTED", "OpenAlex retry was interrupted");
        }
    }

    private HttpResponse<String> timeoutResponse() {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return 0;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public java.net.http.HttpHeaders headers() {
                return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
            }

            @Override
            public String body() {
                return null;
            }

            @Override
            public Optional<javax.net.ssl.SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("https://api.openalex.org");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private String apiUrlValue() {
        String value = OpenAlexUrls.trimSlash(apiUrl == null ? null : apiUrl.orElse(null));
        return value == null ? "https://api.openalex.org" : value;
    }

    private String apiKeyValue() {
        String value = apiKey == null ? null : apiKey.orElse(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String mailtoValue() {
        String value = mailto == null ? null : mailto.orElse(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String userAgentValue() {
        String agent = userAgent == null || userAgent.isBlank()
                ? "OPENSCIENCE-USS (Universidad San Sebastian)"
                : userAgent.trim();
        String mail = mailtoValue();
        if (mail != null && !agent.toLowerCase(Locale.ROOT).contains("mailto:")) {
            return agent + " mailto:" + mail;
        }
        return agent;
    }

}
