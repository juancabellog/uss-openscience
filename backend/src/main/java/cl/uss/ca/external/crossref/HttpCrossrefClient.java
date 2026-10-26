package cl.uss.ca.external.crossref;

import cl.uss.ca.domain.DoiNormalizer;
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
public class HttpCrossrefClient implements CrossrefClient {
    private static final Logger LOG = Logger.getLogger(HttpCrossrefClient.class.getName());

    @Inject
    @ConfigProperty(name = "crossref.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    @ConfigProperty(name = "crossref.api.url", defaultValue = "https://api.crossref.org")
    Optional<String> apiUrl;

    @Inject
    @ConfigProperty(name = "crossref.mailto")
    Optional<String> mailto;

    @Inject
    @ConfigProperty(name = "crossref.user.agent", defaultValue = "OPENSCIENCE-USS (Universidad San Sebastian)")
    String userAgent;

    @Inject
    @ConfigProperty(name = "crossref.plus.token")
    Optional<String> plusToken;

    @Inject
    @ConfigProperty(name = "crossref.connectTimeoutSeconds", defaultValue = "10")
    int connectTimeoutSeconds;

    @Inject
    @ConfigProperty(name = "crossref.readTimeoutSeconds", defaultValue = "20")
    int readTimeoutSeconds;

    @Inject
    @ConfigProperty(name = "crossref.retryMaxAttempts", defaultValue = "2")
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
            throw new ValidationException("CROSSREF_DISABLED", "Crossref is disabled. Set CROSSREF_ENABLED=true.");
        }
        if (!isConfigured()) {
            throw new ValidationException(
                    "CROSSREF_NOT_CONFIGURED",
                    "Crossref is not configured. Set CROSSREF_MAILTO and restart."
            );
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isConfigured() {
        return isEnabled()
                && CrossrefUrls.trimSlash(apiUrlValue()) != null
                && mailtoValue() != null;
    }

    @Override
    public CrossrefFetchResult fetchWork(String doi) {
        assertConfigured();
        String normalized = DoiNormalizer.normalize(doi);
        if (normalized == null || !DataQualityIdentifiers.isValidDoi(normalized)) {
            return CrossrefFetchResult.invalid("DOI format is invalid");
        }
        long started = System.currentTimeMillis();
        HttpResponse<String> workResponse = send(workRequest(normalized), normalized, "works");
        int status = workResponse.statusCode();
        LOG.info("Crossref doi=" + normalized + " endpoint=works status=" + status
                + " durationMs=" + (System.currentTimeMillis() - started));
        if (status == 200) {
            return parseFound(normalized, workResponse);
        }
        if (status == 404) {
            return resolveMissingWork(normalized);
        }
        return httpFailure(status, workResponse);
    }

    @Override
    public String journalUrl(String issn) {
        if (!isConfigured() || issn == null) {
            return null;
        }
        return CrossrefUrls.journal(apiUrlValue(), issn, mailtoValue());
    }

    @Override
    public CrossrefFetchResult searchBibliographic(String query) {
        if (!isConfigured() || query == null || query.isBlank()) {
            return CrossrefClient.super.searchBibliographic(query);
        }
        URI uri = URI.create(CrossrefUrls.searchWorks(apiUrlValue(), query, mailtoValue()));
        HttpResponse<String> response = send(request(uri), "search", "search");
        if (response.statusCode() != 200) {
            return httpFailure(response.statusCode(), response);
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            CrossrefWorkRecord work = new CrossrefWorkRecord();
            work.rawJson = clipRaw(response.body());
            work.title = query;
            CrossrefFetchResult result = CrossrefFetchResult.found(200, work);
            result.errorCode = "CROSSREF_SEARCH_CANDIDATES";
            result.errorMessage = "Search payload received; candidates are not auto-applied";
            return result;
        } catch (Exception ex) {
            return CrossrefFetchResult.failure(
                    CrossrefStatus.ERROR, 200, "CROSSREF_PARSE_ERROR", "Crossref search payload could not be parsed"
            );
        }
    }

    private CrossrefFetchResult resolveMissingWork(String normalized) {
        HttpResponse<String> agencyResponse = send(agencyRequest(normalized), normalized, "agency");
        int status = agencyResponse.statusCode();
        LOG.info("Crossref doi=" + normalized + " endpoint=agency status=" + status);
        if (status == 200) {
            try {
                CrossrefMapper.Agency agency = CrossrefMapper.parseAgency(objectMapper.readTree(agencyResponse.body()));
                if (agency.id != null && !"crossref".equalsIgnoreCase(agency.id)) {
                    return CrossrefFetchResult.otherAgency(status, agency.id, agency.label);
                }
            } catch (Exception ex) {
                LOG.warning("Crossref doi=" + normalized + " agency parse error");
            }
            return CrossrefFetchResult.notFound(status);
        }
        if (status == 404) {
            return CrossrefFetchResult.notFound(404);
        }
        return httpFailure(status, agencyResponse);
    }

    private CrossrefFetchResult parseFound(String normalized, HttpResponse<String> response) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            CrossrefWorkRecord work = CrossrefMapper.parseWork(root);
            work.rawJson = clipRaw(response.body());
            if (work.canonicalDoi == null) {
                work.canonicalDoi = normalized;
            }
            return CrossrefFetchResult.found(200, work);
        } catch (Exception ex) {
            LOG.warning("Crossref doi=" + normalized + " parse error");
            return CrossrefFetchResult.failure(
                    CrossrefStatus.ERROR, 200, "CROSSREF_PARSE_ERROR", "Crossref payload could not be parsed"
            );
        }
    }

    private CrossrefFetchResult httpFailure(int status, HttpResponse<String> response) {
        if (status == 429) {
            return CrossrefFetchResult.failure(
                    CrossrefStatus.RATE_LIMITED, status, "CROSSREF_RATE_LIMITED", "Crossref rate limit exceeded"
            );
        }
        if (status >= 500) {
            return CrossrefFetchResult.failure(
                    CrossrefStatus.ERROR, status, "CROSSREF_TEMPORARY_ERROR", "Crossref HTTP " + status
            );
        }
        if (status == 0) {
            return CrossrefFetchResult.failure(
                    CrossrefStatus.ERROR, 0, "CROSSREF_TIMEOUT", "Crossref request timed out"
            );
        }
        return CrossrefFetchResult.failure(
                CrossrefStatus.ERROR, status, "CROSSREF_HTTP_ERROR", "Crossref HTTP " + status
        );
    }

    private HttpRequest workRequest(String doi) {
        return request(URI.create(CrossrefUrls.work(apiUrlValue(), doi, mailtoValue())));
    }

    private HttpRequest agencyRequest(String doi) {
        return request(URI.create(CrossrefUrls.agency(apiUrlValue(), doi, mailtoValue())));
    }

    private HttpRequest request(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .header("User-Agent", userAgentValue())
                .timeout(Duration.ofSeconds(Math.max(5, readTimeoutSeconds)))
                .GET();
        String token = plusToken == null ? null : plusToken.orElse(null);
        if (token != null && !token.isBlank()) {
            builder.header("Crossref-Plus-API-Token", "Bearer " + token.trim());
        }
        return builder.build();
    }

    private HttpResponse<String> send(HttpRequest request, String doi, String endpoint) {
        HttpResponse<String> response = null;
        int max = Math.max(0, retryMaxAttempts);
        for (int attempt = 0; attempt <= max; attempt++) {
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ValidationException("CROSSREF_INTERRUPTED", "Crossref request was interrupted");
            } catch (IOException ex) {
                LOG.warning("Crossref doi=" + doi + " endpoint=" + endpoint + " timeout/I/O attempt=" + attempt);
                if (attempt == max) {
                    ErrorLog.unexpected(LOG, "Crossref doi=" + doi + " endpoint=" + endpoint
                            + " failed after " + (attempt + 1) + " attempts", ex);
                    return timeoutResponse();
                }
                sleep(backoffMs(attempt, null), doi, "timeout/I/O");
                continue;
            }
            int status = response.statusCode();
            if (status == 429) {
                String retryAfter = response.headers().firstValue("Retry-After").orElse("none");
                LOG.warning("Crossref doi=" + doi + " status=429 retry-after=" + retryAfter);
                if (attempt == max) {
                    return response;
                }
                sleep(backoffMs(attempt, response), doi, "HTTP 429");
                continue;
            }
            if (!retryable(status) || attempt == max) {
                return response;
            }
            LOG.warning("Crossref doi=" + doi + " endpoint=" + endpoint + " status=" + status);
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
        LOG.warning("Crossref doi=" + doi + " retry after " + reason + " in " + millis + " ms");
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ValidationException("CROSSREF_INTERRUPTED", "Crossref retry was interrupted");
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
                return URI.create("https://api.crossref.org");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private String apiUrlValue() {
        String value = CrossrefUrls.trimSlash(apiUrl == null ? null : apiUrl.orElse(null));
        return value == null ? "https://api.crossref.org" : value;
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

    private static String clipRaw(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= 1_000_000 ? body : body.substring(0, 1_000_000);
    }
}
