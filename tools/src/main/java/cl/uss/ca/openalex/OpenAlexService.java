package cl.uss.ca.openalex;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente OpenAlex para obtener un Work por DOI.
 * Replica la URL y el parseo de authorships del OpenAlexService original,
 * usando HttpClient de este proyecto (sin Spring/RestTemplate).
 */
public class OpenAlexService {
    private static final String OPENALEX_BASE_URL = "https://api.openalex.org/works/";
    private static final String DOI_PREFIX = "https://doi.org/";
    private static final int MAX_REINTENTOS_429 = 3;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String userAgent;

    public OpenAlexService() {
        this(null);
    }

    public OpenAlexService(String mailto) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        if (mailto != null && !mailto.isBlank()) {
            this.userAgent = "uss-ca-orcid-search (mailto:" + mailto.trim() + ")";
        } else {
            this.userAgent = "uss-ca-orcid-search";
        }
    }

    public OpenAlexWork obtenerPublicacionPorDoi(String doi) throws Exception {
        String normalizedDoi = normalizeDoi(doi);
        String workId = DOI_PREFIX + normalizedDoi;
        String openAlexUrl = OPENALEX_BASE_URL + java.net.URLEncoder.encode(workId, java.nio.charset.StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(openAlexUrl))
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(60))
                .GET();

        HttpResponse<String> response = enviarConReintento(builder.build());
        int status = response.statusCode();
        if (status == 404) {
            throw new OpenAlexNotFoundException(normalizedDoi);
        }
        if (status < 200 || status >= 300 || response.body() == null) {
            throw new IOException("OpenAlex HTTP " + status + " al consultar DOI " + normalizedDoi
                    + ". Respuesta: " + abreviar(response.body()));
        }

        JsonNode json = objectMapper.readTree(response.body());
        OpenAlexWork work = new OpenAlexWork();
        work.setDoi(normalizedDoi);
        if (json.hasNonNull("title")) {
            work.setTitle(json.get("title").asText());
        } else if (json.hasNonNull("display_name")) {
            work.setTitle(json.get("display_name").asText());
        }
        work.setAuthors(parsearAutores(json.get("authorships")));
        return work;
    }

    public static String normalizeDoi(String doi) {
        if (doi == null || doi.trim().isEmpty()) {
            throw new IllegalArgumentException("DOI cannot be null or empty");
        }
        String normalized = doi.trim();
        if (normalized.startsWith(DOI_PREFIX)) {
            normalized = normalized.substring(DOI_PREFIX.length());
        }
        if (normalized.regionMatches(true, 0, "http://doi.org/", 0, 15)) {
            normalized = normalized.substring(15);
        }
        if (normalized.startsWith("doi.org/")) {
            normalized = normalized.substring(8);
        }
        return normalized;
    }

    public static String normalizarOrcid(String orcid) {
        if (orcid == null) {
            return null;
        }
        String value = orcid.trim();
        if (value.isEmpty()) {
            return null;
        }
        String lower = value.toLowerCase();
        if (lower.startsWith("https://orcid.org/")) {
            value = value.substring("https://orcid.org/".length());
        } else if (lower.startsWith("http://orcid.org/")) {
            value = value.substring("http://orcid.org/".length());
        }
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private List<OpenAlexAuthor> parsearAutores(JsonNode authorships) {
        List<OpenAlexAuthor> authors = new ArrayList<>();
        if (authorships == null || !authorships.isArray()) {
            return authors;
        }
        for (int i = 0; i < authorships.size(); i++) {
            JsonNode node = authorships.get(i);
            if (node == null || node.isNull()) {
                continue;
            }
            OpenAlexAuthor author = new OpenAlexAuthor();
            author.setPosicion(i + 1);
            String raw = text(node, "raw_author_name");
            if (raw != null) {
                int index = raw.indexOf('(');
                if (index != -1) {
                    raw = raw.substring(0, index).trim();
                }
            }
            JsonNode authorObj = node.get("author");
            if (authorObj != null && !authorObj.isNull()) {
                author.setOpenAlexId(text(authorObj, "id"));
                author.setDisplayName(text(authorObj, "display_name"));
                author.setOrcid(normalizarOrcid(text(authorObj, "orcid")));
            }
            if (raw == null || raw.isBlank()) {
                raw = author.getDisplayName();
            }
            author.setRawAuthorName(raw);
            authors.add(author);
        }
        return authors;
    }

    private HttpResponse<String> enviarConReintento(HttpRequest request)
            throws IOException, InterruptedException {
        HttpResponse<String> response = null;
        for (int intento = 0; intento <= MAX_REINTENTOS_429; intento++) {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429 || intento == MAX_REINTENTOS_429) {
                return response;
            }
            long esperaMs = 500L * (intento + 1);
            System.err.println("WARN OpenAlex HTTP 429. Reintento " + (intento + 1)
                    + "/" + MAX_REINTENTOS_429 + " en " + esperaMs + " ms.");
            Thread.sleep(esperaMs);
        }
        return response;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String abreviar(String value) {
        if (value == null || value.isBlank()) {
            return "(sin cuerpo)";
        }
        String compacto = value.replaceAll("\\s+", " ").trim();
        return compacto.length() <= 300 ? compacto : compacto.substring(0, 300) + "...";
    }
}
