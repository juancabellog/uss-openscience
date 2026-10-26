package cl.uss.ca.domain;

/**
 * Matriz ORCID PURE vs OpenAlex y elegibilidad de escritura. No ejecutado por el agente.
 */
public final class OpenAlexOrcidSyncPolicyTest {
    private OpenAlexOrcidSyncPolicyTest() {
    }

    public static void main(String[] args) {
        matchingOrcidsHaveNoSyncButton();
        openalexOnlyRequiresConfirmed();
        strongDoesNotAuthorizeWrite();
        conflictingOrcidsBlockWrite();
        openalexMissingHasNoAction();
        confirmedOpenalexOnlyAllowsSync();
        invalidOpenalexOrcidBlocks();
        rejectedMatchBlocks();
        confirmBlocksAuthorAlreadyLinked();
        pureOnlyDoesNotOfferSync();
        System.out.println("OpenAlexOrcidSyncPolicyTest OK");
    }

    static void matchingOrcidsHaveNoSyncButton() {
        OpenAlexOrcidSyncPolicy.Decision decision = OpenAlexOrcidSyncPolicy.evaluate(
                OpenAlexPersonMatcher.STATUS_CONFIRMED,
                "0000-0002-1410-4121",
                "0000-0002-1410-4121"
        );
        assertEquals(OpenAlexOrcidSyncPolicy.COMPARISON_MATCH, decision.comparison);
        assertTrue(decision.identityConfirmed);
        assertTrue(!decision.syncAvailable);
        assertEquals(OpenAlexOrcidSyncPolicy.BLOCKED_PURE_HAS_ORCID, decision.blockedReason);
    }

    static void openalexOnlyRequiresConfirmed() {
        OpenAlexOrcidSyncPolicy.Decision decision = OpenAlexOrcidSyncPolicy.evaluate(
                OpenAlexPersonMatcher.STATUS_MANUAL_REVIEW,
                null,
                "0000-0002-1410-4121"
        );
        assertEquals(OpenAlexOrcidSyncPolicy.COMPARISON_OPENALEX_ONLY, decision.comparison);
        assertTrue(!decision.syncAvailable);
        assertEquals(OpenAlexOrcidSyncPolicy.BLOCKED_NOT_CONFIRMED, decision.blockedReason);
    }

    static void strongDoesNotAuthorizeWrite() {
        OpenAlexOrcidSyncPolicy.Decision decision = OpenAlexOrcidSyncPolicy.evaluate(
                OpenAlexPersonMatcher.STATUS_STRONG,
                null,
                "0000-0002-1410-4121"
        );
        assertTrue(!decision.syncAvailable);
        assertEquals(OpenAlexOrcidSyncPolicy.BLOCKED_NOT_CONFIRMED, decision.blockedReason);
    }

    static void conflictingOrcidsBlockWrite() {
        OpenAlexOrcidSyncPolicy.Decision decision = OpenAlexOrcidSyncPolicy.evaluate(
                OpenAlexPersonMatcher.STATUS_CONFIRMED,
                "0000-0002-1410-4121",
                "0000-0001-1111-1111"
        );
        assertEquals(OpenAlexOrcidSyncPolicy.COMPARISON_CONFLICT, decision.comparison);
        assertTrue(!decision.syncAvailable);
        assertEquals(OpenAlexOrcidSyncPolicy.BLOCKED_CONFLICT, decision.blockedReason);
    }

    static void openalexMissingHasNoAction() {
        OpenAlexOrcidSyncPolicy.Decision decision = OpenAlexOrcidSyncPolicy.evaluate(
                OpenAlexPersonMatcher.STATUS_CONFIRMED,
                null,
                null
        );
        assertEquals(OpenAlexOrcidSyncPolicy.COMPARISON_NONE, decision.comparison);
        assertTrue(!decision.syncAvailable);
        assertEquals(OpenAlexOrcidSyncPolicy.BLOCKED_MISSING, decision.blockedReason);
    }

    static void confirmedOpenalexOnlyAllowsSync() {
        OpenAlexOrcidSyncPolicy.Decision decision = OpenAlexOrcidSyncPolicy.evaluate(
                OpenAlexPersonMatcher.STATUS_CONFIRMED,
                "  ",
                "https://orcid.org/0000-0002-1410-4121"
        );
        assertEquals(OpenAlexOrcidSyncPolicy.COMPARISON_OPENALEX_ONLY, decision.comparison);
        assertTrue(decision.syncAvailable);
        assertEquals(null, decision.blockedReason);
    }

    static void invalidOpenalexOrcidBlocks() {
        OpenAlexOrcidSyncPolicy.Decision decision = OpenAlexOrcidSyncPolicy.evaluate(
                OpenAlexPersonMatcher.STATUS_CONFIRMED,
                null,
                "not-an-orcid"
        );
        assertTrue(!decision.syncAvailable);
        assertEquals(OpenAlexOrcidSyncPolicy.BLOCKED_INVALID, decision.blockedReason);
    }

    static void confirmBlocksAuthorAlreadyLinked() {
        assertTrue(OpenAlexOrcidSyncPolicy.authorConfirmedForOtherPerson(10L, 22L));
        assertTrue(!OpenAlexOrcidSyncPolicy.authorConfirmedForOtherPerson(10L, 10L));
        assertTrue(!OpenAlexOrcidSyncPolicy.authorConfirmedForOtherPerson(10L, null));
    }

    static void pureOnlyDoesNotOfferSync() {
        OpenAlexOrcidSyncPolicy.Decision decision = OpenAlexOrcidSyncPolicy.evaluate(
                OpenAlexPersonMatcher.STATUS_CONFIRMED,
                "0000-0002-1410-4121",
                null
        );
        assertEquals(OpenAlexOrcidSyncPolicy.COMPARISON_PURE_ONLY, decision.comparison);
        assertTrue(!decision.syncAvailable);
        assertEquals(OpenAlexOrcidSyncPolicy.BLOCKED_PURE_HAS_ORCID, decision.blockedReason);
    }

    static void rejectedMatchBlocks() {
        OpenAlexOrcidSyncPolicy.Decision decision = OpenAlexOrcidSyncPolicy.evaluate(
                OpenAlexPersonMatcher.STATUS_REJECTED,
                null,
                "0000-0002-1410-4121"
        );
        assertTrue(!decision.syncAvailable);
        assertEquals(OpenAlexOrcidSyncPolicy.BLOCKED_REJECTED, decision.blockedReason);
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
