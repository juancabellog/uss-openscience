package cl.uss.ca.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * OpenAlex person matching. Reuses {@link AuthorNameMatcher} and
 * {@link OrcidCandidateMatcher} name/partition rules. Position is evidence only.
 */
public final class OpenAlexPersonMatcher {
    public static final String SAME_DOI = "SAME_DOI";
    public static final String NAME_COMPATIBLE = "NAME_COMPATIBLE";
    public static final String NAME_PARTITION_DIFFERENT = OrcidCandidateMatcher.NAME_PARTITION_DIFFERENT;
    public static final String ORCID_MATCH = "ORCID_MATCH";
    public static final String ORCID_CONFLICT = "ORCID_CONFLICT";
    public static final String USS_AFFILIATION = "USS_AFFILIATION";
    public static final String POSITION_MATCH = OrcidCandidateMatcher.POSITION_MATCH;
    public static final String POSITION_DIFFERENT = OrcidCandidateMatcher.POSITION_DIFFERENT;
    public static final String POSITION_NOT_COMPARABLE = OrcidCandidateMatcher.POSITION_NOT_COMPARABLE;
    public static final String POSITION_INFO = "POSITION_INFO";
    public static final String MULTI_WORK = "MULTI_WORK";
    public static final String OPENALEX_RAW_DISPLAY_MISMATCH = "OPENALEX_RAW_DISPLAY_MISMATCH";

    public static final String STATUS_STRONG = "STRONG";
    public static final String STATUS_MANUAL_REVIEW = "MANUAL_REVIEW";
    public static final String STATUS_CONFLICT = "CONFLICT";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    private final OrcidCandidateMatcher names = new OrcidCandidateMatcher();

    public boolean namesCompatible(String left, String right) {
        return names.namesCompatible(left, right);
    }

    public WorkEvidence evaluateWork(PersonInput person, ContributorInput contributor, AuthorshipInput authorship) {
        WorkEvidence evidence = new WorkEvidence();
        evidence.researchOutputId = contributor == null ? null : contributor.researchOutputId;
        evidence.normalizedDoi = contributor == null ? null : DoiNormalizer.normalize(contributor.doi);
        evidence.contributorId = contributor == null ? null : contributor.contributorId;
        evidence.contributorFirst = contributor == null ? null : contributor.firstName;
        evidence.contributorLast = contributor == null ? null : contributor.lastName;
        evidence.contributorPosition = contributor == null ? null : contributor.position;
        evidence.contributorCount = contributor == null ? null : contributor.contributorCount;
        evidence.authorshipId = authorship == null ? null : authorship.authorshipId;
        evidence.openAlexAuthorId = authorship == null ? null : authorship.openAlexAuthorId;
        evidence.openalexWorkOaId = authorship == null ? null : authorship.openalexWorkOaId;
        evidence.rawAuthorName = authorship == null ? null : firstNonBlank(authorship.rawAuthorName, authorship.displayName);
        evidence.authorDisplayName = authorship == null ? null : authorship.displayName;
        evidence.authorPosition = authorship == null ? null : authorship.authorPosition;
        evidence.sourceOrder = authorship == null ? null : authorship.sourceOrder;
        evidence.orcidPure = person == null ? null : OrcidFormat.normalizeValidOrNull(person.orcid);
        evidence.orcidOpenAlex = authorship == null
                ? null
                : OrcidFormat.normalizeValidOrNull(firstNonBlank(authorship.orcid, authorship.observedOrcid));
        add(evidence, SAME_DOI);

        String personFull = person == null ? null : AuthorNameMatcher.fullName(person.firstName, person.lastName);
        String contributorFull = contributor == null
                ? null
                : AuthorNameMatcher.fullName(contributor.firstName, contributor.lastName);
        String displayName = authorship == null ? null : authorship.displayName;
        boolean rawOk = names.namesCompatible(personFull, evidence.rawAuthorName)
                || names.namesCompatible(contributorFull, evidence.rawAuthorName);
        boolean displayOk = names.namesCompatible(personFull, displayName)
                || names.namesCompatible(contributorFull, displayName);
        boolean nameOk = rawOk || displayOk;
        evidence.nameCompatible = nameOk;
        if (nameOk) {
            add(evidence, NAME_COMPATIBLE);
        }
        if (evidence.rawAuthorName != null && displayName != null
                && !names.namesCompatible(evidence.rawAuthorName, displayName)) {
            add(evidence, OPENALEX_RAW_DISPLAY_MISMATCH);
        }
        evidence.namePartitionDifferent = OrcidCandidateMatcher.namePartitionDifferent(
                person == null ? null : person.firstName,
                person == null ? null : person.lastName,
                contributor == null ? null : contributor.firstName,
                contributor == null ? null : contributor.lastName
        );
        if (Boolean.TRUE.equals(evidence.namePartitionDifferent)) {
            add(evidence, NAME_PARTITION_DIFFERENT);
        }

        if (evidence.orcidPure != null && evidence.orcidOpenAlex != null) {
            if (evidence.orcidPure.equalsIgnoreCase(evidence.orcidOpenAlex)) {
                add(evidence, ORCID_MATCH);
                evidence.orcidMatch = true;
            } else {
                add(evidence, ORCID_CONFLICT);
                evidence.orcidConflict = true;
            }
        }

        evidence.ussAffiliation = authorship != null && authorship.ussAffiliation;
        evidence.affiliationDisplay = authorship == null ? null : authorship.affiliationDisplay;
        evidence.ror = authorship == null ? null : authorship.ror;
        if (evidence.ussAffiliation) {
            add(evidence, USS_AFFILIATION);
        }

        String positionCode = comparePosition(
                evidence.contributorPosition,
                evidence.contributorCount,
                evidence.authorPosition
        );
        evidence.positionCode = positionCode;
        add(evidence, positionCode);
        if (POSITION_DIFFERENT.equals(positionCode) || POSITION_MATCH.equals(positionCode)
                || POSITION_NOT_COMPARABLE.equals(positionCode)) {
            add(evidence, POSITION_INFO);
        }
        return evidence;
    }

    public MatchDecision decide(List<WorkEvidence> works) {
        MatchDecision decision = new MatchDecision();
        if (works != null) {
            decision.works.addAll(works);
        }
        decision.matchingWorkCount = (int) decision.works.stream()
                .map(item -> item.researchOutputId != null
                        ? String.valueOf(item.researchOutputId)
                        : item.normalizedDoi)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .count();
        boolean anyName = decision.works.stream().anyMatch(item -> Boolean.TRUE.equals(item.nameCompatible));
        boolean anyOrcid = decision.works.stream().anyMatch(item -> item.orcidMatch);
        boolean anyConflictOrcid = decision.works.stream().anyMatch(item -> item.orcidConflict);
        boolean anyUss = decision.works.stream().anyMatch(item -> item.ussAffiliation);
        decision.orcidMatch = anyOrcid;
        decision.orcidConflict = anyConflictOrcid;
        decision.ussAffiliation = anyUss;
        if (decision.matchingWorkCount >= 2) {
            decision.reasonCodes.add(MULTI_WORK);
        }
        if (anyConflictOrcid && !anyOrcid) {
            decision.status = STATUS_MANUAL_REVIEW;
            decision.matchMethod = ORCID_CONFLICT;
            return collect(decision);
        }
        if (anyOrcid && anyName) {
            decision.status = STATUS_STRONG;
            decision.matchMethod = ORCID_MATCH;
            return collect(decision);
        }
        if (anyOrcid) {
            decision.status = STATUS_STRONG;
            decision.matchMethod = ORCID_MATCH;
            return collect(decision);
        }
        if (anyName && decision.matchingWorkCount >= 2) {
            decision.status = STATUS_STRONG;
            decision.matchMethod = MULTI_WORK;
            return collect(decision);
        }
        if (anyName && decision.matchingWorkCount >= 1) {
            decision.status = STATUS_STRONG;
            decision.matchMethod = SAME_DOI + "+" + NAME_COMPATIBLE;
            return collect(decision);
        }
        decision.status = STATUS_MANUAL_REVIEW;
        decision.matchMethod = SAME_DOI;
        return collect(decision);
    }

    /**
     * SAME_DOI is co-occurrence on a paper, not identity. Persist only ORCID
     * exact or a compatible name (including name + ORCID conflict as review).
     */
    public boolean isIdentityCandidate(WorkEvidence evidence) {
        return evidence != null
                && (evidence.orcidMatch || Boolean.TRUE.equals(evidence.nameCompatible));
    }

    public boolean shouldPersist(MatchDecision decision) {
        if (decision == null || decision.works.isEmpty()) {
            return false;
        }
        return decision.works.stream().anyMatch(this::isIdentityCandidate);
    }

    private static MatchDecision collect(MatchDecision decision) {
        for (WorkEvidence work : decision.works) {
            decision.reasonCodes.addAll(work.reasonCodes);
        }
        return decision;
    }

    /**
     * OpenAlex {@code first/middle/last} vs PURE 1-based ordinal.
     * Difference is informational and never auto-rejects.
     */
    public static String comparePosition(Integer pureOrdinal, Integer contributorCount, String openAlexPosition) {
        if (pureOrdinal == null || openAlexPosition == null || openAlexPosition.isBlank()) {
            return POSITION_NOT_COMPARABLE;
        }
        String position = openAlexPosition.trim().toLowerCase(Locale.ROOT);
        if ("first".equals(position)) {
            return pureOrdinal == 1 ? POSITION_MATCH : POSITION_DIFFERENT;
        }
        if ("last".equals(position)) {
            if (contributorCount == null || contributorCount < 1) {
                return POSITION_NOT_COMPARABLE;
            }
            return pureOrdinal.equals(contributorCount) ? POSITION_MATCH : POSITION_DIFFERENT;
        }
        if ("middle".equals(position)) {
            if (contributorCount == null || contributorCount < 3) {
                return POSITION_NOT_COMPARABLE;
            }
            return pureOrdinal > 1 && pureOrdinal < contributorCount ? POSITION_MATCH : POSITION_DIFFERENT;
        }
        return POSITION_NOT_COMPARABLE;
    }

    public static boolean ussAffiliation(String ror, String displayName) {
        return UssOrganizationIdentity.isUssIdentifier(ror, "ROR")
                || UssOrganizationIdentity.isUssTextEvidence(displayName);
    }

    private static void add(WorkEvidence evidence, String code) {
        if (code != null && !code.isBlank()) {
            evidence.reasonCodes.add(code);
        }
    }

    private static String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return right;
    }

    public static final class PersonInput {
        public long personId;
        public String firstName;
        public String lastName;
        public String orcid;
        public String uuid;
        public Long pureId;
    }

    public static final class ContributorInput {
        public long researchOutputId;
        public Long contributorId;
        public String doi;
        public String firstName;
        public String lastName;
        public Integer position;
        public Integer contributorCount;
        public String personUuid;
    }

    public static final class AuthorshipInput {
        public Long authorshipId;
        public String openAlexAuthorId;
        public String openalexWorkOaId;
        public String displayName;
        public String rawAuthorName;
        public String authorPosition;
        public Integer sourceOrder;
        public String orcid;
        public String observedOrcid;
        public boolean ussAffiliation;
        public String affiliationDisplay;
        public String ror;
    }

    public static final class WorkEvidence {
        public Long researchOutputId;
        public String normalizedDoi;
        public Long authorshipId;
        public String openAlexAuthorId;
        public String openalexWorkOaId;
        public Long contributorId;
        public String contributorFirst;
        public String contributorLast;
        public Integer contributorPosition;
        public Integer contributorCount;
        public String rawAuthorName;
        public String authorDisplayName;
        public String authorPosition;
        public Integer sourceOrder;
        public Boolean nameCompatible;
        public Boolean namePartitionDifferent;
        public String positionCode;
        public String orcidPure;
        public String orcidOpenAlex;
        public boolean orcidMatch;
        public boolean orcidConflict;
        public boolean ussAffiliation;
        public String affiliationDisplay;
        public String ror;
        public final Set<String> reasonCodes = new LinkedHashSet<>();
    }

    public static final class MatchDecision {
        public String status = STATUS_MANUAL_REVIEW;
        public String matchMethod;
        public int matchingWorkCount;
        public boolean orcidMatch;
        public boolean orcidConflict;
        public boolean ussAffiliation;
        public final Set<String> reasonCodes = new LinkedHashSet<>();
        public final List<WorkEvidence> works = new ArrayList<>();
    }
}
