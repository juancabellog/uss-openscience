package cl.uss.ca.orcid;

import cl.uss.ca.openalex.OpenAlexService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class OrcidPublicClient {
    private static final String ORCID_BASE_URL = "https://orcid.org/";
    private static final int MAX_REINTENTOS = 3;
    private static final Pattern ORCID_PATTERN =
            Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]$");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final UssAffiliationMatcher affiliationMatcher;
    private final String userAgent;

    public OrcidPublicClient() {
        this(new UssAffiliationMatcher());
    }

    public OrcidPublicClient(UssAffiliationMatcher affiliationMatcher) {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.affiliationMatcher = affiliationMatcher;
        this.userAgent = "uss-ca-orcid-validation/1.0 (Universidad San Sebastian)";
    }

    public static String normalizarOrcid(String orcid) {
        String value = OpenAlexService.normalizarOrcid(orcid);
        if (value == null) {
            return null;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    public static boolean esFormatoValido(String orcidNormalizado) {
        return orcidNormalizado != null && ORCID_PATTERN.matcher(orcidNormalizado).matches();
    }

    public OrcidValidationData consultarOrcid(String orcidNormalizado)
            throws InterruptedException {
        String url = ORCID_BASE_URL + orcidNormalizado;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            HttpResponse<String> response = enviarConReintento(request);
            int status = response.statusCode();
            if (status == 404) {
                System.err.println("WARN ORCID no encontrado: " + orcidNormalizado);
                return OrcidValidationData.noEncontrado(orcidNormalizado);
            }
            if (status < 200 || status >= 300 || response.body() == null) {
                return OrcidValidationData.error("HTTP " + status + " al consultar " + orcidNormalizado);
            }
            JsonNode json = objectMapper.readTree(response.body());
            return parsearRegistro(json);
        } catch (InterruptedException ex) {
            throw ex;
        } catch (Exception ex) {
            return OrcidValidationData.error("Error al consultar " + orcidNormalizado + ": " + ex.getMessage());
        }
    }

    public String extraerNombreOrcid(JsonNode json) {
        if (json == null) {
            return "";
        }
        JsonNode name = json.path("person").path("name");
        String given = valorTexto(name.path("given-names").path("value"));
        String family = valorTexto(name.path("family-name").path("value"));
        return unirNombre(given, family);
    }

    public List<String> extraerOtherNames(JsonNode json) {
        List<String> nombres = new ArrayList<>();
        if (json == null) {
            return nombres;
        }
        JsonNode other = json.path("person").path("other-names").path("other-name");
        if (other.isMissingNode() || other.isNull()) {
            return nombres;
        }
        if (other.isArray()) {
            for (JsonNode item : other) {
                agregarSiHayTexto(nombres, valorTexto(item.path("content")));
            }
        } else {
            agregarSiHayTexto(nombres, valorTexto(other.path("content")));
        }
        return nombres;
    }

    public boolean tieneAfiliacionUSS(JsonNode json) {
        List<String> organizaciones = extraerNombresOrganizacion(json);
        return affiliationMatcher.algunaEsUniversidadSanSebastian(organizaciones);
    }

    List<String> extraerNombresOrganizacion(JsonNode json) {
        List<String> nombres = new ArrayList<>();
        if (json == null) {
            return nombres;
        }
        recolectarOrganizaciones(json.path("activities-summary"), nombres);
        return nombres;
    }

    public Set<String> extraerDoisOrcid(JsonNode root) {
        Set<String> dois = new LinkedHashSet<>();
        if (root == null) {
            return dois;
        }
        recolectarDois(root.path("activities-summary").path("works"), dois);
        return dois;
    }

    private OrcidValidationData parsearRegistro(JsonNode json) {
        String nombre = extraerNombreOrcid(json);
        List<String> otherNames = extraerOtherNames(json);
        boolean afiliacionUss = tieneAfiliacionUSS(json);
        Set<String> doisOrcid = extraerDoisOrcid(json);
        return OrcidValidationData.ok(nombre, otherNames, afiliacionUss, doisOrcid);
    }

    private void recolectarDois(JsonNode node, Set<String> dois) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                recolectarDois(child, dois);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        extraerSiEsDoi(node, dois);
        var fields = node.fields();
        while (fields.hasNext()) {
            recolectarDois(fields.next().getValue(), dois);
        }
    }

    private static void extraerSiEsDoi(JsonNode node, Set<String> dois) {
        String tipo = valorTexto(node.path("external-id-type"));
        if (tipo == null || !tipo.equalsIgnoreCase("doi")) {
            return;
        }
        String preferido = valorTexto(node.path("external-id-normalized").path("value"));
        String crudo = preferido != null ? preferido : valorTexto(node.path("external-id-value"));
        String normalizado = DoiNormalizer.normalizarDoi(crudo);
        if (normalizado != null) {
            dois.add(normalizado);
        }
    }

    private void recolectarOrganizaciones(JsonNode node, List<String> nombres) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                recolectarOrganizaciones(child, nombres);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        JsonNode organization = node.get("organization");
        if (organization != null && organization.isObject()) {
            agregarSiHayTexto(nombres, extraerNombreOrganizacion(organization));
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            recolectarOrganizaciones(fields.next().getValue(), nombres);
        }
    }

    private static String extraerNombreOrganizacion(JsonNode organization) {
        JsonNode name = organization.get("name");
        if (name == null || name.isNull() || name.isMissingNode()) {
            return null;
        }
        if (name.isTextual()) {
            return valorTexto(name);
        }
        String value = valorTexto(name.path("value"));
        if (value != null) {
            return value;
        }
        return valorTexto(name);
    }

    private HttpResponse<String> enviarConReintento(HttpRequest request)
            throws IOException, InterruptedException {
        HttpResponse<String> response = null;
        for (int intento = 0; intento <= MAX_REINTENTOS; intento++) {
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException ex) {
                if (intento == MAX_REINTENTOS) {
                    throw ex;
                }
                esperarBackoff(intento, "I/O " + ex.getMessage());
                continue;
            }
            int status = response.statusCode();
            if (!esErrorTemporal(status) || intento == MAX_REINTENTOS) {
                return response;
            }
            esperarBackoff(intento, "HTTP " + status);
        }
        return response;
    }

    private static boolean esErrorTemporal(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static void esperarBackoff(int intento, String motivo) throws InterruptedException {
        long esperaMs = 500L * (intento + 1);
        System.err.println("WARN ORCID " + motivo + ". Reintento " + (intento + 1)
                + "/" + MAX_REINTENTOS + " en " + esperaMs + " ms.");
        Thread.sleep(esperaMs);
    }

    private static String unirNombre(String given, String family) {
        String a = given == null ? "" : given.trim();
        String b = family == null ? "" : family.trim();
        return (a + " " + b).trim();
    }

    private static String valorTexto(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void agregarSiHayTexto(List<String> destino, String value) {
        if (value != null && !value.isBlank()) {
            destino.add(value.trim());
        }
    }
}
