package cl.uss.ca.external.dspace;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HAL bitstream embed shapes. Not executed by the agent.
 */
public final class DspaceHalParserBitstreamTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private DspaceHalParserBitstreamTest() {
    }

    public static void main(String[] args) throws Exception {
        directArrayIsParsedButNotComplete();
        nestedHalPageIsComplete();
        nestedEmptyPageIsConfirmedEmpty();
        malformedBitstreamsAreIncomplete();
        nestedFirstPageOfManyIsIncomplete();
        nestedBundleCollectionIsParsed();
        System.out.println("DspaceHalParserBitstreamTest OK");
    }

    static void directArrayIsParsedButNotComplete() throws Exception {
        DspaceBundleRecord bundle = DspaceHalParser.bundle(MAPPER.readTree("""
                {
                  "uuid": "bundle-1",
                  "name": "ORIGINAL",
                  "_embedded": {
                    "bitstreams": [{
                      "uuid": "file-1",
                      "name": "paper.pdf",
                      "bundleName": "ORIGINAL"
                    }]
                  }
                }
                """));
        assertEquals("bundle-1", bundle.uuid);
        assertEquals(1, bundle.bitstreams.size());
        assertEquals("file-1", bundle.bitstreams.get(0).uuid);
        assertEquals("bundle-1", bundle.bitstreams.get(0).bundleUuid);
        assertEquals("ORIGINAL", bundle.bitstreams.get(0).bundleName);
        assertTrue(!bundle.bitstreamInventoryComplete);
    }

    static void nestedHalPageIsComplete() throws Exception {
        DspaceBundleRecord bundle = DspaceHalParser.bundle(MAPPER.readTree("""
                {
                  "uuid": "bundle-2",
                  "name": "ORIGINAL",
                  "_embedded": {
                    "bitstreams": {
                      "_embedded": {
                        "bitstreams": [{
                          "uuid": "file-2",
                          "name": "tesis.pdf",
                          "bundleName": "ORIGINAL"
                        }]
                      },
                      "page": {"number": 0, "size": 20, "totalPages": 1, "totalElements": 1}
                    }
                  }
                }
                """));
        assertEquals(1, bundle.bitstreams.size());
        assertEquals("file-2", bundle.bitstreams.get(0).uuid);
        assertEquals("bundle-2", bundle.bitstreams.get(0).bundleUuid);
        assertTrue(bundle.bitstreamInventoryComplete);
        DspaceHalPage<DspaceBitstreamRecord> page = DspaceHalParser.embeddedBitstreams(MAPPER.readTree("""
                {
                  "_embedded": {
                    "bitstreams": {
                      "_embedded": {
                        "bitstreams": [{"uuid": "file-2"}]
                      },
                      "page": {"number": 0, "size": 20, "totalPages": 1, "totalElements": 1}
                    }
                  }
                }
                """));
        assertTrue(page.inventoryComplete());
    }

    static void nestedEmptyPageIsConfirmedEmpty() throws Exception {
        DspaceHalPage<DspaceBitstreamRecord> page = DspaceHalParser.bitstreams(MAPPER.readTree("""
                {
                  "_embedded": {"bitstreams": []},
                  "page": {"number": 0, "size": 20, "totalPages": 0, "totalElements": 0}
                }
                """));
        assertTrue(page.confirmedEmpty());
        assertTrue(page.inventoryComplete());
        DspaceBundleRecord emptyBundle = DspaceHalParser.bundle(MAPPER.readTree("""
                {
                  "uuid": "bundle-empty",
                  "name": "ORIGINAL",
                  "_embedded": {
                    "bitstreams": {
                      "_embedded": {"bitstreams": []},
                      "page": {"number": 0, "size": 20, "totalPages": 0, "totalElements": 0}
                    }
                  }
                }
                """));
        assertTrue(emptyBundle.bitstreams.isEmpty());
        assertTrue(emptyBundle.bitstreamInventoryComplete);
    }

    static void malformedBitstreamsAreIncomplete() throws Exception {
        DspaceBundleRecord bundle = DspaceHalParser.bundle(MAPPER.readTree("""
                {
                  "uuid": "bundle-3",
                  "name": "ORIGINAL",
                  "_embedded": { "bitstreams": "oops" }
                }
                """));
        assertTrue(bundle.bitstreams.isEmpty());
        assertTrue(!bundle.bitstreamInventoryComplete);
        DspaceHalPage<DspaceBitstreamRecord> missing = DspaceHalParser.embeddedBitstreams(MAPPER.readTree("{}"));
        assertTrue(!missing.pagePresent);
        assertTrue(!missing.inventoryComplete());
    }

    static void nestedFirstPageOfManyIsIncomplete() throws Exception {
        DspaceBundleRecord bundle = DspaceHalParser.bundle(MAPPER.readTree("""
                {
                  "uuid": "bundle-4",
                  "name": "ORIGINAL",
                  "_embedded": {
                    "bitstreams": {
                      "_embedded": {
                        "bitstreams": [{"uuid": "file-a"}, {"uuid": "file-b"}]
                      },
                      "page": {"number": 0, "size": 2, "totalPages": 3, "totalElements": 5}
                    }
                  }
                }
                """));
        assertEquals(2, bundle.bitstreams.size());
        assertTrue(!bundle.bitstreamInventoryComplete);
    }

    static void nestedBundleCollectionIsParsed() throws Exception {
        DspaceHalPage<DspaceBundleRecord> page = DspaceHalParser.bundles(MAPPER.readTree("""
                {
                  "_embedded": {
                    "bundles": {
                      "_embedded": {
                        "bundles": [
                          {"uuid": "b-text", "name": "TEXT"},
                          {"uuid": "b-original", "name": "ORIGINAL"}
                        ]
                      },
                      "page": {"number": 0, "size": 20, "totalPages": 1, "totalElements": 2}
                    }
                  },
                  "page": {"number": 0, "size": 20, "totalPages": 1, "totalElements": 2}
                }
                """));
        assertEquals(2, page.elements.size());
        assertEquals("b-original", page.elements.get(1).uuid);
        assertTrue(page.inventoryComplete());
        DspaceHalPage<DspaceBundleRecord> direct = DspaceHalParser.bundles(MAPPER.readTree("""
                {
                  "_embedded": {
                    "bundles": [
                      {"uuid": "b-text", "name": "TEXT"},
                      {"uuid": "b-thumb", "name": "THUMBNAIL"},
                      {"uuid": "b-original", "name": "ORIGINAL"}
                    ]
                  },
                  "page": {"number": 0, "size": 20, "totalPages": 1, "totalElements": 3}
                }
                """));
        assertEquals(3, direct.elements.size());
        assertTrue(direct.inventoryComplete());
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("expected true");
        }
    }
}
