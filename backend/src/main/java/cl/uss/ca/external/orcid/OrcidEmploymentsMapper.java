package cl.uss.ca.external.orcid;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps ORCID Public API employment payloads without assuming a single JSON shape.
 * Walks affiliation-group / summaries / employment-summary and any object that
 * already contains organization + put-code.
 */
public final class OrcidEmploymentsMapper {
    private OrcidEmploymentsMapper() {
    }

    public static List<OrcidEmploymentRecord> parse(String orcid, JsonNode root) {
        Map<Long, OrcidEmploymentRecord> byPutCode = new LinkedHashMap<>();
        collect(orcid, root, byPutCode);
        return new ArrayList<>(byPutCode.values());
    }

    private static void collect(String orcid, JsonNode node, Map<Long, OrcidEmploymentRecord> sink) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collect(orcid, child, sink);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        JsonNode summary = node.get("employment-summary");
        if (summary != null && summary.isObject()) {
            add(orcid, summary, sink);
        } else if (looksLikeEmployment(node)) {
            add(orcid, node, sink);
        }
        node.fields().forEachRemaining(entry -> collect(orcid, entry.getValue(), sink));
    }

    private static boolean looksLikeEmployment(JsonNode node) {
        return node.has("put-code") && node.has("organization");
    }

    private static void add(String orcid, JsonNode node, Map<Long, OrcidEmploymentRecord> sink) {
        Long putCode = OrcidWorksMapper.longValue(node.get("put-code"));
        if (putCode == null) {
            return;
        }
        OrcidEmploymentRecord record = new OrcidEmploymentRecord();
        record.orcid = orcid;
        record.putCode = putCode;
        JsonNode organization = node.path("organization");
        record.organizationName = organizationName(organization);
        JsonNode address = organization.path("address");
        record.organizationCity = OrcidWorksMapper.firstText(address.path("city").path("value"), address.get("city"));
        record.organizationRegion = OrcidWorksMapper.firstText(
                address.path("region").path("value"),
                address.get("region")
        );
        record.organizationCountry = OrcidWorksMapper.firstText(
                address.path("country").path("value"),
                address.get("country")
        );
        JsonNode disambiguated = organization.path("disambiguated-organization");
        record.organizationIdentifier = OrcidWorksMapper.firstText(
                disambiguated.get("disambiguated-organization-identifier"),
                disambiguated.path("disambiguated-organization-identifier").path("value")
        );
        record.organizationIdentifierType = OrcidWorksMapper.firstText(
                disambiguated.get("disambiguation-source"),
                disambiguated.path("disambiguation-source").path("value")
        );
        JsonNode start = node.path("start-date");
        record.startYear = OrcidWorksMapper.intValue(start.path("year").path("value"));
        record.startMonth = OrcidWorksMapper.intValue(start.path("month").path("value"));
        record.startDay = OrcidWorksMapper.intValue(start.path("day").path("value"));
        JsonNode end = node.path("end-date");
        record.endYear = OrcidWorksMapper.intValue(end.path("year").path("value"));
        record.endMonth = OrcidWorksMapper.intValue(end.path("month").path("value"));
        record.endDay = OrcidWorksMapper.intValue(end.path("day").path("value"));
        record.currentAffiliation = record.endYear == null;
        record.visibility = OrcidWorksMapper.text(node.get("visibility"));
        sink.put(putCode, record);
    }

    private static String organizationName(JsonNode organization) {
        JsonNode name = organization.get("name");
        if (name == null || name.isNull() || name.isMissingNode()) {
            return null;
        }
        if (name.isTextual()) {
            return OrcidWorksMapper.text(name);
        }
        return OrcidWorksMapper.firstText(name.path("value"), name);
    }
}
