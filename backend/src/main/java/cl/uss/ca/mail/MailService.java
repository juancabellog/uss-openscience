package cl.uss.ca.mail;

public interface MailService {
    boolean isConfigured();

    void send(MailMessage message);
}
