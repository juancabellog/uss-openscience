package cl.uss.ca.mail;

public class MailMessage {
    public String to;
    public String subject;
    public String text;
    public String html;

    public static MailMessage of(String to, String subject, String text, String html) {
        MailMessage message = new MailMessage();
        message.to = to;
        message.subject = subject;
        message.text = text;
        message.html = html;
        return message;
    }
}
