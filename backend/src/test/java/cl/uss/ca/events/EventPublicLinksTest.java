package cl.uss.ca.events;

public final class EventPublicLinksTest {
    private EventPublicLinksTest() {
    }

    public static void main(String[] args) {
        String local = "http://localhost:4300/";
        String prod = "https://apps.cienciavida.org/events/";

        assertEquals(
                "http://localhost:4300/eventos/evento-test/inscripcion?token=abc",
                EventPublicLinks.verificationUrl(local, "evento-test", "abc"),
                "dev verification url"
        );
        assertEquals(
                "https://apps.cienciavida.org/events/eventos/evento-test/inscripcion?token=abc",
                EventPublicLinks.verificationUrl(prod, "evento-test", "abc"),
                "prod verification url"
        );
        assertEquals(
                "https://apps.cienciavida.org/events/eventos/evento-test",
                EventPublicLinks.eventUrl(prod, "evento-test"),
                "prod event url keeps /events/"
        );
        assertEquals(
                "https://apps.cienciavida.org/events/eventos/evento-test/inscripcion",
                EventPublicLinks.join(prod, "events", "eventos", "evento-test", "inscripcion"),
                "no doubled /events/"
        );
        assert EventPublicLinks.looksDevelopment("http://localhost:4300");
        assert EventPublicLinks.looksDevelopment("http://127.0.0.1:4300/events");
        assert !EventPublicLinks.looksDevelopment(prod);
        assert "EVENT_PUBLIC_URL_LOCAL".equals(EventPublicLinks.validate("http://localhost:4300", true));
        assert EventPublicLinks.validate(prod, true) == null;
        assert "EVENT_PUBLIC_URL_MISSING".equals(EventPublicLinks.validate(null, true));
        assert EventPublicLinks.validate("http://localhost:4300", false) == null;

        String encoded = EventPublicLinks.verificationUrl(prod, "evento-test", "ab+c=/d");
        assert encoded.contains("token=ab%2Bc%3D%2Fd") : encoded;
        assert !encoded.contains("localhost") : encoded;

        try {
            EventPublicLinks.verificationUrl(prod, "evento-test", "");
            throw new AssertionError("blank token must fail");
        } catch (IllegalArgumentException ignored) {
        }

        assertEquals(
                EventPublicLinks.eventUrl(prod, "evento-test"),
                EventPublicLinks.confirmationUrl(prod, "evento-test"),
                "confirmation url"
        );
        assertEquals(
                EventPublicLinks.eventUrl(prod, "evento-test"),
                EventPublicLinks.eventInfoUrl(prod, "evento-test"),
                "info url"
        );
        assertEquals(
                EventPublicLinks.PRODUCTION_BASE,
                EventPublicLinks.resolveMailBase(false, "http://localhost:4300", "http://127.0.0.1:4300"),
                "poisoned server.env is ignored in production"
        );
        assertEquals(
                "https://apps.cienciavida.org/events",
                EventPublicLinks.resolveMailBase(false, "http://localhost:4300/", prod),
                "explicit production wins after skipping localhost"
        );
        assertEquals(
                "http://localhost:4300",
                EventPublicLinks.resolveMailBase(true, "http://localhost:4300/"),
                "local mail links stay local only when allowed"
        );
        assertEquals(
                EventPublicLinks.PRODUCTION_BASE,
                EventPublicLinks.resolveMailBase(false),
                "missing config falls back to production Events URL"
        );
        String mail = EventPublicLinks.verificationUrl(
                EventPublicLinks.resolveMailBase(false, "http://localhost:4300"),
                "evento-test",
                "abc"
        );
        assert mail.startsWith("https://apps.cienciavida.org/events/eventos/evento-test/inscripcion") : mail;
        assert !mail.contains("localhost") : mail;
        System.out.println("EventPublicLinksTest OK");
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }
}
