package cl.uss.ca.domain;

import java.util.List;

/**
 * Proposed cases for ORCID apply payload vs authoritative queued selection.
 * Not executed by the agent.
 */
public final class OrcidDiscoveryApplyTargetsTest {

    private OrcidDiscoveryApplyTargetsTest() {
    }

    public static void main(String[] args) {
        uniqueKeepsOrder();
        uniqueIgnoresNulls();
        emptyRequestIsEmpty();
        nullRequestIsEmpty();
        System.out.println("OrcidDiscoveryApplyTargetsTest OK");
    }

    static void uniqueKeepsOrder() {
        assertEquals(List.of(3L, 1L, 2L), OrcidDiscoverySelection.uniquePersonIds(List.of(3L, 1L, 3L, 2L)));
    }

    static void uniqueIgnoresNulls() {
        assertEquals(List.of(8L), OrcidDiscoverySelection.uniquePersonIds(java.util.Arrays.asList(null, 8L, null)));
    }

    static void emptyRequestIsEmpty() {
        assertEquals(List.of(), OrcidDiscoverySelection.uniquePersonIds(List.of()));
    }

    static void nullRequestIsEmpty() {
        assertEquals(List.of(), OrcidDiscoverySelection.uniquePersonIds(null));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
