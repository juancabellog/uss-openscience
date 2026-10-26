package cl.uss.ca.pure;

import cl.uss.ca.pure.json.PersonListResponse;
import cl.uss.ca.pure.json.ResearchOutputListResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class PureApiClient {
    private static final int MAX_REINTENTOS_429 = 3;

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PureApiClient(String baseUrl, String apiKey) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public PersonListResponse obtenerPaginaPersonas(int offset, int size)
            throws IOException, InterruptedException {
        PersonListResponse page = getJson("/persons", offset, size, PersonListResponse.class);
        return page == null ? new PersonListResponse() : page;
    }

    public ResearchOutputListResponse obtenerPaginaResearchOutputs(int offset, int size)
            throws IOException, InterruptedException {
        ResearchOutputListResponse page = getJson("/research-outputs", offset, size,
                ResearchOutputListResponse.class);
        return page == null ? new ResearchOutputListResponse() : page;
    }

    public HttpResponse<String> obtenerPersonaPorUuid(String uuid)
            throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + "/persons/" + uuid);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("api-key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();
        return enviarConReintento(request, "GET /persons/{uuid}");
    }

    public HttpResponse<String> actualizarPersonaPorUuid(String uuid, String jsonBody, String etag)
            throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + "/persons/" + uuid);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("api-key", apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody,
                        StandardCharsets.UTF_8));
        if (etag != null && !etag.isBlank()) {
            builder.header("If-Match", etag.trim());
        }
        return enviarConReintento(builder.build(), "PUT /persons/{uuid}");
    }

    public static String etagDe(HttpResponse<String> response) {
        if (response == null) {
            return null;
        }
        return response.headers().firstValue("ETag")
                .or(() -> response.headers().firstValue("etag"))
                .orElse(null);
    }

    public static boolean esRespuestaExitosa(int status) {
        return status == 200 || status == 201 || status == 204;
    }

    private <T> T getJson(String path, int offset, int size, Class<T> type)
            throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + path + "?size=" + size + "&offset=" + offset);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("api-key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();

        HttpResponse<String> response = enviarConReintento(request, "offset=" + offset + " size=" + size);
        int status = response.statusCode();
        if (status == 200) {
            return objectMapper.readValue(response.body(), type);
        }
        throw crearExcepcionHttp(status, uri, offset, size, response.body());
    }

    private HttpResponse<String> enviarConReintento(HttpRequest request, String contexto)
            throws IOException, InterruptedException {
        HttpResponse<String> response = null;
        for (int intento = 0; intento <= MAX_REINTENTOS_429; intento++) {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (!esErrorTemporal(response.statusCode()) || intento == MAX_REINTENTOS_429) {
                return response;
            }
            long esperaMs = 500L * (intento + 1);
            System.err.println("WARN Pure HTTP " + response.statusCode() + " " + contexto
                    + ". Reintento " + (intento + 1) + "/" + MAX_REINTENTOS_429
                    + " en " + esperaMs + " ms.");
            Thread.sleep(esperaMs);
        }
        return response;
    }

    private static boolean esErrorTemporal(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private PureApiException crearExcepcionHttp(int status, URI uri, int offset, int size, String body) {
        String diagnostico = "Pure HTTP " + status + " " + descripcionStatus(status)
                + " offset=" + offset + " size=" + size
                + " al consultar " + uri + ". Respuesta: " + abreviar(body);
        return new PureApiException(status, diagnostico);
    }

    private static String descripcionStatus(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 412 -> "Precondition Failed";
            case 429 -> "Too Many Requests";
            default -> status >= 500 ? "Server Error" : "Error";
        };
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String abreviar(String value) {
        if (value == null || value.isBlank()) {
            return "(sin cuerpo)";
        }
        String compacto = value.replaceAll("\\s+", " ").trim();
        return compacto.length() <= 400 ? compacto : compacto.substring(0, 400) + "...";
    }
}
