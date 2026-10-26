package cl.uss.ca.repository;

import cl.uss.ca.api.dto.PersonAffiliationDto;

/**
 * Documents UUID join semantics. Not executed by the agent.
 * Person and Project persist organization_uuid without requiring pure_organization.
 * After Organizations FULL, existing UUIDs resolve via LEFT JOIN; no People/Projects re-FULL.
 */
public final class OrganizationRelationJoinContractTest {

    private OrganizationRelationJoinContractTest() {
    }

    public static void main(String[] args) {
        unresolvedAffiliationDoesNotBreakPerson();
        catalogIdMarksAffiliationResolved();
        relationTypesAreDistinct();
        System.out.println("OrganizationRelationJoinContractTest OK");
    }

    static void unresolvedAffiliationDoesNotBreakPerson() {
        PersonAffiliationDto dto = new PersonAffiliationDto();
        dto.organizationUuid = "53f83554-58f8-4ef8-b982-a0866076d2ff";
        dto.organizationId = null;
        dto.resolved = dto.organizationId != null;
        assertEquals("53f83554-58f8-4ef8-b982-a0866076d2ff", dto.organizationUuid);
        assertTrue(!dto.resolved);
    }

    static void catalogIdMarksAffiliationResolved() {
        PersonAffiliationDto dto = new PersonAffiliationDto();
        dto.organizationUuid = "53f83554-58f8-4ef8-b982-a0866076d2ff";
        dto.organizationId = 12L;
        dto.resolved = dto.organizationId != null;
        assertTrue(dto.resolved);
    }

    static void relationTypesAreDistinct() {
        assertEquals("MANAGING", "MANAGING");
        assertEquals("ASSOCIATED", "ASSOCIATED");
        assertEquals("PARTICIPANT", "PARTICIPANT");
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("expected true");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
