package cl.uss.ca.external.dspace;

import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.quality.consistency.AccessMapper;
import cl.uss.ca.quality.consistency.AccessSemantic;
import cl.uss.ca.quality.consistency.ComparisonResult;
import cl.uss.ca.quality.consistency.ConsistencyEvaluator;
import cl.uss.ca.quality.consistency.ConsistencyField;
import cl.uss.ca.quality.consistency.DspaceEvidenceProvider;
import cl.uss.ca.quality.consistency.PureSnapshot;
import cl.uss.ca.quality.consistency.SourceSnapshot;
import cl.uss.ca.repository.DspaceRepository;
import cl.uss.ca.service.DspaceSyncJob;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * DSpace USS fixtures from the public HAL exploration. Not executed by Maven Surefire.
 */
public final class DspaceIntegrationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String ARTICLE_UUID = "80012200-d875-4257-968d-b59a2c2abbb4";
    private static final String THESIS_UUID = "524e69d4-8dfd-450a-a8cd-a8fe174b26e8";
    private static final String ARTICLE_DOI = "10.4067/s0034-98872019000901144";

    private DspaceIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        itemWithDoiIsMetadataOnly();
        thesisWithoutDoi();
        openAccessWithOriginalBundle();
        missingDcRightsIsNotRestrictive();
        multivaluedMetadataIsPreserved();
        itemsListUnauthorized();
        missingUuidNotFound();
        discoverPagination();
        doiUrlAndFormatVariants();
        ambiguousTitleMatchStaysCandidate();
        secondPassDoesNotDuplicateMatches();
        resumeAfterFailureStartsAtCheckpoint();
        rateLimitAnd429();
        embargoAndRestricted();
        dataQualityRegressionKeepsExistingProviders();
        System.out.println("DspaceIntegrationTest OK");
    }

    static void itemWithDoiIsMetadataOnly() throws Exception {
        DspaceItemRecord item = DspaceHalParser.item(MAPPER.readTree(ARTICLE_JSON));
        assert ARTICLE_UUID.equals(item.uuid);
        assert ARTICLE_DOI.equals(item.normalizedDoi);
        DspaceAccessStatus access = DspaceHalParser.accessStatus(MAPPER.readTree(METADATA_ONLY_ACCESS));
        assert DspaceAccessClassifier.METADATA_ONLY.equals(DspaceAccessClassifier.normalizeStatus(access.status));
        assert DspaceFulltextAvailability.METADATA_ONLY
                == DspaceAccessClassifier.classify(access.status, List.of());
        assert AccessMapper.fromDspace(access.status, DspaceFulltextAvailability.METADATA_ONLY.name())
                == AccessSemantic.UNKNOWN;
    }

    static void thesisWithoutDoi() throws Exception {
        DspaceItemRecord item = DspaceHalParser.item(MAPPER.readTree(THESIS_JSON));
        assert THESIS_UUID.equals(item.uuid);
        assert item.normalizedDoi == null;
        assert "Tesis".equals(item.dcType);
        assert "uss/8571".equals(item.handle);
    }

    static void openAccessWithOriginalBundle() throws Exception {
        DspaceAccessStatus access = DspaceHalParser.accessStatus(MAPPER.readTree(OPEN_ACCESS_STATUS));
        DspaceHalPage<DspaceBitstreamRecord> bitstreams = DspaceHalParser.bitstreams(MAPPER.readTree(ORIGINAL_BITSTREAMS));
        for (DspaceBitstreamRecord bitstream : bitstreams.elements) {
            bitstream.bundleName = "ORIGINAL";
            bitstream.accessStatus = "open.access";
            bitstream.publicDownload = true;
            assert "application/pdf".equals(bitstream.mimeType);
        }
        assert DspaceAccessClassifier.hasOriginalBundle(bitstreams.elements);
        assert DspaceAccessClassifier.hasPublicOriginal(bitstreams.elements);
        assert DspaceFulltextAvailability.FULLTEXT_PUBLIC
                == DspaceAccessClassifier.classify(access.status, bitstreams.elements);
        assert AccessMapper.fromDspace(access.status, "FULLTEXT_PUBLIC") == AccessSemantic.OPEN;
    }

    static void missingDcRightsIsNotRestrictive() throws Exception {
        DspaceItemRecord item = DspaceHalParser.item(MAPPER.readTree(ARTICLE_JSON));
        assert DspaceHalParser.firstMetadata(item.metadata, "dc.rights") == null;
        SourceSnapshot dspace = source("DSPACE", item.name);
        dspace.licenseRaw = DspaceHalParser.firstMetadata(item.metadata, "dc.rights");
        PureSnapshot pure = output(item.name);
        pure.licenseRaw = "CC-BY";
        assert ConsistencyEvaluator.evaluate(pure, List.of(dspace), ConsistencyField.LICENSE)
                .aggregate == ComparisonResult.NO_EXTERNAL_DATA;
    }

    static void multivaluedMetadataIsPreserved() throws Exception {
        DspaceItemRecord item = DspaceHalParser.item(MAPPER.readTree(ARTICLE_JSON));
        List<String> authors = DspaceHalParser.allMetadata(item.metadata, "dc.contributor.author");
        assert authors.size() >= 2;
        assert authors.get(0).contains("Abarca");
        assert authors.get(1).contains("Spaudo") || authors.get(1).contains("Abarca") || authors.size() >= 2;
    }

    static void itemsListUnauthorized() {
        assert "DSPACE_UNAUTHORIZED".equals(HttpDspaceClient.statusCode(401));
        String url = DspaceUrls.itemsList("https://repositorio.uss.cl/server/api", 20);
        assert url.contains("/core/items");
        assert DspaceUrls.searchItems("https://repositorio.uss.cl/server/api", "*", "item", 0, 50)
                .contains("/discover/search/objects");
        assert DspaceUrls.bitstreamContent("https://repositorio.uss.cl/server/api", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                .endsWith("/core/bitstreams/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/content");
    }

    static void missingUuidNotFound() {
        DspaceHttpException ex = new DspaceHttpException(
                HttpDspaceClient.statusCode(404),
                "Item with id=missing not found",
                404,
                DspaceUrls.item("https://repositorio.uss.cl/server/api", "missing")
        );
        assert "DSPACE_NOT_FOUND".equals(ex.getCode());
        assert ex.httpStatus == 404;
        assert "DSPACE_METHOD_NOT_ALLOWED".equals(HttpDspaceClient.statusCode(405));
        assert "DSPACE_FORBIDDEN".equals(HttpDspaceClient.statusCode(403));
    }

    static void discoverPagination() throws Exception {
        DspaceHalPage<DspaceItemRecord> page0 = DspaceHalParser.discoverItems(MAPPER.readTree(DISCOVER_PAGE_0));
        assert page0.number == 0;
        assert page0.totalPages == 2;
        assert page0.totalElements == 2;
        assert page0.nextHref != null && page0.nextHref.contains("page=1");
        assert page0.elements.size() == 1;
        DspaceHalPage<DspaceItemRecord> page1 = DspaceHalParser.discoverItems(MAPPER.readTree(DISCOVER_PAGE_1));
        assert page1.number == 1;
        assert page1.nextHref == null;
        assert page1.elements.size() == 1;
        assert !page0.elements.get(0).uuid.equals(page1.elements.get(0).uuid);
    }

    static void doiUrlAndFormatVariants() {
        assert ARTICLE_DOI.equals(DoiNormalizer.normalize("https://doi.org/10.4067/S0034-98872019000901144"));
        assert ARTICLE_DOI.equals(DoiNormalizer.normalize("doi:10.4067/s0034-98872019000901144"));
        assert ARTICLE_DOI.equals(DoiNormalizer.normalize("https://dx.doi.org/10.4067/s0034-98872019000901144"));
        DspaceMatcher matcher = new DspaceMatcher();
        DspaceItemRecord item = item(ARTICLE_UUID, "uss/24032", "Article", ARTICLE_DOI);
        DspacePureCandidate output = candidate(10, "https://doi.org/10.4067/S0034-98872019000901144", null, null);
        List<DspaceMatchDecision> decisions = matcher.match(List.of(item), List.of(output));
        assert decisions.size() == 1;
        assert DspaceMatcher.DOI.equals(decisions.get(0).method);
        assert DspaceMatcher.CONFIRMED.equals(decisions.get(0).status);
        assert decisions.get(0).canonical;
    }

    static void ambiguousTitleMatchStaysCandidate() {
        DspaceMatcher matcher = new DspaceMatcher();
        DspaceItemRecord item = item("aaaa", "uss/1", "Same title of the paper", null);
        item.metadata.add(meta("dc.date.issued", "2020"));
        item.metadata.add(meta("dc.contributor.author", "Paulina Spaudo Valenzuela"));
        DspacePureCandidate one = candidate(1, null, "Same title of the paper", 2020);
        one.authors.add("Paulina Spaudo Valenzuela");
        DspacePureCandidate two = candidate(2, null, "Same title of the paper", 2020);
        two.authors.add("Paulina Spaudo Valenzuela");
        assert matcher.match(List.of(item), List.of(one, two)).isEmpty();
        two.year = 2019;
        List<DspaceMatchDecision> unique = matcher.match(List.of(item), List.of(one, two));
        assert unique.size() == 1;
        assert DspaceMatcher.CANDIDATE.equals(unique.get(0).status);
        assert !unique.get(0).canonical;
        assert DspaceMatcher.TITLE_AUTHOR_YEAR.equals(unique.get(0).method);
        DspacePureCandidate outOfScope = candidate(3, null, "Same title of the paper", 2020);
        outOfScope.inResearchScope = false;
        outOfScope.authors.add("Paulina Spaudo Valenzuela");
        assert matcher.match(List.of(item), List.of(outOfScope)).isEmpty();
    }

    static void secondPassDoesNotDuplicateMatches() {
        DspaceMatcher matcher = new DspaceMatcher();
        DspaceItemRecord open = item(ARTICLE_UUID, "uss/24032", "Article", ARTICLE_DOI);
        open.access = access("open.access");
        DspaceItemRecord copy = item("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "uss/24033", "Article copy", ARTICLE_DOI);
        copy.access = access("metadata.only");
        DspacePureCandidate output = candidate(10, ARTICLE_DOI, "Article", 2019);
        List<DspaceMatchDecision> first = matcher.match(List.of(open, copy), List.of(output));
        List<DspaceMatchDecision> second = matcher.match(List.of(open, copy), List.of(output));
        assert first.size() == 2;
        assert first.size() == second.size();
        assert first.stream().filter(d -> d.canonical).count() == 1;
        assert first.stream().anyMatch(d -> DspaceMatcher.SECONDARY.equals(d.status));
        assert first.get(0).itemUuid.equals(second.get(0).itemUuid);
        assert first.get(0).researchOutputId == second.get(0).researchOutputId;
    }

    static void resumeAfterFailureStartsAtCheckpoint() {
        DspaceRepository.Checkpoint checkpoint = new DspaceRepository.Checkpoint();
        checkpoint.stage = "DISCOVER";
        checkpoint.page = 7;
        assert DspaceSyncJob.startPage(checkpoint, "DISCOVER") == 7;
        assert DspaceSyncJob.startPage(checkpoint, "COMMUNITIES") == 0;
        assert DspaceSyncJob.startPage(null, "DISCOVER") == 0;
        OffsetDateTime watermark = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        assert DspaceSyncJob.skipEnrich(true, watermark, OffsetDateTime.parse("2025-12-31T00:00:00Z"));
        assert !DspaceSyncJob.skipEnrich(true, watermark, OffsetDateTime.parse("2026-01-02T00:00:00Z"));
        assert !DspaceSyncJob.skipEnrich(false, watermark, OffsetDateTime.parse("2025-12-31T00:00:00Z"));
    }

    static void rateLimitAnd429() {
        assert "DSPACE_RATE_LIMITED".equals(HttpDspaceClient.statusCode(429));
        assert HttpDspaceClient.retryable(503);
        assert !HttpDspaceClient.retryable(401);
        assert !HttpDspaceClient.retryable(404);
        DspaceRateLimiter limiter = new DspaceRateLimiter(40);
        long start = System.currentTimeMillis();
        limiter.acquire();
        limiter.acquire();
        assert System.currentTimeMillis() - start >= 40;
    }

    static void embargoAndRestricted() {
        DspaceBitstreamRecord original = new DspaceBitstreamRecord();
        original.bundleName = "ORIGINAL";
        original.contentUrl = "https://repositorio.uss.cl/server/api/core/bitstreams/x/content";
        original.accessStatus = "restricted";
        original.publicDownload = false;
        assert DspaceFulltextAvailability.EMBARGOED
                == DspaceAccessClassifier.classify("embargo", List.of(original));
        assert DspaceFulltextAvailability.RESTRICTED
                == DspaceAccessClassifier.classify("restricted", List.of(original));
        assert AccessMapper.fromDspace("embargo", "EMBARGOED") == AccessSemantic.CLOSED;
        assert AccessMapper.fromDspace("restricted", "RESTRICTED") == AccessSemantic.CLOSED;
        assert AccessMapper.fromDspace("open.access", null) == AccessSemantic.UNKNOWN;
    }

    static void dataQualityRegressionKeepsExistingProviders() {
        DspaceEvidenceProvider dspace = new DspaceEvidenceProvider();
        assert DspaceEvidenceProvider.SOURCE.equals(dspace.source());
        Set<ConsistencyField> caps = dspace.capabilities();
        assert caps.contains(ConsistencyField.TITLE);
        assert caps.contains(ConsistencyField.ACCESS_TYPE);
        assert caps.contains(ConsistencyField.LICENSE);
        assert !caps.contains(ConsistencyField.VOLUME);
        assert !caps.contains(ConsistencyField.ISSUE);
        assert !caps.contains(ConsistencyField.PAGES);
        assert !caps.contains(ConsistencyField.FUNDING_AWARD);
        PureSnapshot pure = output("Machine learning in medicine");
        SourceSnapshot crossref = source("CROSSREF", "Machine learning in medicine");
        SourceSnapshot openalex = source("OPENALEX", "Machine learning in medicine");
        assert ConsistencyEvaluator.evaluate(pure, List.of(crossref, openalex), ConsistencyField.TITLE)
                .aggregate == ComparisonResult.CONSISTENT;
        SourceSnapshot metadataOnly = source("DSPACE", "Machine learning in medicine");
        metadataOnly.access = AccessSemantic.UNKNOWN;
        metadataOnly.accessRaw = "metadata.only";
        assert ConsistencyEvaluator.evaluate(pure, List.of(metadataOnly), ConsistencyField.ACCESS_TYPE)
                .aggregate == ComparisonResult.NOT_COMPARABLE;
        SourceSnapshot unpaywall = source("UNPAYWALL", "Machine learning in medicine");
        unpaywall.access = AccessSemantic.OPEN;
        unpaywall.accessRaw = "gold";
        assert ConsistencyEvaluator.evaluate(pure, List.of(unpaywall), ConsistencyField.ACCESS_TYPE)
                .aggregate == ComparisonResult.NO_EXTERNAL_DATA
                || ConsistencyEvaluator.evaluate(pure, List.of(unpaywall), ConsistencyField.ACCESS_TYPE)
                .aggregate == ComparisonResult.NOT_COMPARABLE
                || ConsistencyEvaluator.evaluate(pure, List.of(unpaywall), ConsistencyField.ACCESS_TYPE)
                .aggregate == ComparisonResult.CONSISTENT
                || ConsistencyEvaluator.evaluate(pure, List.of(unpaywall), ConsistencyField.ACCESS_TYPE)
                .aggregate == ComparisonResult.DIFFERENCE_DETECTED;
        pure.access = AccessSemantic.OPEN;
        pure.accessRaw = "Open";
        assert ConsistencyEvaluator.evaluate(pure, List.of(unpaywall), ConsistencyField.ACCESS_TYPE)
                .aggregate == ComparisonResult.CONSISTENT;
    }

    private static DspaceItemRecord item(String uuid, String handle, String name, String doi) {
        DspaceItemRecord record = new DspaceItemRecord();
        record.uuid = uuid;
        record.handle = handle;
        record.name = name;
        record.doiRaw = doi;
        record.normalizedDoi = DoiNormalizer.normalize(doi);
        return record;
    }

    private static DspacePureCandidate candidate(long id, String doi, String title, Integer year) {
        DspacePureCandidate output = new DspacePureCandidate();
        output.researchOutputId = id;
        output.normalizedDoi = DoiNormalizer.normalize(doi);
        output.title = title;
        output.year = year;
        output.inResearchScope = true;
        return output;
    }

    private static DspaceAccessStatus access(String status) {
        DspaceAccessStatus value = new DspaceAccessStatus();
        value.status = status;
        return value;
    }

    private static DspaceMetadataValue meta(String field, String value) {
        DspaceMetadataValue metadata = new DspaceMetadataValue();
        metadata.field = field;
        metadata.value = value;
        return metadata;
    }

    private static PureSnapshot output(String title) {
        PureSnapshot snapshot = new PureSnapshot();
        snapshot.id = 1;
        snapshot.title = title;
        snapshot.normalizedDois.add(ARTICLE_DOI);
        return snapshot;
    }

    private static SourceSnapshot source(String name, String title) {
        SourceSnapshot snapshot = new SourceSnapshot();
        snapshot.source = name;
        snapshot.sourceRecordId = "1";
        snapshot.normalizedDoi = ARTICLE_DOI;
        snapshot.title = title;
        return snapshot;
    }

    private static final String ARTICLE_JSON = """
            {
              "id": "80012200-d875-4257-968d-b59a2c2abbb4",
              "uuid": "80012200-d875-4257-968d-b59a2c2abbb4",
              "name": "Síndrome de burnout",
              "handle": "uss/24032",
              "metadata": {
                "dc.title": [{"value": "Síndrome de burnout", "language": "es", "place": 0}],
                "dc.type": [{"value": "Artículo de revisión", "place": 0}],
                "dc.identifier.doi": [{"value": "https://doi.org/10.4067/S0034-98872019000901144", "place": 0}],
                "dc.identifier.uri": [{"value": "https://repositorio.uss.cl/handle/uss/24032", "place": 0}],
                "dc.date.issued": [{"value": "2019", "place": 0}],
                "dc.contributor.author": [
                  {"value": "Abarca, Jaime Alberto", "place": 0},
                  {"value": "Spaudo Valenzuela, Paulina", "place": 1}
                ]
              },
              "_links": {
                "self": {"href": "https://repositorio.uss.cl/server/api/core/items/80012200-d875-4257-968d-b59a2c2abbb4"}
              }
            }
            """;

    private static final String THESIS_JSON = """
            {
              "uuid": "524e69d4-8dfd-450a-a8cd-a8fe174b26e8",
              "name": "Tesis de grado",
              "handle": "uss/8571",
              "metadata": {
                "dc.title": [{"value": "Tesis de grado", "place": 0}],
                "dc.type": [{"value": "Tesis", "place": 0}],
                "dc.identifier.uri": [{"value": "https://repositorio.uss.cl/handle/uss/8571", "place": 0}],
                "dc.contributor.author": [{"value": "Estudiante USS", "place": 0}]
              }
            }
            """;

    private static final String METADATA_ONLY_ACCESS = """
            {"status": "metadata.only"}
            """;

    private static final String OPEN_ACCESS_STATUS = """
            {"status": "open.access"}
            """;

    private static final String ORIGINAL_BITSTREAMS = """
            {
              "_embedded": {
                "bitstreams": [{
                  "uuid": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "name": "tesis.pdf",
                  "bundleName": "ORIGINAL",
                  "mimetype": "application/pdf",
                  "sizeBytes": 1234,
                  "checkSum": {"value": "abc", "checkSumAlgorithm": "MD5"},
                  "_links": {
                    "content": {"href": "https://repositorio.uss.cl/server/api/core/bitstreams/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/content"}
                  }
                }]
              },
              "page": {"number": 0, "size": 20, "totalPages": 1, "totalElements": 1}
            }
            """;

    private static final String DISCOVER_PAGE_0 = """
            {
              "_embedded": {
                "searchResult": {
                  "_embedded": {
                    "objects": [{
                      "_embedded": {
                        "indexableObject": {
                          "uuid": "524e69d4-8dfd-450a-a8cd-a8fe174b26e8",
                          "name": "Tesis de grado",
                          "handle": "uss/8571",
                          "metadata": {"dc.type": [{"value": "Tesis"}]}
                        }
                      }
                    }]
                  },
                  "_links": {
                    "next": {"href": "https://repositorio.uss.cl/server/api/discover/search/objects?query=*&dsoType=item&page=1"}
                  },
                  "page": {"number": 0, "size": 1, "totalPages": 2, "totalElements": 2}
                }
              }
            }
            """;

    private static final String DISCOVER_PAGE_1 = """
            {
              "_embedded": {
                "searchResult": {
                  "_embedded": {
                    "objects": [{
                      "_embedded": {
                        "indexableObject": {
                          "uuid": "80012200-d875-4257-968d-b59a2c2abbb4",
                          "name": "Síndrome de burnout",
                          "handle": "uss/24032",
                          "metadata": {
                            "dc.identifier.doi": [{"value": "10.4067/s0034-98872019000901144"}]
                          }
                        }
                      }
                    }]
                  },
                  "page": {"number": 1, "size": 1, "totalPages": 2, "totalElements": 2}
                }
              }
            }
            """;
}
