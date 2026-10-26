package cl.uss.ca.domain;

import java.util.Locale;

/**
 * SQL fragments and match-badge mapping for the DSpace management lists. No JDBC.
 */
public final class DspaceListSupport {
    private DspaceListSupport() {
    }

    public static String matchBadge(String matchStatus) {
        if (matchStatus == null || matchStatus.isBlank()) {
            return "UNMATCHED";
        }
        return switch (matchStatus.trim().toUpperCase(Locale.ROOT)) {
            case "CONFIRMED", "SECONDARY" -> "LINKED";
            case "CANDIDATE" -> "REVIEW";
            default -> "UNMATCHED";
        };
    }

    public static String outcomeFilter(String raw) {
        if (raw == null || raw.isBlank() || "FOUND".equalsIgnoreCase(raw.trim())) {
            return "FOUND";
        }
        if ("ALL".equalsIgnoreCase(raw.trim())) {
            return "ALL";
        }
        if ("MISSING".equalsIgnoreCase(raw.trim())) {
            return "MISSING";
        }
        if ("ERROR".equalsIgnoreCase(raw.trim())) {
            return "ERROR";
        }
        return "FOUND";
    }

    public static String itemOrder(String sort, String direction) {
        boolean desc = direction == null || !"asc".equalsIgnoreCase(direction.trim());
        String field = sort == null ? "" : sort.trim();
        return switch (field) {
            case "title" -> nullsLast(blankAsNull("i.name"), desc);
            case "doi" -> nullsLast(blankAsNull("i.normalized_doi"), desc);
            case "handle" -> nullsLast(blankAsNull("i.handle"), desc);
            case "collection" -> nullsLast(blankAsNull("col.name"), desc);
            case "community" -> nullsLast(blankAsNull("com.name"), desc);
            case "pureTitle" -> nullsLast(blankAsNull("o.title"), desc);
            case "method" -> nullsLast(blankAsNull("m.match_method"), desc);
            case "access" -> nullsLast(accessRankExpr(), desc);
            case "pureAccess" -> nullsLast(pureAccessRankExpr(), desc);
            case "comparison" -> comparisonRankExpr() + (desc ? " DESC" : " ASC");
            case "file" -> fileRankExpr() + (desc ? " DESC" : " ASC");
            case "pure" -> matchRankExpr() + (desc ? " DESC" : " ASC");
            case "status" -> outcomeRankExpr() + (desc ? " DESC" : " ASC");
            default -> nullsLast("i.last_sync_at", desc);
        };
    }

    /**
     * Semantic access ASC: OPEN, METADATA_ONLY, RESTRICTED, EMBARGOED; UNKNOWN/NULL last in both directions.
     */
    static String accessRankExpr() {
        return """
                CASE LOWER(COALESCE(i.access_status, ''))
                    WHEN 'open.access' THEN 0
                    WHEN 'metadata.only' THEN 1
                    WHEN 'restricted' THEN 2
                    WHEN 'embargo' THEN 3
                    ELSE NULL
                END
                """;
    }

    /**
     * PURE badge ASC: LINKED (CONFIRMED/SECONDARY), REVIEW (CANDIDATE), UNMATCHED.
     */
    static String matchRankExpr() {
        return """
                CASE
                    WHEN m.match_status IN ('CONFIRMED', 'SECONDARY') THEN 0
                    WHEN m.match_status = 'CANDIDATE' THEN 1
                    ELSE 2
                END
                """;
    }

    /**
     * Outcome ASC: FOUND (Activo), MISSING (Inactivo), ERROR, other last.
     */
    static String outcomeRankExpr() {
        return """
                CASE i.last_outcome
                    WHEN 'FOUND' THEN 0
                    WHEN 'MISSING' THEN 1
                    WHEN 'ERROR' THEN 2
                    ELSE 3
                END
                """;
    }

    /**
     * PURE access ASC: OPEN, CLOSED; UNKNOWN/NULL last in both directions.
     */
    static String pureAccessRankExpr() {
        return """
                CASE COALESCE(pa.pure_canonical, '')
                    WHEN 'OPEN' THEN 0
                    WHEN 'CLOSED' THEN 1
                    ELSE NULL
                END
                """;
    }

    /**
     * Access comparison ASC: CONSISTENT, DIFFERENCE, NOT_EVALUABLE.
     */
    static String comparisonRankExpr() {
        return """
                CASE
                    WHEN m.match_status IN ('CONFIRMED', 'SECONDARY', 'CANDIDATE')
                     AND (%1$s) <> 'UNKNOWN'
                     AND COALESCE(pa.pure_canonical, 'UNKNOWN') <> 'UNKNOWN'
                    THEN CASE
                        WHEN %2$s = %3$s THEN 0
                        ELSE 1
                    END
                    ELSE 2
                END
                """.formatted(
                dspaceCanonicalExpr("i"),
                collapseAccessExpr("(" + dspaceCanonicalExpr("i") + ")"),
                collapseAccessExpr("COALESCE(pa.pure_canonical, 'UNKNOWN')")
        );
    }

    static String fileRankExpr() {
        return "CASE WHEN COALESCE(files.original_file_count, 0) > 0 THEN 1 ELSE 0 END";
    }

    public static String accessComparisonFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "CONSISTENT", "DIFFERENCE", "NOT_EVALUABLE" -> raw.trim().toUpperCase(Locale.ROOT);
            default -> null;
        };
    }

    static String blankAsNull(String expression) {
        return "NULLIF(TRIM(" + expression + "), '')";
    }

    static String nullsLast(String expression, boolean descending) {
        return "(" + expression + " IS NULL), " + expression + (descending ? " DESC" : " ASC");
    }

    public static String collectionOrder(String sort, String direction) {
        boolean desc = direction == null || !"asc".equalsIgnoreCase(direction.trim());
        String column = switch (sort == null ? "" : sort.trim()) {
            case "name" -> "c.name";
            case "items" -> "item_count";
            default -> "c.last_sync_at";
        };
        return column + (desc ? " DESC" : " ASC");
    }

    public static String communityOrder(String sort, String direction) {
        boolean desc = direction == null || !"asc".equalsIgnoreCase(direction.trim());
        String column = switch (sort == null ? "" : sort.trim()) {
            case "name" -> "c.name";
            case "items" -> "item_count";
            default -> "c.last_sync_at";
        };
        return column + (desc ? " DESC" : " ASC");
    }

    public static String dspaceCanonicalExpr(String alias) {
        return """
                CASE LOWER(COALESCE(%s.access_status, ''))
                    WHEN 'open.access' THEN 'OPEN'
                    WHEN 'metadata.only' THEN 'METADATA_ONLY'
                    WHEN 'restricted' THEN 'RESTRICTED'
                    WHEN 'embargo' THEN 'EMBARGOED'
                    ELSE 'UNKNOWN'
                END
                """.formatted(alias);
    }

    public static String pureAccessConcatExpr(String alias) {
        return "LOWER(CONCAT(IFNULL(" + alias + ".access_type_es,''), ' ', IFNULL("
                + alias + ".access_type_en,''), ' ', IFNULL(" + alias + ".access_type_uri,'')))";
    }

    public static String pureCanonicalAggregate(String concatExpr) {
        return """
                CASE
                    WHEN MAX(CASE
                        WHEN %1$s LIKE '%%open%%' OR %1$s LIKE '%%abiert%%'
                         THEN CASE
                             WHEN %1$s LIKE '%%embargo%%' OR %1$s LIKE '%%closed%%' OR %1$s LIKE '%%cerrad%%'
                                 THEN 0 ELSE 1 END
                        ELSE 0 END) = 1 THEN 'OPEN'
                    WHEN MAX(CASE
                        WHEN %1$s LIKE '%%embargo%%' OR %1$s LIKE '%%closed%%' OR %1$s LIKE '%%cerrad%%'
                            THEN 1 ELSE 0 END) = 1 THEN 'CLOSED'
                    ELSE 'UNKNOWN'
                END
                """.formatted(concatExpr);
    }

    public static String collapseAccessExpr(String canonicalExpr) {
        return "CASE WHEN " + canonicalExpr + " IN ('RESTRICTED','EMBARGOED','CLOSED') THEN 'CLOSED' ELSE "
                + canonicalExpr + " END";
    }

    public static String hasFileFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("yes".equals(value) || "true".equals(value) || "1".equals(value)) {
            return "YES";
        }
        if ("no".equals(value) || "false".equals(value) || "0".equals(value)) {
            return "NO";
        }
        return null;
    }
}
