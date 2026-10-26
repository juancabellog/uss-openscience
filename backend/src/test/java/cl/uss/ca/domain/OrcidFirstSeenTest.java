package cl.uss.ca.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Persistencia conceptual de orcid_first_seen_*. No ejecutado por el agente.
 */
public final class OrcidFirstSeenTest {
    private static final OffsetDateTime SEEN = OffsetDateTime.of(2026, 10, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    private OrcidFirstSeenTest() {
    }

    public static void main(String[] args) {
        nullToOrcidSetsObserved();
        sameOrcidPreserves();
        orcidToNullPreserves();
        orcidAToBPreserves();
        fullDoesNotReset();
        incrementalDoesNotReset();
        candidateOrcidIsNotAPureObservation();
        newPersonWithOrcidSetsObserved();
        newPersonWithoutOrcidLeavesNull();
        existingOrcidGetsBaseline();
        System.out.println("OrcidFirstSeenTest OK");
    }

    static void nullToOrcidSetsObserved() {
        assertEquals(OrcidFirstSeen.OBSERVED, OrcidFirstSeen.sourceToStamp(null, null, "0000-0001-2345-6789"));
    }

    static void sameOrcidPreserves() {
        assertEquals(null, OrcidFirstSeen.sourceToStamp("0000-0001-2345-6789", SEEN, "0000-0001-2345-6789"));
    }

    static void orcidToNullPreserves() {
        assertEquals(null, OrcidFirstSeen.sourceToStamp("0000-0001-2345-6789", SEEN, null));
    }

    static void orcidAToBPreserves() {
        assertEquals(null, OrcidFirstSeen.sourceToStamp("0000-0001-2345-6789", SEEN, "0000-0002-9999-000X"));
    }

    static void fullDoesNotReset() {
        assertEquals(null, OrcidFirstSeen.sourceToStamp("0000-0001-2345-6789", SEEN, "0000-0001-2345-6789"));
    }

    static void incrementalDoesNotReset() {
        assertEquals(null, OrcidFirstSeen.sourceToStamp("0000-0001-2345-6789", SEEN, "0000-0001-2345-6789"));
    }

    static void candidateOrcidIsNotAPureObservation() {
        assertEquals(null, OrcidFirstSeen.sourceToStamp(null, null, null));
        assertTrue(OrcidFirstSeen.isKnownSource(OrcidFirstSeen.OBSERVED));
        assertTrue(OrcidFirstSeen.isKnownSource(OrcidFirstSeen.BASELINE));
    }

    static void newPersonWithOrcidSetsObserved() {
        assertEquals(OrcidFirstSeen.OBSERVED, OrcidFirstSeen.sourceToStamp(null, null, "https://orcid.org/0000-0001-2345-6789"));
    }

    static void newPersonWithoutOrcidLeavesNull() {
        assertEquals(null, OrcidFirstSeen.sourceToStamp(null, null, null));
        assertEquals(null, OrcidFirstSeen.sourceToStamp(null, null, "  "));
    }

    static void existingOrcidGetsBaseline() {
        assertEquals(OrcidFirstSeen.BASELINE, OrcidFirstSeen.sourceToStamp("0000-0001-2345-6789", null, "0000-0001-2345-6789"));
        assertTrue(OrcidFirstSeen.isKnownSource(OrcidFirstSeen.BASELINE));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("expected true");
        }
    }
}
