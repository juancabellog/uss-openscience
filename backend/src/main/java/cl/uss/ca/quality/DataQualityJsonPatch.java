package cl.uss.ca.quality;

import cl.uss.ca.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;

public final class DataQualityJsonPatch {
    private DataQualityJsonPatch() {
    }

    public static String readCurrent(JsonNode root, DataQualityPatch patch) {
        if (patch == null || root == null) {
            return null;
        }
        return switch (normalize(patch.field)) {
            case "TITLE" -> localizedMatching(root.get("title"), patch.expectedValue, patch.locale);
            case "ABSTRACT" -> localizedMatching(root.get("abstractText"), patch.expectedValue, patch.locale);
            case "FIRST_NAME" -> nestedName(root, "firstName");
            case "LAST_NAME" -> nestedName(root, "lastName");
            case "ORCID" -> text(root, "orcid");
            case "PAGES" -> text(root, "pages");
            case "NUMBER_OF_PAGES" -> root.hasNonNull("numberOfPages") ? root.get("numberOfPages").asText() : null;
            case "SUBMISSION_YEAR" -> root.hasNonNull("submissionYear") ? root.get("submissionYear").asText() : null;
            case "DOI" -> childText(root, "electronicVersions", patch.childPureId, "doi");
            case "CONTRIBUTOR_FIRST" -> contributorName(root, patch.childPureId, "firstName");
            case "CONTRIBUTOR_LAST" -> contributorName(root, patch.childPureId, "lastName");
            case "STATUS_YEAR" -> statusYear(root, patch.childPureId);
            default -> null;
        };
    }

    public static void apply(ObjectNode mutable, DataQualityPatch patch) {
        if (mutable == null || patch == null) {
            throw new ValidationException("QUALITY_PATCH_INVALID", "The quality patch is empty.");
        }
        String field = normalize(patch.field);
        switch (field) {
            case "TITLE" -> replaceLocalized(mutable, "title", patch);
            case "ABSTRACT" -> replaceLocalized(mutable, "abstractText", patch);
            case "FIRST_NAME" -> putName(mutable, "firstName", patch.newValue);
            case "LAST_NAME" -> putName(mutable, "lastName", patch.newValue);
            case "ORCID" -> mutable.put("orcid", patch.newValue);
            case "PAGES" -> mutable.put("pages", patch.newValue);
            case "NUMBER_OF_PAGES" -> mutable.put("numberOfPages", Integer.parseInt(patch.newValue.trim()));
            case "SUBMISSION_YEAR" -> mutable.put("submissionYear", Integer.parseInt(patch.newValue.trim()));
            case "DOI" -> putChildField(mutable, "electronicVersions", patch.childPureId, "doi", patch.newValue);
            case "CONTRIBUTOR_FIRST" -> putContributorName(mutable, patch.childPureId, "firstName", patch.newValue);
            case "CONTRIBUTOR_LAST" -> putContributorName(mutable, patch.childPureId, "lastName", patch.newValue);
            case "STATUS_YEAR" -> putStatusYear(mutable, patch.childPureId, Integer.parseInt(patch.newValue.trim()));
            case "REMOVE_CONTRIBUTOR" -> removeContributors(mutable, patch.removeChildPureIds);
            default -> throw new ValidationException("QUALITY_PATCH_UNSUPPORTED", "Unsupported quality field " + patch.field);
        }
    }

    private static void replaceLocalized(ObjectNode parent, String field, DataQualityPatch patch) {
        JsonNode node = parent.get(field);
        if (node != null && node.isTextual()) {
            parent.put(field, patch.newValue);
            return;
        }
        ObjectNode object = node instanceof ObjectNode objectNode ? objectNode : parent.putObject(field);
        String locale = patch.locale;
        if (locale == null || locale.isBlank()) {
            locale = matchingLocale(object, patch.expectedValue);
        }
        if (locale == null) {
            if (object.has("es_CL")) {
                locale = "es_CL";
            } else if (object.has("en_US")) {
                locale = "en_US";
            } else if (object.has("value")) {
                locale = "value";
            } else {
                locale = "es_CL";
            }
        }
        object.put(locale, patch.newValue);
    }

    private static String localizedMatching(JsonNode node, String expected, String locale) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (locale != null && node.hasNonNull(locale)) {
            return node.get(locale).asText();
        }
        if (expected != null) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (entry.getValue() != null && expected.equals(entry.getValue().asText())) {
                    return entry.getValue().asText();
                }
            }
        }
        if (node.hasNonNull("es_CL")) {
            return node.get("es_CL").asText();
        }
        if (node.hasNonNull("en_US")) {
            return node.get("en_US").asText();
        }
        if (node.hasNonNull("value")) {
            return node.get("value").asText();
        }
        return null;
    }

    private static String matchingLocale(ObjectNode object, String expected) {
        if (expected == null) {
            return null;
        }
        var fields = object.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (expected.equals(entry.getValue() == null ? null : entry.getValue().asText())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String nestedName(JsonNode root, String part) {
        JsonNode name = root.get("name");
        if (name != null && name.hasNonNull(part)) {
            return name.get(part).asText();
        }
        return text(root, part);
    }

    private static void putName(ObjectNode mutable, String part, String value) {
        JsonNode name = mutable.get("name");
        ObjectNode object = name instanceof ObjectNode objectNode ? objectNode : mutable.putObject("name");
        object.put(part, value);
    }

    private static String contributorName(JsonNode root, Long childPureId, String part) {
        JsonNode child = findChild(root.get("contributors"), childPureId);
        if (child == null) {
            return null;
        }
        JsonNode name = child.get("name");
        if (name != null && name.hasNonNull(part)) {
            return name.get(part).asText();
        }
        return text(child, part);
    }

    private static void putContributorName(ObjectNode mutable, Long childPureId, String part, String value) {
        JsonNode child = findChild(mutable.get("contributors"), childPureId);
        if (!(child instanceof ObjectNode object)) {
            throw new ValidationException("CHILD_MISMATCH", "The contributor was not found in PURE.");
        }
        JsonNode name = object.get("name");
        ObjectNode nameObject = name instanceof ObjectNode objectNode ? objectNode : object.putObject("name");
        nameObject.put(part, value);
    }

    private static String childText(JsonNode root, String collection, Long childPureId, String field) {
        JsonNode child = findChild(root.get(collection), childPureId);
        return text(child, field);
    }

    private static void putChildField(ObjectNode mutable, String collection, Long childPureId, String field, String value) {
        JsonNode child = findChild(mutable.get(collection), childPureId);
        if (!(child instanceof ObjectNode object)) {
            throw new ValidationException("CHILD_MISMATCH", "The PURE child was not found.");
        }
        object.put(field, value);
    }

    private static String statusYear(JsonNode root, Long childPureId) {
        JsonNode child = findChild(root.get("publicationStatuses"), childPureId);
        if (child == null || !child.hasNonNull("publicationYear")) {
            return null;
        }
        return child.get("publicationYear").asText();
    }

    private static void putStatusYear(ObjectNode mutable, Long childPureId, int year) {
        JsonNode child = findChild(mutable.get("publicationStatuses"), childPureId);
        if (!(child instanceof ObjectNode object)) {
            throw new ValidationException("CHILD_MISMATCH", "The publication status was not found in PURE.");
        }
        object.put("publicationYear", year);
    }

    private static void removeContributors(ObjectNode mutable, List<Long> removeIds) {
        if (removeIds == null || removeIds.isEmpty()) {
            throw new ValidationException("QUALITY_PATCH_INVALID", "No contributor ids to remove.");
        }
        JsonNode collection = mutable.get("contributors");
        if (!(collection instanceof ArrayNode array)) {
            throw new ValidationException("CHILD_MISMATCH", "The publication has no contributors array.");
        }
        for (int i = array.size() - 1; i >= 0; i--) {
            JsonNode item = array.get(i);
            long pureId = item == null ? Long.MIN_VALUE : item.path("pureId").asLong(Long.MIN_VALUE);
            if (removeIds.contains(pureId)) {
                array.remove(i);
            }
        }
    }

    private static JsonNode findChild(JsonNode collection, Long childPureId) {
        if (collection == null || !collection.isArray() || childPureId == null) {
            return null;
        }
        for (JsonNode item : collection) {
            if (item != null && item.path("pureId").asLong(Long.MIN_VALUE) == childPureId) {
                return item;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalize(String field) {
        return field == null ? "" : field.trim().toUpperCase(Locale.ROOT);
    }
}
