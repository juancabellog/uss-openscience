package cl.uss.ca.domain;

import cl.uss.ca.external.dspace.DspaceAccessClassifier;
import cl.uss.ca.external.dspace.DspaceBitstreamRecord;
import cl.uss.ca.external.dspace.DspaceCollectionRecord;
import cl.uss.ca.external.dspace.DspaceFulltextAvailability;
import cl.uss.ca.external.dspace.DspaceHalParser;
import cl.uss.ca.external.dspace.DspaceItemRecord;
import cl.uss.ca.external.dspace.DspaceUrls;
import cl.uss.ca.service.DspaceSyncJob;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Location vs access for DSpace manage view. Not executed by the agent.
 */
public final class DspaceStructureAccessTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String OA_USS_COLLECTION = "93c7cd28-8462-4150-8ae2-d1628c8e6973";
    private static final String INSTITUCIONAL = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1";
    private static final String OA_USS_COMMUNITY = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2";
    private static final String OTHER_COMMUNITY = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3";

    private DspaceStructureAccessTest() {
    }

    public static void main(String[] args) throws Exception {
        owningCollectionFromEmbed();
        communityHierarchyInstitucionalAccesoAbiertoUss();
        mappedCollectionsAreDistinctFromOwning();
        communityFilterIncludesDescendants();
        accesoAbiertoUssIsCollectionIdentityNotOpenAccess();
        openAccessStatus();
        metadataOnlyStatus();
        restrictedStatus();
        unknownStatusWhenAccessMissing();
        publicOriginalBitstream();
        itemWithoutFile();
        cardsTreatMissingInventoryAsUndetermined();
        skipEnrichDoesNotUseDiscoverStub();
        embedUrlsAvoidPerItemLookups();
        System.out.println("DspaceStructureAccessTest OK");
    }

    static void owningCollectionFromEmbed() throws Exception {
        DspaceItemRecord item = DspaceHalParser.item(MAPPER.readTree(ITEM_WITH_OWNING_EMBED));
        assert OA_USS_COLLECTION.equals(item.owningCollectionUuid);
        assert "metadata.only".equals(DspaceAccessClassifier.normalizeStatus(item.access.status));
    }

    static void communityHierarchyInstitucionalAccesoAbiertoUss() {
        Map<String, DspaceCommunityPath.Node> tree = tree();
        String path = DspaceCommunityPath.format(OA_USS_COMMUNITY, "Acceso Abierto USS", tree);
        assert "Institucional > Acceso Abierto USS".equals(path) : path;
    }

    static void mappedCollectionsAreDistinctFromOwning() throws Exception {
        DspaceItemRecord item = DspaceHalParser.item(MAPPER.readTree(ITEM_WITH_MAPPED_EMBED));
        assert OA_USS_COLLECTION.equals(item.owningCollectionUuid);
        assert item.mappedCollectionUuids.contains("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        assert !item.mappedCollectionUuids.contains(OA_USS_COLLECTION);
        assert DspaceCommunityPath.OWNING.equals("OWNING");
        assert DspaceCommunityPath.MAPPED.equals("MAPPED");
    }

    static void communityFilterIncludesDescendants() {
        List<DspaceCommunityPath.Node> nodes = List.of(
                new DspaceCommunityPath.Node(INSTITUCIONAL, null, "Institucional"),
                new DspaceCommunityPath.Node(OA_USS_COMMUNITY, INSTITUCIONAL, "Acceso Abierto USS"),
                new DspaceCommunityPath.Node(OTHER_COMMUNITY, null, "Revistas USS")
        );
        assert DspaceCommunityPath.selfAndDescendants(INSTITUCIONAL, nodes)
                .contains(OA_USS_COMMUNITY);
        assert !DspaceCommunityPath.selfAndDescendants(INSTITUCIONAL, nodes)
                .contains(OTHER_COMMUNITY);
    }

    static void accesoAbiertoUssIsCollectionIdentityNotOpenAccess() throws Exception {
        DspaceCollectionRecord collection = DspaceHalParser.collection(MAPPER.readTree(OA_USS_COLLECTION_JSON));
        assert "Acceso Abierto USS".equals(collection.name);
        assert OA_USS_COLLECTION.equals(collection.uuid);
        DspaceItemRecord item = DspaceHalParser.item(MAPPER.readTree(ITEM_WITH_OWNING_EMBED));
        assert OA_USS_COLLECTION.equals(item.owningCollectionUuid);
        assert DspaceAccessClassifier.METADATA_ONLY.equals(
                DspaceAccessClassifier.normalizeStatus(item.access.status)
        );
        assert DspaceAccessComparison.fromDspace(item.access.status)
                .equals(DspaceAccessComparison.METADATA_ONLY);
        assert !DspaceAccessComparison.OPEN.equals(DspaceAccessComparison.fromDspace(item.access.status));
    }

    static void openAccessStatus() {
        DspaceBitstreamRecord file = original("open.access", true);
        assert DspaceFulltextAvailability.FULLTEXT_PUBLIC
                == DspaceAccessClassifier.classify("open.access", List.of(file));
        assert DspaceAccessComparison.OPEN.equals(DspaceAccessComparison.fromDspace("open.access"));
    }

    static void metadataOnlyStatus() {
        assert DspaceFulltextAvailability.METADATA_ONLY
                == DspaceAccessClassifier.classify("metadata.only", List.of());
        assert DspaceAccessComparison.METADATA_ONLY.equals(
                DspaceAccessComparison.fromDspace("metadata.only")
        );
    }

    static void restrictedStatus() {
        DspaceBitstreamRecord file = original("restricted", false);
        assert DspaceFulltextAvailability.RESTRICTED
                == DspaceAccessClassifier.classify("restricted", List.of(file));
        assert DspaceAccessComparison.RESTRICTED.equals(DspaceAccessComparison.fromDspace("restricted"));
    }

    static void unknownStatusWhenAccessMissing() {
        assert DspaceAccessComparison.UNKNOWN.equals(DspaceAccessComparison.fromDspace(null));
        assert DspaceFulltextAvailability.UNKNOWN
                == DspaceAccessClassifier.classify(null, List.of());
    }

    static void publicOriginalBitstream() {
        DspaceBitstreamRecord file = original("open.access", true);
        assert DspaceAccessClassifier.hasPublicOriginal(List.of(file));
        assert DspaceAccessClassifier.hasOriginalBundle(List.of(file));
    }

    static void itemWithoutFile() {
        assert !DspaceAccessClassifier.hasOriginalBundle(List.of());
        assert !DspaceAccessClassifier.hasPublicOriginal(List.of());
        assert DspaceFulltextAvailability.METADATA_ONLY
                == DspaceAccessClassifier.classify("open.access", List.of());
    }

    static void cardsTreatMissingInventoryAsUndetermined() {
        assert DspaceAccessComparison.UNKNOWN.equals(DspaceAccessComparison.fromDspace(""));
        assert DspaceAccessComparison.fromDspace(null) != DspaceAccessComparison.RESTRICTED;
    }

    static void skipEnrichDoesNotUseDiscoverStub() {
        OffsetDateTime watermark = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        assert DspaceSyncJob.skipEnrich(true, watermark, OffsetDateTime.parse("2025-12-31T00:00:00Z"));
        assert !DspaceSyncJob.skipEnrich(false, watermark, OffsetDateTime.parse("2025-12-31T00:00:00Z"));
    }

    static void embedUrlsAvoidPerItemLookups() {
        String item = DspaceUrls.item("https://repositorio.uss.cl/server/api", "80012200-d875-4257-968d-b59a2c2abbb4");
        assert item.contains("/core/items/80012200-d875-4257-968d-b59a2c2abbb4");
        assert item.contains("embed=");
        assert item.contains("owningCollection");
        assert item.contains("accessStatus");
        String search = DspaceUrls.searchItems("https://repositorio.uss.cl/server/api", "*", "item", 0, 50);
        assert search.contains("/discover/search/objects");
        assert search.contains("embed=");
        String collections = DspaceUrls.collections("https://repositorio.uss.cl/server/api", 0, 100);
        assert collections.contains("embed=parentCommunity");
        assert DspaceUrls.mappedCollections("https://repositorio.uss.cl/server/api", "x", 0, 20)
                .contains("/mappedCollections");
    }

    private static Map<String, DspaceCommunityPath.Node> tree() {
        DspaceCommunityPath.Node root = new DspaceCommunityPath.Node(INSTITUCIONAL, null, "Institucional");
        DspaceCommunityPath.Node child = new DspaceCommunityPath.Node(OA_USS_COMMUNITY, INSTITUCIONAL, "Acceso Abierto USS");
        return Map.of(INSTITUCIONAL, root, OA_USS_COMMUNITY, child);
    }

    private static DspaceBitstreamRecord original(String access, boolean pub) {
        DspaceBitstreamRecord bitstream = new DspaceBitstreamRecord();
        bitstream.bundleName = "ORIGINAL";
        bitstream.accessStatus = access;
        bitstream.publicDownload = pub;
        bitstream.contentUrl = "https://repositorio.uss.cl/server/api/core/bitstreams/x/content";
        return bitstream;
    }

    private static final String OA_USS_COLLECTION_JSON = """
            {
              "uuid": "93c7cd28-8462-4150-8ae2-d1628c8e6973",
              "name": "Acceso Abierto USS",
              "handle": "uss/1785",
              "type": "collection"
            }
            """;

    private static final String ITEM_WITH_OWNING_EMBED = """
            {
              "uuid": "80012200-d875-4257-968d-b59a2c2abbb4",
              "name": "Sample article",
              "handle": "uss/24032",
              "metadata": {
                "dc.identifier.doi": [{ "value": "10.4067/s0034-98872019000901144", "place": 0 }]
              },
              "_embedded": {
                "owningCollection": {
                  "uuid": "93c7cd28-8462-4150-8ae2-d1628c8e6973",
                  "name": "Acceso Abierto USS",
                  "type": "collection"
                },
                "accessStatus": {
                  "status": "metadata.only",
                  "embargoDate": null,
                  "type": "accessStatus"
                }
              }
            }
            """;

    private static final String ITEM_WITH_MAPPED_EMBED = """
            {
              "uuid": "80012200-d875-4257-968d-b59a2c2abbb4",
              "name": "Sample article",
              "_embedded": {
                "owningCollection": {
                  "uuid": "93c7cd28-8462-4150-8ae2-d1628c8e6973",
                  "name": "Acceso Abierto USS"
                },
                "mappedCollections": {
                  "_embedded": {
                    "mappedCollections": [
                      { "uuid": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1", "name": "Otra colección" }
                    ]
                  }
                }
              }
            }
            """;
}
