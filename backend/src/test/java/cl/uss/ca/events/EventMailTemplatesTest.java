package cl.uss.ca.events;

public final class EventMailTemplatesTest {
    private EventMailTemplatesTest() {
    }

    public static void main(String[] args) {
        EventMailTemplates.MailContent es = EventMailTemplates.verification(
                "es-CL",
                "Seminario <prueba>",
                "https://apps.cienciavida.org/events/eventos/evento-test/inscripcion?token=abc",
                30
        );
        EventMailTemplates.MailContent en = EventMailTemplates.verification(
                "en-US",
                "Seminar",
                "https://apps.cienciavida.org/events/eventos/evento-test/inscripcion?token=abc"
        );
        EventMailTemplates.MailContent fallback = EventMailTemplates.verification(
                "de",
                "Charla",
                "https://example.test/verify"
        );

        assert es.subject().equals("Verifica tu correo — OPENSCIENCE Events");
        assert es.text().contains("Seminario <prueba>");
        assert es.text().contains("Hemos recibido una solicitud");
        assert es.html().contains("Seminario &lt;prueba&gt;");
        assert !es.html().contains("Seminario <prueba>");
        assert es.html().contains("rel=\"noreferrer noopener\"");
        assert es.html().contains("30 minutos");
        assert !es.text().contains("localhost");
        assert !es.html().contains("localhost");

        assert en.subject().equals("Verify your email — OPENSCIENCE Events");
        assert en.text().contains("We received a request");
        assert en.html().contains("Verify email");

        assert fallback.subject().startsWith("Verifica tu correo");

        EventMailTemplates.MailContent confirmed = EventMailTemplates.confirmation(
                EventLanguage.localeTag("en"),
                "Ana <b>",
                "Talk",
                "EV1",
                "https://apps.cienciavida.org/events/eventos/evento-test"
        );
        assert confirmed.subject().startsWith("Registration confirmed");
        assert confirmed.html().contains("Ana &lt;b&gt;");
        assert !confirmed.html().contains("Ana <b>");

        EventMailTemplates.MailContent unsupported = EventMailTemplates.confirmation(
                "zh",
                "Ana",
                "Charla",
                "EV1",
                "https://example.test"
        );
        assert unsupported.subject().startsWith("Inscripción confirmada");

        assert EventMailTemplates.verificationSent("en").contains("If the email");
        assert EventMailTemplates.verificationSent("de").contains("Si el correo");
        System.out.println("EventMailTemplatesTest OK");
    }
}
