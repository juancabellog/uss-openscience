package cl.uss.ca.auth;

public enum AppModule {
    DASHBOARD,
    PEOPLE,
    RESEARCH_OUTPUTS,
    JOURNALS,
    PROJECTS,
    ORGANIZATIONS,
    DIRECTORY,
    EVENTS,
    DATA_QUALITY,
    INDICATORS,
    INTEGRATIONS,
    AUDIT,
    ADMINISTRATION;

    public static AppModule from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AppModule.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
