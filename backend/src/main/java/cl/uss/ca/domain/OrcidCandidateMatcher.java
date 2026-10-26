package cl.uss.ca.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ORCID discovery decision: STRONG when the same publication (normalized DOI)
 * has a compatible person/contributor name. Author position is evidence only.
 */
public final class OrcidCandidateMatcher {

    public static final String SAME_PUBLICATION = "SAME_PUBLICATION";
    public static final String NAME_MATCH = "NAME_MATCH";
    public static final String NAME_PARTITION_DIFFERENT = "NAME_PARTITION_DIFFERENT";
    public static final String POSITION_MATCH = "POSITION_MATCH";
    public static final String POSITION_DIFFERENT = "POSITION_DIFFERENT";
    public static final String POSITION_NOT_COMPARABLE = "POSITION_NOT_COMPARABLE";

    public static final String NOTE_POSITION_DIFFERENT =
            "Misma publicación; posición de autor diferente.";
    public static final String NOTE_POSITION_NOT_COMPARABLE =
            "No fue posible comparar la posición del autor.";
    public static final String NOTE_NAME_PARTITION =
            "Nombre completo coincidente, pero first_name/last_name están distribuidos de forma distinta entre Persona y Contributor.";
    public static final String NOTE_MISSING_AUTHORSHIP =
            "DOI coincidente; ORCID no entrega información suficiente de autoría para verificar automáticamente.";

    private final AuthorNameMatcher names = new AuthorNameMatcher();

    public boolean namesCompatible(String left, String right) {
        if (blank(left) || blank(right)) {
            return false;
        }
        if (AuthorNameMatcher.sameNormalizedFullName(left, right)) {
            return true;
        }
        return names.coinciden(left, right);
    }

    public static boolean namePartitionDifferent(
            String personFirst,
            String personLast,
            String contributorFirst,
            String contributorLast
    ) {
        String personFull = AuthorNameMatcher.fullName(personFirst, personLast);
        String contributorFull = AuthorNameMatcher.fullName(contributorFirst, contributorLast);
        if (!AuthorNameMatcher.sameNormalizedFullName(personFull, contributorFull)) {
            return false;
        }
        return !trimEquals(personFirst, contributorFirst) || !trimEquals(personLast, contributorLast);
    }

    public boolean compatiblePersonName(
            String personFirst,
            String personLast,
            String contributorFirst,
            String contributorLast,
            String orcidCreditName
    ) {
        String personFull = AuthorNameMatcher.fullName(personFirst, personLast);
        String contributorFull = AuthorNameMatcher.fullName(contributorFirst, contributorLast);
        return namesCompatible(personFull, orcidCreditName) || namesCompatible(contributorFull, orcidCreditName);
    }

    public Boolean comparePosition(Integer localPosition, Integer orcidPosition) {
        if (localPosition == null || orcidPosition == null) {
            return null;
        }
        return localPosition.equals(orcidPosition);
    }

    public EvidenceRow matchPublication(
            String personFirst,
            String personLast,
            LocalPublicationInput local,
            List<OrcidAuthorInput> orcidAuthors
    ) {
        EvidenceRow row = new EvidenceRow();
        row.researchOutputId = local == null ? null : local.researchOutputId;
        row.doi = local == null ? null : DoiNormalizer.normalize(local.doi);
        row.personFirstName = personFirst;
        row.personLastName = personLast;
        row.personFullName = AuthorNameMatcher.fullName(personFirst, personLast);
        row.contributorFirstName = local == null ? null : local.contributorFirstName;
        row.contributorLastName = local == null ? null : local.contributorLastName;
        row.contributorFullName = local == null
                ? null
                : AuthorNameMatcher.fullName(local.contributorFirstName, local.contributorLastName);
        row.localAuthor = row.contributorFullName;
        row.localPosition = local == null ? null : local.position;
        row.namePartitionDifferent = namePartitionDifferent(
                personFirst,
                personLast,
                row.contributorFirstName,
                row.contributorLastName
        );
        addReason(row, SAME_PUBLICATION);
        if (row.namePartitionDifferent) {
            addReason(row, NAME_PARTITION_DIFFERENT);
            addNote(row, NOTE_NAME_PARTITION);
        }
        List<OrcidAuthorInput> authors = orcidAuthors == null ? List.of() : orcidAuthors;
        if (authors.isEmpty()) {
            addNote(row, NOTE_MISSING_AUTHORSHIP);
            return row;
        }
        OrcidAuthorInput matched = bestCompatibleAuthor(
                personFirst, personLast, row.contributorFirstName, row.contributorLastName, authors, row.localPosition
        );
        if (matched == null) {
            return row;
        }
        row.orcidAuthor = matched.creditName;
        row.orcidPosition = matched.position;
        row.nameMatch = Boolean.TRUE;
        addReason(row, NAME_MATCH);
        Boolean positionMatch = comparePosition(row.localPosition, matched.position);
        row.positionMatch = positionMatch;
        if (positionMatch == null) {
            addReason(row, POSITION_NOT_COMPARABLE);
            addNote(row, NOTE_POSITION_NOT_COMPARABLE);
        } else if (positionMatch) {
            addReason(row, POSITION_MATCH);
        } else {
            addReason(row, POSITION_DIFFERENT);
            addNote(row, NOTE_POSITION_DIFFERENT);
        }
        return row;
    }

    public Evaluation decide(List<EvidenceRow> evidence, boolean samePublication, Long alreadyUsedPersonId) {
        Evaluation evaluation = new Evaluation();
        if (evidence != null) {
            evaluation.evidence.addAll(evidence);
        }
        boolean anyName = evaluation.evidence.stream().anyMatch(item -> Boolean.TRUE.equals(item.nameMatch));
        boolean anyAuthor = anyName;
        boolean anyPosition = evaluation.evidence.stream().anyMatch(item -> Boolean.TRUE.equals(item.positionMatch));
        boolean anyPositionDifferent = evaluation.evidence.stream()
                .anyMatch(item -> item.reasonCodes.contains(POSITION_DIFFERENT));
        if (!samePublication) {
            evaluation.score = 20;
            evaluation.matchLevel = "MANUAL_REVIEW";
            evaluation.status = "MANUAL_REVIEW";
        } else if (anyName) {
            evaluation.score = 100;
            evaluation.matchLevel = "STRONG";
            evaluation.status = "STRONG";
        } else {
            evaluation.score = 20;
            evaluation.matchLevel = "MANUAL_REVIEW";
            evaluation.status = "MANUAL_REVIEW";
        }
        if (alreadyUsedPersonId != null) {
            evaluation.status = "ALREADY_USED";
            if (!"STRONG".equals(evaluation.matchLevel)) {
                evaluation.matchLevel = "ALREADY_USED";
            }
        }
        evaluation.nameMatch = anyName ? Boolean.TRUE : (samePublication ? Boolean.FALSE : null);
        evaluation.authorMatch = anyAuthor ? Boolean.TRUE : null;
        if (anyPosition) {
            evaluation.positionMatch = Boolean.TRUE;
        } else if (anyPositionDifferent) {
            evaluation.positionMatch = Boolean.FALSE;
        } else {
            evaluation.positionMatch = null;
        }
        evaluation.doiMatchCount = (int) evaluation.evidence.stream()
                .map(item -> item.doi)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return evaluation;
    }

    private OrcidAuthorInput bestCompatibleAuthor(
            String personFirst,
            String personLast,
            String contributorFirst,
            String contributorLast,
            List<OrcidAuthorInput> authors,
            Integer localPosition
    ) {
        OrcidAuthorInput matched = null;
        for (OrcidAuthorInput author : authors) {
            if (author == null || !compatiblePersonName(
                    personFirst, personLast, contributorFirst, contributorLast, author.creditName
            )) {
                continue;
            }
            if (matched == null) {
                matched = author;
                continue;
            }
            boolean betterPosition = Boolean.TRUE.equals(comparePosition(localPosition, author.position))
                    && !Boolean.TRUE.equals(comparePosition(localPosition, matched.position));
            if (betterPosition) {
                matched = author;
            }
        }
        return matched;
    }

    private static void addReason(EvidenceRow row, String reason) {
        if (reason != null && !reason.isBlank()) {
            row.reasonCodes.add(reason);
        }
    }

    private static void addNote(EvidenceRow row, String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        if (row.note == null || row.note.isBlank()) {
            row.note = note;
            return;
        }
        if (!row.note.contains(note)) {
            row.note = row.note + " " + note;
        }
    }

    private static boolean trimEquals(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equals(b);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public static String joinReasonCodes(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return null;
        }
        return String.join(",", new LinkedHashSet<>(reasons));
    }

    public static List<String> splitReasonCodes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    public static final class LocalPublicationInput {
        public Long researchOutputId;
        public String doi;
        public String contributorFirstName;
        public String contributorLastName;
        public Integer position;
    }

    public static final class OrcidAuthorInput {
        public String creditName;
        public Integer position;
    }

    public static final class EvidenceRow {
        public Long researchOutputId;
        public String doi;
        public String localAuthor;
        public Integer localPosition;
        public String orcidAuthor;
        public Integer orcidPosition;
        public Boolean nameMatch;
        public Boolean positionMatch;
        public String note;
        public String personFullName;
        public String contributorFullName;
        public String personFirstName;
        public String personLastName;
        public String contributorFirstName;
        public String contributorLastName;
        public Boolean namePartitionDifferent;
        public final Set<String> reasonCodes = new LinkedHashSet<>();
    }

    public static final class Evaluation {
        public int score;
        public String matchLevel = "MANUAL_REVIEW";
        public String status = "MANUAL_REVIEW";
        public Boolean nameMatch;
        public Boolean authorMatch;
        public Boolean positionMatch;
        public int doiMatchCount;
        public final List<EvidenceRow> evidence = new ArrayList<>();
    }
}
