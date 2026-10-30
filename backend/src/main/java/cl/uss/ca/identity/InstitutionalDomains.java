package cl.uss.ca.identity;

import java.util.List;
import java.util.Locale;

/**
 * Dominios que pueden autoregistrarse o autoaprovisionar Ethics.
 * La comparación es el dominio completo, después de {@code @}, en minúsculas.
 * No usa {@code endsWith("uss.cl")}.
 */
public final class InstitutionalDomains {
    public static final List<String> ALLOWED_SELF_REGISTRATION_DOMAINS = List.of(
            "uss.cl",
            "correo.uss.cl",
            "docente.uss.cl"
    );

    public static final String SELF_REGISTRATION_DENIED =
            "El registro en Ética de Proyectos está disponible únicamente para cuentas institucionales USS. "
                    + "Los revisores externos deben ser incorporados por Administración.";

    private InstitutionalDomains() {
    }

    public static boolean isAllowedInstitutionalEmail(String email) {
        String domain = domainOf(email);
        return domain != null && ALLOWED_SELF_REGISTRATION_DOMAINS.contains(domain);
    }

    /** Dominio en minúsculas, o null si el correo no tiene una sola @ y un dominio. */
    public static String domainOf(String email) {
        String normalized = EmailAddresses.normalize(email);
        if (normalized == null) {
            return null;
        }
        int at = normalized.indexOf('@');
        if (at <= 0 || at != normalized.lastIndexOf('@') || at == normalized.length() - 1) {
            return null;
        }
        String domain = normalized.substring(at + 1);
        if (domain.indexOf(' ') >= 0 || domain.startsWith(".") || domain.endsWith(".") || !domain.contains(".")) {
            return null;
        }
        return domain.toLowerCase(Locale.ROOT);
    }
}
