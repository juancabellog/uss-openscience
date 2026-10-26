package cl.uss.ca.auth;

import java.time.OffsetDateTime;

public final class TokenPolicy {
    private TokenPolicy() {
    }

    public static boolean isUsable(OffsetDateTime usedAt, OffsetDateTime expiresAt, OffsetDateTime now) {
        return usedAt == null && expiresAt != null && now != null && !expiresAt.isBefore(now);
    }
}
