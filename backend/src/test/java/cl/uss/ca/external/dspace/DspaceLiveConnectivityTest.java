package cl.uss.ca.external.dspace;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Optional public GET against repositorio.uss.cl. Disabled unless -Ddspace.live.test=true.
 * Read-only: root HAL + one discover page. No cookies, tokens or writes.
 */
public final class DspaceLiveConnectivityTest {
    private static final String BASE = "https://repositorio.uss.cl/server/api";

    private DspaceLiveConnectivityTest() {
    }

    public static void main(String[] args) throws Exception {
        if (!Boolean.parseBoolean(System.getProperty("dspace.live.test", "false"))) {
            System.out.println("DspaceLiveConnectivityTest SKIPPED (set -Ddspace.live.test=true)");
            return;
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonNode root = get(client, mapper, DspaceUrls.root(BASE));
        DspaceRoot parsed = DspaceHalParser.root(root);
        assert parsed.dspaceVersion != null && parsed.dspaceVersion.startsWith("9.");
        DspaceHalPage<DspaceItemRecord> page = DspaceHalParser.discoverItems(
                get(client, mapper, DspaceUrls.searchItems(BASE, "*", "item", 0, 1))
        );
        assert page.totalElements > 0;
        assert !page.elements.isEmpty();
        System.out.println("DspaceLiveConnectivityTest OK version=" + parsed.dspaceVersion
                + " discoverTotal=" + page.totalElements);
    }

    private static JsonNode get(HttpClient client, ObjectMapper mapper, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/hal+json")
                .header("User-Agent", "OPENSCIENCE-USS/dspace-live-test (Universidad San Sebastian)")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assert response.statusCode() >= 200 && response.statusCode() < 300;
        return mapper.readTree(response.body());
    }
}
