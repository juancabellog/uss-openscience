package cl.uss.ca.mail;

import cl.uss.ca.events.EventLanguage;

/**
 * Idioma de un correo saliente.
 * Gana la preferencia guardada del destinatario. Si todavía no existe,
 * se usa el locale del flujo que inició la persona. Si no hay ninguno, español.
 * El dominio del correo y el idioma de otra sesión no participan.
 */
public final class MailLocale {
    private MailLocale() {
    }

    public static String resolve(String preferredLanguage, String flowLocale) {
        if (present(preferredLanguage)) {
            return EventLanguage.normalize(preferredLanguage);
        }
        if (present(flowLocale)) {
            return EventLanguage.normalize(flowLocale);
        }
        return EventLanguage.ES;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
