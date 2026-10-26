package cl.uss.ca.auth;

import java.time.OffsetDateTime;

public enum TokenPreviewStatus {
    VALID,
    INVALID,
    EXPIRED,
    USED,
    ALREADY_ACTIVE;

    public static TokenPreviewStatus classify(
            OffsetDateTime usedAt,
            OffsetDateTime expiresAt,
            OffsetDateTime now,
            String userStatus
    ) {
        boolean active = UserStatus.ACTIVE.name().equals(userStatus);
        if (active) {
            return ALREADY_ACTIVE;
        }
        if (usedAt != null) {
            return USED;
        }
        if (expiresAt == null || now == null || expiresAt.isBefore(now)) {
            return EXPIRED;
        }
        return VALID;
    }
}
