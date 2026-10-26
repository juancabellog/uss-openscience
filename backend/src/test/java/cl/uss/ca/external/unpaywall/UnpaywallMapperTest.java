package cl.uss.ca.external.unpaywall;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;

/**
 * Proposed Unpaywall v2 cases. Not executed by the agent.
 */
public final class UnpaywallMapperTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String SAMPLE_DOI = "10.2174/156720507783018190";

    private UnpaywallMapperTest() {
    }

    public static void main(String[] args) throws Exception {
        buildsEncodedUrlWithEmail();
        parsesOaTrueAndBestLocation();
        bestLocationNull();
        multipleLocationsAndUnknownFields();
        repositoryCopyIgnoresBestOnly();
        notFoundIsNotClosed();
        locationOrderDoesNotChangeSnapshot();
        sharedDoiIsOneKey();
        incrementalUnpaywallSelectsAndSkips();
        System.out.println("UnpaywallMapperTest OK");
    }

    static void buildsEncodedUrlWithEmail() {
        URI uri = UnpaywallUrls.doiRequest(
                "https://api.unpaywall.org/v2/",
                SAMPLE_DOI,
                "correo@uss.cl"
        );
        assert uri.getPath().contains("10.2174%2F156720507783018190") || uri.toString().contains("10.2174%2F156720507783018190");
        assert uri.getQuery().contains("email=correo%40uss.cl") || uri.toString().contains("email=correo%40uss.cl");
        assert !uri.toString().contains("/v2/search");
    }

    static void parsesOaTrueAndBestLocation() throws Exception {
        UnpaywallWorkRecord work = UnpaywallMapper.parse(MAPPER.readTree(OA_JSON));
        assert Boolean.TRUE.equals(work.isOa);
        assert "hybrid".equals(work.oaStatus);
        assert work.bestOaLocation != null;
        assert "publisher".equals(work.bestOaLocation.hostType);
        assert work.oaLocations.size() == 2;
        assert UnpaywallLocationIdentity.hasRepositoryCopy(work.oaLocations);
    }

    static void bestLocationNull() throws Exception {
        UnpaywallWorkRecord work = UnpaywallMapper.parse(MAPPER.readTree(CLOSED_JSON));
        assert Boolean.FALSE.equals(work.isOa);
        assert work.bestOaLocation == null;
        assert work.oaLocations.isEmpty();
        assert !UnpaywallLocationIdentity.hasRepositoryCopy(work.oaLocations);
    }

    static void multipleLocationsAndUnknownFields() throws Exception {
        UnpaywallWorkRecord work = UnpaywallMapper.parse(MAPPER.readTree(OA_JSON));
        assert work.oaLocations.size() == 2;
    }

    static void repositoryCopyIgnoresBestOnly() throws Exception {
        UnpaywallWorkRecord work = UnpaywallMapper.parse(MAPPER.readTree(OA_JSON));
        assert "publisher".equals(work.bestOaLocation.hostType);
        assert UnpaywallLocationIdentity.hasRepositoryCopy(work.oaLocations);
    }

    static void notFoundIsNotClosed() {
        UnpaywallFetchResult result = UnpaywallFetchResult.notFound(404);
        assert result.notFound();
        assert !result.found();
        assert result.work == null;
    }

    static void locationOrderDoesNotChangeSnapshot() throws Exception {
        UnpaywallWorkRecord first = UnpaywallMapper.parse(MAPPER.readTree(OA_JSON));
        UnpaywallWorkRecord second = UnpaywallMapper.parse(MAPPER.readTree(OA_JSON_REORDERED));
        assert UnpaywallWorkSnapshot.sameLocations(
                UnpaywallWorkSnapshot.locations(first.oaLocations),
                UnpaywallWorkSnapshot.locations(second.oaLocations)
        );
        assert UnpaywallWorkSnapshot.sameParent(
                UnpaywallWorkSnapshot.parent(first, UnpaywallFetchResult.FOUND),
                UnpaywallWorkSnapshot.parent(second, UnpaywallFetchResult.FOUND)
        );
    }

    static void sharedDoiIsOneKey() {
        cl.uss.ca.repository.UnpaywallDoiLink a = new cl.uss.ca.repository.UnpaywallDoiLink();
        a.doi = "https://doi.org/" + SAMPLE_DOI;
        a.researchOutputId = 1;
        cl.uss.ca.repository.UnpaywallDoiLink b = new cl.uss.ca.repository.UnpaywallDoiLink();
        b.doi = SAMPLE_DOI.toUpperCase();
        b.researchOutputId = 2;
        var unique = cl.uss.ca.service.UnpaywallSyncJob.uniqueDois(List.of(a, b));
        assert unique.size() == 1;
        assert unique.values().iterator().next().size() == 2;
    }

    static void incrementalUnpaywallSelectsAndSkips() {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.parse("2026-10-05T12:00:00Z");
        cl.uss.ca.sync.WorkSyncState fresh = new cl.uss.ca.sync.WorkSyncState();
        fresh.present = true;
        fresh.status = UnpaywallFetchResult.FOUND;
        fresh.lastSyncAt = now.minusDays(2);
        fresh.mapped = true;
        cl.uss.ca.sync.WorkSyncState stale = new cl.uss.ca.sync.WorkSyncState();
        stale.present = true;
        stale.status = UnpaywallFetchResult.FOUND;
        stale.lastSyncAt = now.minusDays(40);
        stale.mapped = true;
        cl.uss.ca.sync.WorkSyncState error = new cl.uss.ca.sync.WorkSyncState();
        error.present = true;
        error.status = UnpaywallFetchResult.ERROR;
        error.lastSyncAt = now.minusDays(1);
        error.mapped = true;
        cl.uss.ca.sync.WorkSyncState notFound = new cl.uss.ca.sync.WorkSyncState();
        notFound.present = true;
        notFound.status = UnpaywallFetchResult.NOT_FOUND;
        notFound.lastSyncAt = now.minusDays(2);
        notFound.mapped = true;
        java.util.Map<String, java.util.List<cl.uss.ca.repository.UnpaywallDoiLink>> unique = new java.util.LinkedHashMap<>();
        unique.put("10.1/new", java.util.List.of(new cl.uss.ca.repository.UnpaywallDoiLink()));
        unique.put("10.1/fresh", java.util.List.of(new cl.uss.ca.repository.UnpaywallDoiLink()));
        unique.put("10.1/stale", java.util.List.of(new cl.uss.ca.repository.UnpaywallDoiLink()));
        unique.put("10.1/error", java.util.List.of(new cl.uss.ca.repository.UnpaywallDoiLink()));
        unique.put("10.1/missing", java.util.List.of(new cl.uss.ca.repository.UnpaywallDoiLink()));
        java.util.Map<String, cl.uss.ca.sync.WorkSyncState> stored = java.util.Map.of(
                "10.1/fresh", fresh,
                "10.1/stale", stale,
                "10.1/error", error,
                "10.1/missing", notFound
        );
        var selected = cl.uss.ca.service.CrossrefSyncJob.retainIncremental(
                unique, stored, java.util.Set.of("10.1/fresh", "10.1/stale", "10.1/error", "10.1/missing"), 30, now, false
        );
        assert selected.containsKey("10.1/new");
        assert selected.containsKey("10.1/stale");
        assert selected.containsKey("10.1/error");
        assert !selected.containsKey("10.1/fresh");
        assert !selected.containsKey("10.1/missing");
        assert cl.uss.ca.sync.DoiIncrementalClassifier.classifyUnpaywall(notFound, 0, now) == null;
    }

    private static final String OA_JSON = """
            {
              "doi": "10.2174/156720507783018190",
              "doi_url": "https://doi.org/10.2174/156720507783018190",
              "title": "Sample OA article",
              "genre": "journal-article",
              "is_oa": true,
              "oa_status": "hybrid",
              "year": 2007,
              "publisher": "Example",
              "journal_name": "Example Journal",
              "journal_is_in_doaj": false,
              "future_field": "ignored",
              "best_oa_location": {
                "url": "https://example.org/best",
                "url_for_pdf": "https://example.org/best.pdf",
                "url_for_landing_page": "https://example.org/best-land",
                "host_type": "publisher",
                "license": "cc-by",
                "version": "publishedVersion",
                "is_best": true
              },
              "oa_locations": [
                {
                  "url": "https://example.org/best",
                  "host_type": "publisher",
                  "license": "cc-by",
                  "version": "publishedVersion",
                  "is_best": true
                },
                {
                  "url": "https://repo.example.org/item",
                  "url_for_pdf": "https://repo.example.org/item.pdf",
                  "host_type": "repository",
                  "license": null,
                  "version": "acceptedVersion",
                  "is_best": false
                }
              ]
            }
            """;

    private static final String OA_JSON_REORDERED = """
            {
              "doi": "10.2174/156720507783018190",
              "is_oa": true,
              "oa_status": "hybrid",
              "title": "Sample OA article",
              "genre": "journal-article",
              "year": 2007,
              "publisher": "Example",
              "journal_name": "Example Journal",
              "journal_is_in_doaj": false,
              "best_oa_location": {
                "url": "https://example.org/best",
                "url_for_pdf": "https://example.org/best.pdf",
                "url_for_landing_page": "https://example.org/best-land",
                "host_type": "publisher",
                "license": "cc-by",
                "version": "publishedVersion",
                "is_best": true
              },
              "oa_locations": [
                {
                  "url": "https://repo.example.org/item",
                  "url_for_pdf": "https://repo.example.org/item.pdf",
                  "host_type": "repository",
                  "version": "acceptedVersion",
                  "is_best": false
                },
                {
                  "url": "https://example.org/best",
                  "host_type": "publisher",
                  "license": "cc-by",
                  "version": "publishedVersion",
                  "is_best": true
                }
              ]
            }
            """;

    private static final String CLOSED_JSON = """
            {
              "doi": "10.2174/156720507783018190",
              "is_oa": false,
              "oa_status": "closed",
              "best_oa_location": null,
              "oa_locations": []
            }
            """;
}
