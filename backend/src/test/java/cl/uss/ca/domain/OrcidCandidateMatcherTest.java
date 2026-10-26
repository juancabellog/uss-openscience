package cl.uss.ca.domain;

import cl.uss.ca.domain.OrcidCandidateMatcher.EvidenceRow;
import cl.uss.ca.domain.OrcidCandidateMatcher.Evaluation;
import cl.uss.ca.domain.OrcidCandidateMatcher.LocalPublicationInput;
import cl.uss.ca.domain.OrcidCandidateMatcher.OrcidAuthorInput;

import java.util.List;

/**
 * Proposed cases for ORCID discovery STRONG rules and PURE name partitions.
 * Not executed by the agent.
 */
public final class OrcidCandidateMatcherTest {

    private OrcidCandidateMatcherTest() {
    }

    public static void main(String[] args) {
        paulinaPartitionIsNameMatch();
        sameDoiNameMatchSamePositionIsStrong();
        sameDoiNameMatchDifferentPositionIsStrong();
        sameDoiNameMatchMissingPositionIsStrong();
        sameDoiNameMismatchIsNotStrong();
        noCommonPublicationIsNotStrong();
        initialsStillMatch();
        System.out.println("OrcidCandidateMatcherTest OK");
    }

    static void paulinaPartitionIsNameMatch() {
        assertTrue(OrcidCandidateMatcher.namePartitionDifferent(
                "Paulina", "Spaudo Valenzuela",
                "Paulina Spaudo", "Valenzuela"
        ));
        String personFull = AuthorNameMatcher.fullName("Paulina", "Spaudo Valenzuela");
        String contributorFull = AuthorNameMatcher.fullName("Paulina Spaudo", "Valenzuela");
        assertEquals("Paulina Spaudo Valenzuela", personFull);
        assertEquals("Paulina Spaudo Valenzuela", contributorFull);
        assertTrue(AuthorNameMatcher.sameNormalizedFullName(personFull, contributorFull));
        OrcidCandidateMatcher orcid = new OrcidCandidateMatcher();
        assertTrue(orcid.namesCompatible(personFull, contributorFull));
        assertTrue(orcid.compatiblePersonName(
                "Paulina", "Spaudo Valenzuela",
                "Paulina Spaudo", "Valenzuela",
                "Paulina Spaudo Valenzuela"
        ));
        assertTrue(!OrcidCandidateMatcher.namePartitionDifferent(
                "Paulina", "Spaudo Valenzuela",
                "Paulina", "Spaudo Valenzuela"
        ));
    }

    static void sameDoiNameMatchSamePositionIsStrong() {
        Evaluation evaluation = evaluate(
                "Paulina", "Spaudo Valenzuela",
                local("10.1000/test", "Paulina Spaudo", "Valenzuela", 3),
                author("Paulina Spaudo Valenzuela", 3)
        );
        assertEquals("STRONG", evaluation.matchLevel);
        assertEquals(100, evaluation.score);
        assertTrue(Boolean.TRUE.equals(evaluation.nameMatch));
        assertTrue(Boolean.TRUE.equals(evaluation.positionMatch));
        EvidenceRow row = evaluation.evidence.get(0);
        assertTrue(Boolean.TRUE.equals(row.nameMatch));
        assertTrue(Boolean.TRUE.equals(row.namePartitionDifferent));
        assertTrue(row.reasonCodes.contains(OrcidCandidateMatcher.SAME_PUBLICATION));
        assertTrue(row.reasonCodes.contains(OrcidCandidateMatcher.NAME_MATCH));
        assertTrue(row.reasonCodes.contains(OrcidCandidateMatcher.NAME_PARTITION_DIFFERENT));
        assertTrue(row.reasonCodes.contains(OrcidCandidateMatcher.POSITION_MATCH));
    }

    static void sameDoiNameMatchDifferentPositionIsStrong() {
        Evaluation evaluation = evaluate(
                "Paulina", "Spaudo Valenzuela",
                local("10.1000/test", "Paulina Spaudo", "Valenzuela", 3),
                author("Paulina Spaudo Valenzuela", 5)
        );
        assertEquals("STRONG", evaluation.matchLevel);
        assertEquals(100, evaluation.score);
        assertTrue(Boolean.FALSE.equals(evaluation.positionMatch));
        EvidenceRow row = evaluation.evidence.get(0);
        assertTrue(Boolean.TRUE.equals(row.nameMatch));
        assertTrue(row.reasonCodes.contains(OrcidCandidateMatcher.POSITION_DIFFERENT));
        assertTrue(row.note.contains(OrcidCandidateMatcher.NOTE_POSITION_DIFFERENT));
    }

    static void sameDoiNameMatchMissingPositionIsStrong() {
        Evaluation evaluation = evaluate(
                "Ana", "Perez",
                local("10.1000/test", "Ana", "Perez", 2),
                author("Ana Perez", null)
        );
        assertEquals("STRONG", evaluation.matchLevel);
        assertEquals(100, evaluation.score);
        EvidenceRow row = evaluation.evidence.get(0);
        assertEquals(null, row.positionMatch);
        assertTrue(row.reasonCodes.contains(OrcidCandidateMatcher.POSITION_NOT_COMPARABLE));
        assertTrue(row.note.contains(OrcidCandidateMatcher.NOTE_POSITION_NOT_COMPARABLE));
        assertTrue(!Boolean.TRUE.equals(row.namePartitionDifferent));
    }

    static void sameDoiNameMismatchIsNotStrong() {
        Evaluation evaluation = evaluate(
                "Paulina", "Spaudo Valenzuela",
                local("10.1000/test", "Paulina Spaudo", "Valenzuela", 1),
                author("Juan Perez", 1)
        );
        assertEquals("MANUAL_REVIEW", evaluation.matchLevel);
        assertEquals(20, evaluation.score);
        assertTrue(!Boolean.TRUE.equals(evaluation.nameMatch));
        assertTrue(!"STRONG".equals(evaluation.matchLevel));
    }

    static void noCommonPublicationIsNotStrong() {
        OrcidCandidateMatcher matcher = new OrcidCandidateMatcher();
        Evaluation evaluation = matcher.decide(List.of(), false, null);
        assertEquals("MANUAL_REVIEW", evaluation.matchLevel);
        assertEquals(20, evaluation.score);
        assertTrue(!"STRONG".equals(evaluation.matchLevel));
        assertEquals(0, evaluation.doiMatchCount);
    }

    static void initialsStillMatch() {
        AuthorNameMatcher matcher = new AuthorNameMatcher();
        assertTrue(matcher.coinciden("J. Abarca", "Jaime Alberto Abarca Reveco"));
        assertTrue(!matcher.coinciden("J.E. Abarca", "Jaime Alberto Abarca Reveco"));
    }

    private static Evaluation evaluate(
            String personFirst,
            String personLast,
            LocalPublicationInput local,
            OrcidAuthorInput author
    ) {
        OrcidCandidateMatcher matcher = new OrcidCandidateMatcher();
        EvidenceRow row = matcher.matchPublication(
                personFirst,
                personLast,
                local,
                author == null ? List.of() : List.of(author)
        );
        return matcher.decide(List.of(row), true, null);
    }

    private static LocalPublicationInput local(String doi, String first, String last, Integer position) {
        LocalPublicationInput input = new LocalPublicationInput();
        input.researchOutputId = 10L;
        input.doi = doi;
        input.contributorFirstName = first;
        input.contributorLastName = last;
        input.position = position;
        return input;
    }

    private static OrcidAuthorInput author(String creditName, Integer position) {
        OrcidAuthorInput input = new OrcidAuthorInput();
        input.creditName = creditName;
        input.position = position;
        return input;
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
