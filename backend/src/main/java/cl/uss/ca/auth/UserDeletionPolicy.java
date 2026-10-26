package cl.uss.ca.auth;

import java.util.Set;

/**
 * Centralizes hard vs soft user deletion. The frontend cannot force a mode.
 */
public final class UserDeletionPolicy {
    public static final String MODE_PHYSICAL = "PHYSICAL";
    public static final String MODE_SOFT = "SOFT";
    public static final String EVENT_DELETED = "USER_DELETED";
    public static final String CANNOT_DELETE_SELF = "CANNOT_DELETE_SELF";
    public static final String ALREADY_DELETED = "USER_ALREADY_DELETED";
    public static final String DISPLAY_STATUS = "DELETED";

    static final Set<String> LIFECYCLE_EVENTS = Set.of(
            "SELF_REGISTRATION_REQUESTED",
            "USER_CREATED",
            "ROLE_ASSIGNED",
            "ACTIVATION_EMAIL_SENT",
            "ACTIVATION_EMAIL_FAILED",
            "USER_ACTIVATED"
    );

    private UserDeletionPolicy() {
    }

    public static boolean isDeleted(java.time.OffsetDateTime deletedAt) {
        return deletedAt != null;
    }

    public static boolean isLifecycleEvent(String eventType, Long actorId) {
        if (eventType == null || !LIFECYCLE_EVENTS.contains(eventType)) {
            return false;
        }
        if ("ROLE_ASSIGNED".equals(eventType)) {
            return actorId == null;
        }
        return true;
    }

    public static boolean actorEventIsHistorical(String eventType, Long actorId) {
        if (actorId == null) {
            return false;
        }
        return !isLifecycleEvent(eventType, actorId);
    }

    public static String mode(boolean historicalActivity) {
        return historicalActivity ? MODE_SOFT : MODE_PHYSICAL;
    }

    public static String eventDetail(String mode, String emailSnapshot, String reason) {
        String email = emailSnapshot == null ? "" : emailSnapshot;
        String why = reason == null || reason.isBlank() ? mode : reason;
        String detail = mode + "|" + email + "|" + why;
        return detail.length() <= 500 ? detail : detail.substring(0, 500);
    }
}
