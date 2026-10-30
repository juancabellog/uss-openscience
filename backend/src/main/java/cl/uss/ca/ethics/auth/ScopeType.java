package cl.uss.ca.ethics.auth;

public enum ScopeType {
    GLOBAL,
    COMMITTEE,
    PROJECT,
    PROCESS;

    public static ScopeType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ScopeType.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
