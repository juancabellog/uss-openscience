package cl.uss.ca.service;

import cl.uss.ca.events.EventPublicLinks;
import cl.uss.ca.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Resolves the public Events SPA base used in mail links.
 * <p>
 * Root cause of localhost in production: {@code package-prod.sh} embeds the
 * developer {@code server.env} / {@code bootstrap.properties}. Liberty
 * {@code bin/server} then overwrites {@code prod.env} with
 * {@code EVENTS_PUBLIC_URL=http://localhost:4300}. Development values are
 * therefore ignored unless {@code EVENTS_ALLOW_LOCAL_MAIL_LINKS=true}.
 */
@ApplicationScoped
public class EventPublicUrlSettings {
    private static final Logger LOG = Logger.getLogger(EventPublicUrlSettings.class.getName());

    @Inject
    @ConfigProperty(name = "openscience.events.publicUrl")
    Optional<String> configuredUrl;

    @Inject
    @ConfigProperty(name = "openscience.events.allowLocalMailLinks", defaultValue = "false")
    boolean allowLocalMailLinks;

    public String resolve() {
        String preferred = firstDevelopmentOrBlank(
                System.getenv("EVENTS_PUBLIC_BASE_URL"),
                System.getenv("EVENTS_PUBLIC_URL"),
                configuredUrl == null ? null : configuredUrl.orElse(null)
        );
        String resolved = EventPublicLinks.resolveMailBase(
                allowLocalMailLinks(),
                System.getenv("EVENTS_PUBLIC_BASE_URL"),
                System.getenv("EVENTS_PUBLIC_URL"),
                configuredUrl == null ? null : configuredUrl.orElse(null)
        );
        if (!allowLocalMailLinks() && EventPublicLinks.looksDevelopment(preferred)) {
            LOG.warning("Ignoring development Events public URL from server.env/bootstrap. Using "
                    + EventPublicLinks.PRODUCTION_BASE);
        }
        return resolved;
    }

    public boolean allowLocalMailLinks() {
        if (allowLocalMailLinks) {
            return true;
        }
        String env = System.getenv("EVENTS_ALLOW_LOCAL_MAIL_LINKS");
        return env != null && "true".equalsIgnoreCase(env.trim());
    }

    public String requireMailableBase() {
        String base = resolve();
        if (base == null || base.isBlank()) {
            throw new ValidationException(
                    "EVENT_PUBLIC_URL_MISSING",
                    "Defina EVENTS_PUBLIC_URL o EVENTS_PUBLIC_BASE_URL con la URL pública de OPENSCIENCE Events"
            );
        }
        if (EventPublicLinks.looksDevelopment(base) && !allowLocalMailLinks()) {
            throw new ValidationException(
                    "EVENT_PUBLIC_URL_LOCAL",
                    "EVENTS_PUBLIC_URL no puede ser localhost en producción. Use https://apps.cienciavida.org/events"
            );
        }
        return base;
    }

    private static String firstDevelopmentOrBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
