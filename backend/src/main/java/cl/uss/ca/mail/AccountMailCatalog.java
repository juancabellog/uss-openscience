package cl.uss.ca.mail;

import cl.uss.ca.auth.ActivationOrigin;
import cl.uss.ca.events.EventLanguage;

import java.util.Map;

/**
 * Textos de correo de cuenta y de Ética. La estructura del mensaje es una sola;
 * el idioma solo elige las cadenas.
 */
public final class AccountMailCatalog {
    private static final Map<String, String> ES = Map.ofEntries(
            Map.entry("activation.subject", "Active su cuenta en OPENSCIENCE-USS"),
            Map.entry("activation.subject.ethics", "Active su acceso a Ética de Proyectos"),
            Map.entry("activation.greeting", "Estimado/a %s:"),
            Map.entry("activation.confirm", "Para confirmar su correo y definir su contraseña, ingrese al siguiente enlace:"),
            Map.entry("activation.action", "Activar cuenta"),
            Map.entry("activation.expires", "Este enlace estará disponible hasta %s."),
            Map.entry("activation.closing", "Saludos cordiales,\nOPENSCIENCE-USS\nUniversidad San Sebastián"),
            Map.entry("activation.intro.ethics", "Se inició su acceso a la Plataforma de Ética de Proyectos. Use el enlace para definir su contraseña. Este mensaje no contiene una contraseña."),
            Map.entry("activation.intro.self", "Se recibió una solicitud para crear su cuenta en OPENSCIENCE-USS."),
            Map.entry("activation.intro.admin", "Se ha creado una cuenta para usted en la plataforma OPENSCIENCE-USS, correspondiente al proyecto InES Ciencia Abierta de la Universidad San Sebastián."),
            Map.entry("activation.ignore.self", "Si usted no realizó esta solicitud, puede ignorar este mensaje."),
            Map.entry("activation.ignore.other", "Si usted no esperaba esta invitación, puede ignorar este mensaje."),
            Map.entry("welcome.subject", "Acceso habilitado a Ética de Proyectos"),
            Map.entry("welcome.greeting", "Estimado/a %s:"),
            Map.entry("welcome.body", "Se ha habilitado tu acceso a la Plataforma de Ética de Proyectos. Puedes ingresar utilizando las credenciales de tu cuenta actual."),
            Map.entry("welcome.closing", "Saludos cordiales,\nUniversidad San Sebastián"),
            Map.entry("reset.subject", "Restablecimiento de contraseña OPENSCIENCE-USS"),
            Map.entry("reset.greeting", "Estimado/a %s:"),
            Map.entry("reset.body", "Se recibió una solicitud para restablecer la contraseña de su cuenta OPENSCIENCE-USS."),
            Map.entry("reset.action", "Restablecer contraseña"),
            Map.entry("reset.expires", "El enlace expira el %s."),
            Map.entry("reset.ignore", "Si usted no realizó esta solicitud, puede ignorar el mensaje."),
            Map.entry("reset.closing", "Saludos cordiales,\nOPENSCIENCE-USS")
    );

    private static final Map<String, String> EN = Map.ofEntries(
            Map.entry("activation.subject", "Activate your OPENSCIENCE-USS account"),
            Map.entry("activation.subject.ethics", "Activate your Project Ethics access"),
            Map.entry("activation.greeting", "Dear %s,"),
            Map.entry("activation.confirm", "To confirm your email and set your password, open the following link:"),
            Map.entry("activation.action", "Activate account"),
            Map.entry("activation.expires", "This link is available until %s."),
            Map.entry("activation.closing", "Kind regards,\nOPENSCIENCE-USS\nUniversidad San Sebastián"),
            Map.entry("activation.intro.ethics", "Your access to the Project Ethics platform has been started. Use the link to set your password. This message does not contain a password."),
            Map.entry("activation.intro.self", "We received a request to create your OPENSCIENCE-USS account."),
            Map.entry("activation.intro.admin", "An OPENSCIENCE-USS account has been created for you, for the InES Open Science project at Universidad San Sebastián."),
            Map.entry("activation.ignore.self", "If you did not make this request, you can ignore this message."),
            Map.entry("activation.ignore.other", "If you were not expecting this invitation, you can ignore this message."),
            Map.entry("welcome.subject", "Access enabled for Project Ethics"),
            Map.entry("welcome.greeting", "Dear %s,"),
            Map.entry("welcome.body", "Your access to the Project Ethics platform has been enabled. You can sign in using your current account credentials."),
            Map.entry("welcome.closing", "Kind regards,\nUniversidad San Sebastián"),
            Map.entry("reset.subject", "Reset your OPENSCIENCE-USS password"),
            Map.entry("reset.greeting", "Dear %s,"),
            Map.entry("reset.body", "We received a request to reset the password for your OPENSCIENCE-USS account."),
            Map.entry("reset.action", "Reset password"),
            Map.entry("reset.expires", "The link expires on %s."),
            Map.entry("reset.ignore", "If you did not make this request, you can ignore this message."),
            Map.entry("reset.closing", "Kind regards,\nOPENSCIENCE-USS")
    );

    private AccountMailCatalog() {
    }

    public static String activationIntro(String language, ActivationOrigin origin) {
        return line(language, introKey(origin));
    }

    public static String activationIgnore(String language, ActivationOrigin origin) {
        String key = origin == ActivationOrigin.SELF_REGISTRATION ? "activation.ignore.self" : "activation.ignore.other";
        return line(language, key);
    }

    public static MailContent activation(String language, ActivationOrigin origin, String name, String url, String until) {
        String lang = MailLocale.resolve(language, null);
        ActivationOrigin resolved = origin == null ? ActivationOrigin.ADMIN_INVITE : origin;
        String subjectKey = resolved == ActivationOrigin.ETHICS_INVITE ? "activation.subject.ethics" : "activation.subject";
        String greeting = line(lang, "activation.greeting").formatted(blank(name));
        String intro = activationIntro(lang, resolved);
        String confirm = line(lang, "activation.confirm");
        String action = line(lang, "activation.action");
        String expires = line(lang, "activation.expires").formatted(blank(until));
        String ignore = activationIgnore(lang, resolved);
        String closing = line(lang, "activation.closing");
        String text = """
                %s

                %s

                %s
                %s

                %s

                %s

                %s
                """.formatted(greeting, intro, confirm, blank(url), expires, ignore, closing);
        String html = """
                <html lang="%s"><body>
                <p>%s</p>
                <p>%s</p>
                <p>%s</p>
                <p><a href="%s">%s</a></p>
                <p>%s</p>
                <p>%s</p>
                <p>%s</p>
                </body></html>
                """.formatted(
                "en".equals(lang) ? EventLanguage.EN_US : EventLanguage.ES_CL,
                escape(greeting),
                escape(intro),
                escape(confirm),
                escape(url),
                escape(action),
                escape(expires),
                escape(ignore),
                escape(closing).replace("\n", "<br>")
        );
        return new MailContent(line(lang, subjectKey), text, html);
    }

    public static MailContent welcome(String language, String name) {
        String lang = MailLocale.resolve(language, null);
        String greeting = line(lang, "welcome.greeting").formatted(blank(name));
        String body = line(lang, "welcome.body");
        String closing = line(lang, "welcome.closing");
        String text = """
                %s

                %s

                %s
                """.formatted(greeting, body, closing);
        String html = """
                <html lang="%s"><body>
                <p>%s</p>
                <p>%s</p>
                <p>%s</p>
                </body></html>
                """.formatted(
                "en".equals(lang) ? EventLanguage.EN_US : EventLanguage.ES_CL,
                escape(greeting),
                escape(body),
                escape(closing).replace("\n", "<br>")
        );
        return new MailContent(line(lang, "welcome.subject"), text, html);
    }

    public static MailContent reset(String language, String name, String url, String until) {
        String lang = MailLocale.resolve(language, null);
        String greeting = line(lang, "reset.greeting").formatted(blank(name));
        String body = line(lang, "reset.body");
        String action = line(lang, "reset.action");
        String expires = line(lang, "reset.expires").formatted(blank(until));
        String ignore = line(lang, "reset.ignore");
        String closing = line(lang, "reset.closing");
        String text = """
                %s

                %s

                %s
                %s

                %s

                %s
                """.formatted(greeting, body, action, blank(url), expires, ignore, closing);
        String html = """
                <html lang="%s"><body>
                <p>%s</p>
                <p>%s</p>
                <p><a href="%s">%s</a></p>
                <p>%s</p>
                <p>%s</p>
                <p>%s</p>
                </body></html>
                """.formatted(
                "en".equals(lang) ? EventLanguage.EN_US : EventLanguage.ES_CL,
                escape(greeting),
                escape(body),
                escape(url),
                escape(action),
                escape(expires),
                escape(ignore),
                escape(closing).replace("\n", "<br>")
        );
        return new MailContent(line(lang, "reset.subject"), text, html);
    }

    private static String introKey(ActivationOrigin origin) {
        if (origin == ActivationOrigin.ETHICS_INVITE) {
            return "activation.intro.ethics";
        }
        if (origin == ActivationOrigin.SELF_REGISTRATION) {
            return "activation.intro.self";
        }
        return "activation.intro.admin";
    }

    private static String line(String language, String key) {
        String lang = MailLocale.resolve(language, null);
        String value = (EventLanguage.EN.equals(lang) ? EN : ES).get(key);
        if (value == null) {
            value = ES.get(key);
        }
        return value == null ? "" : value;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public record MailContent(String subject, String text, String html) {
    }
}
