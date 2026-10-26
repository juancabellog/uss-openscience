package cl.uss.ca.audit;

import cl.uss.ca.sync.SyncMode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Filter/order helpers for audit listings. No JDBC.
 */
public final class AuditListSupport {
    private static final Set<String> STATUSES = Set.of(
            "PENDING", "RUNNING", "CANCEL_REQUESTED", "CANCELLED",
            "COMPLETED", "COMPLETED_WITH_WARNINGS", "FAILED"
    );
    private static final Set<String> SORTS = Set.of("startedAt", "finishedAt", "status", "entity");

    private AuditListSupport() {
    }

    public static String statusFilter(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return STATUSES.contains(value) ? value : null;
    }

    public static String syncModeFilter(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            SyncMode mode = SyncMode.parse(raw);
            return mode == null ? null : mode.name();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static String entityFilter(String raw) {
        return AuditCatalog.canonicalEntity(raw);
    }

    public static List<String> sourceEntities(String source) {
        return AuditCatalog.entitiesForSource(source);
    }

    public static OffsetDateTime fromTimestamp(String raw) {
        return parseBound(raw, false);
    }

    public static OffsetDateTime toTimestamp(String raw) {
        return parseBound(raw, true);
    }

    public static String orderBy(String sort, String direction) {
        String field = sort == null || sort.isBlank() ? "startedAt" : sort.trim();
        if (!SORTS.contains(field)) {
            field = "startedAt";
        }
        boolean desc = direction == null || !"asc".equalsIgnoreCase(direction.trim());
        String column = switch (field) {
            case "finishedAt" -> "e.finished_at";
            case "status" -> "e.status";
            case "entity" -> "e.entity";
            default -> "e.started_at";
        };
        String suffix = desc ? " DESC" : " ASC";
        if ("startedAt".equals(field) || "finishedAt".equals(field)) {
            return column + suffix + ", e.id" + suffix;
        }
        return column + suffix + ", e.started_at DESC, e.id DESC";
    }

    public static int safePage(int page) {
        return Math.max(1, page);
    }

    public static int safeSize(int size) {
        return Math.min(100, Math.max(1, size));
    }

    private static OffsetDateTime parseBound(String raw, boolean endOfDay) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // date-only below
        }
        try {
            LocalDate date = LocalDate.parse(value);
            return endOfDay
                    ? date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1)
                    : date.atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
