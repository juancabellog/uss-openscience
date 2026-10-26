package cl.uss.ca.external.dspace;

public interface DspaceClient {
    void assertConfigured();

    boolean isConfigured();

    boolean isEnabled();

    DspaceRoot fetchRoot();

    DspaceHalPage<DspaceCommunityRecord> fetchCommunities(int page, int size);

    DspaceHalPage<DspaceCommunityRecord> fetchSubcommunities(String communityUuid, int page, int size);

    DspaceHalPage<DspaceCollectionRecord> fetchCollections(int page, int size);

    DspaceCommunityRecord fetchParentCommunity(String collectionUuid);

    DspaceHalPage<DspaceItemRecord> searchItems(String query, String dsoType, int page, int size);

    DspaceItemRecord fetchItem(String uuid);

    DspaceAccessStatus fetchAccessStatus(String itemUuid);

    DspaceCollectionRecord fetchOwningCollection(String itemUuid);

    DspaceHalPage<DspaceBundleRecord> fetchBundles(String itemUuid);

    DspaceHalPage<DspaceBundleRecord> fetchBundles(String itemUuid, int page, int size);

    DspaceHalPage<DspaceBitstreamRecord> fetchBitstreams(String bundleUuid);

    DspaceHalPage<DspaceBitstreamRecord> fetchBitstreams(String bundleUuid, int page, int size);

    DspaceAccessStatus fetchBitstreamAccess(String bitstreamUuid);

    DspaceBitstreamContent fetchBitstreamContent(String bitstreamUuid);

    String resolveHandle(String handle);
}
