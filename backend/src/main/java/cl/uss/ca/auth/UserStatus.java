package cl.uss.ca.auth;

public enum UserStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    DISABLED,
    LOCKED;

    public static UserStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
