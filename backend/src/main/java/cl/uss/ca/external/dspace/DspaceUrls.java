package cl.uss.ca.external.dspace;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class DspaceUrls {
    private DspaceUrls() {
    }

    public static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }

    public static String root(String apiBase) {
        return requireBase(apiBase);
    }

    public static String communities(String apiBase, int page, int size) {
        return requireBase(apiBase) + "/core/communities?page=" + page + "&size=" + size;
    }

    public static String subcommunities(String apiBase, String uuid, int page, int size) {
        return requireBase(apiBase) + "/core/communities/" + uuid + "/subcommunities?page=" + page + "&size=" + size;
    }

    public static String collections(String apiBase, int page, int size) {
        return requireBase(apiBase) + "/core/collections?page=" + page + "&size=" + size
                + "&embed=parentCommunity";
    }

    public static String collectionParent(String apiBase, String uuid) {
        return requireBase(apiBase) + "/core/collections/" + uuid + "/parentCommunity";
    }

    public static String searchItems(String apiBase, String query, String dsoType, int page, int size) {
        String q = query == null || query.isBlank() ? "*" : query.trim();
        String type = dsoType == null || dsoType.isBlank() ? "item" : dsoType.trim();
        return requireBase(apiBase) + "/discover/search/objects?query=" + enc(q)
                + "&dsoType=" + enc(type)
                + "&page=" + page
                + "&size=" + size
                + "&embed=" + enc("indexableObject/owningCollection,indexableObject/accessStatus");
    }

    public static String item(String apiBase, String uuid) {
        return requireBase(apiBase) + "/core/items/" + uuid
                + "?embed=" + enc("owningCollection,accessStatus,bundles/bitstreams,mappedCollections");
    }

    public static String mappedCollections(String apiBase, String itemUuid, int page, int size) {
        return requireBase(apiBase) + "/core/items/" + itemUuid + "/mappedCollections?page=" + page + "&size=" + size;
    }

    public static String itemsList(String apiBase, int size) {
        return requireBase(apiBase) + "/core/items?size=" + size;
    }

    public static String accessStatus(String apiBase, String itemUuid) {
        return requireBase(apiBase) + "/core/items/" + itemUuid + "/accessStatus";
    }

    public static String owningCollection(String apiBase, String itemUuid) {
        return requireBase(apiBase) + "/core/items/" + itemUuid + "/owningCollection";
    }

    public static String bundles(String apiBase, String itemUuid) {
        return bundles(apiBase, itemUuid, 0, 20);
    }

    public static String bundles(String apiBase, String itemUuid, int page, int size) {
        return requireBase(apiBase) + "/core/items/" + itemUuid + "/bundles?page=" + page + "&size=" + size;
    }

    public static String bundleBitstreams(String apiBase, String bundleUuid) {
        return bundleBitstreams(apiBase, bundleUuid, 0, 20);
    }

    public static String bundleBitstreams(String apiBase, String bundleUuid, int page, int size) {
        return requireBase(apiBase) + "/core/bundles/" + bundleUuid + "/bitstreams?page=" + page + "&size=" + size;
    }

    public static String bitstreamAccess(String apiBase, String bitstreamUuid) {
        return requireBase(apiBase) + "/core/bitstreams/" + bitstreamUuid + "/accessStatus";
    }

    public static String bitstreamContent(String apiBase, String bitstreamUuid) {
        return requireBase(apiBase) + "/core/bitstreams/" + bitstreamUuid + "/content";
    }

    public static String pidFind(String apiBase, String id) {
        return requireBase(apiBase) + "/pid/find?id=" + enc(id);
    }

    public static int clampSize(int size, int max) {
        int ceiling = Math.max(1, max);
        if (size < 1) {
            return 1;
        }
        return Math.min(size, ceiling);
    }

    private static String requireBase(String apiBase) {
        String value = trimSlash(apiBase);
        if (value == null) {
            throw new IllegalArgumentException("DSpace API base URL is blank");
        }
        return value;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
