package cl.uss.ca.events;

import java.time.LocalDateTime;

public enum EventChallengeState {
    USABLE,
    EXPIRED,
    USED,
    INVALID;

    public static EventChallengeState classify(
            Long challengeEventId,
            long expectedEventId,
            LocalDateTime expiresAt,
            LocalDateTime consumedAt,
            LocalDateTime now
    ) {
        if (challengeEventId == null || challengeEventId != expectedEventId) {
            return INVALID;
        }
        if (consumedAt != null) {
            return USED;
        }
        if (now == null || expiresAt == null || !expiresAt.isAfter(now)) {
            return EXPIRED;
        }
        return USABLE;
    }

    public String errorCode() {
        return switch (this) {
            case EXPIRED -> "EVENT_TOKEN_EXPIRED";
            case USED -> "EVENT_TOKEN_USED";
            case USABLE, INVALID -> "EVENT_TOKEN_INVALID";
        };
    }

    public String errorMessage() {
        return switch (this) {
            case EXPIRED -> "El enlace expiró. Solicite uno nuevo";
            case USED -> "Este enlace ya fue utilizado. Solicite uno nuevo si necesita continuar";
            case USABLE, INVALID -> "El enlace no es válido";
        };
    }
}
