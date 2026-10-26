package cl.uss.ca.service;

import cl.uss.ca.api.dto.DspaceStatsDto;
import cl.uss.ca.external.dspace.DspaceAccessClassifier;
import cl.uss.ca.external.dspace.DspaceAccessStatus;
import cl.uss.ca.external.dspace.DspaceBitstreamInventory;
import cl.uss.ca.external.dspace.DspaceBitstreamRecord;
import cl.uss.ca.external.dspace.DspaceBundleRecord;
import cl.uss.ca.external.dspace.DspaceClient;
import cl.uss.ca.external.dspace.DspaceInventoryState;
import cl.uss.ca.external.dspace.DspaceCollectionRecord;
import cl.uss.ca.external.dspace.DspaceCommunityRecord;
import cl.uss.ca.external.dspace.DspaceHalPage;
import cl.uss.ca.external.dspace.DspaceHttpException;
import cl.uss.ca.external.dspace.DspaceItemRecord;
import cl.uss.ca.external.dspace.DspaceMatchDecision;
import cl.uss.ca.external.dspace.DspaceMatcher;
import cl.uss.ca.external.dspace.DspaceRoot;
import cl.uss.ca.repository.DspaceRepository;
import cl.uss.ca.repository.SyncRunRepository;
import cl.uss.ca.sync.SyncChangeRepository;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncRunState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class DspaceSyncJob {
    private static final Logger LOG = Logger.getLogger(DspaceSyncJob.class.getName());
    static final String ENTITY = "DSPACE";
    static final String STAGE_COMMUNITIES = "COMMUNITIES";
    static final String STAGE_COLLECTIONS = "COLLECTIONS";
    static final String STAGE_DISCOVER = "DISCOVER";
    static final String STAGE_MATCH = "MATCH";
    static final String STAGE_BITSTREAMS = "BITSTREAMS";
    static final String STAGE_BITSTREAM_BACKFILL = "BITSTREAM_BACKFILL";
    static final String SUSPECT_INVENTORY = "DSPACE_BITSTREAM_INVENTORY_SUSPECT";

    @Inject
    DspaceService dspaceService;

    @Inject
    DspaceClient dspaceClient;

    @Inject
    DspaceBitstreamInventory bitstreamInventory;

    @Inject
    DspaceRepository dspaceRepository;

    @Inject
    SyncRunRepository syncRunRepository;

    @Inject
    SyncChangeRepository syncChangeRepository;

    @Inject
    SyncJobSupport jobSupport;

    @Inject
    @ConfigProperty(name = "dspace.pageSize", defaultValue = "50")
    int pageSize;

    private final DspaceMatcher matcher = new DspaceMatcher();

    public void run(SyncRunState run) throws Exception {
        dspaceService.assertTables();
        int size = Math.max(1, pageSize);
        DspaceRoot root = dspaceClient.fetchRoot();
        String version = root == null ? null : root.dspaceVersion;
        LOG.info("Starting DSPACE sync id=" + run.syncId + " mode=" + run.syncMode + " version=" + version);
        DspaceRepository.Checkpoint checkpoint = run.isIncremental() ? null : dspaceRepository.loadCheckpoint();
        int communityStart = startPage(checkpoint, STAGE_COMMUNITIES);
        syncCommunities(run, size, communityStart);
        int collectionStart = startPage(checkpoint, STAGE_COLLECTIONS);
        syncCollections(run, size, collectionStart);
        int discoverStart = startPage(checkpoint, STAGE_DISCOVER);
        DiscoverStats discover = syncDiscover(run, size, discoverStart);
        run.totalExpected = toExpected(discover.totalElements);
        if (!run.fatal && discover.complete) {
            match(run);
            if (!run.isIncremental() && jobSupport.allowsAbsenceReconciliation(run)) {
                Long executionId = parseExecution(run.syncId);
                if (executionId != null) {
                    int missing = dspaceRepository.markMissing(executionId);
                    run.markedInactive.addAndGet(missing);
                }
            }
            dspaceRepository.clearCheckpoint();
        } else if (!discover.complete) {
            run.fatal = true;
            run.errorCode = SyncJobSupport.PURE_INCOMPLETE;
            run.message = "DSpace discover reported " + discover.totalElements
                    + " items but the crawl did not finish. Absence marks were not applied.";
        }
        DspaceStatsDto snapshot = dspaceRepository.counts();
        snapshot.dspaceVersion = version;
        dspaceRepository.saveRunStats(run, snapshot, version, discover.totalElements);
        persistProgress(run);
        LOG.info("syncId=" + run.syncId + " DSpace processed=" + run.processed.get()
                + " created=" + run.created.get()
                + " updated=" + run.updated.get()
                + " unchanged=" + run.unchanged.get()
                + " errors=" + run.errors.get()
                + " discoverTotal=" + discover.totalElements);
    }

    private void syncCommunities(SyncRunState run, int size, int startPage) {
        int page = startPage;
        while (true) {
            DspaceHalPage<DspaceCommunityRecord> batch = dspaceClient.fetchCommunities(page, size);
            for (DspaceCommunityRecord community : batch.elements) {
                persistCommunity(run, community, null);
                syncSubcommunities(run, community, size, new HashSet<>());
            }
            dspaceRepository.saveCheckpoint(STAGE_COMMUNITIES, page, size, null);
            persistProgress(run);
            if (page + 1 >= Math.max(1, batch.totalPages) || batch.elements.isEmpty()) {
                break;
            }
            page++;
        }
    }

    private void syncSubcommunities(SyncRunState run, DspaceCommunityRecord parent, int size, Set<String> visited) {
        if (parent == null || parent.uuid == null || visited == null || !visited.add(parent.uuid)) {
            return;
        }
        int page = 0;
        while (true) {
            DspaceHalPage<DspaceCommunityRecord> batch = dspaceClient.fetchSubcommunities(parent.uuid, page, size);
            for (DspaceCommunityRecord child : batch.elements) {
                child.parentUuid = parent.uuid;
                persistCommunity(run, child, parent.uuid);
                syncSubcommunities(run, child, size, visited);
            }
            if (page + 1 >= Math.max(1, batch.totalPages) || batch.elements.isEmpty()) {
                break;
            }
            page++;
        }
    }

    private void persistCommunity(SyncRunState run, DspaceCommunityRecord community, String parentUuid) {
        try {
            if (community.parentUuid == null) {
                community.parentUuid = parentUuid;
            }
            dspaceRepository.upsertCommunity(community, run.syncId);
        } catch (Exception ex) {
            jobSupport.record(run, ENTITY, STAGE_COMMUNITIES, null, null, community.uuid, ex);
        }
    }

    private void syncCollections(SyncRunState run, int size, int startPage) {
        int page = startPage;
        while (true) {
            DspaceHalPage<DspaceCollectionRecord> batch = dspaceClient.fetchCollections(page, size);
            for (DspaceCollectionRecord collection : batch.elements) {
                try {
                    if (collection.parentCommunityUuid == null && collection.parentCommunityHref != null) {
                        try {
                            DspaceCommunityRecord parent = dspaceClient.fetchParentCommunity(collection.uuid);
                            collection.parentCommunityUuid = parent == null ? null : parent.uuid;
                        } catch (Exception ignored) {
                            // Collection is still persisted; community path stays empty until parent exists.
                        }
                    }
                    dspaceRepository.upsertCollection(collection, run.syncId);
                } catch (Exception ex) {
                    jobSupport.record(run, ENTITY, STAGE_COLLECTIONS, null, null, collection.uuid, ex);
                }
            }
            dspaceRepository.saveCheckpoint(STAGE_COLLECTIONS, page, size, null);
            persistProgress(run);
            if (page + 1 >= Math.max(1, batch.totalPages) || batch.elements.isEmpty()) {
                break;
            }
            page++;
        }
    }

    private DiscoverStats syncDiscover(SyncRunState run, int size, int startPage) {
        DiscoverStats stats = new DiscoverStats();
        OffsetDateTime watermark = run.isIncremental() ? dspaceRepository.maxLastModified() : null;
        int page = startPage;
        Long executionId = parseExecution(run.syncId);
        boolean complete = false;
        while (true) {
            DspaceHalPage<DspaceItemRecord> batch = dspaceClient.searchItems("*", "item", page, size);
            stats.totalElements = batch.totalElements;
            if (page == startPage) {
                run.totalExpected = toExpected(batch.totalElements);
                persistProgress(run);
            }
            for (DspaceItemRecord hit : batch.elements) {
                try {
                    processItem(run, hit, watermark, executionId);
                } catch (Exception ex) {
                    jobSupport.record(run, ENTITY, STAGE_DISCOVER, null, null, hit.uuid, ex);
                    run.processed.incrementAndGet();
                }
            }
            dspaceRepository.saveCheckpoint(STAGE_DISCOVER, page, size, null);
            persistProgress(run);
            boolean last = page + 1 >= Math.max(1, batch.totalPages) || batch.elements.isEmpty();
            if (last) {
                complete = batch.totalPages == 0
                        || page + 1 >= batch.totalPages
                        || (batch.nextHref == null && batch.elements.size() < size);
                break;
            }
            page++;
        }
        stats.complete = complete && run.errors.get() == 0;
        return stats;
    }

    private void processItem(SyncRunState run, DspaceItemRecord hit, OffsetDateTime watermark, Long executionId) {
        boolean skipEnrich = skipEnrich(run.isIncremental(), watermark, hit.lastModified);
        if (skipEnrich && dspaceRepository.markItemSeen(hit.uuid, run.syncId)) {
            run.unchanged.incrementAndGet();
            run.processed.incrementAndGet();
            return;
        }
        DspaceItemRecord item = enrich(hit);
        if (item.bitstreamInventory == DspaceInventoryState.FETCH_FAILED) {
            jobSupport.record(
                    run,
                    ENTITY,
                    STAGE_BITSTREAMS,
                    null,
                    null,
                    item.uuid,
                    new IllegalStateException("ORIGINAL bitstream inventory could not be confirmed")
            );
        }
        DspaceRepository.ItemUpsert result = dspaceRepository.upsertItem(item, run.syncId, executionId);
        if ("CREATED".equals(result.kind)) {
            run.created.incrementAndGet();
        } else if ("UPDATED".equals(result.kind)) {
            run.updated.incrementAndGet();
        } else {
            run.unchanged.incrementAndGet();
        }
        if (DspaceAccessClassifier.OPEN_ACCESS.equals(result.accessStatus)) {
            run.oa.incrementAndGet();
        }
        syncChangeRepository.save(run.syncId, result.changes);
        run.processed.incrementAndGet();
    }

    private DspaceItemRecord enrich(DspaceItemRecord hit) {
        DspaceItemRecord item = hit;
        try {
            DspaceItemRecord detailed = dspaceClient.fetchItem(hit.uuid);
            if (detailed.uuid != null) {
                item = detailed;
            }
        } catch (DspaceHttpException ex) {
            if (ex.httpStatus != 404) {
                throw ex;
            }
        }
        if (item.access == null) {
            try {
                item.access = dspaceClient.fetchAccessStatus(item.uuid);
            } catch (DspaceHttpException ex) {
                if (ex.httpStatus != 404) {
                    throw ex;
                }
            }
        }
        if (item.owningCollectionUuid == null) {
            try {
                DspaceCollectionRecord owning = dspaceClient.fetchOwningCollection(item.uuid);
                item.owningCollectionUuid = owning.uuid;
            } catch (DspaceHttpException ex) {
                if (ex.httpStatus != 404) {
                    throw ex;
                }
            }
        }
        applyOriginalInventory(item);
        return item;
    }

    private void applyOriginalInventory(DspaceItemRecord item) {
        DspaceBitstreamInventory.Result inventory = bitstreamInventory.load(item);
        item.bitstreamInventory = inventory.state;
        if (inventory.state == DspaceInventoryState.CONFIRMED) {
            item.bundles.clear();
            item.bundles.addAll(inventory.bundles);
            item.bundleListingComplete = true;
        }
        applyBitstreamAccess(item);
    }

    public void runBackfill(SyncRunState run) {
        dspaceService.assertTables();
        run.phase = STAGE_BITSTREAMS;
        run.message = STAGE_BITSTREAM_BACKFILL;
        persistProgress(run);
        List<DspaceRepository.FoundItem> items = dspaceRepository.listFoundItems();
        run.totalExpected = items.size();
        persistProgress(run);
        LOG.info("Starting DSPACE bitstream backfill id=" + run.syncId + " items=" + items.size());
        int index = 0;
        for (DspaceRepository.FoundItem found : items) {
            persistProgress(run);
            index++;
            try {
                backfillOne(run, found);
            } catch (cl.uss.ca.sync.SyncOwnershipLostException ex) {
                throw ex;
            } catch (Exception ex) {
                jobSupport.record(run, ENTITY, STAGE_BITSTREAM_BACKFILL, null, null, found.uuid, ex);
            }
            if (index % 25 == 0) {
                persistProgress(run);
            }
        }
        DspaceStatsDto snapshot = dspaceRepository.counts();
        applyBackfillValidation(run, snapshot, items.size());
        dspaceRepository.saveRunStats(run, snapshot, snapshot.dspaceVersion, null);
        persistProgress(run);
        LOG.info("syncId=" + run.syncId + " DSpace bitstream backfill processed=" + run.processed.get()
                + " updated=" + run.updated.get()
                + " unchanged=" + run.unchanged.get()
                + " errors=" + run.errors.get()
                + " withFile=" + snapshot.withFile
                + " bitstreamCount=" + snapshot.bitstreamCount);
    }

    private void backfillOne(SyncRunState run, DspaceRepository.FoundItem found) {
        DspaceItemRecord item = new DspaceItemRecord();
        item.uuid = found.uuid;
        if (found.accessStatus != null) {
            item.access = new DspaceAccessStatus();
            item.access.status = found.accessStatus;
        }
        DspaceBitstreamInventory.Result inventory = bitstreamInventory.load(item);
        if (inventory.state != DspaceInventoryState.CONFIRMED) {
            jobSupport.record(
                    run,
                    ENTITY,
                    STAGE_BITSTREAM_BACKFILL,
                    null,
                    null,
                    found.uuid,
                    inventory.error != null
                            ? inventory.error
                            : new IllegalStateException("ORIGINAL bitstream inventory could not be confirmed")
            );
            run.processed.incrementAndGet();
            return;
        }
        item.bitstreamInventory = DspaceInventoryState.CONFIRMED;
        item.bundles.clear();
        item.bundles.addAll(inventory.bundles);
        applyBitstreamAccess(item);
        boolean changed = dspaceRepository.replaceConfirmedBitstreams(item, found.accessStatus);
        if (changed) {
            run.updated.incrementAndGet();
        } else {
            run.unchanged.incrementAndGet();
        }
        run.processed.incrementAndGet();
    }

    private void applyBackfillValidation(SyncRunState run, DspaceStatsDto snapshot, int foundCount) {
        String summary = inventorySummary(snapshot);
        if (withFileWithoutFileMismatch(snapshot)) {
            run.fatal = false;
            run.errorCode = SUSPECT_INVENTORY;
            run.message = summary + ". withFile+withoutFile does not equal FOUND items.";
            jobSupport.record(
                    run,
                    ENTITY,
                    STAGE_BITSTREAM_BACKFILL,
                    null,
                    null,
                    null,
                    new IllegalStateException(run.message)
            );
            return;
        }
        if (isSuspectEmptyInventory(snapshot)) {
            run.errorCode = SUSPECT_INVENTORY;
            run.message = summary
                    + ". open.access/restricted/embargo="
                    + (snapshot.openAccess + snapshot.restricted + snapshot.embargo)
                    + " but bitstream_count=0. Inventory is still empty.";
            jobSupport.record(
                    run,
                    ENTITY,
                    STAGE_BITSTREAM_BACKFILL,
                    null,
                    null,
                    null,
                    new IllegalStateException(run.message)
            );
            return;
        }
        run.message = summary + " foundRequested=" + foundCount;
    }

    static boolean withFileWithoutFileMismatch(DspaceStatsDto snapshot) {
        return snapshot == null || snapshot.withFile + snapshot.withoutFile != snapshot.items;
    }

    static boolean isSuspectEmptyInventory(DspaceStatsDto snapshot) {
        if (snapshot == null) {
            return false;
        }
        long accessWithPossibleFile = snapshot.openAccess + snapshot.restricted + snapshot.embargo;
        return snapshot.bitstreamCount == 0L && accessWithPossibleFile > 0L;
    }

    static String inventorySummary(DspaceStatsDto snapshot) {
        if (snapshot == null) {
            return "BITSTREAM_BACKFILL items=0 withFile=0 withoutFile=0 bitstreamCount=0";
        }
        return "BITSTREAM_BACKFILL items=" + snapshot.items
                + " withFile=" + snapshot.withFile
                + " withoutFile=" + snapshot.withoutFile
                + " bitstreamCount=" + snapshot.bitstreamCount;
    }

    private static void applyBitstreamAccess(DspaceItemRecord item) {
        for (DspaceBundleRecord bundle : item.bundles) {
            applyBundleAccess(item, bundle);
        }
    }

    private static void applyBundleAccess(DspaceItemRecord item, DspaceBundleRecord bundle) {
        if (bundle == null) {
            return;
        }
        String itemAccess = item.access == null ? null : DspaceAccessClassifier.normalizeStatus(item.access.status);
        for (DspaceBitstreamRecord bitstream : bundle.bitstreams) {
            if (bitstream.bundleUuid == null) {
                bitstream.bundleUuid = bundle.uuid;
            }
            if (bitstream.bundleName == null) {
                bitstream.bundleName = bundle.name;
            }
            if (bitstream.accessStatus == null && item.access != null) {
                bitstream.accessStatus = item.access.status;
            }
            String fileAccess = DspaceAccessClassifier.normalizeStatus(bitstream.accessStatus);
            bitstream.publicDownload = DspaceAccessClassifier.OPEN_ACCESS.equals(fileAccess)
                    || (fileAccess == null && DspaceAccessClassifier.OPEN_ACCESS.equals(itemAccess));
        }
    }

    private void match(SyncRunState run) {
        try {
            List<DspaceMatchDecision> decisions = matcher.match(
                    dspaceRepository.listItemsForMatch(),
                    dspaceRepository.listPureCandidates()
            );
            int matched = 0;
            int candidates = 0;
            for (DspaceMatchDecision decision : decisions) {
                dspaceRepository.saveMatch(decision);
                if (DspaceMatcher.CANDIDATE.equals(decision.status)) {
                    candidates++;
                } else {
                    matched++;
                }
            }
            LOG.info("syncId=" + run.syncId + " DSpace matches confirmed/secondary=" + matched
                    + " candidates=" + candidates);
        } catch (Exception ex) {
            jobSupport.record(run, ENTITY, STAGE_MATCH, null, null, null, ex);
        }
    }

    private void persistProgress(SyncRunState run) {
        syncRunRepository.requireOwnership(run);
    }

    static Integer toExpected(long value) {
        if (value <= 0L) {
            return 0;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    public static boolean skipEnrich(boolean incremental, OffsetDateTime watermark, OffsetDateTime lastModified) {
        return incremental && watermark != null && lastModified != null && !lastModified.isAfter(watermark);
    }

    public static int startPage(DspaceRepository.Checkpoint checkpoint, String stage) {
        if (checkpoint == null || checkpoint.stage == null) {
            return 0;
        }
        if (!stage.equals(checkpoint.stage)) {
            return 0;
        }
        return Math.max(0, checkpoint.page);
    }

    private static Long parseExecution(String syncId) {
        if (syncId == null || !syncId.matches("\\d+")) {
            return null;
        }
        return Long.parseLong(syncId);
    }

    private static final class DiscoverStats {
        long totalElements;
        boolean complete;
    }
}
