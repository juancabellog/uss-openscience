package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.ProjectIdentifierData;
import cl.uss.ca.external.pure.model.ProjectOrganizationData;
import cl.uss.ca.external.pure.model.ProjectParticipantData;
import cl.uss.ca.external.pure.model.PureProject;
import cl.uss.ca.sync.SyncJobSupport;
import cl.uss.ca.sync.SyncRunState;

/**
 * Semantic diff and FULL inactivation contract. Not executed by the agent.
 */
public final class ProjectAggregateComparerTest {
    private static final String UUID = "a93fb6e9-3314-4a87-86e4-6d01169dd581";

    private ProjectAggregateComparerTest() {
    }

    public static void main(String[] args) {
        created();
        unchangedIgnoresArrayOrder();
        parentUpdate();
        participantUpdate();
        participantRemoved();
        identifierUpdate();
        organizationUpdate();
        reactivation();
        failedFullDoesNotInactivate();
        cancelledFullDoesNotReachInactivation();
        completeFullCanInactivate();
        System.out.println("ProjectAggregateComparerTest OK");
    }

    static void created() {
        AggregateOutcome outcome = new ProjectAggregateComparer().compare(incoming(), null);
        assertEquals(AggregateOutcome.Kind.CREATED, outcome.kind);
        assertTrue(outcome.persistParent());
        assertTrue(outcome.persistChildren());
    }

    static void unchangedIgnoresArrayOrder() {
        ProjectSnapshot existing = snapshot();
        existing.organizations.add(org("bbb"));
        existing.organizations.add(org("aaa"));
        PureProject incoming = incoming();
        incoming.getOrganizations().add(org("aaa"));
        incoming.getOrganizations().add(org("bbb"));
        AggregateOutcome outcome = new ProjectAggregateComparer().compare(incoming, existing);
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
    }

    static void parentUpdate() {
        ProjectSnapshot existing = snapshot();
        PureProject incoming = incoming();
        incoming.setAcronym("NEW");
        AggregateOutcome outcome = new ProjectAggregateComparer().compare(incoming, existing);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertTrue(outcome.parentChanged);
    }

    static void participantUpdate() {
        ProjectSnapshot existing = snapshot();
        existing.participants.add(participant(1L, "Danixa", "uuid-1"));
        PureProject incoming = incoming();
        incoming.getParticipants().add(participant(1L, "Danixa Pamela", "uuid-1"));
        AggregateOutcome outcome = new ProjectAggregateComparer().compare(incoming, existing);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertTrue(outcome.childrenChanged);
    }

    static void participantRemoved() {
        ProjectSnapshot existing = snapshot();
        existing.participants.add(participant(1L, "Danixa", "uuid-1"));
        existing.participants.add(participant(2L, "Otro", "uuid-2"));
        PureProject incoming = incoming();
        incoming.getParticipants().add(participant(1L, "Danixa", "uuid-1"));
        AggregateOutcome outcome = new ProjectAggregateComparer().compare(incoming, existing);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
    }

    static void identifierUpdate() {
        ProjectSnapshot existing = snapshot();
        existing.identifiers.add(identifier(10L, "3200418"));
        PureProject incoming = incoming();
        incoming.getIdentifiers().add(identifier(10L, "999"));
        AggregateOutcome outcome = new ProjectAggregateComparer().compare(incoming, existing);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
    }

    static void organizationUpdate() {
        ProjectSnapshot existing = snapshot();
        existing.organizations.add(org("aaa"));
        PureProject incoming = incoming();
        incoming.getOrganizations().add(org("bbb"));
        AggregateOutcome outcome = new ProjectAggregateComparer().compare(incoming, existing);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
    }

    static void reactivation() {
        ProjectSnapshot existing = snapshot();
        existing.activeInPure = false;
        AggregateOutcome outcome = new ProjectAggregateComparer().compare(incoming(), existing);
        assertEquals(AggregateOutcome.Kind.REACTIVATED, outcome.kind);
    }

    static void failedFullDoesNotInactivate() {
        SyncRunState run = new SyncRunState("PROJECTS", "user-1");
        run.processed.set(10);
        run.errors.set(1);
        assertTrue(!new SyncJobSupport().canInactivate(run, 11));
    }

    static void cancelledFullDoesNotReachInactivation() {
        SyncRunState run = new SyncRunState("PROJECTS", "user-1");
        run.processed.set(100);
        assertTrue(new SyncJobSupport().canInactivate(run, 100));
        // Cancellation throws ownership loss before markMissing; a failed/incomplete census cannot inactivate.
        run.errors.set(1);
        assertTrue(!new SyncJobSupport().canInactivate(run, 100));
    }

    static void completeFullCanInactivate() {
        SyncRunState run = new SyncRunState("PROJECTS", "user-1");
        run.processed.set(631);
        assertTrue(new SyncJobSupport().canInactivate(run, 631));
    }

    private static ProjectSnapshot snapshot() {
        ProjectSnapshot snapshot = new ProjectSnapshot();
        snapshot.id = 1L;
        snapshot.uuid = UUID;
        snapshot.pureId = 25387407L;
        snapshot.activeInPure = true;
        snapshot.acronym = "FONDECYT P-3200418";
        return snapshot;
    }

    private static PureProject incoming() {
        PureProject project = new PureProject();
        project.setUuid(UUID);
        project.setPureId(25387407L);
        project.setAcronym("FONDECYT P-3200418");
        return project;
    }

    private static ProjectParticipantData participant(long pureId, String firstName, String personUuid) {
        ProjectParticipantData data = new ProjectParticipantData();
        data.setPureId(pureId);
        data.setFirstName(firstName);
        data.setPersonUuid(personUuid);
        return data;
    }

    private static ProjectIdentifierData identifier(long pureId, String value) {
        ProjectIdentifierData data = new ProjectIdentifierData();
        data.setPureId(pureId);
        data.setValue(value);
        return data;
    }

    private static ProjectOrganizationData org(String uuid) {
        ProjectOrganizationData data = new ProjectOrganizationData();
        data.setOrganizationUuid(uuid);
        return data;
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
