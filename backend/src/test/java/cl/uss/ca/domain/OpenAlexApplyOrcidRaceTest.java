package cl.uss.ca.domain;

import cl.uss.ca.service.PureCorrectionService;

/**
 * Race/idempotencia conceptual del PUT ORCID reutilizado por OpenAlex.
 * No ejecutado por el agente.
 */
public final class OpenAlexApplyOrcidRaceTest {
    private OpenAlexApplyOrcidRaceTest() {
    }

    public static void main(String[] args) {
        sameOrcidIsIdempotent();
        differentOrcidIsConflict();
        openalexEvidenceSourceIsExplicit();
        System.out.println("OpenAlexApplyOrcidRaceTest OK");
    }

    static void sameOrcidIsIdempotent() {
        String remote = OrcidFormat.normalize("0000-0002-1410-4121");
        String incoming = OrcidFormat.normalizeValidOrNull("https://orcid.org/0000-0002-1410-4121");
        assertEquals(remote, incoming);
        assertEquals("ALREADY_PRESENT", PureCorrectionService.STATUS_ORCID_ALREADY_PRESENT);
    }

    static void differentOrcidIsConflict() {
        String remote = OrcidFormat.normalize("0000-0002-1410-4121");
        String incoming = OrcidFormat.normalizeValidOrNull("0000-0001-1111-1111");
        assertTrue(!java.util.Objects.equals(remote, incoming));
        assertEquals("CONFLICT_REMOTE_CHANGED", PureCorrectionService.STATUS_REMOTE_CHANGED);
    }

    static void openalexEvidenceSourceIsExplicit() {
        assertEquals("OPENALEX", PureCorrectionService.EVIDENCE_OPENALEX);
        assertEquals("ORCID_ASSIGN", PureCorrectionService.ACTION_ORCID);
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
