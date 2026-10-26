package cl.uss.ca.external.crossref;

import cl.uss.ca.domain.AuthorNameMatcher;
import cl.uss.ca.domain.DoiNormalizer;
import cl.uss.ca.quality.DataQualityIdentifiers;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CrossrefMapper {
    private CrossrefMapper() {
    }

    public static CrossrefWorkRecord parseWork(JsonNode root) {
        CrossrefWorkRecord work = new CrossrefWorkRecord();
        JsonNode message = message(root);
        if (message == null) {
            return work;
        }
        work.doi = text(message, "DOI");
        work.canonicalDoi = DoiNormalizer.normalize(work.doi);
        work.type = text(message, "type");
        work.title = firstText(message.get("title"));
        work.subtitle = firstText(message.get("subtitle"));
        work.shortTitle = firstText(message.get("short-title"));
        work.containerTitle = firstText(message.get("container-title"));
        work.publisher = text(message, "publisher");
        work.volume = text(message, "volume");
        work.issue = text(message, "issue");
        work.page = text(message, "page");
        work.articleNumber = text(message, "article-number");
        work.language = text(message, "language");
        work.referenceCount = integer(message, "reference-count");
        work.isReferencedByCount = integer(message, "is-referenced-by-count");
        work.publishedDate = dateParts(message.get("published"));
        work.publishedPrintDate = dateParts(message.get("published-print"));
        work.publishedOnlineDate = dateParts(message.get("published-online"));
        work.issuedDate = dateParts(message.get("issued"));
        work.createdCrossrefAt = timestamp(message.get("created"));
        work.depositedCrossrefAt = timestamp(message.get("deposited"));
        work.indexedCrossrefAt = timestamp(message.get("indexed"));
        work.authors.addAll(authors(message.get("author")));
        work.issns.addAll(issns(message));
        work.funders.addAll(funders(message.get("funder")));
        work.licenses.addAll(licenses(message.get("license")));
        work.relations.addAll(relations(message.get("relation"), message.get("update-to")));
        return work;
    }

    public static Agency parseAgency(JsonNode root) {
        Agency agency = new Agency();
        JsonNode message = message(root);
        if (message == null) {
            return agency;
        }
        JsonNode node = message.get("agency");
        if (node == null || !node.isObject()) {
            return agency;
        }
        agency.id = text(node, "id");
        agency.label = text(node, "label");
        return agency;
    }

    private static List<CrossrefAuthorRecord> authors(JsonNode node) {
        List<CrossrefAuthorRecord> items = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return items;
        }
        int order = 0;
        for (JsonNode child : node) {
            if (child == null || !child.isObject()) {
                continue;
            }
            CrossrefAuthorRecord author = new CrossrefAuthorRecord();
            author.sequenceNo = order++;
            author.given = text(child, "given");
            author.family = text(child, "family");
            author.suffix = text(child, "suffix");
            author.fullName = AuthorNameMatcher.displayName(author.given, author.family);
            author.orcid = DataQualityIdentifiers.normalizeOrcid(text(child, "ORCID"));
            JsonNode authenticated = child.get("authenticated-orcid");
            if (authenticated != null && authenticated.isBoolean()) {
                author.authenticatedOrcid = authenticated.booleanValue();
            }
            author.affiliations.addAll(affiliations(child.get("affiliation")));
            items.add(author);
        }
        return items;
    }

    private static List<CrossrefAffiliationRecord> affiliations(JsonNode node) {
        List<CrossrefAffiliationRecord> items = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return items;
        }
        int order = 0;
        for (JsonNode child : node) {
            if (child == null || !child.isObject()) {
                continue;
            }
            CrossrefAffiliationRecord affiliation = new CrossrefAffiliationRecord();
            affiliation.sequenceNo = order++;
            affiliation.name = text(child, "name");
            affiliation.ror = ror(child);
            if (affiliation.name != null || affiliation.ror != null) {
                items.add(affiliation);
            }
        }
        return items;
    }

    private static String ror(JsonNode affiliation) {
        JsonNode id = affiliation.get("id");
        if (id != null && id.isTextual() && id.asText().toLowerCase(Locale.ROOT).contains("ror.org")) {
            return id.asText();
        }
        JsonNode ids = affiliation.get("id");
        if (ids != null && ids.isArray()) {
            for (JsonNode item : ids) {
                String value = item == null || !item.isTextual() ? null : item.asText();
                if (value != null && value.toLowerCase(Locale.ROOT).contains("ror.org")) {
                    return value;
                }
            }
        }
        return null;
    }

    private static List<CrossrefIssnRecord> issns(JsonNode message) {
        List<CrossrefIssnRecord> items = new ArrayList<>();
        JsonNode typed = message.get("issn-type");
        if (typed != null && typed.isArray()) {
            for (JsonNode child : typed) {
                if (child == null || !child.isObject()) {
                    continue;
                }
                CrossrefIssnRecord issn = new CrossrefIssnRecord();
                issn.issn = text(child, "value");
                issn.type = text(child, "type");
                if (issn.issn != null) {
                    items.add(issn);
                }
            }
            if (!items.isEmpty()) {
                return items;
            }
        }
        JsonNode issn = message.get("ISSN");
        if (issn != null && issn.isArray()) {
            for (JsonNode child : issn) {
                if (child == null || !child.isTextual()) {
                    continue;
                }
                CrossrefIssnRecord item = new CrossrefIssnRecord();
                item.issn = child.asText();
                items.add(item);
            }
        }
        return items;
    }

    private static List<CrossrefFunderRecord> funders(JsonNode node) {
        List<CrossrefFunderRecord> items = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return items;
        }
        for (JsonNode child : node) {
            if (child == null || !child.isObject()) {
                continue;
            }
            String name = text(child, "name");
            String doi = text(child, "DOI");
            JsonNode awards = child.get("award");
            if (awards != null && awards.isArray() && awards.size() > 0) {
                for (JsonNode award : awards) {
                    CrossrefFunderRecord funder = new CrossrefFunderRecord();
                    funder.name = name;
                    funder.funderDoi = doi;
                    funder.award = award == null || award.isNull() ? null : award.asText();
                    items.add(funder);
                }
            } else {
                CrossrefFunderRecord funder = new CrossrefFunderRecord();
                funder.name = name;
                funder.funderDoi = doi;
                items.add(funder);
            }
        }
        return items;
    }

    private static List<CrossrefLicenseRecord> licenses(JsonNode node) {
        List<CrossrefLicenseRecord> items = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return items;
        }
        for (JsonNode child : node) {
            if (child == null || !child.isObject()) {
                continue;
            }
            CrossrefLicenseRecord license = new CrossrefLicenseRecord();
            license.url = text(child, "URL");
            license.startDate = dateParts(child.get("start"));
            license.contentVersion = text(child, "content-version");
            license.delayInDays = integer(child, "delay-in-days");
            items.add(license);
        }
        return items;
    }

    private static List<CrossrefRelationRecord> relations(JsonNode relation, JsonNode updateTo) {
        List<CrossrefRelationRecord> items = new ArrayList<>();
        if (relation != null && relation.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = relation.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode list = entry.getValue();
                if (list == null || !list.isArray()) {
                    continue;
                }
                for (JsonNode child : list) {
                    if (child == null || !child.isObject()) {
                        continue;
                    }
                    CrossrefRelationRecord item = new CrossrefRelationRecord();
                    item.type = entry.getKey();
                    item.id = text(child, "id");
                    item.assertedBy = text(child, "asserted-by");
                    item.doi = DoiNormalizer.normalize(item.id);
                    items.add(item);
                }
            }
        }
        if (updateTo != null && updateTo.isArray()) {
            for (JsonNode child : updateTo) {
                if (child == null || !child.isObject()) {
                    continue;
                }
                CrossrefRelationRecord item = new CrossrefRelationRecord();
                item.type = text(child, "type");
                if (item.type == null) {
                    item.type = "update";
                }
                item.doi = DoiNormalizer.normalize(text(child, "DOI"));
                item.id = text(child, "DOI");
                item.assertedBy = "crossref";
                items.add(item);
            }
        }
        return items;
    }

    private static JsonNode message(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        JsonNode message = root.get("message");
        if (message != null && message.isObject()) {
            return message;
        }
        return root.isObject() ? root : null;
    }

    private static String firstText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            String value = node.asText();
            return value == null || value.isBlank() ? null : value.trim();
        }
        if (node.isArray() && node.size() > 0) {
            return firstText(node.get(0));
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

    static String dateParts(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode parts = node.get("date-parts");
        if (parts == null || !parts.isArray() || parts.size() == 0 || !parts.get(0).isArray()) {
            return timestamp(node);
        }
        JsonNode first = parts.get(0);
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : first) {
            if (part == null || !part.isNumber()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('-');
            }
            int value = part.intValue();
            if (value < 10 && builder.length() > 0) {
                builder.append('0');
            }
            builder.append(value);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    static String timestamp(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String dateTime = text(node, "date-time");
        if (dateTime != null) {
            return mysqlDateTime(dateTime);
        }
        return mysqlDateTime(dateParts(node));
    }

    /**
     * MySQL DATETIME rejects ISO-8601 {@code 2026-09-16T07:58:59Z}. Persist UTC
     * {@code yyyy-MM-dd HH:mm:ss[.fraction]}.
     */
    public static String mysqlDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        OffsetDateTime offset = parseOffset(value);
        if (offset != null) {
            return MYSQL_DATE_TIME.format(offset.withOffsetSameInstant(ZoneOffset.UTC));
        }
        try {
            return MYSQL_DATE_TIME.format(Instant.parse(value).atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            // continue
        }
        try {
            LocalDateTime local = LocalDateTime.parse(value.replace(' ', 'T'));
            return MYSQL_DATE_TIME.format(local);
        } catch (DateTimeParseException ignored) {
            // continue
        }
        try {
            LocalDate date = LocalDate.parse(value);
            return MYSQL_DATE_TIME.format(date.atStartOfDay());
        } catch (DateTimeException ignored) {
            return sanitizeIso(value);
        }
    }

    private static OffsetDateTime parseOffset(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value.replace(' ', 'T'));
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private static String sanitizeIso(String value) {
        String sanitized = value.replace('T', ' ');
        if (sanitized.endsWith("Z") || sanitized.endsWith("z")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        int plus = sanitized.lastIndexOf('+');
        if (plus >= 10) {
            sanitized = sanitized.substring(0, plus);
        }
        sanitized = sanitized.trim();
        return sanitized.isEmpty() ? null : sanitized;
    }

    private static final DateTimeFormatter MYSQL_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    public static final class Agency {
        public String id;
        public String label;
    }
}
