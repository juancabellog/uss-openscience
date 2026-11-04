package cl.uss.ca.identity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Correos completos, solo para reproducir el alta institucional que no está en PURE.
 * La lista productiva queda vacía. No acepta dominios ni comodines.
 */
@ApplicationScoped
public class EthicsTestInstitutionalEmails {
    private final Set<String> emails;

    /**
     * Lo usa el proxy de Weld. La instancia real entra por el constructor con la configuración.
     */
    protected EthicsTestInstitutionalEmails() {
        this.emails = Set.of();
    }

    @Inject
    public EthicsTestInstitutionalEmails(
            @ConfigProperty(name = "ethics.testInstitutionalEmails", defaultValue = "") String raw
    ) {
        this.emails = parse(raw);
    }

    public boolean contains(String email) {
        String normalized = EmailAddresses.normalize(email);
        return normalized != null && emails.contains(normalized);
    }

    /** Dominio USS real, o un correo exacto de la lista de prueba. */
    public boolean eligible(String email) {
        return InstitutionalDomains.isAllowedInstitutionalEmail(email) || contains(email);
    }

    public static Set<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> accepted = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String normalized = EmailAddresses.normalize(part);
            if (normalized == null || !exactAddress(normalized)) {
                continue;
            }
            accepted.add(normalized);
        }
        return Collections.unmodifiableSet(accepted);
    }

    private static boolean exactAddress(String email) {
        if (email.indexOf('*') >= 0 || email.indexOf(' ') >= 0) {
            return false;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@') || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1);
        return domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".");
    }
}
