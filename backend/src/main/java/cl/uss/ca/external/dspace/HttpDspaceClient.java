package cl.uss.ca.external.dspace;

import cl.uss.ca.exception.ValidationException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class HttpDspaceClient implements DspaceClient {
    private static final Logger LOG = Logger.getLogger(HttpDspaceClient.class.getName());
    private static final String ACCEPT = "application/hal+json";

    @Inject
    @ConfigProperty(name = "dspace.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    @ConfigProperty(name = "dspace.api.url", defaultValue = "https://repositorio.uss.cl/server/api")
    Optional<String> apiUrl;

    @Inject
    @ConfigProperty(name = "dspace.user.agent", defaultValue = "OPENSCIENCE-USS/dspace-sync (Universidad San Sebastian)")
    String userAgent;

    @Inject
    @ConfigProperty(name = "dspace.connectTimeoutSeconds", defaultValue = "10")
    int connectTimeoutSeconds;

    @Inject
    @ConfigProperty(name = "dspace.readTimeoutSeconds", defaultValue = "30")
    int readTimeoutSeconds;

    @Inject
    @ConfigProperty(name = "dspace.retryMaxAttempts", defaultValue = "2")
    int retryMaxAttempts;

    @Inject
    @ConfigProperty(name = "dspace.sync.delayMs", defaultValue = "200")
    long delayMs;

    @Inject
    @ConfigProperty(name = "dspace.maxPageSize", defaultValue = "100")
    int maxPageSize;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private HttpClient httpClient;
    private DspaceRateLimiter rateLimiter;

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(Math.max(5, connectTimeoutSeconds)))
                .build();
        rateLimiter = new DspaceRateLimiter(delayMs);
    }

    @Override
    public void assertConfigured() {
        if (!isEnabled()) {
            throw new ValidationException("DSPACE_DISABLED", "DSpace is disabled. Set DSPACE_ENABLED=true.");
        }
        if (!isConfigured()) {
            throw new ValidationException(
                    "DSPACE_NOT_CONFIGURED",
                    "DSpace is not configured. Set DSPACE_API_URL and restart."
            );
        }
    }

    @Override
    public boolean isConfigured() {
        return DspaceUrls.trimSlash(apiUrl == null ? null : apiUrl.orElse(null)) != null;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public DspaceRoot fetchRoot() {
        return DspaceHalParser.root(getJson(DspaceUrls.root(apiBase())));
    }

    @Override
    public DspaceHalPage<DspaceCommunityRecord> fetchCommunities(int page, int size) {
        return DspaceHalParser.communities(getJson(DspaceUrls.communities(apiBase(), page, size(size))));
    }

    @Override
    public DspaceHalPage<DspaceCommunityRecord> fetchSubcommunities(String communityUuid, int page, int size) {
        return DspaceHalParser.communities(getJson(
                DspaceUrls.subcommunities(apiBase(), communityUuid, page, size(size))
        ));
    }

    @Override
    public DspaceHalPage<DspaceCollectionRecord> fetchCollections(int page, int size) {
        return DspaceHalParser.collections(getJson(DspaceUrls.collections(apiBase(), page, size(size))));
    }

    @Override
    public DspaceCommunityRecord fetchParentCommunity(String collectionUuid) {
        JsonNode node = getJson(DspaceUrls.collectionParent(apiBase(), collectionUuid));
        return DspaceHalParser.community(node);
    }

    @Override
    public DspaceHalPage<DspaceItemRecord> searchItems(String query, String dsoType, int page, int size) {
        return DspaceHalParser.discoverItems(getJson(
                DspaceUrls.searchItems(apiBase(), query, dsoType, page, size(size))
        ));
    }

    @Override
    public DspaceItemRecord fetchItem(String uuid) {
        return DspaceHalParser.item(getJson(DspaceUrls.item(apiBase(), uuid)));
    }

    @Override
    public DspaceAccessStatus fetchAccessStatus(String itemUuid) {
        return DspaceHalParser.accessStatus(getJson(DspaceUrls.accessStatus(apiBase(), itemUuid)));
    }

    @Override
    public DspaceCollectionRecord fetchOwningCollection(String itemUuid) {
        return DspaceHalParser.collection(getJson(DspaceUrls.owningCollection(apiBase(), itemUuid)));
    }

    @Override
    public DspaceHalPage<DspaceBundleRecord> fetchBundles(String itemUuid) {
        return fetchBundles(itemUuid, 0, size(pageSizeFallback()));
    }

    @Override
    public DspaceHalPage<DspaceBundleRecord> fetchBundles(String itemUuid, int page, int size) {
        return DspaceHalParser.bundles(getJson(DspaceUrls.bundles(apiBase(), itemUuid, page, size(size))));
    }

    @Override
    public DspaceHalPage<DspaceBitstreamRecord> fetchBitstreams(String bundleUuid) {
        return fetchBitstreams(bundleUuid, 0, size(pageSizeFallback()));
    }

    @Override
    public DspaceHalPage<DspaceBitstreamRecord> fetchBitstreams(String bundleUuid, int page, int size) {
        return DspaceHalParser.bitstreams(getJson(DspaceUrls.bundleBitstreams(apiBase(), bundleUuid, page, size(size))));
    }

    @Override
    public DspaceAccessStatus fetchBitstreamAccess(String bitstreamUuid) {
        return DspaceHalParser.accessStatus(getJson(DspaceUrls.bitstreamAccess(apiBase(), bitstreamUuid)));
    }

    @Override
    public DspaceBitstreamContent fetchBitstreamContent(String bitstreamUuid) {
        ensureClient();
        String url = DspaceUrls.bitstreamContent(apiBase(), bitstreamUuid);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "*/*")
                .header("User-Agent", userAgentValue())
                .timeout(Duration.ofSeconds(Math.max(30, readTimeoutSeconds)))
                .GET()
                .build();
        rateLimiter.acquire();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                closeQuietly(response.body());
                throw new cl.uss.ca.exception.ForbiddenException(
                        "DSPACE_BITSTREAM_RESTRICTED",
                        "DSpace did not allow downloading this bitstream"
                );
            }
            if (status < 200 || status >= 300) {
                closeQuietly(response.body());
                throw mapStatus(status, "", url);
            }
            String type = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            Long length = response.headers().firstValue("Content-Length").map(value -> {
                try {
                    return Long.parseLong(value.trim());
                } catch (NumberFormatException ex) {
                    return null;
                }
            }).orElse(null);
            return new DspaceBitstreamContent(response.body(), type, length);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ValidationException("DSPACE_INTERRUPTED", "DSpace request was interrupted");
        } catch (IOException ex) {
            throw new ValidationException("DSPACE_TEMPORARY_ERROR", "DSpace content request failed", ex);
        }
    }

    @Override
    public String resolveHandle(String handle) {
        if (handle == null || handle.isBlank()) {
            return null;
        }
        HttpResponse<String> response = send(GET(DspaceUrls.pidFind(apiBase(), handle.trim())));
        int status = response.statusCode();
        if (status == 302 || status == 301) {
            return response.headers().firstValue("location").orElse(null);
        }
        if (status == 404) {
            return null;
        }
        if (status >= 200 && status < 300) {
            JsonNode node = parseBody(response.body(), status, DspaceUrls.pidFind(apiBase(), handle.trim()));
            return DspaceHalParser.item(node).uuid;
        }
        throw mapStatus(status, response.body(), DspaceUrls.pidFind(apiBase(), handle.trim()));
    }

    JsonNode getJson(String url) {
        HttpResponse<String> response = send(GET(url));
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            if (response.body() == null || response.body().isBlank()) {
                throw new DspaceHttpException("DSPACE_EMPTY_BODY", "DSpace returned an empty body", status, url);
            }
            return parseBody(response.body(), status, url);
        }
        throw mapStatus(status, response.body(), url);
    }

    private HttpRequest GET(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Accept", ACCEPT)
                .header("User-Agent", userAgentValue())
                .timeout(Duration.ofSeconds(Math.max(5, readTimeoutSeconds)))
                .GET()
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        ensureClient();
        HttpResponse<String> response = null;
        int max = Math.max(0, retryMaxAttempts);
        for (int attempt = 0; attempt <= max; attempt++) {
            rateLimiter.acquire();
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ValidationException("DSPACE_INTERRUPTED", "DSpace request was interrupted");
            } catch (IOException ex) {
                if (attempt == max) {
                    throw new ValidationException("DSPACE_TEMPORARY_ERROR", "DSpace request timed out", ex);
                }
                sleep(backoffMs(attempt, null), request.uri().toString(), "timeout/I/O");
                continue;
            }
            int status = response.statusCode();
            if (status == 429) {
                if (attempt == max) {
                    throw mapStatus(status, response.body(), request.uri().toString());
                }
                sleep(backoffMs(attempt, response), request.uri().toString(), "HTTP 429");
                continue;
            }
            if (!retryable(status) || attempt == max) {
                return response;
            }
            sleep(backoffMs(attempt, response), request.uri().toString(), "HTTP " + status);
        }
        return response;
    }

    static boolean retryable(int status) {
        return status == 500 || status == 502 || status == 503 || status == 504;
    }

    static String statusCode(int status) {
        return switch (status) {
            case 401 -> "DSPACE_UNAUTHORIZED";
            case 403 -> "DSPACE_FORBIDDEN";
            case 404 -> "DSPACE_NOT_FOUND";
            case 405 -> "DSPACE_METHOD_NOT_ALLOWED";
            case 429 -> "DSPACE_RATE_LIMITED";
            default -> status >= 500 ? "DSPACE_TEMPORARY_ERROR" : "DSPACE_HTTP_ERROR";
        };
    }

    private DspaceHttpException mapStatus(int status, String body, String url) {
        JsonNode node = null;
        try {
            if (body != null && !body.isBlank() && body.trim().startsWith("{")) {
                node = objectMapper.readTree(body);
            }
        } catch (Exception ignored) {
            // keep generic message
        }
        String message = DspaceHalParser.springErrorMessage(node);
        if (message == null) {
            message = "DSpace HTTP " + status;
        }
        LOG.warning("DSpace url=" + url + " status=" + status);
        return new DspaceHttpException(statusCode(status), message, status, url);
    }

    private JsonNode parseBody(String body, int status, String url) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new DspaceHttpException("DSPACE_PARSE_ERROR", "DSpace payload could not be parsed", status, url);
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
        return 500L * (1L << Math.min(attempt, 6));
    }

    private void sleep(long millis, String url, String reason) {
        LOG.warning("DSpace url=" + url + " retry after " + reason + " in " + millis + " ms");
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ValidationException("DSPACE_INTERRUPTED", "DSpace retry was interrupted");
        }
    }

    private void ensureClient() {
        if (httpClient == null) {
            init();
        }
        if (rateLimiter == null) {
            rateLimiter = new DspaceRateLimiter(delayMs);
        }
    }

    private int size(int size) {
        return DspaceUrls.clampSize(size, Math.max(1, maxPageSize));
    }

    private int pageSizeFallback() {
        return 50;
    }

    private String apiBase() {
        assertConfigured();
        String value = DspaceUrls.trimSlash(apiUrl == null ? null : apiUrl.orElse(null));
        return value == null ? "https://repositorio.uss.cl/server/api" : value;
    }

    private String userAgentValue() {
        return userAgent == null || userAgent.isBlank()
                ? "OPENSCIENCE-USS/dspace-sync (Universidad San Sebastian)"
                : userAgent.trim();
    }

    private static void closeQuietly(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // best-effort close of a rejected content body
        }
    }
}
