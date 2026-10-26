package cl.uss.ca.external.pure.metrics;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses PURE metric collection payloads without collapsing dimensionality.
 * One metricValue becomes one observation. Multiple categories for the same
 * year remain multiple rows.
 */
public final class PureMetricsParser {
    private PureMetricsParser() {
    }

    public static PureMetricFetchResult parse(JsonNode root, String expectedUuid, String expectedCollection) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return PureMetricFetchResult.invalid("EMPTY_BODY", "PURE metrics response was empty");
        }
        if (!root.isObject()) {
            return PureMetricFetchResult.invalid("NOT_OBJECT", "PURE metrics response was not a JSON object");
        }
        String owner = ownerUuid(root);
        if (expectedUuid != null && owner != null && !expectedUuid.equalsIgnoreCase(owner)) {
            return PureMetricFetchResult.invalid("OWNER_MISMATCH", "Metrics owner UUID did not match the requested UUID");
        }
        String collection = firstText(root, "collectionId", "collection");
        if (expectedCollection != null && collection != null && !expectedCollection.equals(collection)) {
            return PureMetricFetchResult.invalid(
                    "COLLECTION_MISMATCH",
                    "Metrics collectionId did not match the requested collection"
            );
        }
        String resolvedCollection = expectedCollection != null ? expectedCollection : collection;
        JsonNode items = firstArray(root, "items", "metrics", "metricList");
        if (items == null) {
            if (root.has("metricValues") || root.has("metricId")) {
                List<PureMetricObservation> single = parseItem(root, resolvedCollection);
                return single.isEmpty()
                        ? PureMetricFetchResult.invalid("NO_METRIC_VALUES", "Metric object had no usable metricValues")
                        : PureMetricFetchResult.success(single);
            }
            if (hasZeroCount(root)) {
                return PureMetricFetchResult.empty();
            }
            return PureMetricFetchResult.invalid("MISSING_ITEMS", "Metrics response had no items array");
        }
        if (items.size() == 0) {
            return PureMetricFetchResult.empty();
        }
        List<PureMetricObservation> observations = new ArrayList<>();
        int malformedItems = 0;
        for (JsonNode item : items) {
            List<PureMetricObservation> parsed = parseItem(item, resolvedCollection);
            if (parsed.isEmpty()) {
                malformedItems++;
            } else {
                observations.addAll(parsed);
            }
        }
        if (observations.isEmpty()) {
            return PureMetricFetchResult.invalid(
                    "NO_METRIC_VALUES",
                    "Metrics items were present but no metricValues could be parsed (" + malformedItems + ")"
            );
        }
        return PureMetricFetchResult.success(observations);
    }

    static List<PureMetricObservation> parseItem(JsonNode item, String collectionId) {
        if (item == null || item.isNull() || !item.isObject()) {
            return List.of();
        }
        Integer year = integer(item, "year", "metricYear");
        JsonNode source = firstObject(item, "source", "classificationSource");
        String sourceUri = uriOf(source);
        String sourceEs = termEs(source);
        String sourceEn = termEn(source);
        JsonNode categoryNode = firstObject(item, "classification", "category", "wosCategory", "asjc");
        String categoryUri = uriOf(categoryNode);
        String categoryEs = termEs(categoryNode);
        String categoryEn = termEn(categoryNode);
        JsonNode values = firstArray(item, "metricValues", "values");
        List<PureMetricObservation> observations = new ArrayList<>();
        if (values == null) {
            PureMetricObservation direct = parseMetricValue(item, collectionId, year);
            if (direct != null) {
                direct.withSource(sourceUri, sourceEs, sourceEn).withCategory(categoryUri, categoryEs, categoryEn);
                observations.add(direct);
            }
            return observations;
        }
        for (JsonNode value : values) {
            PureMetricObservation observation = parseMetricValue(value, collectionId, year);
            if (observation == null) {
                continue;
            }
            observation.withSource(sourceUri, sourceEs, sourceEn).withCategory(categoryUri, categoryEs, categoryEn);
            if (PureMetricCatalog.METRIC_CATEGORY.equals(observation.metricId) && observation.stringValue != null) {
                if (observation.categoryTermEn == null) {
                    observation.categoryTermEn = observation.stringValue;
                }
                if (observation.categoryTermEs == null) {
                    observation.categoryTermEs = observation.stringValue;
                }
            }
            observations.add(observation);
        }
        return observations;
    }

    static PureMetricObservation parseMetricValue(JsonNode node, String collectionId, Integer year) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String metricId = firstText(node, "metricId", "id", "name");
        if (metricId == null) {
            return null;
        }
        Integer valueYear = integer(node, "year", "metricYear");
        Integer resolvedYear = valueYear != null ? valueYear : year;
        int present = 0;
        if (hasNumber(node, "integerValue")) {
            present++;
        }
        if (hasNumber(node, "decimalValue")) {
            present++;
        }
        if (hasText(node, "stringValue")) {
            present++;
        }
        if (present == 0) {
            if (hasNumber(node, "value")) {
                BigDecimal decimal = decimal(node, "value");
                if (decimal == null) {
                    return null;
                }
                if (decimal.scale() <= 0) {
                    return PureMetricObservation.integer(collectionId, metricId, resolvedYear, decimal.longValueExact());
                }
                return PureMetricObservation.decimal(collectionId, metricId, resolvedYear, decimal);
            }
            return null;
        }
        if (present > 1) {
            return null;
        }
        if (hasNumber(node, "integerValue")) {
            Long integer = longValue(node, "integerValue");
            if (integer == null) {
                return null;
            }
            return PureMetricObservation.integer(collectionId, metricId, resolvedYear, integer);
        }
        if (hasNumber(node, "decimalValue")) {
            BigDecimal decimal = decimal(node, "decimalValue");
            if (decimal == null) {
                return null;
            }
            return PureMetricObservation.decimal(collectionId, metricId, resolvedYear, decimal);
        }
        String string = firstText(node, "stringValue");
        if (string == null) {
            return null;
        }
        return PureMetricObservation.string(collectionId, metricId, resolvedYear, string);
    }

    private static String ownerUuid(JsonNode root) {
        String uuid = firstText(root, "uuid", "pureUuid");
        if (uuid != null) {
            return uuid;
        }
        JsonNode owner = firstObject(root, "owner", "journal", "researchOutput", "person");
        return owner == null ? null : firstText(owner, "uuid");
    }

    private static boolean hasZeroCount(JsonNode root) {
        JsonNode count = root.get("count");
        return count != null && count.isNumber() && count.asInt() == 0;
    }

    private static JsonNode firstArray(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && child.isArray()) {
                return child;
            }
        }
        return null;
    }

    private static JsonNode firstObject(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && child.isObject()) {
                return child;
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode child = node.get(name);
            String text = text(child);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static String uriOf(JsonNode node) {
        return node == null ? null : firstText(node, "uri", "value");
    }

    private static String termEs(JsonNode node) {
        JsonNode term = termNode(node);
        if (term == null) {
            return null;
        }
        String es = firstText(term, "es_CL", "es", "es_ES");
        return es != null ? es : text(term);
    }

    private static String termEn(JsonNode node) {
        JsonNode term = termNode(node);
        if (term == null) {
            return null;
        }
        String en = firstText(term, "en_US", "en_GB", "en");
        return en != null ? en : text(term);
    }

    private static JsonNode termNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        JsonNode term = node.get("term");
        return term != null ? term : node;
    }

    private static Integer integer(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && child.isNumber()) {
                return child.intValue();
            }
            String text = text(child);
            if (text != null) {
                try {
                    return Integer.valueOf(text);
                } catch (NumberFormatException ignored) {
                    // keep looking
                }
            }
        }
        return null;
    }

    private static Long longValue(JsonNode node, String name) {
        JsonNode child = node.get(name);
        if (child == null || child.isNull()) {
            return null;
        }
        if (child.isNumber()) {
            return child.longValue();
        }
        String text = text(child);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal decimal(JsonNode node, String name) {
        JsonNode child = node.get(name);
        if (child == null || child.isNull()) {
            return null;
        }
        if (child.isNumber() || child.isTextual()) {
            try {
                return new BigDecimal(child.asText());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static boolean hasNumber(JsonNode node, String name) {
        JsonNode child = node.get(name);
        return child != null && !child.isNull() && (child.isNumber() || (child.isTextual() && !child.asText().isBlank()));
    }

    private static boolean hasText(JsonNode node, String name) {
        return text(node.get(name)) != null;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber()) {
            String value = node.asText();
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return null;
    }

    public static boolean looksLikeScopus(String uri, String termEn, String termEs) {
        return contains(uri, "scopus") || contains(termEn, "scopus") || contains(termEs, "scopus");
    }

    public static boolean looksLikeAllYears(String uri, String termEn, String termEs) {
        if (contains(termEn, "10 year") || contains(termEn, "5 year")
                || contains(termEs, "10 año") || contains(termEs, "5 año")
                || contains(uri, "10-year") || contains(uri, "5-year")) {
            return false;
        }
        return contains(termEn, "all year")
                || contains(termEs, "todos los a")
                || contains(uri, "all-years")
                || contains(uri, "allyears")
                || contains(uri, "/all");
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
