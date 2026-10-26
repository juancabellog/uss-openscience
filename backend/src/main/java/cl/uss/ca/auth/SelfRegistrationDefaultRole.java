package cl.uss.ca.auth;

import cl.uss.ca.repository.AccessRoleRecord;

import java.util.Locale;

/**
 * Resolves the role assigned at self-registration by stable code, never by id.
 * Missing or unsafe roles fail closed: no silent PLATFORM_ADMIN fallback.
 */
public final class SelfRegistrationDefaultRole {
    public static final String DEFAULT_CODE = ConsultaRolePolicy.CODE;
    public static final String MISSING_CODE = "AUTH_DEFAULT_ROLE_MISSING";
    public static final String UNSAFE_CODE = "AUTH_DEFAULT_ROLE_UNSAFE";

    private SelfRegistrationDefaultRole() {
    }

    public static String code(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_CODE;
        }
        return configured.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean forbidsAutomaticAssignment(String code) {
        return "PLATFORM_ADMIN".equalsIgnoreCase(code(code));
    }

    public static boolean isAssignable(AccessRoleRecord role) {
        return role != null
                && role.active
                && role.code != null
                && !role.code.isBlank()
                && !forbidsAutomaticAssignment(role.code);
    }
}
