package cl.uss.ca.service;

import cl.uss.ca.auth.ActivationOrigin;
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
        String url = publicBase() + "/auth/activate-account?token=" + rawToken;
        String name = displayName(user);
        String until = expiresAt == null ? "" : WHEN.format(expiresAt);
        String intro = activationIntro(origin);
        String ignore = activationIgnore(origin);
        String text = """
                Estimado/a %s:

                %s

                Para confirmar su correo y definir su contraseña, ingrese al siguiente enlace:
                %s

                Este enlace estará disponible hasta %s.

                %s

                Saludos cordiales,
                OPENSCIENCE-USS
                Universidad San Sebastián
                """.formatted(name, intro, url, until, ignore);
        String html = """
                <p>Estimado/a %s:</p>
                <p>%s</p>
                <p><a href="%s">Activar cuenta</a></p>
                <p>Este enlace estará disponible hasta %s.</p>
                <p>%s</p>
                <p>Saludos cordiales,<br>OPENSCIENCE-USS<br>Universidad San Sebastián</p>
                """.formatted(escape(name), escape(intro), escape(url), escape(until), escape(ignore));
        mailService.send(MailMessage.of(
                user.email,
                "Active su cuenta en OPENSCIENCE-USS",
                text,
                html
        ));
    }

    public static String activationIntro(ActivationOrigin origin) {
        if (origin == ActivationOrigin.SELF_REGISTRATION) {
            return "Se recibió una solicitud para crear su cuenta en OPENSCIENCE-USS.";
        }
        return "Se ha creado una cuenta para usted en la plataforma OPENSCIENCE-USS, "
                + "correspondiente al proyecto InES Ciencia Abierta de la Universidad San Sebastián.";
    }

    public static String activationIgnore(ActivationOrigin origin) {
        if (origin == ActivationOrigin.SELF_REGISTRATION) {
            return "Si usted no realizó esta solicitud, puede ignorar este mensaje.";
        }
        return "Si usted no esperaba esta invitación, puede ignorar este mensaje.";
    }

    public void sendReset(AccessUserRecord user, String rawToken, OffsetDateTime expiresAt) {
        String url = publicBase() + "/auth/reset-password?token=" + rawToken;
        String name = displayName(user);
        String until = expiresAt == null ? "" : WHEN.format(expiresAt);
        String text = """
                Estimado/a %s:

                Se recibió una solicitud para restablecer la contraseña de su cuenta OPENSCIENCE-USS.

                Enlace: %s

                El enlace expira el %s.

                Si usted no realizó esta solicitud, puede ignorar el mensaje.

                Saludos cordiales,
                OPENSCIENCE-USS
                """.formatted(name, url, until);
        String html = """
                <p>Estimado/a %s:</p>
                <p>Se recibió una solicitud para restablecer la contraseña de su cuenta.</p>
                <p><a href="%s">Restablecer contraseña</a></p>
                <p>El enlace expira el %s.</p>
                <p>Si usted no realizó esta solicitud, puede ignorar el mensaje.</p>
                <p>OPENSCIENCE-USS</p>
                """.formatted(escape(name), escape(url), escape(until));
        mailService.send(MailMessage.of(
                user.email,
                "Restablecimiento de contraseña OPENSCIENCE-USS",
                text,
                html
        ));
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

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
