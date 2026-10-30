package cl.uss.ca.ethics.auth;

public enum AssignmentStatus {
    ACTIVE,
    COMPLETED,
    REVOKED;

    public boolean operative() {
        return this == ACTIVE;
    }

    public static AssignmentStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AssignmentStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
