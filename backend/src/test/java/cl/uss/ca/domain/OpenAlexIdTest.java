package cl.uss.ca.domain;

/**
 * Canonical OpenAlex IDs. Not executed by the agent.
 */
public final class OpenAlexIdTest {
    private OpenAlexIdTest() {
    }

    public static void main(String[] args) {
        urlAndBareAuthor();
        workAndInstitution();
        blankIsNull();
        System.out.println("OpenAlexIdTest OK");
    }

    static void urlAndBareAuthor() {
        assertEquals("A123456789", OpenAlexId.normalize("https://openalex.org/A123456789"));
        assertEquals("A123456789", OpenAlexId.normalize("a123456789"));
        assertEquals("https://openalex.org/A123456789", OpenAlexId.url("https://openalex.org/A123456789"));
    }

    static void workAndInstitution() {
        assertEquals("W123", OpenAlexId.normalize("https://openalex.org/W123"));
        assertEquals("I987", OpenAlexId.normalize("https://openalex.org/I987"));
        assertEquals("T1", OpenAlexId.normalize("https://openalex.org/T1"));
    }

    static void blankIsNull() {
        assert OpenAlexId.normalize(null) == null;
        assert OpenAlexId.normalize("  ") == null;
        assert OpenAlexId.url(" ") == null;
    }

    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
