package cl.uss.ca.external.unpaywall;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UnpaywallMapper {
    private UnpaywallMapper() {
    }

    public static UnpaywallWorkRecord parse(JsonNode root) {
        UnpaywallWorkRecord work = new UnpaywallWorkRecord();
        if (root == null || root.isNull() || root.isMissingNode()) {
            return work;
        }
        work.doi = text(root, "doi");
        work.doiUrl = text(root, "doi_url");
        work.isOa = bool(root, "is_oa");
        work.oaStatus = text(root, "oa_status");
        work.genre = text(root, "genre");
        work.title = text(root, "title");
        work.year = integer(root, "year");
        work.publishedDate = text(root, "published_date");
        work.publisher = text(root, "publisher");
        work.journalName = text(root, "journal_name");
        work.journalIssns = text(root, "journal_issns");
        work.journalIssnL = text(root, "journal_issn_l");
        work.journalIsOa = bool(root, "journal_is_oa");
        work.journalIsInDoaj = bool(root, "journal_is_in_doaj");
        work.hasRepositoryCopy = bool(root, "has_repository_copy");
        work.apiUpdatedAt = timestamp(root, "updated");
        work.bestOaLocation = location(root.get("best_oa_location"));
        Map<String, UnpaywallLocationRecord> unique = new LinkedHashMap<>();
        for (UnpaywallLocationRecord item : locations(root.get("oa_locations"))) {
            String key = UnpaywallLocationIdentity.key(item);
            if (key != null) {
                unique.putIfAbsent(key, item);
            }
        }
        if (work.bestOaLocation != null) {
            String bestKey = UnpaywallLocationIdentity.key(work.bestOaLocation);
            if (bestKey != null) {
                unique.putIfAbsent(bestKey, work.bestOaLocation);
                unique.get(bestKey).isBest = Boolean.TRUE;
            }
        }
        work.oaLocations.addAll(unique.values());
        if (work.hasRepositoryCopy == null) {
            work.hasRepositoryCopy = UnpaywallLocationIdentity.hasRepositoryCopy(work.oaLocations);
        }
        return work;
    }

    private static List<UnpaywallLocationRecord> locations(JsonNode node) {
        List<UnpaywallLocationRecord> items = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return items;
        }
        for (JsonNode child : node) {
            UnpaywallLocationRecord location = location(child);
            if (location != null) {
                items.add(location);
            }
        }
        return items;
    }

    private static UnpaywallLocationRecord location(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            return null;
        }
        UnpaywallLocationRecord location = new UnpaywallLocationRecord();
        location.endpointId = text(node, "endpoint_id");
        location.hostType = text(node, "host_type");
        location.license = text(node, "license");
        location.version = text(node, "version");
        location.url = text(node, "url");
        location.urlForLandingPage = text(node, "url_for_landing_page");
        location.urlForPdf = text(node, "url_for_pdf");
        location.oaDate = text(node, "oa_date");
        location.isBest = bool(node, "is_best");
        location.evidence = text(node, "evidence");
        location.repositoryInstitution = text(node, "repository_institution");
        location.pmhId = text(node, "pmh_id");
        if (UnpaywallLocationIdentity.key(location) == null) {
            return null;
        }
        return location;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asText(null);
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode() || !value.isBoolean()) {
            return null;
        }
        return value.booleanValue();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode() || !value.canConvertToInt()) {
            return null;
        }
        return value.intValue();
    }

    private static OffsetDateTime timestamp(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(raw.replace(" ", "T"));
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }
}
