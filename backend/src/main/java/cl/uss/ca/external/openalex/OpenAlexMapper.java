package cl.uss.ca.external.openalex;

import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.domain.OpenAlexId;
import cl.uss.ca.domain.OrcidFormat;
import cl.uss.ca.domain.UssOrganizationIdentity;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OpenAlexMapper {
    private OpenAlexMapper() {
    }

    public static OpenAlexWorkRecord parseWork(JsonNode root) {
        OpenAlexWorkRecord work = new OpenAlexWorkRecord();
        if (root == null || !root.isObject()) {
            return work;
        }
        work.openalexWorkId = OpenAlexId.normalize(text(root, "id"));
        work.doi = DoiNormalizer.normalize(text(root, "doi"));
        work.displayName = text(root, "display_name");
        work.publicationYear = integer(root, "publication_year");
        work.publicationDate = text(root, "publication_date");
        work.workType = text(root, "type");
        work.citedByCount = integer(root, "cited_by_count");
        work.referencedWorksCount = integer(root, "referenced_works_count");
        work.language = text(root, "language");
        work.updatedDate = text(root, "updated_date");
        JsonNode oa = root.get("open_access");
        if (oa != null && oa.isObject()) {
            JsonNode isOa = oa.get("is_oa");
            if (isOa != null && isOa.isBoolean()) {
                work.isOa = isOa.booleanValue();
            }
            work.oaStatus = text(oa, "oa_status");
        }
        JsonNode location = root.get("primary_location");
        if (location != null && location.isObject()) {
            JsonNode source = location.get("source");
            if (source != null && source.isObject()) {
                work.primarySourceId = OpenAlexId.normalize(text(source, "id"));
                work.primarySourceDisplayName = text(source, "display_name");
                work.primarySourceIssnL = text(source, "issn_l");
            }
        }
        work.authorships.addAll(authorships(root.get("authorships")));
        work.topics.addAll(topics(root.get("primary_topic"), root.get("topics")));
        return work;
    }

    public static OpenAlexAuthorRecord parseAuthor(JsonNode root) {
        OpenAlexAuthorRecord author = new OpenAlexAuthorRecord();
        if (root == null || !root.isObject()) {
            return author;
        }
        author.openalexAuthorId = OpenAlexId.normalize(text(root, "id"));
        author.displayName = text(root, "display_name");
        author.orcid = OrcidFormat.normalizeValidOrNull(text(root, "orcid"));
        author.worksCount = integer(root, "works_count");
        author.citedByCount = integer(root, "cited_by_count");
        author.updatedDate = text(root, "updated_date");
        return author;
    }

    private static List<OpenAlexAuthorshipRecord> authorships(JsonNode node) {
        List<OpenAlexAuthorshipRecord> items = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return items;
        }
        int order = 0;
        for (JsonNode child : node) {
            if (child == null || !child.isObject()) {
                continue;
            }
            OpenAlexAuthorshipRecord row = new OpenAlexAuthorshipRecord();
            row.sourceOrder = order++;
            row.authorPosition = text(child, "author_position");
            JsonNode corresponding = child.get("is_corresponding");
            row.corresponding = corresponding != null && corresponding.isBoolean() && corresponding.booleanValue();
            row.rawAuthorName = text(child, "raw_author_name");
            row.rawAffiliationString = firstAffiliationString(child);
            JsonNode author = child.get("author");
            if (author != null && author.isObject()) {
                row.openalexAuthorId = OpenAlexId.normalize(text(author, "id"));
                row.authorDisplayName = text(author, "display_name");
                row.authorOrcid = OrcidFormat.normalizeValidOrNull(text(author, "orcid"));
            }
            row.observedOrcid = OrcidFormat.normalizeValidOrNull(text(child, "raw_orcid"));
            if (row.rawAuthorName == null) {
                row.rawAuthorName = row.authorDisplayName;
            }
            row.institutions.addAll(institutions(child.get("institutions")));
            items.add(row);
        }
        return items;
    }

    private static List<OpenAlexInstitutionRecord> institutions(JsonNode node) {
        List<OpenAlexInstitutionRecord> items = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return items;
        }
        int order = 0;
        for (JsonNode child : node) {
            if (child == null || !child.isObject()) {
                continue;
            }
            OpenAlexInstitutionRecord institution = new OpenAlexInstitutionRecord();
            institution.sourceOrder = order++;
            institution.openalexInstitutionId = OpenAlexId.normalize(text(child, "id"));
            institution.displayName = text(child, "display_name");
            institution.ror = ror(text(child, "ror"));
            institution.countryCode = text(child, "country_code");
            institution.type = text(child, "type");
            items.add(institution);
        }
        return items;
    }

    private static List<OpenAlexTopicRecord> topics(JsonNode primary, JsonNode topics) {
        List<OpenAlexTopicRecord> items = new ArrayList<>();
        String primaryId = null;
        if (primary != null && primary.isObject()) {
            OpenAlexTopicRecord topic = topic(primary, true);
            primaryId = topic.topicId;
            items.add(topic);
        }
        if (topics == null || !topics.isArray()) {
            return items;
        }
        for (JsonNode child : topics) {
            if (child == null || !child.isObject()) {
                continue;
            }
            OpenAlexTopicRecord topic = topic(child, false);
            if (primaryId != null && primaryId.equals(topic.topicId)) {
                continue;
            }
            items.add(topic);
        }
        return items;
    }

    private static OpenAlexTopicRecord topic(JsonNode node, boolean primary) {
        OpenAlexTopicRecord topic = new OpenAlexTopicRecord();
        topic.topicId = OpenAlexId.normalize(text(node, "id"));
        topic.displayName = text(node, "display_name");
        topic.score = decimal(node, "score");
        topic.primary = primary;
        JsonNode domain = node.get("domain");
        if (domain != null && domain.isObject()) {
            topic.domainId = OpenAlexId.normalize(text(domain, "id"));
            topic.domainDisplayName = text(domain, "display_name");
        }
        JsonNode field = node.get("field");
        if (field != null && field.isObject()) {
            topic.fieldId = OpenAlexId.normalize(text(field, "id"));
            topic.fieldDisplayName = text(field, "display_name");
        }
        JsonNode subfield = node.get("subfield");
        if (subfield != null && subfield.isObject()) {
            topic.subfieldId = OpenAlexId.normalize(text(subfield, "id"));
            topic.subfieldDisplayName = text(subfield, "display_name");
        }
        return topic;
    }

    public static boolean ussAffiliation(OpenAlexAuthorshipRecord authorship) {
        if (authorship == null) {
            return false;
        }
        if (UssOrganizationIdentity.isUssTextEvidence(authorship.rawAffiliationString)) {
            return true;
        }
        for (OpenAlexInstitutionRecord institution : authorship.institutions) {
            if (UssOrganizationIdentity.isUssIdentifier(institution.ror, "ROR")
                    || UssOrganizationIdentity.isUssTextEvidence(institution.displayName)) {
                return true;
            }
        }
        return false;
    }

    private static String firstAffiliationString(JsonNode authorship) {
        JsonNode raw = authorship.get("raw_affiliation_strings");
        if (raw != null && raw.isArray() && raw.size() > 0) {
            String value = firstText(raw.get(0));
            if (value != null) {
                return value;
            }
        }
        JsonNode affiliations = authorship.get("affiliations");
        if (affiliations != null && affiliations.isArray()) {
            for (JsonNode child : affiliations) {
                if (child != null && child.isObject()) {
                    String value = text(child, "raw_affiliation_string");
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static String ror(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        int slash = value.lastIndexOf('/');
        if (slash >= 0 && slash < value.length() - 1) {
            return "https://ror.org/" + value.substring(slash + 1).toLowerCase(Locale.ROOT);
        }
        if (value.toLowerCase(Locale.ROOT).startsWith("https://ror.org/")) {
            return value;
        }
        return "https://ror.org/" + value.toLowerCase(Locale.ROOT);
    }

    private static String firstText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            String value = node.asText();
            return value == null || value.isBlank() ? null : value.trim();
        }
        return null;
    }

    static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asText();
        }
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    static Integer integer(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.intValue();
    }

    static Double decimal(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.doubleValue();
    }
}
