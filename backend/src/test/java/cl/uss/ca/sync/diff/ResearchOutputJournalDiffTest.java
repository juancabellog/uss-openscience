package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.JournalData;
import cl.uss.ca.external.pure.model.ResearchOutput;

/**
 * Proposed cases: Research Output journal identity is UUID only.
 * Not executed by the agent.
 */
public final class ResearchOutputJournalDiffTest {

    private static final String UUID_A = "c3615b25-65ab-4855-917c-b77afccbf206";
    private static final String UUID_B = "357955c2-6dd7-4171-a237-90dac48f6091";
    private static final String ISSN_PRINT = "0717-7518";
    private static final String ISSN_ONLINE = "0716-1549";

    private ResearchOutputJournalDiffTest() {
    }

    public static void main(String[] args) {
        sameUuidDifferentIssnUnchanged();
        sameUuidDifferentTitleUnchanged();
        differentUuidUpdated();
        bothNullUuidUnchanged();
        uuidFilledInUnchanged();
        journalAdded();
        journalRemoved();
        System.out.println("ResearchOutputJournalDiffTest OK");
    }

    static void sameUuidDifferentIssnUnchanged() {
        AggregateOutcome outcome = compare(
                journal(ISSN_ONLINE, "Revista Chilena de Nutrición", UUID_A),
                ISSN_PRINT,
                "Revista Chilena de Nutrición",
                UUID_A,
                10L
        );
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
        assertEquals(0, countScope(outcome, "JOURNAL"));
    }

    static void sameUuidDifferentTitleUnchanged() {
        AggregateOutcome outcome = compare(
                journal(ISSN_PRINT, "Revista Chilena de Nutrición", UUID_A),
                ISSN_PRINT,
                "Revista Chilena de Nutricion",
                UUID_A,
                11L
        );
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
    }

    static void differentUuidUpdated() {
        AggregateOutcome outcome = compare(
                journal(ISSN_PRINT, "Revista A", UUID_A),
                ISSN_ONLINE,
                "Revista B",
                UUID_B,
                12L
        );
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertEquals(1, countScope(outcome, "JOURNAL"));
        SyncChange change = firstScope(outcome, "JOURNAL");
        assertEquals("Journal changed", change.description);
        assertTrue(change.oldValue.contains(UUID_A));
        assertTrue(change.newValue.contains(UUID_B));
    }

    static void bothNullUuidUnchanged() {
        AggregateOutcome outcome = compare(
                journal(ISSN_ONLINE, "Revista Chilena de Nutrición", null),
                ISSN_PRINT,
                "Other title",
                null,
                13L
        );
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
    }

    static void uuidFilledInUnchanged() {
        JournalData oldJournal = journal(ISSN_PRINT, "Revista Chilena de Nutrición", null);
        JournalData newJournal = journal(ISSN_PRINT, "Revista Chilena de Nutrición", UUID_A);
        AggregateOutcome outcome = compare(
                oldJournal,
                newJournal.getIssn(),
                newJournal.getTitle(),
                newJournal.getUuid(),
                14L
        );
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
        assertEquals(0, countScope(outcome, "JOURNAL"));
    }

    static void journalAdded() {
        ResearchOutputSnapshot existing = snapshot(null, null);
        ResearchOutput incoming = incoming(journal(ISSN_PRINT, "Revista Chilena de Nutrición", UUID_A), 1L);
        AggregateOutcome outcome = new ResearchOutputAggregateComparer().compare(incoming, existing);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertEquals("Journal added", firstScope(outcome, "JOURNAL").description);
    }

    static void journalRemoved() {
        ResearchOutputSnapshot existing = snapshot(journal(ISSN_PRINT, "Revista Chilena de Nutrición", UUID_A), 1L);
        ResearchOutput incoming = incoming(null, null);
        AggregateOutcome outcome = new ResearchOutputAggregateComparer().compare(incoming, existing);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertEquals("Journal removed", firstScope(outcome, "JOURNAL").description);
    }

    private static AggregateOutcome compare(
            JournalData oldJournal,
            String newIssn,
            String newTitle,
            String newUuid,
            Long newAssociationPureId
    ) {
        ResearchOutputSnapshot existing = snapshot(oldJournal, 1L);
        ResearchOutput incoming = incoming(journal(newIssn, newTitle, newUuid), newAssociationPureId);
        return new ResearchOutputAggregateComparer().compare(incoming, existing);
    }

    private static ResearchOutputSnapshot snapshot(JournalData journal, Long associationPureId) {
        ResearchOutputSnapshot snapshot = new ResearchOutputSnapshot();
        snapshot.id = 50L;
        snapshot.activeInPure = true;
        snapshot.parent.setUuid("ro-uuid");
        snapshot.parent.setPureId(9589074L);
        snapshot.parent.setTitle("Anyons in 1 + 1 dimensions");
        snapshot.journal = journal;
        snapshot.journalAssociationPureId = associationPureId;
        return snapshot;
    }

    private static ResearchOutput incoming(JournalData journal, Long associationPureId) {
        ResearchOutput output = new ResearchOutput();
        output.setUuid("ro-uuid");
        output.setPureId(9589074L);
        output.setTitle("Anyons in 1 + 1 dimensions");
        output.setJournal(journal);
        output.setJournalAssociationPureId(associationPureId);
        return output;
    }

    private static JournalData journal(String issn, String title, String uuid) {
        JournalData journal = new JournalData();
        journal.setIssn(issn);
        journal.setTitle(title);
        journal.setUuid(uuid);
        return journal;
    }

    private static long countScope(AggregateOutcome outcome, String scope) {
        return outcome.changes.stream().filter(change -> scope.equals(change.changeScope)).count();
    }

    private static SyncChange firstScope(AggregateOutcome outcome, String scope) {
        return outcome.changes.stream()
                .filter(change -> scope.equals(change.changeScope))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing scope " + scope));
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
