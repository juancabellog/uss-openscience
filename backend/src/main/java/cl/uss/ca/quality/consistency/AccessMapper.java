package cl.uss.ca.quality.consistency;

import java.util.Locale;

public final class AccessMapper {
    private AccessMapper() {
    }

    public static AccessSemantic fromPure(String accessType) {
        if (accessType == null || accessType.isBlank()) {
            return AccessSemantic.UNKNOWN;
        }
        String value = accessType.toLowerCase(Locale.ROOT);
        if (value.contains("embargo") || value.contains("closed") || value.contains("cerrad")) {
            return AccessSemantic.CLOSED;
        }
        if (value.contains("open") || value.contains("abiert")) {
            return AccessSemantic.OPEN;
        }
        return AccessSemantic.UNKNOWN;
    }

    public static AccessSemantic fromOa(Boolean isOa, String oaStatus) {
        if (isOa != null) {
            return isOa ? AccessSemantic.OPEN : AccessSemantic.CLOSED;
        }
        if (oaStatus == null || oaStatus.isBlank()) {
            return AccessSemantic.UNKNOWN;
        }
        String status = oaStatus.trim().toLowerCase(Locale.ROOT);
        if ("closed".equals(status)) {
            return AccessSemantic.CLOSED;
        }
        if ("gold".equals(status) || "green".equals(status) || "hybrid".equals(status)
                || "bronze".equals(status) || "open".equals(status)) {
            return AccessSemantic.OPEN;
        }
        return AccessSemantic.UNKNOWN;
    }

    /**
     * Institutional repository access. {@code metadata.only} is not full-text OA.
     */
    public static AccessSemantic fromDspace(String accessStatus, String fulltextAvailability) {
        if (fulltextAvailability != null) {
            String availability = fulltextAvailability.trim().toUpperCase(Locale.ROOT);
            if ("FULLTEXT_PUBLIC".equals(availability)) {
                return AccessSemantic.OPEN;
            }
            if ("RESTRICTED".equals(availability) || "EMBARGOED".equals(availability)) {
                return AccessSemantic.CLOSED;
            }
            if ("METADATA_ONLY".equals(availability)) {
                return AccessSemantic.UNKNOWN;
            }
        }
        if (accessStatus == null || accessStatus.isBlank()) {
            return AccessSemantic.UNKNOWN;
        }
        String status = accessStatus.trim().toLowerCase(Locale.ROOT);
        if ("open.access".equals(status)) {
            return AccessSemantic.UNKNOWN;
        }
        if ("restricted".equals(status) || "embargo".equals(status)) {
            return AccessSemantic.CLOSED;
        }
        return AccessSemantic.UNKNOWN;
    }
}
