package cl.uss.ca.external.unpaywall;

import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.exception.ValidationException;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class HttpUnpaywallClient implements UnpaywallClient {
    private static final Logger LOG = Logger.getLogger(HttpUnpaywallClient.class.getName());
    private static final int MAX_RETRIES = 2;

    @Inject
    @ConfigProperty(name = "unpaywall.api.url", defaultValue = "https://api.unpaywall.org/v2")
    Optional<String> apiUrl;

    @Inject
    @ConfigProperty(name = "unpaywall.email")
    Optional<String> email;

    @Inject
    @ConfigProperty(name = "unpaywall.timeoutSeconds", defaultValue = "20")
    int timeoutSeconds;

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
                    "UNPAYWALL_NOT_CONFIGURED",
                    "Unpaywall is not configured. Set UNPAYWALL_EMAIL and restart."
            );
        }
    }

    @Override
    public boolean isConfigured() {
        return UnpaywallUrls.trimSlash(apiUrl == null ? null : apiUrl.orElse(null)) != null
                && emailValue() != null;
    }

    @Override
    public UnpaywallFetchResult fetchByDoi(String doi) {
        assertConfigured();
        String normalized = DoiNormalizer.normalize(doi);
        if (normalized == null) {
            throw new ValidationException("UNPAYWALL_INVALID_DOI", "DOI is empty");
        }
        URI uri = UnpaywallUrls.doiRequest(apiUrl(), normalized, emailValue());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .GET()
                .build();
        HttpResponse<String> response = send(request, normalized);
        int status = response.statusCode();
        if (status == 404) {
            LOG.info("Unpaywall doi=" + normalized + " status=404");
            return UnpaywallFetchResult.notFound(status);
        }
        if (status == 400 || status == 422) {
            LOG.warning("Unpaywall doi=" + normalized + " status=" + status);
            return UnpaywallFetchResult.failure(
                    UnpaywallFetchResult.INVALID,
                    status,
                    "UNPAYWALL_INVALID",
                    "Unpaywall rejected the DOI (HTTP " + status + ")"
            );
        }
        if (status == 429) {
            LOG.warning("Unpaywall doi=" + normalized + " status=429");
            return UnpaywallFetchResult.failure(
                    UnpaywallFetchResult.RATE_LIMITED,
                    status,
                    "UNPAYWALL_RATE_LIMITED",
                    "Unpaywall rate limit exceeded"
            );
        }
        if (status >= 500) {
            LOG.warning("Unpaywall doi=" + normalized + " status=" + status);
            return UnpaywallFetchResult.failure(
                    UnpaywallFetchResult.ERROR,
                    status,
                    "UNPAYWALL_TEMPORARY_ERROR",
                    "Unpaywall HTTP " + status
            );
        }
        if (status < 200 || status >= 300 || response.body() == null) {
            LOG.warning("Unpaywall doi=" + normalized + " status=" + status);
            return UnpaywallFetchResult.failure(
                    UnpaywallFetchResult.ERROR,
                    status,
                    "UNPAYWALL_HTTP_ERROR",
                    "Unpaywall HTTP " + status
            );
        }
        try {
            UnpaywallWorkRecord work = UnpaywallMapper.parse(objectMapper.readTree(response.body()));
            LOG.info("Unpaywall doi=" + normalized + " status=200");
            return UnpaywallFetchResult.found(status, work);
        } catch (Exception ex) {
            LOG.warning("Unpaywall doi=" + normalized + " parse error");
            return UnpaywallFetchResult.failure(
                    UnpaywallFetchResult.ERROR,
                    status,
                    "UNPAYWALL_PARSE_ERROR",
                    "Unpaywall payload could not be parsed"
            );
        }
    }

    private HttpResponse<String> send(HttpRequest request, String doi) {
        HttpResponse<String> response = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ValidationException("UNPAYWALL_INTERRUPTED", "Unpaywall request was interrupted");
            } catch (IOException ex) {
                if (attempt == MAX_RETRIES) {
                    throw new ValidationException("UNPAYWALL_TEMPORARY_ERROR", "Unpaywall request timed out", ex);
                }
                sleep(backoffMs(attempt, null), doi, "timeout/I/O");
                continue;
            }
            int status = response.statusCode();
            if (status == 429) {
                String retryAfter = response.headers().firstValue("Retry-After").orElse("none");
                LOG.warning("Unpaywall doi=" + doi + " status=429 retry-after=" + retryAfter);
                if (attempt == MAX_RETRIES) {
                    return response;
                }
                sleep(backoffMs(attempt, response), doi, "HTTP 429");
                continue;
            }
            if (!retryable(status) || attempt == MAX_RETRIES) {
                return response;
            }
            LOG.warning("Unpaywall doi=" + doi + " status=" + status);
            sleep(backoffMs(attempt, response), doi, "HTTP " + status);
        }
        return response;
    }

    private static boolean retryable(int status) {
        return status == 500 || status == 502 || status == 503 || status == 504;
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
        return 500L * (attempt + 1);
    }

    private static void sleep(long millis, String doi, String reason) {
        LOG.warning("Unpaywall doi=" + doi + " retry after " + reason + " in " + millis + " ms");
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ValidationException("UNPAYWALL_INTERRUPTED", "Unpaywall retry was interrupted");
        }
    }

    private String apiUrl() {
        String value = UnpaywallUrls.trimSlash(apiUrl == null ? null : apiUrl.orElse(null));
        return value == null ? "https://api.unpaywall.org/v2" : value;
    }

    private String emailValue() {
        if (email == null) {
            return null;
        }
        String value = email.orElse(null);
        if (value == null || value.isBlank() || value.contains("<") || "CAMBIAR".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }
}
