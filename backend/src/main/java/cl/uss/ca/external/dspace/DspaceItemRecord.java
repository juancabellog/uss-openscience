package cl.uss.ca.external.dspace;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class DspaceItemRecord {
    public String uuid;
    public String handle;
    public String name;
    public String handleUrl;
    public String doiRaw;
    public String normalizedDoi;
    public String dcType;
    public Boolean inArchive;
    public Boolean discoverable;
    public Boolean withdrawn;
    public OffsetDateTime lastModified;
    public String owningCollectionHref;
    public String owningCollectionUuid;
    public String bundlesHref;
    public String accessStatusHref;
    public DspaceAccessStatus access;
    public boolean bundleListingComplete;
    public DspaceInventoryState bitstreamInventory = DspaceInventoryState.NOT_LOADED;
    public final List<String> mappedCollectionUuids = new ArrayList<>();
    public final List<DspaceMetadataValue> metadata = new ArrayList<>();
    public final List<DspaceBundleRecord> bundles = new ArrayList<>();
}
