package cl.uss.ca.domain;

import java.util.Locale;

public final class OrcidExternalIdNormalizer {
    private OrcidExternalIdNormalizer() {
    }

    public static Result normalize(String type, String rawValue) {
        String kind = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (rawValue == null || rawValue.isBlank()) {
            return Result.error(kind, "EMPTY_VALUE");
        }
        try {
            String normalized = switch (kind) {
                case "doi" -> DoiNormalizer.normalize(rawValue);
                case "issn" -> normalizeIssn(rawValue);
                default -> rawValue.trim().toLowerCase(Locale.ROOT);
            };
            if (normalized == null || normalized.isBlank()) {
                return Result.error(kind, "NORMALIZE_EMPTY");
            }
            return Result.ok(kind, normalized, false);
        } catch (RuntimeException ex) {
            return Result.error(kind, "NORMALIZE_FAILED");
        }
    }

    public static boolean isIdentityType(String type) {
        String kind = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return "doi".equals(kind) || "eid".equals(kind) || "wosuid".equals(kind);
    }

    public static boolean isSelf(String relationship) {
        return relationship == null || relationship.isBlank() || "self".equalsIgnoreCase(relationship.trim());
    }

    static String normalizeIssn(String raw) {
        String digits = raw.replaceAll("[^0-9Xx]", "").toUpperCase(Locale.ROOT);
        if (digits.length() != 8) {
            String trimmed = raw.trim();
            return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
        }
        return digits.substring(0, 4) + "-" + digits.substring(4);
    }

    public static final class Result {
        public final String type;
        public final String normalizedValue;
        public final boolean transientNormalization;
        public final String error;

        private Result(String type, String normalizedValue, boolean transientNormalization, String error) {
            this.type = type;
            this.normalizedValue = normalizedValue;
            this.transientNormalization = transientNormalization;
            this.error = error;
        }

        static Result ok(String type, String value, boolean transientNormalization) {
            return new Result(type, value, transientNormalization, null);
        }

        static Result error(String type, String error) {
            return new Result(type, null, true, error);
        }
    }
}
