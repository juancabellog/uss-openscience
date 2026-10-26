package cl.uss.ca.audit;

import java.util.List;
import java.util.Locale;

/**
 * Maps sync_execution.entity to audit source/operation codes already used by the platform.
 */
public final class AuditCatalog {
    public static final String TYPE_SYNC = "SYNC";
    public static final String ACTOR_SYSTEM = "SYSTEM";
    public static final String ACTOR_USER = "USER";
    private static final String EVENT_PREFIX = "SYNC:";

    private AuditCatalog() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String sourceOf(String entity) {
        return switch (normalize(entity)) {
            case "PEOPLE", "RESEARCH_OUTPUT", "RESEARCH_OUTPUTS", "JOURNALS", "JOURNAL", "PROJECTS", "PROJECT",
                 "ORGANIZATIONS", "ORGANIZATION",
                 "JOURNAL_METRICS", "RESEARCH_OUTPUT_METRICS", "PERSON_METRICS" -> "PURE";
            case "ORCID", "ORCID_WORKS" -> "ORCID";
            case "UNPAYWALL" -> "UNPAYWALL";
            case "CROSSREF" -> "CROSSREF";
            case "OPENALEX" -> "OPENALEX";
            case "DSPACE" -> "DSPACE";
            default -> {
                String normalized = normalize(entity);
                yield normalized.isEmpty() ? null : normalized;
            }
        };
    }

    /**
     * @return entity codes for a source filter, empty list when the source has no sync_execution rows,
     *         or null when the filter should not constrain entity.
     */
    public static List<String> entitiesForSource(String source) {
        String normalized = normalize(source);
        if (normalized.isEmpty() || "ALL".equals(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "PURE" -> List.of(
                    "PEOPLE", "RESEARCH_OUTPUT", "JOURNALS", "PROJECTS", "ORGANIZATIONS",
                    "JOURNAL_METRICS", "RESEARCH_OUTPUT_METRICS", "PERSON_METRICS"
            );
            case "ORCID" -> List.of("ORCID");
            case "UNPAYWALL" -> List.of("UNPAYWALL");
            case "CROSSREF" -> List.of("CROSSREF");
            case "OPENALEX" -> List.of("OPENALEX");
            case "DSPACE" -> List.of("DSPACE");
            case "SCOPUS", "SCIVAL" -> List.of();
            case "PEOPLE", "RESEARCH_OUTPUT", "JOURNALS", "JOURNAL", "RESEARCH_OUTPUTS", "PROJECTS", "PROJECT",
                 "ORGANIZATIONS", "ORGANIZATION" ->
                    List.of(canonicalEntity(normalized));
            default -> List.of(normalized);
        };
    }

    public static String canonicalEntity(String entity) {
        return switch (normalize(entity)) {
            case "RESEARCH_OUTPUTS" -> "RESEARCH_OUTPUT";
            case "JOURNAL" -> "JOURNALS";
            case "PROJECT" -> "PROJECTS";
            case "ORGANIZATION" -> "ORGANIZATIONS";
            case "ORCID_WORKS" -> "ORCID";
            default -> {
                String normalized = normalize(entity);
                yield normalized.isEmpty() ? null : normalized;
            }
        };
    }

    public static String operationOf(String entity) {
        return operationOf(entity, null);
    }

    public static String operationOf(String entity, String syncMode) {
        if ("OPENALEX".equals(normalize(entity)) && "RECOVERY".equals(normalize(syncMode))) {
            return "OPENALEX_AUTHORS_RECOVERY";
        }
        if ("DSPACE".equals(normalize(entity)) && "RECOVERY".equals(normalize(syncMode))) {
            return "DSPACE_BITSTREAM_BACKFILL";
        }
        return switch (normalize(entity)) {
            case "PEOPLE" -> "SYNC_PERSONS";
            case "RESEARCH_OUTPUT", "RESEARCH_OUTPUTS" -> "SYNC_RESEARCH_OUTPUTS";
            case "JOURNALS", "JOURNAL" -> "SYNC_JOURNALS";
            case "PROJECTS", "PROJECT" -> "SYNC_PROJECTS";
            case "ORGANIZATIONS", "ORGANIZATION" -> "SYNC_ORGANIZATIONS";
            case "ORCID", "ORCID_WORKS" -> "ORCID_SYNC";
            case "UNPAYWALL" -> "UNPAYWALL_SYNC";
            case "CROSSREF" -> "CROSSREF_SYNC";
            case "OPENALEX" -> "OPENALEX_SYNC";
            case "DSPACE" -> "DSPACE_SYNC";
            case "JOURNAL_METRICS" -> "SYNC_JOURNAL_METRICS";
            case "RESEARCH_OUTPUT_METRICS" -> "SYNC_RESEARCH_OUTPUT_METRICS";
            case "PERSON_METRICS" -> "SYNC_PERSON_METRICS";
            default -> {
                String normalized = normalize(entity);
                yield normalized.isEmpty() ? "SYNC" : "SYNC_" + normalized;
            }
        };
    }

    public static String eventId(String syncId) {
        return syncId == null || syncId.isBlank() ? null : EVENT_PREFIX + syncId.trim();
    }

    public static String executionIdOf(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        String trimmed = eventId.trim();
        if (trimmed.regionMatches(true, 0, EVENT_PREFIX, 0, EVENT_PREFIX.length())) {
            String rest = trimmed.substring(EVENT_PREFIX.length()).trim();
            return rest.isEmpty() ? null : rest;
        }
        return trimmed;
    }

    public static boolean isSupportedEventType(String eventType) {
        String normalized = normalize(eventType);
        return normalized.isEmpty() || "ALL".equals(normalized) || TYPE_SYNC.equals(normalized);
    }

    public static String actorKind(String startedBy) {
        return startedBy == null || startedBy.isBlank() ? ACTOR_SYSTEM : ACTOR_USER;
    }

    public static String actorLabel(String startedBy, String resolvedName) {
        if (startedBy == null || startedBy.isBlank()) {
            return null;
        }
        if (resolvedName != null && !resolvedName.isBlank()) {
            return resolvedName.trim();
        }
        return startedBy.trim();
    }

    public static String displayName(String firstName, String lastName, String email, long id) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return String.valueOf(id);
    }
}
