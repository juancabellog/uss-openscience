package cl.uss.ca.auth;

public final class AuthorizationPolicy {
    private AuthorizationPolicy() {
    }

    public static boolean isPublic(String method, String path) {
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        if (path == null) {
            return false;
        }
        if (path.equals("/health") || path.startsWith("/health/")) {
            return true;
        }
        if (isPublicEventsPath(method, path)) {
            return true;
        }
        return path.equals("/auth/login")
                || path.equals("/auth/register")
                || path.equals("/auth/ethics-start")
                || path.equals("/auth/config")
                || path.equals("/auth/forgot-password")
                || path.equals("/auth/reset-password")
                || path.equals("/auth/activate")
                || path.equals("/auth/resend-activation");
    }

    public static Requirement requirement(String method, String path) {
        return requirement(method, path, null);
    }

    public static Requirement requirement(String method, String path, String syncEntity) {
        if (path == null) {
            return Requirement.denied();
        }
        if (path.equals("/auth/me") || path.equals("/auth/logout") || path.startsWith("/system/")
                || path.equals("/ethics") || path.startsWith("/ethics/")) {
            return Requirement.authenticated();
        }
        if (path.startsWith("/sync/")) {
            return syncRequirement(method, path, syncEntity);
        }
        AppModule module = moduleOf(path);
        if (module == null) {
            return Requirement.denied();
        }
        boolean write = isWrite(method);
        return Requirement.of(module, write ? AppPermission.ADMINISTER : AppPermission.CONSULT);
    }

    public static AppModule moduleOf(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("/dashboard")) {
            return AppModule.DASHBOARD;
        }
        if (path.startsWith("/people") || path.startsWith("/researchers")) {
            return AppModule.PEOPLE;
        }
        if (path.startsWith("/research-outputs") || path.startsWith("/research-output")) {
            return AppModule.RESEARCH_OUTPUTS;
        }
        if (path.startsWith("/journals")) {
            return AppModule.JOURNALS;
        }
        if (path.startsWith("/projects") || path.startsWith("/project")) {
            return AppModule.PROJECTS;
        }
        if (path.startsWith("/organizations") || path.startsWith("/organization")) {
            return AppModule.ORGANIZATIONS;
        }
        if (path.startsWith("/directory")) {
            return AppModule.DIRECTORY;
        }
        if (path.startsWith("/events")) {
            return AppModule.EVENTS;
        }
        if (path.startsWith("/data-quality")) {
            return AppModule.DATA_QUALITY;
        }
        if (path.startsWith("/indicators")) {
            return AppModule.INDICATORS;
        }
        if (path.startsWith("/integrations") || path.startsWith("/orcid") || path.startsWith("/unpaywall")
                || path.startsWith("/crossref") || path.startsWith("/openalex") || path.startsWith("/dspace")) {
            return AppModule.INTEGRATIONS;
        }
        if (path.startsWith("/audit")) {
            return AppModule.AUDIT;
        }
        if (path.startsWith("/admin")) {
            return AppModule.ADMINISTRATION;
        }
        return null;
    }

    public static AppModule moduleOfSyncEntity(String entity) {
        if (entity == null || entity.isBlank()) {
            return null;
        }
        String value = entity.trim().toUpperCase();
        return switch (value) {
            case "PEOPLE", "PERSON_METRICS" -> AppModule.PEOPLE;
            case "RESEARCH_OUTPUT", "RESEARCH_OUTPUTS", "RESEARCH_OUTPUT_METRICS" -> AppModule.RESEARCH_OUTPUTS;
            case "JOURNAL", "JOURNALS", "JOURNAL_METRICS" -> AppModule.JOURNALS;
            case "PROJECT", "PROJECTS" -> AppModule.PROJECTS;
            case "ORGANIZATION", "ORGANIZATIONS" -> AppModule.ORGANIZATIONS;
            case "ORCID", "ORCID_WORKS", "UNPAYWALL", "CROSSREF", "OPENALEX", "DSPACE" -> AppModule.INTEGRATIONS;
            default -> null;
        };
    }

    public static AppModule moduleOfSyncPath(String path, String entity) {
        if (path == null) {
            return moduleOfSyncEntity(entity);
        }
        if (path.contains("/people")) {
            return AppModule.PEOPLE;
        }
        if (path.contains("/research-output")) {
            return AppModule.RESEARCH_OUTPUTS;
        }
        if (path.contains("/person-metrics") || path.contains("/people-metrics")) {
            return AppModule.PEOPLE;
        }
        if (path.contains("/research-output-metrics")) {
            return AppModule.RESEARCH_OUTPUTS;
        }
        if (path.contains("/journal-metrics")) {
            return AppModule.JOURNALS;
        }
        if (path.contains("/journals") || path.contains("/journal")) {
            return AppModule.JOURNALS;
        }
        if (path.contains("/projects") || path.contains("/project")) {
            return AppModule.PROJECTS;
        }
        if (path.contains("/organizations") || path.contains("/organization")) {
            return AppModule.ORGANIZATIONS;
        }
        if (path.contains("/orcid") || path.contains("/unpaywall") || path.contains("/crossref")
                || path.contains("/openalex") || path.contains("/dspace")) {
            return AppModule.INTEGRATIONS;
        }
        return moduleOfSyncEntity(entity);
    }

    private static Requirement syncRequirement(String method, String path, String entity) {
        if (isCancelPath(path)) {
            return Requirement.authenticated();
        }
        AppModule module = moduleOfSyncPath(path, entity);
        if (isWrite(method)) {
            return Requirement.of(module == null ? AppModule.INTEGRATIONS : module, AppPermission.ADMINISTER);
        }
        if (module != null) {
            return Requirement.of(module, AppPermission.CONSULT);
        }
        return Requirement.authenticated();
    }

    private static boolean isPublicEventsPath(String method, String path) {
        if (path == null || path.startsWith("/events/admin")) {
            return false;
        }
        if (path.equals("/events") || path.equals("/events/health")) {
            return isSafeMethod(method);
        }
        if (path.matches("^/events/[a-z0-9][a-z0-9-]*/register/request$")
                || path.matches("^/events/[a-z0-9][a-z0-9-]*/register/confirm$")
                || path.matches("^/events/[a-z0-9][a-z0-9-]*/register/verify$")) {
            return "POST".equalsIgnoreCase(method) || (path.endsWith("/register/verify") && isSafeMethod(method));
        }
        if (path.matches("^/events/[a-z0-9][a-z0-9-]*/register/profile$")) {
            return isSafeMethod(method);
        }
        return isSafeMethod(method) && path.matches("^/events/[a-z0-9][a-z0-9-]*$");
    }

    private static boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private static boolean isWrite(String method) {
        return !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
    }

    private static boolean isCancelPath(String path) {
        return path != null && path.matches("^/sync/[^/]+/cancel$");
    }

    public static final class Requirement {
        public final boolean authenticatedOnly;
        public final boolean denied;
        public final AppModule module;
        public final AppPermission permission;

        private Requirement(boolean authenticatedOnly, boolean denied, AppModule module, AppPermission permission) {
            this.authenticatedOnly = authenticatedOnly;
            this.denied = denied;
            this.module = module;
            this.permission = permission;
        }

        public static Requirement authenticated() {
            return new Requirement(true, false, null, null);
        }

        public static Requirement denied() {
            return new Requirement(false, true, null, null);
        }

        public static Requirement of(AppModule module, AppPermission permission) {
            return new Requirement(false, false, module, permission);
        }
    }
}
