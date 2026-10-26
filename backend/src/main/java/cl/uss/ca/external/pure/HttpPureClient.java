package cl.uss.ca.external.pure;

import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.external.pure.json.JournalItem;
import cl.uss.ca.external.pure.json.JournalListResponse;
import cl.uss.ca.external.pure.json.OrganizationItem;
import cl.uss.ca.external.pure.json.OrganizationListResponse;
import cl.uss.ca.external.pure.json.PersonItem;
import cl.uss.ca.external.pure.json.PersonListResponse;
import cl.uss.ca.external.pure.json.ProjectItem;
import cl.uss.ca.external.pure.json.ProjectListResponse;
import cl.uss.ca.external.pure.json.ResearchOutputItem;
import cl.uss.ca.external.pure.json.ResearchOutputListResponse;
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
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class HttpPureClient implements PureClient {
    private static final Logger LOG = Logger.getLogger(HttpPureClient.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final ThreadLocal<String> LAST_ETAG = new ThreadLocal<>();

    public static void clearRequestConcurrencyToken() {
        LAST_ETAG.remove();
    }

    @Inject
    @ConfigProperty(name = "pure.api.url")
    Optional<String> apiUrl;

    @Inject
    @ConfigProperty(name = "pure.api.key")
    Optional<String> apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    @Override
    public void assertConfigured() {
        credentials();
    }

    @Override
    public PurePage<PersonItem> fetchPeoplePage(int offset, int size) {
        PersonListResponse page = getJson("/persons", offset, size, PersonListResponse.class);
        if (page == null) {
            return new PurePage<>(null, null);
        }
        return new PurePage<>(page.getCount(), page.getItems());
    }

    @Override
    public PurePage<ResearchOutputItem> fetchResearchOutputsPage(int offset, int size) {
        ResearchOutputListResponse page = getJson("/research-outputs", offset, size, ResearchOutputListResponse.class);
        if (page == null) {
            return new PurePage<>(null, null);
        }
        return new PurePage<>(page.getCount(), page.getItems());
    }

    @Override
    public PurePage<JournalItem> fetchJournalsPage(int offset, int size) {
        JournalListResponse page = getJson("/journals", offset, size, JournalListResponse.class);
        if (page == null) {
            return new PurePage<>(null, null);
        }
        return new PurePage<>(page.getCount(), page.getItems());
    }

    @Override
    public PurePage<ProjectItem> fetchProjectsPage(int offset, int size) {
        ProjectListResponse page = getJson("/projects", offset, size, ProjectListResponse.class);
        if (page == null) {
            return new PurePage<>(null, null);
        }
        return new PurePage<>(page.getCount(), page.getItems());
    }

    @Override
    public PurePage<OrganizationItem> fetchOrganizationsPage(int offset, int size) {
        OrganizationListResponse page = getJson("/organizations", offset, size, OrganizationListResponse.class);
        if (page == null) {
            return new PurePage<>(null, null);
        }
        return new PurePage<>(page.getCount(), page.getItems());
    }

    @Override
    public JsonNode getPerson(String uuid) {
        return exchange("GET", "/persons/" + uuid, null);
    }

    @Override
    public JsonNode putPerson(String uuid, JsonNode body) {
        return exchange("PUT", "/persons/" + uuid, body);
    }

    @Override
    public JsonNode getResearchOutput(String uuid) {
        return exchange("GET", "/research-outputs/" + uuid, null);
    }

    @Override
    public JsonNode putResearchOutput(String uuid, JsonNode body) {
        return exchange("PUT", "/research-outputs/" + uuid, body);
    }

    @Override
    public JsonNode getJournal(String uuid) {
        return exchange("GET", "/journals/" + uuid, null);
    }

    @Override
    public JsonNode putJournal(String uuid, JsonNode body) {
        return exchange("PUT", "/journals/" + uuid, body);
    }

    @Override
    public JsonNode getProject(String uuid) {
        return exchange("GET", "/projects/" + uuid, null);
    }

    @Override
    public JsonNode putProject(String uuid, JsonNode body) {
        return exchange("PUT", "/projects/" + uuid, body);
    }

    @Override
    public JsonNode getOrganization(String uuid) {
        return exchange("GET", "/organizations/" + uuid, null);
    }

    @Override
    public JsonNode putOrganization(String uuid, JsonNode body) {
        return exchange("PUT", "/organizations/" + uuid, body);
    }

    @Override
    public JsonNode getJournalMetrics(String uuid, String collectionId) {
        return getMetrics("/journals/" + uuid + "/metrics/" + collectionId);
    }

    @Override
    public JsonNode getResearchOutputMetrics(String uuid, String collectionId) {
        return getMetrics("/research-outputs/" + uuid + "/metrics/" + collectionId);
    }

    @Override
    public JsonNode getPersonMetrics(String uuid, String collectionId) {
        return getMetrics("/persons/" + uuid + "/metrics/" + collectionId);
    }

    private JsonNode getMetrics(String path) {
        return exchange("GET", path, null);
    }

    private JsonNode exchange(String method, String path, JsonNode body) {
        Credentials credentials = credentials();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(credentials.baseUrl + path))
                .header("api-key", credentials.apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60));
        if (body == null) {
            builder.GET();
        } else {
            try {
                String ifMatch = LAST_ETAG.get();
                if (ifMatch != null && !ifMatch.isBlank()) {
                    builder.header("If-Match", ifMatch);
                }
                builder.header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            } catch (IOException ex) {
                throw new PureClientException(0, "Unable to serialize PURE payload", ex);
            }
        }
        try {
            HttpResponse<String> response = sendWithRetry(builder.build(), 0, 1);
            int status = response.statusCode();
            if ("GET".equals(method)) {
                response.headers().firstValue("ETag").ifPresent(value -> {
                    if (value != null && !value.isBlank()) {
                        LAST_ETAG.set(value.trim());
                    }
                });
            }
            if (status == 200 || status == 201 || status == 204) {
                if (response.body() == null || response.body().isBlank()) {
                    return objectMapper.createObjectNode();
                }
                return objectMapper.readTree(response.body());
            }
            if (status == 409 || status == 412) {
                throw new PureClientException(status, "PURE_RECORD_CHANGED");
            }
            throw new PureClientException(status, "PURE HTTP " + status + " " + method + " " + path);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PureClientException(0, "PURE request interrupted", ex);
        } catch (IOException ex) {
            throw new PureClientException(0, "PURE request failed", ex);
        }
    }

    private <T> T getJson(String path, int offset, int size, Class<T> type) {
        Credentials credentials = credentials();
        URI uri = URI.create(credentials.baseUrl + path + "?size=" + size + "&offset=" + offset);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("api-key", credentials.apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();
        try {
            HttpResponse<String> response = sendWithRetry(request, offset, size);
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), type);
            }
            throw new PureClientException(
                    response.statusCode(),
                    "PURE HTTP " + response.statusCode() + " offset=" + offset + " size=" + size
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PureClientException(0, "PURE request interrupted", ex);
        } catch (IOException ex) {
            throw new PureClientException(0, "PURE request failed", ex);
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request, int offset, int size)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        HttpResponse<String> response = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (!isRetryable(status) || attempt == MAX_RETRIES) {
                return response;
            }
            long waitMs = 500L * (attempt + 1);
            LOG.warning("PURE HTTP " + status + " offset=" + offset + " size=" + size
                    + " retry=" + (attempt + 1));
            Thread.sleep(waitMs);
        }
        return response;
    }

    private Credentials credentials() {
        String url = firstNonBlank(apiUrl.orElse(null), System.getenv("PURE_API_URL"));
        String key = firstNonBlank(apiKey.orElse(null), System.getenv("PURE_API_KEY"));
        if (url == null || key == null) {
            throw new ValidationException(
                    "PURE_NOT_CONFIGURED",
                    "PURE API is not configured. Set pure.api.url and PURE_API_KEY."
            );
        }
        return new Credentials(trimTrailingSlash(url), key);
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record Credentials(String baseUrl, String apiKey) {
    }
}
