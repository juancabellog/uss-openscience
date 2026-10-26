package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.PersonIdentifier;
import cl.uss.ca.external.pure.PurePerson;
import cl.uss.ca.external.pure.StaffAssociation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Proposed unit cases for the organization business-key diff.
 * Not executed by the agent. Run manually after adding a test runner if desired.
 */
public final class PersonOrganizationDiffTest {

    private static final String ORG_A = "1c368b9a-66f0-4580-87fe-8f87c1249002";
    private static final String ORG_B = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final String ORG_C = "11111111-2222-3333-4444-555555555555";

    private PersonOrganizationDiffTest() {
    }

    public static void main(String[] args) {
        sameUuidUnchanged();
        orderDoesNotMatter();
        realRemove();
        realAdd();
        realUpdateDoesNotRemoveAndAdd();
        localIdAbsentOnIncomingIsUnchanged();
        uuidCaseAndWhitespaceDoNotChange();
        fteSameScaleUnchanged();
        fteDifferentScaleUnchanged();
        fteRoundedVsExactUpdated();
        fteBothNullUnchanged();
        fteNullToValueUpdated();
        fteValueToNullUpdated();
        identifierUnchanged();
        identifierRealRemove();
        System.out.println("PersonOrganizationDiffTest OK");
    }

    static void sameUuidUnchanged() {
        StaffAssociation oldOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        StaffAssociation newOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        AggregateOutcome outcome = compare(List.of(oldOrg), List.of(newOrg));
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
        assertEquals(0, count(outcome, "Organization added"));
        assertEquals(0, count(outcome, "Organization removed"));
        assertFalse(outcome.childrenChanged);
    }

    static void orderDoesNotMatter() {
        List<StaffAssociation> oldOrgs = List.of(
                org(ORG_A, "A", LocalDate.of(2020, 1, 1)),
                org(ORG_B, "B", LocalDate.of(2021, 1, 1)),
                org(ORG_C, "C", LocalDate.of(2022, 1, 1))
        );
        List<StaffAssociation> newOrgs = List.of(
                org(ORG_C, "C", LocalDate.of(2022, 1, 1)),
                org(ORG_A, "A", LocalDate.of(2020, 1, 1)),
                org(ORG_B, "B", LocalDate.of(2021, 1, 1))
        );
        AggregateOutcome outcome = compare(oldOrgs, newOrgs);
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
        assertFalse(outcome.childrenChanged);
    }

    static void realRemove() {
        List<StaffAssociation> oldOrgs = List.of(
                org(ORG_A, "A", LocalDate.of(2020, 1, 1)),
                org(ORG_B, "B", LocalDate.of(2021, 1, 1)),
                org(ORG_C, "C", LocalDate.of(2022, 1, 1))
        );
        List<StaffAssociation> newOrgs = List.of(
                org(ORG_A, "A", LocalDate.of(2020, 1, 1)),
                org(ORG_C, "C", LocalDate.of(2022, 1, 1))
        );
        AggregateOutcome outcome = compare(oldOrgs, newOrgs);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertEquals(0, count(outcome, "Organization added"));
        assertEquals(1, count(outcome, "Organization removed"));
        assertEquals(ORG_B, first(outcome, "Organization removed").oldValue);
        assertTrue(outcome.childrenChanged);
    }

    static void realAdd() {
        List<StaffAssociation> oldOrgs = List.of(
                org(ORG_A, "A", LocalDate.of(2020, 1, 1)),
                org(ORG_C, "C", LocalDate.of(2022, 1, 1))
        );
        List<StaffAssociation> newOrgs = List.of(
                org(ORG_A, "A", LocalDate.of(2020, 1, 1)),
                org(ORG_B, "B", LocalDate.of(2021, 1, 1)),
                org(ORG_C, "C", LocalDate.of(2022, 1, 1))
        );
        AggregateOutcome outcome = compare(oldOrgs, newOrgs);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertEquals(1, count(outcome, "Organization added"));
        assertEquals(0, count(outcome, "Organization removed"));
        assertEquals(ORG_B, first(outcome, "Organization added").newValue);
    }

    static void realUpdateDoesNotRemoveAndAdd() {
        StaffAssociation oldOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        StaffAssociation newOrg = org(ORG_A, "Principal Investigator", LocalDate.of(2020, 1, 1));
        AggregateOutcome outcome = compare(List.of(oldOrg), List.of(newOrg));
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertEquals(0, count(outcome, "Organization added"));
        assertEquals(0, count(outcome, "Organization removed"));
        SyncChange field = first(outcome, "employmentType");
        assertEquals("ORGANIZATION", field.changeScope);
        assertEquals("Researcher", field.oldValue);
        assertEquals("Principal Investigator", field.newValue);
    }

    static void localIdAbsentOnIncomingIsUnchanged() {
        StaffAssociation oldOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        oldOrg.setPureId(500L);
        StaffAssociation newOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        newOrg.setPureId(null);
        AggregateOutcome outcome = compare(List.of(oldOrg), List.of(newOrg));
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
        assertEquals(0, count(outcome, "Organization added"));
        assertEquals(0, count(outcome, "Organization removed"));
    }

    static void fteSameScaleUnchanged() {
        StaffAssociation oldOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        oldOrg.setFte(new BigDecimal("0.7950"));
        StaffAssociation newOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        newOrg.setFte(new BigDecimal("0.7950"));
        AggregateOutcome outcome = compare(List.of(oldOrg), List.of(newOrg));
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
        assertEquals(0, countField(outcome, "fte"));
    }

    static void fteDifferentScaleUnchanged() {
        StaffAssociation oldOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        oldOrg.setFte(new BigDecimal("0.7950"));
        StaffAssociation newOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        newOrg.setFte(new BigDecimal("0.795"));
        AggregateOutcome outcome = compare(List.of(oldOrg), List.of(newOrg));
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
        assertEquals(0, countField(outcome, "fte"));
    }

    static void fteRoundedVsExactUpdated() {
        StaffAssociation oldOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        oldOrg.setFte(new BigDecimal("0.8000"));
        oldOrg.setOrganizationName("Universidad San Sebastián");
        StaffAssociation newOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        newOrg.setFte(new BigDecimal("0.795"));
        newOrg.setOrganizationName("Universidad San Sebastián");
        AggregateOutcome outcome = compare(List.of(oldOrg), List.of(newOrg));
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertEquals(0, count(outcome, "Organization added"));
        assertEquals(0, count(outcome, "Organization removed"));
        assertEquals(1, countField(outcome, "fte"));
        SyncChange field = firstField(outcome, "fte");
        assertEquals("0.8", field.oldValue);
        assertEquals("0.795", field.newValue);
        assertTrue(field.description.contains(ORG_A));
        assertTrue(field.description.contains("Universidad San Sebastián"));
    }

    static void fteBothNullUnchanged() {
        StaffAssociation oldOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        oldOrg.setFte(null);
        StaffAssociation newOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        newOrg.setFte(null);
        AggregateOutcome outcome = compare(List.of(oldOrg), List.of(newOrg));
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
    }

    static void fteNullToValueUpdated() {
        StaffAssociation oldOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        oldOrg.setFte(null);
        StaffAssociation newOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        newOrg.setFte(new BigDecimal("0.795"));
        AggregateOutcome outcome = compare(List.of(oldOrg), List.of(newOrg));
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertEquals(1, countField(outcome, "fte"));
    }

    static void fteValueToNullUpdated() {
        StaffAssociation oldOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        oldOrg.setFte(new BigDecimal("0.795"));
        StaffAssociation newOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        newOrg.setFte(null);
        AggregateOutcome outcome = compare(List.of(oldOrg), List.of(newOrg));
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertEquals(1, countField(outcome, "fte"));
    }

    static void uuidCaseAndWhitespaceDoNotChange() {
        StaffAssociation oldOrg = org("  " + ORG_A.toUpperCase() + "  ", "Researcher", LocalDate.of(2020, 1, 1));
        StaffAssociation newOrg = org(ORG_A, "Researcher", LocalDate.of(2020, 1, 1));
        AggregateOutcome outcome = compare(List.of(oldOrg), List.of(newOrg));
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
    }

    static void identifierUnchanged() {
        PersonIdentifier oldId = identifier("ScopusAuthor", "Scopus ID", "57915892800");
        PersonIdentifier newId = identifier("ScopusAuthor", "Scopus ID", "57915892800");
        AggregateOutcome outcome = compareIdentifiers(List.of(oldId), List.of(newId));
        assertEquals(AggregateOutcome.Kind.UNCHANGED, outcome.kind);
    }

    static void identifierRealRemove() {
        List<PersonIdentifier> oldIds = List.of(
                identifier("ScopusAuthor", "Scopus ID", "57915892800"),
                identifier("ScopusAuthor", "Scopus ID", "58668650700")
        );
        List<PersonIdentifier> newIds = List.of(
                identifier("ScopusAuthor", "Scopus ID", "57915892800")
        );
        AggregateOutcome outcome = compareIdentifiers(oldIds, newIds);
        assertEquals(AggregateOutcome.Kind.UPDATED, outcome.kind);
        assertEquals(1, outcome.changes.stream().filter(change ->
                "IDENTIFIER".equals(change.changeScope) && change.oldValue != null).count());
        assertEquals("58668650700", firstScope(outcome, "IDENTIFIER").oldValue);
    }

    private static AggregateOutcome compare(List<StaffAssociation> oldOrgs, List<StaffAssociation> newOrgs) {
        PersonSnapshot existing = personSnapshot();
        existing.asociaciones.addAll(oldOrgs);
        PurePerson incoming = personIncoming();
        incoming.setAsociaciones(new ArrayList<>(newOrgs));
        return new PersonAggregateComparer().compare(incoming, existing, false);
    }

    private static AggregateOutcome compareIdentifiers(
            List<PersonIdentifier> oldIds,
            List<PersonIdentifier> newIds
    ) {
        PersonSnapshot existing = personSnapshot();
        existing.identifiers.addAll(oldIds);
        PurePerson incoming = personIncoming();
        incoming.setIdentifiers(new ArrayList<>(newIds));
        return new PersonAggregateComparer().compare(incoming, existing, true);
    }

    private static PersonSnapshot personSnapshot() {
        PersonSnapshot snapshot = new PersonSnapshot();
        snapshot.id = 10L;
        snapshot.uuid = "person-uuid";
        snapshot.pureId = 99L;
        snapshot.firstName = "Ana";
        snapshot.lastName = "Perez";
        snapshot.activeInPure = true;
        return snapshot;
    }

    private static PurePerson personIncoming() {
        PurePerson person = new PurePerson();
        person.setUuid("person-uuid");
        person.setPureId(99L);
        person.setFirstName("Ana");
        person.setLastName("Perez");
        return person;
    }

    private static StaffAssociation org(String uuid, String employmentType, LocalDate start) {
        StaffAssociation association = new StaffAssociation();
        association.setOrganizationUuid(uuid);
        association.setEmploymentType(employmentType);
        association.setStartDate(start);
        association.setPrimaryAssociation(true);
        association.setFte(new BigDecimal("1.0000"));
        return association;
    }

    private static PersonIdentifier identifier(String discriminator, String type, String value) {
        PersonIdentifier identifier = new PersonIdentifier();
        identifier.setTypeDiscriminator(discriminator);
        identifier.setIdentifierType(type);
        identifier.setValue(value);
        return identifier;
    }

    private static long count(AggregateOutcome outcome, String description) {
        return outcome.changes.stream().filter(change -> description.equals(change.description)).count();
    }

    private static SyncChange first(AggregateOutcome outcome, String description) {
        return outcome.changes.stream()
                .filter(change -> description.equals(change.description)
                        || (change.description != null && change.description.endsWith("||" + description)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing change " + description));
    }

    private static long countField(AggregateOutcome outcome, String field) {
        return outcome.changes.stream()
                .filter(change -> "ORGANIZATION".equals(change.changeScope))
                .filter(change -> change.description != null
                        && (change.description.equals(field) || change.description.endsWith("||" + field)))
                .count();
    }

    private static SyncChange firstField(AggregateOutcome outcome, String field) {
        return first(outcome, field);
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

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("expected false");
        }
    }
}
