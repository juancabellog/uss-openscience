package cl.uss.ca.mail;

import cl.uss.ca.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.naming.InitialContext;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;

@ApplicationScoped
public class SmtpMailService implements MailService {
    private static final Logger LOG = Logger.getLogger(SmtpMailService.class.getName());

    @Inject
    @ConfigProperty(name = "mail.smtp.host")
    Optional<String> host;

    @Inject
    @ConfigProperty(name = "mail.smtp.port", defaultValue = "587")
    int port;

    @Inject
    @ConfigProperty(name = "mail.smtp.username")
    Optional<String> username;

    @Inject
    @ConfigProperty(name = "mail.smtp.password")
    Optional<String> password;

    @Inject
    @ConfigProperty(name = "mail.smtp.starttls", defaultValue = "true")
    boolean startTls;

    @Inject
    @ConfigProperty(name = "mail.from.address")
    Optional<String> fromAddress;

    @Inject
    @ConfigProperty(name = "mail.from.name", defaultValue = "OPENSCIENCE-USS")
    String fromName;

    @Override
    public boolean isConfigured() {
        return session() != null && present(fromAddress);
    }

    @Override
    public void send(MailMessage message) {
        if (!isConfigured()) {
            throw new ValidationException("MAIL_NOT_CONFIGURED", "Mail is not configured");
        }
        if (message == null || message.to == null || message.to.isBlank()) {
            throw new ValidationException("MAIL_INVALID", "Recipient is required");
        }
        try {
            MimeMessage mime = new MimeMessage(session());
            mime.setFrom(new InternetAddress(fromAddress.get().trim(), fromName, "UTF-8"));
            mime.setRecipient(Message.RecipientType.TO, new InternetAddress(message.to.trim()));
            mime.setSubject(message.subject == null ? "" : message.subject, "UTF-8");
            if (message.html != null && !message.html.isBlank()) {
                MimeMultipart mixed = new MimeMultipart("alternative");
                MimeBodyPart text = new MimeBodyPart();
                text.setText(message.text == null ? "" : message.text, "UTF-8");
                MimeBodyPart html = new MimeBodyPart();
                html.setContent(message.html, "text/html; charset=UTF-8");
                mixed.addBodyPart(text);
                mixed.addBodyPart(html);
                mime.setContent(mixed);
            } else {
                mime.setText(message.text == null ? "" : message.text, "UTF-8");
            }
            Transport.send(mime);
            LOG.info("Mail sent subject=" + safe(message.subject));
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            String secret = password == null ? "" : password.orElse("");
            LOG.warning("Mail send failed: " + ex.getClass().getSimpleName()
                    + " host=" + (host == null ? "" : host.orElse(""))
                    + " port=" + port
                    + " user=" + (username == null ? "" : username.orElse(""))
                    + " passwordLength=" + secret.length()
                    + " passwordContainsDollar=" + secret.contains("$"));
            throw new ValidationException("MAIL_SEND_FAILED", "The invitation email could not be sent");
        }
    }

    private Session session() {
        Session jndi = lookupJndi();
        if (jndi != null) {
            return jndi;
        }
        if (!present(host) || !present(username) || !present(password)) {
            return null;
        }
        Properties properties = new Properties();
        properties.put("mail.smtp.host", host.get().trim());
        properties.put("mail.smtp.port", String.valueOf(port));
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        properties.put("mail.smtp.starttls.required", String.valueOf(startTls));
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
        String user = username.get().trim();
        String secret = password.get();
        return Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, secret);
            }
        });
    }

    private static Session lookupJndi() {
        try {
            Object value = new InitialContext().lookup("mail/openscience");
            return value instanceof Session session ? session : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean present(Optional<String> value) {
        return value != null && value.isPresent() && !value.get().isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]", " ");
    }
}
