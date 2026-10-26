package cl.uss.ca.events;

public final class EventMailTemplates {
    private EventMailTemplates() {
    }

    public static MailContent verification(String language, String title, String url) {
        return verification(language, title, url, null);
    }

    public static MailContent verification(String language, String title, String url, Integer ttlMinutes) {
        String eventName = title == null ? "" : title;
        String link = url == null ? "" : url;
        if (EventLanguage.isEnglish(language)) {
            String ttl = ttlLineEn(ttlMinutes);
            return new MailContent(
                    "Verify your email — OPENSCIENCE Events",
                    """
                    Hello,

                    We received a request to register for the event "%s".

                    To continue your registration, please verify your email address using the following link:

                    %s
                    %s
                    If you did not request this registration, you can ignore this message.

                    Best regards,
                    OPENSCIENCE Events
                    Universidad San Sebastián
                    """.formatted(eventName, link, ttl),
                    htmlDocument(
                            EventLanguage.EN_US,
                            "Verify your email — OPENSCIENCE Events",
                            """
                            <p>Hello,</p>
                            <p>We received a request to register for the event &laquo;%s&raquo;.</p>
                            <p>To continue your registration, please verify your email address:</p>
                            <p>%s</p>
                            <p>If the button does not work, copy this link:<br>%s</p>
                            <p>%s</p>
                            <p>If you did not request this registration, you can ignore this message.</p>
                            <p>Best regards,<br>OPENSCIENCE Events<br>Universidad San Sebastián</p>
                            """.formatted(
                                    escape(eventName),
                                    actionButton(link, "Verify email"),
                                    escape(link),
                                    escape(ttlLineEn(ttlMinutes).trim())
                            )
                    )
            );
        }
        String ttl = ttlLineEs(ttlMinutes);
        return new MailContent(
                "Verifica tu correo — OPENSCIENCE Events",
                """
                Hola:

                Hemos recibido una solicitud para inscribirte en el evento «%s».

                Para continuar con tu inscripción, verifica tu correo electrónico mediante el siguiente enlace:

                %s
                %s
                Si no solicitaste esta inscripción, puedes ignorar este mensaje.

                Saludos,
                OPENSCIENCE Events
                Universidad San Sebastián
                """.formatted(eventName, link, ttl),
                htmlDocument(
                        EventLanguage.ES_CL,
                        "Verifica tu correo — OPENSCIENCE Events",
                        """
                        <p>Hola:</p>
                        <p>Hemos recibido una solicitud para inscribirte en el evento &laquo;%s&raquo;.</p>
                        <p>Para continuar con tu inscripción, verifica tu correo electrónico:</p>
                        <p>%s</p>
                        <p>Si el botón no funciona, copia este enlace:<br>%s</p>
                        <p>%s</p>
                        <p>Si no solicitaste esta inscripción, puedes ignorar este mensaje.</p>
                        <p>Saludos,<br>OPENSCIENCE Events<br>Universidad San Sebastián</p>
                        """.formatted(
                                escape(eventName),
                                actionButton(link, "Verificar correo"),
                                escape(link),
                                escape(ttlLineEs(ttlMinutes).trim())
                        )
                )
        );
    }

    public static MailContent confirmation(String language, String name, String title, String publicCode, String url) {
        String displayName = name == null ? "" : name;
        String eventName = title == null ? "" : title;
        String code = publicCode == null ? "" : publicCode;
        String link = url == null ? "" : url;
        if (EventLanguage.isEnglish(language)) {
            return new MailContent(
                    "Registration confirmed — OPENSCIENCE Events",
                    """
                    Hello %s,

                    Your registration for the event "%s" is confirmed.

                    Registration code: %s

                    Event information:
                    %s

                    Best regards,
                    OPENSCIENCE Events
                    Universidad San Sebastián
                    """.formatted(displayName, eventName, code, link),
                    htmlDocument(
                            EventLanguage.EN_US,
                            "Registration confirmed — OPENSCIENCE Events",
                            """
                            <p>Hello %s,</p>
                            <p>Your registration for the event &laquo;%s&raquo; is confirmed.</p>
                            <p>Registration code: <strong>%s</strong></p>
                            <p>%s</p>
                            <p>If the button does not work, copy this link:<br>%s</p>
                            <p>Best regards,<br>OPENSCIENCE Events<br>Universidad San Sebastián</p>
                            """.formatted(
                                    escape(displayName),
                                    escape(eventName),
                                    escape(code),
                                    actionButton(link, "View event"),
                                    escape(link)
                            )
                    )
            );
        }
        return new MailContent(
                "Inscripción confirmada — OPENSCIENCE Events",
                """
                Hola %s:

                Tu inscripción al evento «%s» quedó confirmada.

                Código de inscripción: %s

                Información del evento:
                %s

                Saludos,
                OPENSCIENCE Events
                Universidad San Sebastián
                """.formatted(displayName, eventName, code, link),
                htmlDocument(
                        EventLanguage.ES_CL,
                        "Inscripción confirmada — OPENSCIENCE Events",
                        """
                        <p>Hola %s:</p>
                        <p>Tu inscripción al evento &laquo;%s&raquo; quedó confirmada.</p>
                        <p>Código de inscripción: <strong>%s</strong></p>
                        <p>%s</p>
                        <p>Si el botón no funciona, copia este enlace:<br>%s</p>
                        <p>Saludos,<br>OPENSCIENCE Events<br>Universidad San Sebastián</p>
                        """.formatted(
                                escape(displayName),
                                escape(eventName),
                                escape(code),
                                actionButton(link, "Ver evento"),
                                escape(link)
                        )
                )
        );
    }

    public static String verificationSent(String language) {
        return EventLanguage.isEnglish(language)
                ? "If the email is valid, you will receive a link to continue registration."
                : "Si el correo es válido, recibirá un enlace para continuar la inscripción.";
    }

    public static String registrationConfirmed(String language) {
        return EventLanguage.isEnglish(language)
                ? "Registration confirmed. We keep a snapshot of the data at the time of registration."
                : "Inscripción confirmada. Conservamos una fotografía de los datos al momento de inscribirse.";
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String ttlLineEs(Integer ttlMinutes) {
        if (ttlMinutes != null && ttlMinutes > 0) {
            return "Este enlace tiene una vigencia limitada (" + ttlMinutes + " minutos).\n";
        }
        return "Este enlace tiene una vigencia limitada.\n";
    }

    private static String ttlLineEn(Integer ttlMinutes) {
        if (ttlMinutes != null && ttlMinutes > 0) {
            return "This link is valid for a limited time (" + ttlMinutes + " minutes).\n";
        }
        return "This link is valid for a limited time.\n";
    }

    private static String actionButton(String url, String label) {
        return "<a href=\"" + escape(url) + "\" rel=\"noreferrer noopener\" style=\"display:inline-block;padding:12px 18px;background:#102b42;color:#ffffff;text-decoration:none;border-radius:6px;font-weight:600;\">"
                + escape(label)
                + "</a>";
    }

    private static String htmlDocument(String lang, String title, String body) {
        return """
                <!DOCTYPE html>
                <html lang="%s">
                <head><meta charset="utf-8"><meta name="referrer" content="no-referrer"><title>%s</title></head>
                <body style="margin:0;background:#f4f7fa;color:#102b42;font-family:Arial,Helvetica,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f7fa;padding:24px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border:1px solid #d5e0e8;border-radius:10px;">
                        <tr><td style="padding:24px;">
                          <p style="margin:0 0 16px;font-size:13px;letter-spacing:0.04em;color:#5b7384;">OPENSCIENCE Events<br>Universidad San Sebastián</p>
                          %s
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escape(lang), escape(title), body);
    }

    public record MailContent(String subject, String text, String html) {
    }
}
