package cl.uss.ca.domain;

/**
 * Proposed cases for ORCID discovery checkbox enablement.
 * Not executed by the agent.
 */
public final class OrcidDiscoverySelectionTest {

    private OrcidDiscoverySelectionTest() {
    }

    public static void main(String[] args) {
        strongUniqueEnabled();
        manualWithoutChoiceDisabled();
        manualWithChoiceEnabled();
        tooManyDisabled();
        conflictWithoutChoiceDisabled();
        conflictWithChoiceEnabled();
        alreadyUsedDisabled();
        originDoesNotPromoteManualToStrong();
        System.out.println("OrcidDiscoverySelectionTest OK");
    }

    static void strongUniqueEnabled() {
        assertTrue(OrcidDiscoverySelection.checkboxEnabled("STRONG", null, true, 1));
        assertEquals(null, OrcidDiscoverySelection.disabledReason("STRONG", null, true, 1));
    }

    static void manualWithoutChoiceDisabled() {
        assertTrue(!OrcidDiscoverySelection.checkboxEnabled("MANUAL_REVIEW", null, false, 5));
        assertEquals(
                OrcidDiscoverySelection.DISABLED_CHOOSE_CANDIDATE,
                OrcidDiscoverySelection.disabledReason("MANUAL_REVIEW", null, false, 5)
        );
    }

    static void manualWithChoiceEnabled() {
        assertTrue(OrcidDiscoverySelection.checkboxEnabled("MANUAL_REVIEW", 99L, false, 5));
        assertEquals("MANUAL_REVIEW", "MANUAL_REVIEW");
        assertEquals(OrcidDiscoverySelection.ORIGIN_MANUAL, OrcidDiscoverySelection.originForStatus("MANUAL_REVIEW"));
    }

    static void tooManyDisabled() {
        assertTrue(!OrcidDiscoverySelection.checkboxEnabled("TOO_MANY_CANDIDATES", null, false, 0));
        assertEquals(
                OrcidDiscoverySelection.DISABLED_TOO_MANY,
                OrcidDiscoverySelection.disabledReason("TOO_MANY_CANDIDATES", null, false, 0)
        );
    }

    static void conflictWithoutChoiceDisabled() {
        assertTrue(!OrcidDiscoverySelection.checkboxEnabled("CONFLICT", null, false, 2));
        assertEquals(
                OrcidDiscoverySelection.DISABLED_MULTIPLE_STRONG,
                OrcidDiscoverySelection.disabledReason("CONFLICT", null, false, 2)
        );
    }

    static void conflictWithChoiceEnabled() {
        assertTrue(OrcidDiscoverySelection.checkboxEnabled("CONFLICT", 7L, false, 2));
    }

    static void alreadyUsedDisabled() {
        assertTrue(!OrcidDiscoverySelection.checkboxEnabled("ALREADY_USED", null, true, 1));
        assertEquals(
                OrcidDiscoverySelection.DISABLED_ALREADY_USED,
                OrcidDiscoverySelection.disabledReason("ALREADY_USED", null, true, 1)
        );
    }

    static void originDoesNotPromoteManualToStrong() {
        assertEquals(OrcidDiscoverySelection.ORIGIN_MANUAL, OrcidDiscoverySelection.originForStatus("MANUAL_REVIEW"));
        assertEquals(OrcidDiscoverySelection.ORIGIN_AUTO_STRONG, OrcidDiscoverySelection.originForStatus("STRONG"));
        assertTrue(OrcidDiscoverySelection.candidateSelectable("MANUAL_REVIEW", "MANUAL_REVIEW", null));
        assertTrue(!OrcidDiscoverySelection.candidateSelectable("CONFLICT", "CONFLICT", null));
        assertTrue(!OrcidDiscoverySelection.candidateSelectable("STRONG", "STRONG", 12L));
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
