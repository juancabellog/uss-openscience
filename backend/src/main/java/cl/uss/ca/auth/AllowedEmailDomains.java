package cl.uss.ca.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class AllowedEmailDomains {
    @Inject
    @ConfigProperty(name = "openscience.auth.allowedEmailDomains", defaultValue = "uss.cl")
    String configuredDomains;

    @Inject
    @ConfigProperty(name = "openscience.auth.registration.ussOnly", defaultValue = "true")
    boolean registrationUssOnly;

    public boolean registrationUssOnly() {
        return registrationUssOnly;
    }

    public boolean accepts(String email) {
        return accepts(email, configuredDomains);
    }

    public boolean acceptsForRegistration(String email) {
        return acceptsForRegistration(email, registrationUssOnly, configuredDomains);
    }

    public List<String> domains() {
        return parse(configuredDomains);
    }

    public static boolean acceptsForRegistration(String email, boolean ussOnly, String configured) {
        if (!EmailNormalizer.looksLikeEmail(email)) {
            return false;
        }
        return !ussOnly || accepts(email, configured);
    }

    public static boolean accepts(String email, String configured) {
        String normalized = EmailNormalizer.normalize(email);
        if (normalized == null) {
            return false;
        }
        int at = normalized.lastIndexOf('@');
        if (at <= 0 || at == normalized.length() - 1) {
            return false;
        }
        String domain = normalized.substring(at + 1);
        for (String allowed : parse(configured)) {
            if (domain.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    static List<String> parse(String configured) {
        List<String> domains = new ArrayList<>();
        if (configured == null || configured.isBlank()) {
            return domains;
        }
        for (String part : configured.split(",")) {
            String domain = part.trim().toLowerCase(Locale.ROOT);
            if (domain.startsWith("@")) {
                domain = domain.substring(1);
            }
            if (!domain.isEmpty() && !domains.contains(domain)) {
                domains.add(domain);
            }
        }
        return domains;
    }
}
