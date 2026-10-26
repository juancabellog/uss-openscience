package cl.uss.ca.external.orcid;

import cl.uss.ca.domain.OrcidExternalIdNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class OrcidWorksMapper {
    private OrcidWorksMapper() {
    }

    public static List<OrcidWorkGroupRecord> parseWorks(String orcid, JsonNode root, ObjectMapper mapper) {
        List<OrcidWorkGroupRecord> groups = new ArrayList<>();
        JsonNode nodes = root.path("group");
        if (!nodes.isArray()) {
            return groups;
        }
        for (JsonNode node : nodes) {
            OrcidWorkGroupRecord group = new OrcidWorkGroupRecord();
            group.orcid = orcid;
            group.lastModifiedOrcid = longValue(node.path("last-modified-date").path("value"));
            group.rawJson = raw(node, mapper);
            group.externalIds.addAll(mapExternalIds(node.path("external-ids").path("external-id")));
            JsonNode summaries = node.path("work-summary");
            if (summaries.isArray()) {
                for (JsonNode summary : summaries) {
                    OrcidWorkRecord work = mapSummary(orcid, summary, mapper);
                    if (work.putCode != null) {
                        group.summaries.add(work);
                    }
                }
            }
            if (!group.summaries.isEmpty() || !group.externalIds.isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }

    public static OrcidWorkRecord mapSummary(String orcid, JsonNode node, ObjectMapper mapper) {
        OrcidWorkRecord work = mapCommon(orcid, node);
        work.rawJson = raw(node, mapper);
        work.detailLoaded = false;
        return work;
    }

    public static OrcidWorkRecord mapDetail(String orcid, JsonNode node, ObjectMapper mapper) {
        OrcidWorkRecord work = mapCommon(orcid, node);
        work.createdDateOrcid = longValue(node.path("created-date").path("value"));
        work.subtitle = text(node.path("title").path("subtitle").path("value"));
        work.translatedTitle = text(node.path("title").path("translated-title").path("value"));
        work.translatedTitleLanguage = firstText(
                node.path("title").path("translated-title").path("language"),
                node.path("title").path("translated-title").get("language")
        );
        work.shortDescription = text(node.get("short-description"));
        work.citationType = text(node.path("citation").path("citation-type"));
        work.citationValue = text(node.path("citation").path("citation-value"));
        work.languageCode = text(node.get("language-code"));
        work.countryCode = firstText(node.path("country").path("value"), node.get("country"));
        work.path = text(node.get("path"));
        work.rawJson = raw(node, mapper);
        work.detailLoaded = true;
        work.contributors.addAll(mapContributors(node.path("contributors").path("contributor"), mapper));
        return work;
    }

    private static OrcidWorkRecord mapCommon(String orcid, JsonNode node) {
        OrcidWorkRecord work = new OrcidWorkRecord();
        work.orcid = orcid;
        work.putCode = longValue(node.get("put-code"));
        work.lastModifiedOrcid = longValue(node.path("last-modified-date").path("value"));
        work.workType = text(node.get("type"));
        work.title = text(node.path("title").path("title").path("value"));
        work.journalTitle = text(node.path("journal-title").path("value"));
        work.url = text(node.path("url").path("value"));
        work.visibility = text(node.get("visibility"));
        work.path = text(node.get("path"));
        work.displayIndex = intValue(node.get("display-index"));
        mapSource(work, node.path("source"));
        JsonNode date = node.path("publication-date");
        work.publicationYear = intValue(date.path("year").path("value"));
        work.publicationMonth = intValue(date.path("month").path("value"));
        work.publicationDay = intValue(date.path("day").path("value"));
        work.externalIds.addAll(mapExternalIds(node.path("external-ids").path("external-id")));
        return work;
    }

    private static void mapSource(OrcidWorkRecord work, JsonNode source) {
        work.sourceOrcidUri = text(source.path("source-orcid").path("uri"));
        work.sourceOrcidPath = text(source.path("source-orcid").path("path"));
        work.sourceClientIdUri = text(source.path("source-client-id").path("uri"));
        work.sourceClientIdPath = text(source.path("source-client-id").path("path"));
        work.sourceName = text(source.path("source-name").path("value"));
        work.assertionOriginOrcidUri = text(source.path("assertion-origin-orcid").path("uri"));
        work.assertionOriginOrcidPath = text(source.path("assertion-origin-orcid").path("path"));
        work.assertionOriginClientIdUri = text(source.path("assertion-origin-client-id").path("uri"));
        work.assertionOriginClientIdPath = text(source.path("assertion-origin-client-id").path("path"));
        work.assertionOriginName = text(source.path("assertion-origin-name").path("value"));
    }

    static List<OrcidExternalIdRecord> mapExternalIds(JsonNode ids) {
        List<OrcidExternalIdRecord> items = new ArrayList<>();
        if (!ids.isArray()) {
            return items;
        }
        for (JsonNode id : ids) {
            OrcidExternalIdRecord external = new OrcidExternalIdRecord();
            external.type = text(id.get("external-id-type"));
            external.value = text(id.get("external-id-value"));
            external.url = text(id.path("external-id-url").path("value"));
            external.relationship = text(id.get("external-id-relationship"));
            if (external.type == null || external.value == null) {
                continue;
            }
            OrcidExternalIdNormalizer.Result normalized = OrcidExternalIdNormalizer.normalize(
                    external.type,
                    external.value
            );
            external.normalizedValue = normalized.normalizedValue;
            external.normalizedTransient = normalized.transientNormalization;
            external.normalizationError = normalized.error;
            items.add(external);
        }
        return items;
    }

    private static List<OrcidContributorRecord> mapContributors(JsonNode nodes, ObjectMapper mapper) {
        List<OrcidContributorRecord> items = new ArrayList<>();
        if (!nodes.isArray()) {
            return items;
        }
        int position = 0;
        for (JsonNode node : nodes) {
            OrcidContributorRecord contributor = new OrcidContributorRecord();
            contributor.position = position++;
            contributor.contributorOrcid = firstText(
                    node.path("contributor-orcid").path("path"),
                    node.path("contributor-orcid").path("uri")
            );
            contributor.creditName = text(node.path("credit-name").path("value"));
            contributor.contributorEmail = firstText(
                    node.path("contributor-email").path("value"),
                    node.get("contributor-email")
            );
            JsonNode attributes = node.get("contributor-attributes");
            if (attributes != null && !attributes.isNull() && !attributes.isMissingNode()) {
                contributor.attributesJson = raw(attributes, mapper);
            }
            items.add(contributor);
        }
        return items;
    }

    private static String raw(JsonNode node, ObjectMapper mapper) {
        if (node == null || node.isMissingNode() || node.isNull() || mapper == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception ex) {
            return null;
        }
    }

    static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            String value = text(node);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static Long longValue(JsonNode node) {
        String value = text(node);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Integer intValue(JsonNode node) {
        String value = text(node);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.replaceFirst("^0+(?!$)", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
