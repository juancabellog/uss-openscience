package cl.uss.ca.external.dspace;

import cl.uss.ca.domain.DoiNormalizer;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class DspaceHalParser {
    private DspaceHalParser() {
    }

    public static DspaceRoot root(JsonNode node) {
        DspaceRoot root = new DspaceRoot();
        if (node == null) {
            return root;
        }
        root.dspaceVersion = text(node, "dspaceVersion");
        root.dspaceName = text(node, "dspaceName");
        root.dspaceUi = text(node, "dspaceUI");
        root.dspaceServer = text(node, "dspaceServer");
        return root;
    }

    public static DspaceHalPage<DspaceCommunityRecord> communities(JsonNode node) {
        DspaceHalPage<DspaceCommunityRecord> page = pageShell(node);
        JsonNode list = embeddedList(node, "communities");
        if (list != null && list.isArray()) {
            for (JsonNode item : list) {
                page.elements.add(community(item));
            }
        }
        return page;
    }

    public static DspaceHalPage<DspaceCollectionRecord> collections(JsonNode node) {
        DspaceHalPage<DspaceCollectionRecord> page = pageShell(node);
        JsonNode list = embeddedList(node, "collections");
        if (list != null && list.isArray()) {
            for (JsonNode item : list) {
                page.elements.add(collection(item));
            }
        }
        return page;
    }

    public static DspaceHalPage<DspaceItemRecord> discoverItems(JsonNode node) {
        DspaceHalPage<DspaceItemRecord> page = new DspaceHalPage<>();
        JsonNode searchResult = node == null ? null : node.path("_embedded").path("searchResult");
        JsonNode pageNode = searchResult != null ? searchResult.path("page") : missing();
        fillPage(page, pageNode, searchResult);
        JsonNode objects = searchResult == null ? missing() : searchResult.path("_embedded").path("objects");
        if (objects.isArray()) {
            for (JsonNode object : objects) {
                JsonNode item = object.path("_embedded").path("indexableObject");
                if (!item.isMissingNode() && !item.isNull()) {
                    page.elements.add(item(item));
                }
            }
        }
        return page;
    }

    public static DspaceCommunityRecord community(JsonNode node) {
        DspaceCommunityRecord record = new DspaceCommunityRecord();
        if (node == null) {
            return record;
        }
        record.uuid = firstNonBlank(text(node, "uuid"), text(node, "id"));
        record.handle = text(node, "handle");
        record.name = text(node, "name");
        record.archivedItemsCount = integer(node, "archivedItemsCount");
        record.subcommunitiesHref = href(node, "subcommunities");
        record.collectionsHref = href(node, "collections");
        JsonNode parent = node.path("_embedded").path("parentCommunity");
        if (!parent.isMissingNode() && !parent.isNull()) {
            record.parentUuid = firstNonBlank(text(parent, "uuid"), text(parent, "id"));
        }
        return record;
    }

    public static DspaceCollectionRecord collection(JsonNode node) {
        DspaceCollectionRecord record = new DspaceCollectionRecord();
        if (node == null) {
            return record;
        }
        record.uuid = firstNonBlank(text(node, "uuid"), text(node, "id"));
        record.handle = text(node, "handle");
        record.name = text(node, "name");
        record.archivedItemsCount = integer(node, "archivedItemsCount");
        record.parentCommunityHref = href(node, "parentCommunity");
        JsonNode parent = node.path("_embedded").path("parentCommunity");
        if (!parent.isMissingNode() && !parent.isNull()) {
            record.parentCommunityUuid = firstNonBlank(text(parent, "uuid"), text(parent, "id"));
        }
        return record;
    }

    public static DspaceItemRecord item(JsonNode node) {
        DspaceItemRecord record = new DspaceItemRecord();
        if (node == null) {
            return record;
        }
        record.uuid = firstNonBlank(text(node, "uuid"), text(node, "id"));
        record.handle = text(node, "handle");
        record.name = text(node, "name");
        record.inArchive = bool(node, "inArchive");
        record.discoverable = bool(node, "discoverable");
        record.withdrawn = bool(node, "withdrawn");
        record.lastModified = offset(text(node, "lastModified"));
        record.owningCollectionHref = href(node, "owningCollection");
        record.bundlesHref = href(node, "bundles");
        record.accessStatusHref = href(node, "accessStatus");
        record.metadata.addAll(metadata(node.path("metadata")));
        record.doiRaw = firstMetadata(record.metadata, "dc.identifier.doi");
        record.normalizedDoi = DoiNormalizer.normalize(record.doiRaw);
        record.dcType = firstMetadata(record.metadata, "dc.type");
        record.handleUrl = firstMetadata(record.metadata, "dc.identifier.uri");
        if (record.handleUrl == null && record.handle != null) {
            record.handleUrl = "https://repositorio.uss.cl/handle/" + record.handle;
        }
        applyItemEmbeds(record, node.path("_embedded"));
        return record;
    }

    static void applyItemEmbeds(DspaceItemRecord record, JsonNode embedded) {
        if (record == null || embedded == null || embedded.isMissingNode() || embedded.isNull()) {
            return;
        }
        JsonNode owning = embedded.path("owningCollection");
        if (!owning.isMissingNode() && !owning.isNull()) {
            DspaceCollectionRecord collection = collection(owning);
            if (collection.uuid != null) {
                record.owningCollectionUuid = collection.uuid;
            }
        }
        JsonNode access = embedded.path("accessStatus");
        if (!access.isMissingNode() && !access.isNull()) {
            record.access = accessStatus(access);
        }
        JsonNode mapped = embedded.path("mappedCollections");
        if (!mapped.isMissingNode() && !mapped.isNull()) {
            JsonNode list = mapped.isArray() ? mapped : embeddedList(mapped, "mappedCollections");
            if (list != null && list.isArray()) {
                for (JsonNode entry : list) {
                    DspaceCollectionRecord collection = collection(entry);
                    if (collection.uuid != null && !record.mappedCollectionUuids.contains(collection.uuid)) {
                        record.mappedCollectionUuids.add(collection.uuid);
                    }
                }
            }
        }
        JsonNode bundlesNode = embedded.path("bundles");
        if (!bundlesNode.isMissingNode() && !bundlesNode.isNull()) {
            DspaceHalPage<DspaceBundleRecord> page = bundlesFromEmbedded(bundlesNode);
            record.bundles.clear();
            record.bundles.addAll(page.elements);
            record.bundleListingComplete = page.inventoryComplete();
            record.bitstreamInventory = DspaceInventoryState.NOT_LOADED;
        }
    }

    static DspaceHalPage<DspaceBundleRecord> bundlesFromEmbedded(JsonNode node) {
        if (node != null && node.isArray()) {
            DspaceHalPage<DspaceBundleRecord> page = new DspaceHalPage<>();
            page.pagePresent = false;
            for (JsonNode item : node) {
                page.elements.add(bundle(item));
            }
            return page;
        }
        return bundles(node);
    }

    public static List<DspaceMetadataValue> metadata(JsonNode node) {
        List<DspaceMetadataValue> values = new ArrayList<>();
        if (node == null || !node.isObject()) {
            return values;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode array = field.getValue();
            if (array == null || !array.isArray()) {
                continue;
            }
            for (JsonNode entry : array) {
                DspaceMetadataValue value = new DspaceMetadataValue();
                value.field = field.getKey();
                value.value = text(entry, "value");
                value.language = text(entry, "language");
                value.authority = text(entry, "authority");
                value.confidence = integer(entry, "confidence");
                Integer place = integer(entry, "place");
                value.place = place == null ? 0 : place;
                if (value.value != null) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    public static DspaceAccessStatus accessStatus(JsonNode node) {
        DspaceAccessStatus status = new DspaceAccessStatus();
        if (node == null) {
            return status;
        }
        status.status = text(node, "status");
        status.embargoDate = text(node, "embargoDate");
        return status;
    }

    public static DspaceHalPage<DspaceBundleRecord> bundles(JsonNode node) {
        JsonNode nestedPage = nestedCollectionPage(node, "bundles");
        if (nestedPage != null) {
            return bundles(nestedPage);
        }
        DspaceHalPage<DspaceBundleRecord> page = pageShell(node);
        JsonNode list = collectionArray(node, "bundles");
        if (list != null && list.isArray()) {
            for (JsonNode item : list) {
                page.elements.add(bundle(item));
            }
        }
        return page;
    }

    public static DspaceBundleRecord bundle(JsonNode node) {
        DspaceBundleRecord record = new DspaceBundleRecord();
        if (node == null) {
            return record;
        }
        record.uuid = firstNonBlank(text(node, "uuid"), text(node, "id"));
        record.name = text(node, "name");
        record.bitstreamsHref = href(node, "bitstreams");
        DspaceHalPage<DspaceBitstreamRecord> page = embeddedBitstreams(node);
        for (DspaceBitstreamRecord bitstream : page.elements) {
            bitstream.bundleUuid = record.uuid;
            if (bitstream.bundleName == null) {
                bitstream.bundleName = record.name;
            }
            record.bitstreams.add(bitstream);
        }
        record.bitstreamInventoryComplete = page.inventoryComplete();
        return record;
    }

    /**
     * CASE A: {@code _embedded.bitstreams} is a JSON array (no page metadata → incomplete).
     * CASE B: {@code _embedded.bitstreams} is a HAL page with nested {@code _embedded.bitstreams[]} + {@code page}.
     */
    public static DspaceHalPage<DspaceBitstreamRecord> embeddedBitstreams(JsonNode bundleNode) {
        if (bundleNode == null || bundleNode.isMissingNode() || bundleNode.isNull()) {
            return new DspaceHalPage<>();
        }
        JsonNode embedded = bundleNode.path("_embedded").path("bitstreams");
        if (embedded.isMissingNode() || embedded.isNull()) {
            return new DspaceHalPage<>();
        }
        if (embedded.isArray()) {
            DspaceHalPage<DspaceBitstreamRecord> page = new DspaceHalPage<>();
            page.pagePresent = false;
            for (JsonNode item : embedded) {
                page.elements.add(bitstream(item));
            }
            return page;
        }
        if (embedded.isObject()) {
            return bitstreams(embedded);
        }
        DspaceHalPage<DspaceBitstreamRecord> malformed = new DspaceHalPage<>();
        malformed.pagePresent = false;
        return malformed;
    }

    public static DspaceHalPage<DspaceBitstreamRecord> bitstreams(JsonNode node) {
        JsonNode nestedPage = nestedCollectionPage(node, "bitstreams");
        if (nestedPage != null) {
            return bitstreams(nestedPage);
        }
        DspaceHalPage<DspaceBitstreamRecord> page = pageShell(node);
        JsonNode list = collectionArray(node, "bitstreams");
        if (list != null && list.isArray()) {
            for (JsonNode item : list) {
                page.elements.add(bitstream(item));
            }
        }
        return page;
    }

    public static DspaceBitstreamRecord bitstream(JsonNode node) {
        DspaceBitstreamRecord record = new DspaceBitstreamRecord();
        if (node == null) {
            return record;
        }
        record.uuid = firstNonBlank(text(node, "uuid"), text(node, "id"));
        record.name = text(node, "name");
        record.bundleName = text(node, "bundleName");
        record.sizeBytes = longValue(node, "sizeBytes");
        record.sequenceId = integer(node, "sequenceId");
        record.contentUrl = href(node, "content");
        JsonNode checksum = node.path("checkSum");
        record.checksumMd5 = text(checksum, "value");
        record.mimeType = firstNonBlank(
                text(node, "mimeType"),
                text(node, "mimetype"),
                text(node.path("format"), "mimetype"),
                text(node.path("_embedded").path("format"), "mimetype")
        );
        JsonNode access = node.path("_embedded").path("accessStatus");
        if (!access.isMissingNode() && !access.isNull()) {
            record.accessStatus = text(access, "status");
        }
        return record;
    }

    public static String springErrorMessage(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        return firstNonBlank(text(node, "message"), text(node, "error"));
    }

    public static int springStatus(JsonNode node, int fallback) {
        Integer status = integer(node, "status");
        return status == null ? fallback : status;
    }

    public static String firstMetadata(List<DspaceMetadataValue> metadata, String field) {
        if (metadata == null) {
            return null;
        }
        for (DspaceMetadataValue value : metadata) {
            if (value != null && field.equals(value.field) && value.value != null && !value.value.isBlank()) {
                return value.value.trim();
            }
        }
        return null;
    }

    public static List<String> allMetadata(List<DspaceMetadataValue> metadata, String field) {
        List<String> values = new ArrayList<>();
        if (metadata == null) {
            return values;
        }
        for (DspaceMetadataValue value : metadata) {
            if (value != null && field.equals(value.field) && value.value != null && !value.value.isBlank()) {
                values.add(value.value.trim());
            }
        }
        return values;
    }

    static <T> DspaceHalPage<T> pageShell(JsonNode node) {
        DspaceHalPage<T> page = new DspaceHalPage<>();
        fillPage(page, node == null ? missing() : node.path("page"), node);
        return page;
    }

    private static void fillPage(DspaceHalPage<?> page, JsonNode pageNode, JsonNode container) {
        boolean present = pageNode != null && !pageNode.isMissingNode() && !pageNode.isNull()
                && (pageNode.has("totalElements") || pageNode.has("totalPages"));
        page.pagePresent = present;
        Integer number = integer(pageNode, "number");
        Integer size = integer(pageNode, "size");
        Integer totalPages = integer(pageNode, "totalPages");
        Long totalElements = longValue(pageNode, "totalElements");
        page.number = number == null ? 0 : number;
        page.size = size == null ? 0 : size;
        page.totalPages = totalPages == null ? 0 : totalPages;
        page.totalElements = totalElements == null ? 0L : totalElements;
        page.nextHref = href(container, "next");
    }

    private static JsonNode embeddedList(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        JsonNode list = node.path("_embedded").path(name);
        return list.isMissingNode() ? null : list;
    }

    static JsonNode collectionArray(JsonNode node, String rel) {
        JsonNode embedded = embeddedList(node, rel);
        if (embedded != null && embedded.isArray()) {
            return embedded;
        }
        if (embedded != null && embedded.isObject()) {
            JsonNode nested = embedded.path("_embedded").path(rel);
            if (nested.isArray()) {
                return nested;
            }
        }
        return embedded;
    }

    static JsonNode nestedCollectionPage(JsonNode node, String rel) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode embedded = node.path("_embedded").path(rel);
        if (!embedded.isObject() || embedded.has("uuid") || embedded.has("id")) {
            return null;
        }
        if (embedded.has("page") || embedded.path("_embedded").has(rel)) {
            return embedded;
        }
        return null;
    }

    private static String href(JsonNode node, String rel) {
        if (node == null) {
            return null;
        }
        JsonNode link = node.path("_links").path(rel);
        if (link.isArray() && link.size() > 0) {
            return text(link.get(0), "href");
        }
        return text(link, "href");
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() || "null".equals(text) ? null : text.trim();
    }

    private static Integer integer(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isNumber() || value.isTextual()) {
            try {
                return value.asInt();
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private static Long longValue(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        try {
            return node.get(field).asLong();
        } catch (Exception ex) {
            return null;
        }
    }

    private static Boolean bool(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asBoolean();
    }

    private static OffsetDateTime offset(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static JsonNode missing() {
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }
}
