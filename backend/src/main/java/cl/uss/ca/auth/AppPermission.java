package cl.uss.ca.auth;

public enum AppPermission {
    CONSULT,
    ADMINISTER,
    EXPORT,
    EXECUTE,
    CORRECT,
    SYNC,
    APPROVE;

    public static AppPermission from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AppPermission.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static boolean administerImplies(AppPermission required, AppPermission granted) {
        return required == CONSULT && granted == ADMINISTER;
    }
}
