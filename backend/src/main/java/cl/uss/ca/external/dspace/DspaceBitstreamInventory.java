package cl.uss.ca.external.dspace;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Loads ORIGINAL bitstream inventory without treating embed presence as completeness.
 */
@ApplicationScoped
public class DspaceBitstreamInventory {
    static final String ORIGINAL = "ORIGINAL";
    static final int MAX_PAGES = 5_000;

    @Inject
    DspaceClient dspaceClient;

    @Inject
    @ConfigProperty(name = "dspace.pageSize", defaultValue = "50")
    int pageSize;

    public Result load(DspaceItemRecord seed) {
        Result result = new Result();
        if (seed == null || seed.uuid == null || seed.uuid.isBlank()) {
            result.state = DspaceInventoryState.FETCH_FAILED;
            result.errorCode = "DSPACE_ITEM_UUID_REQUIRED";
            return result;
        }
        try {
            List<DspaceBundleRecord> bundles = listBundles(seed);
            List<DspaceBundleRecord> originals = new ArrayList<>();
            for (DspaceBundleRecord bundle : bundles) {
                if (bundle == null || !isOriginal(bundle.name)) {
                    continue;
                }
                if (!fillOriginalBitstreams(bundle)) {
                    result.state = DspaceInventoryState.FETCH_FAILED;
                    result.errorCode = "DSPACE_BITSTREAM_FETCH_FAILED";
                    result.bundles.addAll(originals);
                    return result;
                }
                originals.add(bundle);
            }
            result.bundles.addAll(originals);
            result.state = DspaceInventoryState.CONFIRMED;
            return result;
        } catch (RuntimeException ex) {
            result.state = DspaceInventoryState.FETCH_FAILED;
            result.errorCode = "DSPACE_BITSTREAM_FETCH_FAILED";
            result.error = ex;
            return result;
        }
    }

    private List<DspaceBundleRecord> listBundles(DspaceItemRecord seed) {
        if (seed.bundleListingComplete && !seed.bundles.isEmpty()) {
            return seed.bundles;
        }
        if (seed.bundleListingComplete && seed.bundles.isEmpty()) {
            return List.of();
        }
        return fetchAllBundles(seed.uuid);
    }

    private boolean fillOriginalBitstreams(DspaceBundleRecord bundle) {
        if (bundle.bitstreamInventoryComplete) {
            stamp(bundle);
            return true;
        }
        if (bundle.uuid == null || bundle.uuid.isBlank()) {
            return false;
        }
        try {
            DspaceHalPage<DspaceBitstreamRecord> page = fetchAllBitstreams(bundle.uuid);
            bundle.bitstreams.clear();
            bundle.bitstreams.addAll(page.elements);
            stamp(bundle);
            bundle.bitstreamInventoryComplete = true;
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    List<DspaceBundleRecord> fetchAllBundles(String itemUuid) {
        DspaceHalPage<DspaceBundleRecord> all = fetchAllPages(
                (page, size) -> dspaceClient.fetchBundles(itemUuid, page, size),
                bundle -> bundle == null ? null : bundle.uuid
        );
        if (!all.pagePresent && all.elements.isEmpty()) {
            throw new DspaceHttpException("DSPACE_PARSE_ERROR", "DSpace bundle listing had no page metadata", 0, itemUuid);
        }
        if (all.pagePresent && all.totalElements != all.elements.size()) {
            throw new DspaceHttpException(
                    "DSPACE_INCOMPLETE",
                    "DSpace bundle listing was incomplete: collected=" + all.elements.size()
                            + " totalElements=" + all.totalElements
                            + " totalPages=" + all.totalPages,
                    0,
                    itemUuid
            );
        }
        return all.elements;
    }

    DspaceHalPage<DspaceBitstreamRecord> fetchAllBitstreams(String bundleUuid) {
        DspaceHalPage<DspaceBitstreamRecord> all = fetchAllPages(
                (page, size) -> dspaceClient.fetchBitstreams(bundleUuid, page, size),
                bitstream -> bitstream == null ? null : bitstream.uuid
        );
        if (!all.pagePresent) {
            throw new DspaceHttpException("DSPACE_PARSE_ERROR", "DSpace bitstream listing had no page metadata", 0, bundleUuid);
        }
        if (all.totalElements != all.elements.size()) {
            throw new DspaceHttpException(
                    "DSPACE_INCOMPLETE",
                    "DSpace bitstream listing was incomplete: collected=" + all.elements.size()
                            + " totalElements=" + all.totalElements
                            + " totalPages=" + all.totalPages,
                    0,
                    bundleUuid
            );
        }
        return all;
    }

    private <T> DspaceHalPage<T> fetchAllPages(
            BiFunction<Integer, Integer, DspaceHalPage<T>> fetcher,
            java.util.function.Function<T, String> key
    ) {
        int size = pageSize < 1 ? 20 : Math.max(1, pageSize);
        DspaceHalPage<T> aggregate = new DspaceHalPage<>();
        Map<String, T> unique = new LinkedHashMap<>();
        int unnamed = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            DspaceHalPage<T> batch = fetcher.apply(page, size);
            if (batch == null) {
                throw new DspaceHttpException("DSPACE_EMPTY_BODY", "DSpace returned an empty listing page", 0, null);
            }
            if (page == 0) {
                aggregate.pagePresent = batch.pagePresent;
                aggregate.size = batch.size;
                aggregate.totalPages = batch.totalPages;
                aggregate.totalElements = batch.totalElements;
            }
            for (T element : batch.elements) {
                String id = key.apply(element);
                if (id == null || id.isBlank()) {
                    unique.put("_anon_" + unnamed++, element);
                } else {
                    unique.putIfAbsent(id, element);
                }
            }
            if (aggregate.pagePresent && aggregate.totalElements > 0 && unique.size() >= aggregate.totalElements) {
                break;
            }
            if (batch.elements.isEmpty()) {
                break;
            }
            boolean claimedLast = batch.lastPage(size);
            boolean shortOfTotal = aggregate.pagePresent
                    && aggregate.totalElements > unique.size();
            if (claimedLast && shortOfTotal && batch.elements.size() >= size) {
                continue;
            }
            if (claimedLast) {
                break;
            }
            if (page + 1 >= MAX_PAGES) {
                throw new DspaceHttpException("DSPACE_INCOMPLETE", "DSpace listing exceeded max pages", 0, null);
            }
        }
        aggregate.elements.addAll(unique.values());
        if (aggregate.pagePresent && aggregate.totalElements == 0L && aggregate.elements.isEmpty()) {
            aggregate.number = 0;
        }
        return aggregate;
    }

    static boolean isOriginal(String name) {
        return name != null && ORIGINAL.equalsIgnoreCase(name.trim());
    }

    private static void stamp(DspaceBundleRecord bundle) {
        for (DspaceBitstreamRecord bitstream : bundle.bitstreams) {
            if (bitstream.bundleUuid == null) {
                bitstream.bundleUuid = bundle.uuid;
            }
            if (bitstream.bundleName == null) {
                bitstream.bundleName = bundle.name;
            }
        }
    }

    public static final class Result {
        public DspaceInventoryState state = DspaceInventoryState.NOT_LOADED;
        public String errorCode;
        public RuntimeException error;
        public final List<DspaceBundleRecord> bundles = new ArrayList<>();

        public boolean confirmedEmpty() {
            return state == DspaceInventoryState.CONFIRMED && originalBitstreams().isEmpty();
        }

        public List<DspaceBitstreamRecord> originalBitstreams() {
            List<DspaceBitstreamRecord> items = new ArrayList<>();
            for (DspaceBundleRecord bundle : bundles) {
                if (bundle == null) {
                    continue;
                }
                for (DspaceBitstreamRecord bitstream : bundle.bitstreams) {
                    if (bitstream != null) {
                        items.add(bitstream);
                    }
                }
            }
            return items;
        }
    }
}
