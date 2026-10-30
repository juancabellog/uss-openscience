package cl.uss.ca.identity;

/**
 * Resolución por correo del alta institucional.
 * Una cuenta existente gana sobre PURE. Un correo que no es USS no entra en este flujo.
 */
public final class InstitutionalOnboarding {
    public static final String EXISTING_USER = "EXISTING_USER";
    public static final String PURE_PERSON = "PURE_PERSON";
    public static final String NEW_INSTITUTIONAL_PERSON = "NEW_INSTITUTIONAL_PERSON";

    private InstitutionalOnboarding() {
    }

    public static String classify(boolean accountExists, boolean pureExists) {
        if (accountExists) {
            return EXISTING_USER;
        }
        if (pureExists) {
            return PURE_PERSON;
        }
        return NEW_INSTITUTIONAL_PERSON;
    }

    public static boolean profileRequired(String givenName, String familyName) {
        return givenName == null || givenName.isBlank() || familyName == null || familyName.isBlank();
    }
}
