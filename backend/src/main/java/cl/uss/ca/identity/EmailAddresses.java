package cl.uss.ca.identity;

import java.util.Locale;

/** El correo normalizado sirve para buscar. No es clave primaria y no pisa el texto original. */
public final class EmailAddresses {
    private EmailAddresses() {
    }

    public static String presentation(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String normalize(String email) {
        String presented = presentation(email);
        return presented == null ? null : presented.toLowerCase(Locale.ROOT);
    }
}
