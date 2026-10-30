package cl.uss.ca.service;

import cl.uss.ca.auth.ActivationOrigin;
import cl.uss.ca.events.EventLanguage;
import cl.uss.ca.mail.AccountMailCatalog;
import cl.uss.ca.mail.MailLocale;
import cl.uss.ca.mail.MailMessage;
import cl.uss.ca.mail.MailService;
import cl.uss.ca.repository.AccessUserRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class AccountMailer {
    private static final Logger LOG = Logger.getLogger(AccountMailer.class.getName());
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");

    @Inject
    MailService mailService;

    @Inject
    @ConfigProperty(name = "openscience.app.publicUrl")
    Optional<String> publicUrl;

    public boolean configured() {
        return mailService.isConfigured() && publicBase() != null;
    }

    public void sendActivation(AccessUserRecord user, String rawToken, OffsetDateTime expiresAt) {
        sendActivation(user, rawToken, expiresAt, ActivationOrigin.ADMIN_INVITE);
    }

    public void sendActivation(
            AccessUserRecord user,
            String rawToken,
            OffsetDateTime expiresAt,
            ActivationOrigin origin
    ) {
        String language = MailLocale.resolve(user == null ? null : user.preferredLanguage, null);
        String url = publicBase() + "/auth/activate-account?token=" + rawToken + "&lang=" + language;
        String until = expiresAt == null ? "" : WHEN.format(expiresAt);
        AccountMailCatalog.MailContent content = AccountMailCatalog.activation(
                language,
                origin,
                displayName(user),
                url,
                until
        );
        mailService.send(MailMessage.of(user.email, content.subject(), content.text(), content.html()));
    }

    public void sendEthicsWelcome(String email, String name) {
        sendEthicsWelcome(email, name, null);
    }

    public void sendEthicsWelcome(String email, String name, String preferredLanguage) {
        String language = MailLocale.resolve(preferredLanguage, null);
        AccountMailCatalog.MailContent content = AccountMailCatalog.welcome(language, name == null ? "" : name.trim());
        mailService.send(MailMessage.of(email, content.subject(), content.text(), content.html()));
    }

    public static String activationIntro(ActivationOrigin origin) {
        return AccountMailCatalog.activationIntro(EventLanguage.ES, origin);
    }

    public static String activationIgnore(ActivationOrigin origin) {
        return AccountMailCatalog.activationIgnore(EventLanguage.ES, origin);
    }

    public void sendReset(AccessUserRecord user, String rawToken, OffsetDateTime expiresAt) {
        String language = MailLocale.resolve(user == null ? null : user.preferredLanguage, null);
        String url = publicBase() + "/auth/reset-password?token=" + rawToken + "&lang=" + language;
        String until = expiresAt == null ? "" : WHEN.format(expiresAt);
        AccountMailCatalog.MailContent content = AccountMailCatalog.reset(language, displayName(user), url, until);
        mailService.send(MailMessage.of(user.email, content.subject(), content.text(), content.html()));
    }

    private String publicBase() {
        String resolved = PublicAppUrl.resolve(
                System.getenv("APP_PUBLIC_URL"),
                publicUrl == null ? null : publicUrl.orElse(null)
        );
        if (PublicAppUrl.looksLocal(resolved)) {
            LOG.warning("Activation/reset links will use a local URL ("
                    + resolved
                    + "). Set APP_PUBLIC_URL in prod.env, e.g. https://apps.cienciavida.org/openscience");
        }
        return resolved;
    }

    private static String displayName(AccessUserRecord user) {
        if (user == null) {
            return "";
        }
        String name = ((user.firstName == null ? "" : user.firstName) + " " + (user.lastName == null ? "" : user.lastName)).trim();
        return name.isEmpty() ? user.email : name;
    }

}
