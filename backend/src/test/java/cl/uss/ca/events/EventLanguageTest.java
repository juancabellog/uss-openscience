package cl.uss.ca.events;

public final class EventLanguageTest {
    private EventLanguageTest() {
    }

    public static void main(String[] args) {
        assert EventLanguage.ES.equals(EventLanguage.normalize(null));
        assert EventLanguage.ES.equals(EventLanguage.normalize(""));
        assert EventLanguage.ES.equals(EventLanguage.normalize("es-CL"));
        assert EventLanguage.EN.equals(EventLanguage.normalize("en-US"));
        assert EventLanguage.EN.equals(EventLanguage.normalize("en_GB"));
        assert EventLanguage.ES.equals(EventLanguage.normalize("pt-BR"));
        assert EventLanguage.ES.equals(EventLanguage.normalize("fr"));
        assert EventLanguage.EN.equals(EventLanguage.fromRequest("en-US", "es"));
        assert EventLanguage.ES.equals(EventLanguage.fromRequest(null, "es-CL"));
        assert EventLanguage.ES.equals(EventLanguage.fromRequest("pt-BR", "fr"));
        assert EventLanguage.ES_CL.equals(EventLanguage.localeTag("es"));
        assert EventLanguage.EN_US.equals(EventLanguage.localeTag("en-US"));
        assert EventLanguage.ES_CL.equals(EventLanguage.localeTag("de"));
        assert !EventLanguage.isEnglish("es");
        assert EventLanguage.isEnglish("en");
        System.out.println("EventLanguageTest OK");
    }
}
