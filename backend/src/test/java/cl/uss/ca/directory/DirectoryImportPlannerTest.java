package cl.uss.ca.directory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class DirectoryImportPlannerTest {
    private static final byte[] PEPPER = "test-directory-pepper".getBytes(StandardCharsets.UTF_8);

    private DirectoryImportPlannerTest() {
    }

    public static void main(String[] args) {
        firstCensusCreatesAllRows();
        sameMatrixIsIdempotent();
        emailChangeKeepsExistingPerson();
        fileDuplicatesAreRejected();
        identityConflictDoesNotMerge();
        invalidRutBlocksInactivation();
        reactivationIsDetected();
        missingActivePeopleArePotentialInactivations();
        System.out.println("DirectoryImportPlannerTest OK");
    }

    static void firstCensusCreatesAllRows() {
        DirectoryIncomingRow row = row(2, "ana@uss.cl", "12345678");
        DirectoryImportPlan plan = new DirectoryImportPlanner().plan(List.of(row), new DirectoryCatalog(), List.of());
        assertEquals(1, plan.created);
        assertEquals(0, plan.updated);
        assertEquals(0, plan.rejected);
        assertEquals(true, plan.censusReliable);
    }

    static void sameMatrixIsIdempotent() {
        DirectoryIncomingRow row = row(2, "ana@uss.cl", "12345678");
        DirectoryPersonRef existing = person(9L, hmac("12345678"), true, DirectorySnapshotHash.of(row), "ana@uss.cl");
        DirectoryImportPlan plan = new DirectoryImportPlanner().plan(List.of(row), DirectoryCatalog.of(List.of(existing)), List.of());
        assertEquals(0, plan.created);
        assertEquals(1, plan.unchanged);
        assertEquals(DirectoryReconcileDecision.UNCHANGED, plan.rows.get(0).decision);
        assertEquals(9L, plan.rows.get(0).existing.id);
    }

    static void emailChangeKeepsExistingPerson() {
        DirectoryIncomingRow previous = row(2, "ana@uss.cl", "12345678");
        DirectoryPersonRef existing = person(9L, hmac("12345678"), true, DirectorySnapshotHash.of(previous), "ana@uss.cl");
        DirectoryIncomingRow next = row(2, "ana.nuevo@uss.cl", "12345678");
        DirectoryImportPlan plan = new DirectoryImportPlanner().plan(List.of(next), DirectoryCatalog.of(List.of(existing)), List.of());
        assertEquals(1, plan.updated);
        assertEquals(0, plan.created);
        assertEquals(9L, plan.rows.get(0).existing.id);
    }

    static void fileDuplicatesAreRejected() {
        DirectoryIncomingRow first = row(2, "ana@uss.cl", "12345678");
        DirectoryIncomingRow second = row(3, "otra@uss.cl", "12345678");
        DirectoryImportPlan plan = new DirectoryImportPlanner().plan(List.of(first, second), new DirectoryCatalog(), List.of());
        assertEquals(2, plan.rejected);
        assertEquals(0, plan.created);
        assertEquals(false, plan.censusReliable);
    }

    static void identityConflictDoesNotMerge() {
        DirectoryPersonRef byRut = person(1L, hmac("12345678"), true, "hash-a", "ana@uss.cl");
        DirectoryPersonRef byEmail = person(2L, hmac("87654321"), true, "hash-b", "otra@uss.cl");
        DirectoryIncomingRow row = row(2, "otra@uss.cl", "12345678");
        DirectoryImportPlan plan = new DirectoryImportPlanner().plan(
                List.of(row),
                DirectoryCatalog.of(List.of(byRut, byEmail)),
                List.of()
        );
        assertEquals(DirectoryReconcileDecision.ISSUE, plan.rows.get(0).decision);
        assertEquals("RUT_EMAIL_CONFLICT", plan.rows.get(0).issueCode);
        assertEquals(false, plan.censusReliable);
        assertEquals(0, plan.created);
        assertEquals(0, plan.updated);
    }

    static void invalidRutBlocksInactivation() {
        DirectoryIncomingRow valid = row(2, "ana@uss.cl", "12345678");
        DirectoryPersonRef existing = person(9L, hmac("12345678"), true, DirectorySnapshotHash.of(valid), "ana@uss.cl");
        DirectoryIncomingRow invalid = new DirectoryIncomingRow(
                3, "bad@uss.cl", "1234567X", null, "Bad", null, "Rut", null,
                "Bellavista", "c. Facultad de Medicina", "Docente", "bad@uss.cl", "hash-bad"
        );
        List<DirectoryIssue> issues = new ArrayList<>();
        issues.add(new DirectoryIssue(3, "INVALID_RUT", null, null));
        DirectoryImportPlan plan = new DirectoryImportPlanner().plan(
                List.of(valid, invalid),
                DirectoryCatalog.of(List.of(existing)),
                issues
        );
        assertEquals(false, plan.censusReliable);
        assertEquals(1, plan.rejected);
        assertEquals(1, plan.unchanged);
    }

    static void reactivationIsDetected() {
        DirectoryIncomingRow row = row(2, "ana@uss.cl", "12345678");
        DirectoryPersonRef existing = person(9L, hmac("12345678"), false, DirectorySnapshotHash.of(row), "ana@uss.cl");
        DirectoryImportPlan plan = new DirectoryImportPlanner().plan(List.of(row), DirectoryCatalog.of(List.of(existing)), List.of());
        assertEquals(1, plan.reactivated);
        assertEquals(DirectoryReconcileDecision.REACTIVATE, plan.rows.get(0).decision);
    }

    static void missingActivePeopleArePotentialInactivations() {
        DirectoryIncomingRow row = row(2, "ana@uss.cl", "12345678");
        DirectoryPersonRef seen = person(9L, hmac("12345678"), true, DirectorySnapshotHash.of(row), "ana@uss.cl");
        DirectoryPersonRef missing = person(8L, hmac("11111111"), true, "other-hash", "otra@uss.cl");
        DirectoryImportPlan plan = new DirectoryImportPlanner().plan(
                List.of(row),
                DirectoryCatalog.of(List.of(seen, missing)),
                List.of()
        );
        assertEquals(1, plan.wouldInactivate);
        assertEquals(true, plan.censusReliable);
    }

    private static DirectoryIncomingRow row(int source, String email, String body) {
        String rut = body + DirectoryRut.checkDigit(body);
        DirectoryIncomingRow draft = new DirectoryIncomingRow(
                source, email, rut, DirectoryRut.hmac(rut, PEPPER), "Ana", null, "Pérez", null,
                "Bellavista", "c. Facultad de Medicina", "Docente", email, null
        );
        return DirectoryRowMapper.withHash(draft);
    }

    private static DirectoryPersonRef person(long id, String rutHmac, boolean active, String hash, String email) {
        return new DirectoryPersonRef(id, "11111111-1111-1111-1111-11111111111" + id, rutHmac, active, hash, Set.of(email));
    }

    private static String hmac(String body) {
        return DirectoryRut.hmac(body + DirectoryRut.checkDigit(body), PEPPER);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
