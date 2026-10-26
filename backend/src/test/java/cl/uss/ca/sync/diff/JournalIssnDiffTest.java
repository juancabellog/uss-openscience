package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.JournalIssnData;
import cl.uss.ca.external.pure.model.JournalTitleData;
import cl.uss.ca.external.pure.model.PureJournal;

import java.util.List;

/**
 * Proposed cases: one Journal can carry multiple ISSNs.
 * Not executed by the agent.
 */
public final class JournalIssnDiffTest {

    private static final String UUID = "c3615b25-65ab-4855-917c-b77afccbf206";

    private JournalIssnDiffTest() {
    }

    public static void main(String[] args) {
        twoIssnsUnchanged();
        issnAddedIsUpdated();
        System.out.println("JournalIssnDiffTest OK");
    }

    static void twoIssnsUnchanged() {
        JournalSnapshot existing = snapshot(
                issn(7950139L, "0717-7518"),
                issn(61172239L, "0716-1549")
        );
        PureJournal incoming = incoming(
                issn(7950139L, "0717-7518"),
                issn(61172239L, "0716-1549")
        );
        AggregateOutcome outcome = new JournalAggregateComparer().compare(incoming, existing);
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
    }

    static void issnAddedIsUpdated() {
        JournalSnapshot existing = snapshot(issn(7950139L, "0717-7518"));
        PureJournal incoming = incoming(
                issn(7950139L, "0717-7518"),
                issn(61172239L, "0716-1549")
        );
        AggregateOutcome outcome = new JournalAggregateComparer().compare(incoming, existing);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertEquals(1, outcome.changes.stream().filter(change -> "ISSN".equals(change.changeScope)).count());
    }

    private static JournalSnapshot snapshot(JournalIssnData... issns) {
        JournalSnapshot snapshot = new JournalSnapshot();
        snapshot.id = 1L;
        snapshot.activeInPure = true;
        snapshot.uuid = UUID;
        snapshot.pureId = 7950138L;
        snapshot.titles.add(title(7950141L, "Revista Chilena de Nutrición"));
        snapshot.issns.addAll(List.of(issns));
        return snapshot;
    }

    private static PureJournal incoming(JournalIssnData... issns) {
        PureJournal journal = new PureJournal();
        journal.setUuid(UUID);
        journal.setPureId(7950138L);
        journal.getTitles().add(title(7950141L, "Revista Chilena de Nutrición"));
        journal.getIssns().addAll(List.of(issns));
        return journal;
    }

    private static JournalTitleData title(long pureId, String value) {
        JournalTitleData title = new JournalTitleData();
        title.setPureId(pureId);
        title.setTitle(value);
        return title;
    }

    private static JournalIssnData issn(long pureId, String value) {
        JournalIssnData issn = new JournalIssnData();
        issn.setPureId(pureId);
        issn.setIssn(value);
        return issn;
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
