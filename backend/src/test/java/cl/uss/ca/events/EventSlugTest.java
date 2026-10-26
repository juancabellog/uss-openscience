package cl.uss.ca.events;

public final class EventSlugTest {
    private EventSlugTest() {
    }

    public static void main(String[] args) {
        assert "jornada-de-investigacion".equals(EventSlug.fromTitle("Jornada de Investigación"));
        assert EventSlug.isValid("jornada-investigacion-2026");
        assert !EventSlug.isValid("Admin");
        assert !EventSlug.isValid("/events");
        assert "evento-2".equals(EventSlug.withSuffix("evento", 2));
        System.out.println("EventSlugTest OK");
    }
}
