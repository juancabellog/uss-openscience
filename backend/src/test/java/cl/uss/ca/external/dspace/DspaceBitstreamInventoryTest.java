package cl.uss.ca.external.dspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paging, fallback and EMPTY vs FAILED. Not executed by the agent.
 */
public final class DspaceBitstreamInventoryTest {
    private DspaceBitstreamInventoryTest() {
    }

    public static void main(String[] args) {
        embedCompleteSkipsDirectFetch();
        embedIncompleteUsesDirectPages();
        metadataOnlyStillLoadsInventory();
        fetchFailedDoesNotConfirm();
        confirmedEmptyIsConfirmed();
        pagingCollectsMoreThanTwenty();
        severalBitstreamPagesAreMergedWithoutDuplicates();
        continuesWhenTotalPagesLies();
        System.out.println("DspaceBitstreamInventoryTest OK");
    }

    static void embedCompleteSkipsDirectFetch() {
        FakeClient client = new FakeClient();
        DspaceItemRecord item = originalItem("item-1", completeOriginal("bundle-1", "file-1"));
        item.bundleListingComplete = true;
        DspaceBitstreamInventory.Result result = inventory(client).load(item);
        assertEquals(DspaceInventoryState.CONFIRMED, result.state);
        assertEquals(1, result.originalBitstreams().size());
        assertEquals(0, client.bitstreamFetches);
        assertEquals(0, client.bundleFetches);
    }

    static void embedIncompleteUsesDirectPages() {
        FakeClient client = new FakeClient();
        client.bitstreamPages.put("bundle-1", List.of(
                bitstreamPage(0, 2, 2, bitstream("file-a", "bundle-1")),
                bitstreamPage(1, 2, 2, bitstream("file-b", "bundle-1"))
        ));
        DspaceItemRecord item = originalItem("item-2", incompleteOriginal("bundle-1"));
        item.bundleListingComplete = true;
        DspaceBitstreamInventory.Result result = inventory(client).load(item);
        assertEquals(DspaceInventoryState.CONFIRMED, result.state);
        assertEquals(2, result.originalBitstreams().size());
        assertTrue(client.bitstreamFetches >= 2);
        assertEquals(0, client.discoverCalls);
        assertEquals(0, client.matchCalls);
        assertEquals(0, client.accessCalls);
    }

    static void metadataOnlyStillLoadsInventory() {
        FakeClient client = new FakeClient();
        client.bundlePages.put("item-3", List.of(bundlePage(completeOriginal("bundle-3", "file-3"))));
        DspaceItemRecord item = new DspaceItemRecord();
        item.uuid = "item-3";
        item.access = new DspaceAccessStatus();
        item.access.status = "metadata.only";
        DspaceBitstreamInventory.Result result = inventory(client).load(item);
        assertEquals(DspaceInventoryState.CONFIRMED, result.state);
        assertEquals(1, result.originalBitstreams().size());
        assertTrue(client.bundleFetches > 0);
    }

    static void fetchFailedDoesNotConfirm() {
        FakeClient client = new FakeClient();
        client.failBitstreams = true;
        DspaceItemRecord item = originalItem("item-4", incompleteOriginal("bundle-4"));
        item.bundleListingComplete = true;
        DspaceBitstreamInventory.Result result = inventory(client).load(item);
        assertEquals(DspaceInventoryState.FETCH_FAILED, result.state);
        assertTrue(!result.state.mayReplaceBitstreams());
    }

    static void confirmedEmptyIsConfirmed() {
        FakeClient client = new FakeClient();
        DspaceHalPage<DspaceBitstreamRecord> empty = new DspaceHalPage<>();
        empty.pagePresent = true;
        empty.totalElements = 0;
        empty.totalPages = 0;
        client.bitstreamPages.put("bundle-5", List.of(empty));
        DspaceItemRecord item = originalItem("item-5", incompleteOriginal("bundle-5"));
        item.bundleListingComplete = true;
        DspaceBitstreamInventory.Result result = inventory(client).load(item);
        assertEquals(DspaceInventoryState.CONFIRMED, result.state);
        assertTrue(result.confirmedEmpty());
        assertTrue(result.state.mayReplaceBitstreams());
    }

    static void pagingCollectsMoreThanTwenty() {
        FakeClient client = new FakeClient();
        List<DspaceHalPage<DspaceBundleRecord>> bundlePages = new ArrayList<>();
        List<DspaceBundleRecord> first = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            first.add(namedBundle("other-" + i, "TEXT"));
        }
        first.add(namedBundle("original-21", "ORIGINAL"));
        bundlePages.add(bundlePage(0, 2, 21, first.toArray(DspaceBundleRecord[]::new)));
        bundlePages.add(bundlePage(1, 2, 1, namedBundle("original-21", "ORIGINAL")));
        client.bundlePages.put("item-6", bundlePages);
        DspaceHalPage<DspaceBitstreamRecord> files = bitstreamPage(0, 1, 21);
        for (int i = 0; i < 21; i++) {
            files.elements.add(bitstream("pdf-" + i, "original-21"));
        }
        files.totalElements = 21;
        files.totalPages = 1;
        client.bitstreamPages.put("original-21", List.of(files));
        DspaceItemRecord item = new DspaceItemRecord();
        item.uuid = "item-6";
        DspaceBitstreamInventory.Result result = inventory(client).load(item);
        assertEquals(DspaceInventoryState.CONFIRMED, result.state);
        assertEquals(21, result.originalBitstreams().size());
        assertTrue(client.bundleFetches >= 1);
    }

    static void severalBitstreamPagesAreMergedWithoutDuplicates() {
        FakeClient client = new FakeClient();
        List<DspaceHalPage<DspaceBitstreamRecord>> pages = new ArrayList<>();
        DspaceHalPage<DspaceBitstreamRecord> first = bitstreamPage(0, 3, 21);
        DspaceHalPage<DspaceBitstreamRecord> second = bitstreamPage(1, 3, 21);
        DspaceHalPage<DspaceBitstreamRecord> third = bitstreamPage(2, 3, 21);
        for (int i = 0; i < 20; i++) {
            first.elements.add(bitstream("pdf-" + i, "bundle-7"));
        }
        second.elements.add(bitstream("pdf-0", "bundle-7"));
        second.elements.add(bitstream("pdf-20", "bundle-7"));
        third.elements.add(bitstream("pdf-20", "bundle-7"));
        pages.add(first);
        pages.add(second);
        pages.add(third);
        client.bitstreamPages.put("bundle-7", pages);
        DspaceItemRecord item = originalItem("item-7", incompleteOriginal("bundle-7"));
        item.bundleListingComplete = true;
        DspaceBitstreamInventory.Result result = inventory(client).load(item);
        assertEquals(DspaceInventoryState.CONFIRMED, result.state);
        assertEquals(21, result.originalBitstreams().size());
        assertTrue(client.bitstreamFetches >= 3);
        assertEquals(0, client.discoverCalls);
        assertEquals(0, client.accessCalls);
    }

    static void continuesWhenTotalPagesLies() {
        FakeClient client = new FakeClient();
        client.bundlePages.put("item-8", List.of(
                bundlePage(0, 1, 3, namedBundle("text-8", "TEXT")),
                bundlePage(1, 1, 3, namedBundle("thumb-8", "THUMBNAIL")),
                bundlePage(2, 1, 3, namedBundle("original-8", "ORIGINAL"))
        ));
        DspaceHalPage<DspaceBitstreamRecord> files = bitstreamPage(0, 1, 1, bitstream("pdf-8", "original-8"));
        client.bitstreamPages.put("original-8", List.of(files));
        DspaceItemRecord item = new DspaceItemRecord();
        item.uuid = "item-8";
        DspaceBitstreamInventory inventory = inventory(client);
        inventory.pageSize = 1;
        DspaceBitstreamInventory.Result result = inventory.load(item);
        assertEquals(DspaceInventoryState.CONFIRMED, result.state);
        assertEquals(1, result.originalBitstreams().size());
        assertTrue(client.bundleFetches >= 3);
    }

    private static DspaceBitstreamInventory inventory(FakeClient client) {
        DspaceBitstreamInventory inventory = new DspaceBitstreamInventory();
        inventory.dspaceClient = client;
        inventory.pageSize = 20;
        return inventory;
    }

    private static DspaceItemRecord originalItem(String itemUuid, DspaceBundleRecord bundle) {
        DspaceItemRecord item = new DspaceItemRecord();
        item.uuid = itemUuid;
        item.bundles.add(bundle);
        return item;
    }

    private static DspaceBundleRecord completeOriginal(String bundleUuid, String fileUuid) {
        DspaceBundleRecord bundle = namedBundle(bundleUuid, "ORIGINAL");
        bundle.bitstreams.add(bitstream(fileUuid, bundleUuid));
        bundle.bitstreamInventoryComplete = true;
        return bundle;
    }

    private static DspaceBundleRecord incompleteOriginal(String bundleUuid) {
        DspaceBundleRecord bundle = namedBundle(bundleUuid, "ORIGINAL");
        bundle.bitstreamInventoryComplete = false;
        return bundle;
    }

    private static DspaceBundleRecord namedBundle(String uuid, String name) {
        DspaceBundleRecord bundle = new DspaceBundleRecord();
        bundle.uuid = uuid;
        bundle.name = name;
        return bundle;
    }

    private static DspaceBitstreamRecord bitstream(String uuid, String bundleUuid) {
        DspaceBitstreamRecord record = new DspaceBitstreamRecord();
        record.uuid = uuid;
        record.bundleUuid = bundleUuid;
        record.bundleName = "ORIGINAL";
        record.name = uuid + ".pdf";
        return record;
    }

    private static DspaceHalPage<DspaceBundleRecord> bundlePage(DspaceBundleRecord... bundles) {
        return bundlePage(0, 1, bundles.length, bundles);
    }

    private static DspaceHalPage<DspaceBundleRecord> bundlePage(
            int number,
            int totalPages,
            long totalElements,
            DspaceBundleRecord... bundles
    ) {
        DspaceHalPage<DspaceBundleRecord> page = new DspaceHalPage<>();
        page.pagePresent = true;
        page.number = number;
        page.size = 20;
        page.totalPages = totalPages;
        page.totalElements = totalElements;
        page.elements.addAll(List.of(bundles));
        return page;
    }

    private static DspaceHalPage<DspaceBitstreamRecord> bitstreamPage(
            int number,
            int totalPages,
            long totalElements,
            DspaceBitstreamRecord... files
    ) {
        DspaceHalPage<DspaceBitstreamRecord> page = new DspaceHalPage<>();
        page.pagePresent = true;
        page.number = number;
        page.size = 1;
        page.totalPages = totalPages;
        page.totalElements = totalElements;
        page.elements.addAll(List.of(files));
        return page;
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

    static final class FakeClient implements DspaceClient {
        final Map<String, List<DspaceHalPage<DspaceBundleRecord>>> bundlePages = new LinkedHashMap<>();
        final Map<String, List<DspaceHalPage<DspaceBitstreamRecord>>> bitstreamPages = new LinkedHashMap<>();
        int bundleFetches;
        int bitstreamFetches;
        int discoverCalls;
        int matchCalls;
        int accessCalls;
        boolean failBitstreams;

        @Override
        public void assertConfigured() {
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public DspaceRoot fetchRoot() {
            return new DspaceRoot();
        }

        @Override
        public DspaceHalPage<DspaceCommunityRecord> fetchCommunities(int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DspaceHalPage<DspaceCommunityRecord> fetchSubcommunities(String communityUuid, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DspaceHalPage<DspaceCollectionRecord> fetchCollections(int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DspaceCommunityRecord fetchParentCommunity(String collectionUuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DspaceHalPage<DspaceItemRecord> searchItems(String query, String dsoType, int page, int size) {
            discoverCalls++;
            throw new UnsupportedOperationException("discover must not run during bitstream inventory");
        }

        @Override
        public DspaceItemRecord fetchItem(String uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DspaceAccessStatus fetchAccessStatus(String itemUuid) {
            accessCalls++;
            throw new UnsupportedOperationException("accessStatus must not run during bitstream inventory");
        }

        @Override
        public DspaceCollectionRecord fetchOwningCollection(String itemUuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DspaceHalPage<DspaceBundleRecord> fetchBundles(String itemUuid) {
            return fetchBundles(itemUuid, 0, 20);
        }

        @Override
        public DspaceHalPage<DspaceBundleRecord> fetchBundles(String itemUuid, int page, int size) {
            bundleFetches++;
            List<DspaceHalPage<DspaceBundleRecord>> pages = bundlePages.getOrDefault(itemUuid, List.of());
            if (page >= pages.size()) {
                DspaceHalPage<DspaceBundleRecord> empty = new DspaceHalPage<>();
                empty.pagePresent = true;
                empty.number = page;
                empty.totalPages = pages.size();
                empty.totalElements = pages.isEmpty() ? 0 : pages.get(0).totalElements;
                return empty;
            }
            return pages.get(page);
        }

        @Override
        public DspaceHalPage<DspaceBitstreamRecord> fetchBitstreams(String bundleUuid) {
            return fetchBitstreams(bundleUuid, 0, 20);
        }

        @Override
        public DspaceHalPage<DspaceBitstreamRecord> fetchBitstreams(String bundleUuid, int page, int size) {
            bitstreamFetches++;
            if (failBitstreams) {
                throw new DspaceHttpException("DSPACE_TEMPORARY_ERROR", "boom", 503, bundleUuid);
            }
            List<DspaceHalPage<DspaceBitstreamRecord>> pages = bitstreamPages.getOrDefault(bundleUuid, List.of());
            if (page >= pages.size()) {
                DspaceHalPage<DspaceBitstreamRecord> empty = new DspaceHalPage<>();
                empty.pagePresent = true;
                empty.number = page;
                empty.totalPages = pages.size();
                empty.totalElements = pages.isEmpty() ? 0 : pages.get(0).totalElements;
                return empty;
            }
            return pages.get(page);
        }

        @Override
        public DspaceAccessStatus fetchBitstreamAccess(String bitstreamUuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DspaceBitstreamContent fetchBitstreamContent(String bitstreamUuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String resolveHandle(String handle) {
            matchCalls++;
            throw new UnsupportedOperationException();
        }
    }
}
