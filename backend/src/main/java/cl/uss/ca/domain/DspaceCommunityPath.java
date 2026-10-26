package cl.uss.ca.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Community ancestry for DSpace item location. Not access status.
 */
public final class DspaceCommunityPath {
    public static final String OWNING = "OWNING";
    public static final String MAPPED = "MAPPED";

    private DspaceCommunityPath() {
    }

    public static final class Node {
        public final String uuid;
        public final String parentUuid;
        public final String name;

        public Node(String uuid, String parentUuid, String name) {
            this.uuid = uuid;
            this.parentUuid = parentUuid;
            this.name = name;
        }
    }

    public static Set<String> selfAndDescendants(String rootUuid, List<Node> nodes) {
        Set<String> out = new LinkedHashSet<>();
        if (rootUuid == null || rootUuid.isBlank()) {
            return out;
        }
        out.add(rootUuid.trim());
        if (nodes == null || nodes.isEmpty()) {
            return out;
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Node node : nodes) {
                if (node == null || node.uuid == null || node.parentUuid == null) {
                    continue;
                }
                if (out.contains(node.parentUuid) && out.add(node.uuid)) {
                    grew = true;
                }
            }
        }
        return out;
    }

    public static String format(String communityUuid, String collectionName, Map<String, Node> byUuid) {
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String cursor = communityUuid;
        while (cursor != null && !cursor.isBlank() && seen.add(cursor) && byUuid != null) {
            Node node = byUuid.get(cursor);
            if (node == null) {
                break;
            }
            if (node.name != null && !node.name.isBlank()) {
                names.add(node.name.trim());
            }
            cursor = node.parentUuid;
        }
        Collections.reverse(names);
        if (collectionName != null && !collectionName.isBlank()) {
            String collection = collectionName.trim();
            if (names.isEmpty() || !collection.equalsIgnoreCase(names.get(names.size() - 1))) {
                names.add(collection);
            }
        }
        return names.isEmpty() ? null : String.join(" > ", names);
    }
}
