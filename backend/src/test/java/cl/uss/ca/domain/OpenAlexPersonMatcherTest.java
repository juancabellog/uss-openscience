package cl.uss.ca.domain;

import cl.uss.ca.domain.OpenAlexPersonMatcher.AuthorshipInput;
import cl.uss.ca.domain.OpenAlexPersonMatcher.ContributorInput;
import cl.uss.ca.domain.OpenAlexPersonMatcher.MatchDecision;
import cl.uss.ca.domain.OpenAlexPersonMatcher.PersonInput;
import cl.uss.ca.domain.OpenAlexPersonMatcher.WorkEvidence;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAlex matching cases A–I, position, ORCID and affiliation.
 * Not executed by the agent.
 */
public final class OpenAlexPersonMatcherTest {
    private static final OpenAlexPersonMatcher MATCHER = new OpenAlexPersonMatcher();

    private OpenAlexPersonMatcherTest() {
    }

    public static void main(String[] args) {
        aSameDoiCompatibleNameIsStrong();
        bNamePartitionStillCompatible();
        cSameDoiNameAndOrcidIsStrong();
        dPositionDifferentRemainsStrong();
        eIncompatibleNameIsManual();
        fSameNameNoCommonWorkIsNotAutomaticStrong();
        gMultiWorkIncreasesConfidence();
        hMultipleAuthorsIsConflictReview();
        iOneAuthorMultiplePersonsIsConflictReview();
        positionFirstMatchesOrdinalOne();
        positionMiddleMatchesInterior();
        positionLastDifferentIsInformational();
        orcidFormattingNormalized();
        orcidConflictIsReview();
        noOrcidContinuesWithName();
        ussRorAddsEvidence();
        differentInstitutionDoesNotReject();
        acceptanceJuanPerez();
        sameDoiIncompatibleOrcidConflictIsNotCandidate();
        sameDoiIncompatibleNoOrcidIsNotCandidate();
        sameDoiCompatibleNameIsCandidate();
        orcidExactIsStrongCandidate();
        nameCompatibleOrcidConflictIsManualReview();
        megaAuthorPaperDoesNotCreateIrrelevantCandidates();
        twoAuthorshipsSameWorkCountAsOne();
        rawDisplayMismatchIsPreserved();
        System.out.println("OpenAlexPersonMatcherTest OK");
    }

    static void aSameDoiCompatibleNameIsStrong() {
        MatchDecision decision = decide(work(
                person("Juan", "Pérez", null),
                contributor("Juan", "Pérez", 1, 3),
                authorship("Juan Perez", "middle", null, false)
        ));
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, decision.status);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.SAME_DOI);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.NAME_COMPATIBLE);
    }

    static void bNamePartitionStillCompatible() {
        MatchDecision decision = decide(work(
                person("Paulina", "Spaudo Valenzuela", null),
                contributor("Paulina Spaudo", "Valenzuela", 1, 3),
                authorship("Paulina Spaudo Valenzuela", "first", null, false)
        ));
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, decision.status);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.NAME_COMPATIBLE);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.NAME_PARTITION_DIFFERENT);
    }

    static void cSameDoiNameAndOrcidIsStrong() {
        MatchDecision decision = decide(work(
                person("Juan", "Pérez", "https://orcid.org/0000-0001-2345-6789"),
                contributor("Juan", "Pérez", 1, 2),
                authorship("Juan Perez", "first", "0000-0001-2345-6789", false)
        ));
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, decision.status);
        assertEquals(OpenAlexPersonMatcher.ORCID_MATCH, decision.matchMethod);
        assert decision.orcidMatch;
    }

    static void dPositionDifferentRemainsStrong() {
        MatchDecision decision = decide(work(
                person("Juan", "Pérez", null),
                contributor("Juan", "Pérez", 4, 4),
                authorship("Juan Perez", "middle", null, false)
        ));
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, decision.status);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.POSITION_DIFFERENT);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.POSITION_INFO);
        assert !OpenAlexPersonMatcher.STATUS_REJECTED.equals(decision.status);
    }

    static void eIncompatibleNameIsManual() {
        MatchDecision decision = decide(work(
                person("Juan", "Pérez", null),
                contributor("Juan", "Pérez", 1, 2),
                authorship("Maria Gonzalez", "first", null, false)
        ));
        assertEquals(OpenAlexPersonMatcher.STATUS_MANUAL_REVIEW, decision.status);
        assert !decision.reasonCodes.contains(OpenAlexPersonMatcher.NAME_COMPATIBLE);
        assert !MATCHER.shouldPersist(decision);
    }

    static void fSameNameNoCommonWorkIsNotAutomaticStrong() {
        MatchDecision empty = MATCHER.decide(List.of());
        assertEquals(OpenAlexPersonMatcher.STATUS_MANUAL_REVIEW, empty.status);
        assert empty.matchingWorkCount == 0;
        assert !OpenAlexPersonMatcher.STATUS_STRONG.equals(empty.status);
    }

    static void gMultiWorkIncreasesConfidence() {
        PersonInput person = person("Juan", "Pérez", null);
        List<WorkEvidence> works = new ArrayList<>();
        works.add(evidence(person, contributor("Juan", "Pérez", 1, 3), authorship("Juan Perez", "first", null, false)));
        works.get(0).normalizedDoi = "10.x/one";
        works.add(evidence(person, contributor("Juan", "Pérez", 2, 3), authorship("Juan Perez", "middle", null, false)));
        works.get(1).normalizedDoi = "10.x/two";
        works.add(evidence(person, contributor("Juan", "Pérez", 1, 2), authorship("Juan Perez", "first", null, true)));
        works.get(2).normalizedDoi = "10.x/three";
        MatchDecision decision = MATCHER.decide(works);
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, decision.status);
        assertEquals(OpenAlexPersonMatcher.MULTI_WORK, decision.matchMethod);
        assert decision.matchingWorkCount == 3;
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.MULTI_WORK);
    }

    static void hMultipleAuthorsIsConflictReview() {
        MatchDecision first = decide(work(
                person("Juan", "Pérez", null),
                contributor("Juan", "Pérez", 1, 2),
                authorship("Juan Perez", "first", null, false)
        ));
        MatchDecision second = decide(work(
                person("Juan", "Pérez", null),
                contributor("Juan", "Pérez", 1, 2),
                authorship("J Perez", "last", null, false)
        ));
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, first.status);
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, second.status);
        assert OpenAlexPersonMatcher.STATUS_CONFLICT.equals("CONFLICT");
    }

    static void iOneAuthorMultiplePersonsIsConflictReview() {
        MatchDecision a = decide(work(
                person("Juan", "Pérez", null),
                contributor("Juan", "Pérez", 1, 2),
                authorship("Juan Perez", "first", null, false)
        ));
        MatchDecision b = decide(work(
                person("Juan Carlos", "Pérez Soto", null),
                contributor("Juan Carlos", "Pérez Soto", 1, 2),
                authorship("Juan Perez", "first", null, false)
        ));
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, a.status);
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, b.status);
    }

    static void positionFirstMatchesOrdinalOne() {
        assertEquals(
                OpenAlexPersonMatcher.POSITION_MATCH,
                OpenAlexPersonMatcher.comparePosition(1, 5, "first")
        );
    }

    static void positionMiddleMatchesInterior() {
        assertEquals(
                OpenAlexPersonMatcher.POSITION_MATCH,
                OpenAlexPersonMatcher.comparePosition(4, 6, "middle")
        );
    }

    static void positionLastDifferentIsInformational() {
        assertEquals(
                OpenAlexPersonMatcher.POSITION_DIFFERENT,
                OpenAlexPersonMatcher.comparePosition(4, 6, "last")
        );
        WorkEvidence evidence = evidence(
                person("Juan", "Pérez", null),
                contributor("Juan", "Pérez", 4, 6),
                authorship("Juan Perez", "last", null, false)
        );
        MatchDecision decision = MATCHER.decide(List.of(evidence));
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, decision.status);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.POSITION_DIFFERENT);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.POSITION_INFO);
    }

    static void orcidFormattingNormalized() {
        WorkEvidence evidence = evidence(
                person("Juan", "Pérez", "https://orcid.org/0000-0001-2345-6789"),
                contributor("Juan", "Pérez", 1, 2),
                authorship("Juan Perez", "first", "0000-0001-2345-6789", false)
        );
        assert evidence.orcidMatch;
        assertEquals("0000-0001-2345-6789", evidence.orcidPure);
        assertEquals("0000-0001-2345-6789", evidence.orcidOpenAlex);
    }

    static void orcidConflictIsReview() {
        MatchDecision decision = decide(work(
                person("Juan", "Pérez", "0000-0001-2345-6789"),
                contributor("Juan", "Pérez", 1, 2),
                authorship("Juan Perez", "first", "0000-0002-1111-2222", false)
        ));
        assertEquals(OpenAlexPersonMatcher.STATUS_MANUAL_REVIEW, decision.status);
        assert decision.orcidConflict;
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.ORCID_CONFLICT);
    }

    static void noOrcidContinuesWithName() {
        MatchDecision decision = decide(work(
                person("Juan", "Pérez", null),
                contributor("Juan", "Pérez", 1, 2),
                authorship("Juan Perez", "first", null, false)
        ));
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, decision.status);
        assert !decision.orcidMatch;
        assert !decision.orcidConflict;
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.NAME_COMPATIBLE);
    }

    static void ussRorAddsEvidence() {
        assert OpenAlexPersonMatcher.ussAffiliation("https://ror.org/047gc3g35", "Universidad San Sebastián");
        WorkEvidence evidence = evidence(
                person("Juan", "Pérez", null),
                contributor("Juan", "Pérez", 2, 3),
                authorship("Juan Perez", "middle", null, true)
        );
        assert evidence.ussAffiliation;
        assert evidence.reasonCodes.contains(OpenAlexPersonMatcher.USS_AFFILIATION);
    }

    static void differentInstitutionDoesNotReject() {
        MatchDecision decision = decide(work(
                person("Juan", "Pérez", null),
                contributor("Juan", "Pérez", 1, 2),
                authorship("Juan Perez", "first", null, false)
        ));
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, decision.status);
        assert !decision.ussAffiliation;
    }

    static void acceptanceJuanPerez() {
        WorkEvidence evidence = evidence(
                person("Juan", "Pérez", null),
                contributor("Juan", "Pérez", 2, 3),
                authorship("Juan Perez", "middle", null, true)
        );
        MatchDecision decision = MATCHER.decide(List.of(evidence));
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, decision.status);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.SAME_DOI);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.NAME_COMPATIBLE);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.USS_AFFILIATION);
        assert decision.reasonCodes.contains(OpenAlexPersonMatcher.POSITION_INFO);
    }

    static void sameDoiIncompatibleOrcidConflictIsNotCandidate() {
        WorkEvidence evidence = evidence(
                person("Soledad", "Matus", "0000-0001-1111-1111"),
                contributor("Soledad", "Matus", 1, 2467),
                authorship("Dongsheng Cai", "Jingjing Cai", "A5004790427", "middle", "0000-0003-0708-3809", false, 0)
        );
        assert !Boolean.TRUE.equals(evidence.nameCompatible);
        assert evidence.orcidConflict;
        assert evidence.reasonCodes.contains(OpenAlexPersonMatcher.SAME_DOI);
        assert !MATCHER.isIdentityCandidate(evidence);
        assert !MATCHER.shouldPersist(MATCHER.decide(List.of(evidence)));
    }

    static void sameDoiIncompatibleNoOrcidIsNotCandidate() {
        WorkEvidence evidence = evidence(
                person("Soledad", "Matus", null),
                contributor("Soledad", "Matus", 1, 2467),
                authorship("Dongsheng Cai", "first", null, false)
        );
        assert !MATCHER.isIdentityCandidate(evidence);
        assert !MATCHER.shouldPersist(MATCHER.decide(List.of(evidence)));
    }

    static void sameDoiCompatibleNameIsCandidate() {
        WorkEvidence evidence = evidence(
                person("Jingjing", "Cai", null),
                contributor("Jingjing", "Cai", 2, 8),
                authorship("Jingjing Cai", "middle", null, false)
        );
        assert MATCHER.isIdentityCandidate(evidence);
        MatchDecision decision = MATCHER.decide(List.of(evidence));
        assert MATCHER.shouldPersist(decision);
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, decision.status);
    }

    static void orcidExactIsStrongCandidate() {
        WorkEvidence evidence = evidence(
                person("Soledad", "Matus", "0000-0001-2345-6789"),
                contributor("Soledad", "Matus", 1, 8),
                authorship("S Matus", "first", "0000-0001-2345-6789", false)
        );
        assert evidence.orcidMatch;
        assert MATCHER.isIdentityCandidate(evidence);
        MatchDecision decision = MATCHER.decide(List.of(evidence));
        assert MATCHER.shouldPersist(decision);
        assertEquals(OpenAlexPersonMatcher.STATUS_STRONG, decision.status);
        assertEquals(OpenAlexPersonMatcher.ORCID_MATCH, decision.matchMethod);
    }

    static void nameCompatibleOrcidConflictIsManualReview() {
        MatchDecision decision = decide(work(
                person("Juan", "Pérez", "0000-0001-2345-6789"),
                contributor("Juan", "Pérez", 1, 2),
                authorship("Juan Perez", "first", "0000-0002-1111-2222", false)
        ));
        assert MATCHER.shouldPersist(decision);
        assertEquals(OpenAlexPersonMatcher.STATUS_MANUAL_REVIEW, decision.status);
        assert decision.orcidConflict;
    }

    static void megaAuthorPaperDoesNotCreateIrrelevantCandidates() {
        PersonInput person = person("Soledad", "Matus", "0000-0001-1111-1111");
        ContributorInput contributor = contributor("Soledad", "Matus", 12, 2467);
        int kept = 0;
        for (int i = 0; i < 2467; i++) {
            AuthorshipInput remote = authorship(
                    "Author " + i,
                    "Author " + i,
                    "A" + i,
                    "middle",
                    "0000-0003-0000-" + String.format("%04d", i % 10000),
                    false,
                    i
            );
            if (i == 12) {
                remote.rawAuthorName = "Soledad Matus";
                remote.displayName = "Soledad Matus";
                remote.orcid = "0000-0001-1111-1111";
                remote.openAlexAuthorId = "A500SOLEDAD";
            }
            WorkEvidence evidence = MATCHER.evaluateWork(person, contributor, remote);
            if (MATCHER.isIdentityCandidate(evidence)) {
                kept++;
            }
        }
        assert kept == 1;
    }

    static void twoAuthorshipsSameWorkCountAsOne() {
        PersonInput person = person("Jingjing", "Cai", "0000-0003-0708-3809");
        ContributorInput contributor = contributor("Jingjing", "Cai", 2, 8);
        contributor.researchOutputId = 1772L;
        List<WorkEvidence> works = new ArrayList<>();
        works.add(evidence(person, contributor, authorship(
                "Dongsheng Cai", "Jingjing Cai", "A5004790427", "first", "0000-0003-0708-3809", false, 0)));
        works.add(evidence(person, contributor, authorship(
                "Jingjing Cai", "Jingjing Cai", "A5004790427", "middle", "0000-0003-0708-3809", false, 1)));
        MatchDecision decision = MATCHER.decide(works);
        assert decision.matchingWorkCount == 1;
        assert MATCHER.shouldPersist(decision);
    }

    static void rawDisplayMismatchIsPreserved() {
        WorkEvidence evidence = evidence(
                person("Jingjing", "Cai", "0000-0003-0708-3809"),
                contributor("Jingjing", "Cai", 2, 8),
                authorship("Dongsheng Cai", "Jingjing Cai", "A5004790427", "first", "0000-0003-0708-3809", false, 0)
        );
        assertEquals("Dongsheng Cai", evidence.rawAuthorName);
        assertEquals("Jingjing Cai", evidence.authorDisplayName);
        assert evidence.reasonCodes.contains(OpenAlexPersonMatcher.OPENALEX_RAW_DISPLAY_MISMATCH);
        assert Boolean.TRUE.equals(evidence.nameCompatible);
    }

    private static PersonInput person(String first, String last, String orcid) {
        PersonInput person = new PersonInput();
        person.personId = 1L;
        person.firstName = first;
        person.lastName = last;
        person.orcid = orcid;
        person.uuid = "P1";
        return person;
    }

    private static ContributorInput contributor(String first, String last, int position, int count) {
        ContributorInput contributor = new ContributorInput();
        contributor.researchOutputId = 10L;
        contributor.contributorId = 20L;
        contributor.doi = "10.x/abc";
        contributor.firstName = first;
        contributor.lastName = last;
        contributor.position = position;
        contributor.contributorCount = count;
        contributor.personUuid = "P1";
        return contributor;
    }

    private static AuthorshipInput authorship(String rawName, String position, String orcid, boolean uss) {
        return authorship(rawName, rawName, "A987", position, orcid, uss, 1);
    }

    private static AuthorshipInput authorship(
            String rawName,
            String displayName,
            String openAlexAuthorId,
            String position,
            String orcid,
            boolean uss,
            int sourceOrder
    ) {
        AuthorshipInput authorship = new AuthorshipInput();
        authorship.openAlexAuthorId = openAlexAuthorId;
        authorship.displayName = displayName;
        authorship.rawAuthorName = rawName;
        authorship.authorPosition = position;
        authorship.sourceOrder = sourceOrder;
        authorship.orcid = orcid;
        authorship.ussAffiliation = uss;
        return authorship;
    }

    private static WorkEvidence evidence(PersonInput person, ContributorInput contributor, AuthorshipInput authorship) {
        return MATCHER.evaluateWork(person, contributor, authorship);
    }

    private static WorkEvidence work(PersonInput person, ContributorInput contributor, AuthorshipInput authorship) {
        return evidence(person, contributor, authorship);
    }

    private static MatchDecision decide(WorkEvidence evidence) {
        return MATCHER.decide(List.of(evidence));
    }

    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
