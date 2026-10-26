package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.PureOrganization;

/**
 * Semantic diff for Organizations. Not executed by the agent.
 */
public final class OrganizationAggregateComparerTest {

    private static final String UUID = "53f83554-58f8-4ef8-b982-a0866076d2ff";

    private OrganizationAggregateComparerTest() {
    }

    public static void main(String[] args) {
        createdWhenMissing();
        unchangedWhenSame();
        updatedWhenNameChanges();
        reactivatedWhenInactive();
        System.out.println("OrganizationAggregateComparerTest OK");
    }

    static void createdWhenMissing() {
        AggregateOutcome outcome = new OrganizationAggregateComparer().compare(incoming("Facultad"), null);
        assertEquals(AggregateOutcome.Kind.CREATED, outcome.kind);
    }

    static void unchangedWhenSame() {
        OrganizationSnapshot existing = snapshot("Facultad", true);
        AggregateOutcome outcome = new OrganizationAggregateComparer().compare(incoming("Facultad"), existing);
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
    }

    static void updatedWhenNameChanges() {
        OrganizationSnapshot existing = snapshot("Facultad", true);
        AggregateOutcome outcome = new OrganizationAggregateComparer().compare(incoming("Facultad de Medicina"), existing);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
    }

    static void reactivatedWhenInactive() {
        OrganizationSnapshot existing = snapshot("Facultad", false);
        AggregateOutcome outcome = new OrganizationAggregateComparer().compare(incoming("Facultad"), existing);
        assertEquals(AggregateOutcome.Kind.REACTIVATED, outcome.kind);
    }

    private static PureOrganization incoming(String nameEs) {
        PureOrganization organization = new PureOrganization();
        organization.setUuid(UUID);
        organization.setPureId(101L);
        organization.setNameEs(nameEs);
        organization.setNameEn("Faculty");
        return organization;
    }

    private static OrganizationSnapshot snapshot(String nameEs, boolean active) {
        OrganizationSnapshot snapshot = new OrganizationSnapshot();
        snapshot.id = 7L;
        snapshot.uuid = UUID;
        snapshot.pureId = 101L;
        snapshot.nameEs = nameEs;
        snapshot.nameEn = "Faculty";
        snapshot.activeInPure = active;
        return snapshot;
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
