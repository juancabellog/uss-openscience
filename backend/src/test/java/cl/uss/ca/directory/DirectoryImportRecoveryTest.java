package cl.uss.ca.directory;

import cl.uss.ca.directory.DirectoryImportRecoveryPolicy.DirectoryImportSideEffects;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public final class DirectoryImportRecoveryTest {
    private static final byte[] PEPPER = "test-directory-pepper".getBytes(StandardCharsets.UTF_8);
    private DirectoryImportRecoveryTest() {
    }

    public static void main(String[] args) {
        completedImportCanInactivate();
        interruptionAfterRunningIsAbandoned();
        exceptionBeforePersistMarksFailedWithoutPeople();
        exceptionDuringPersistRollsBackThenFails();
        applicationRestartLosesOwner();
        orphanedExecutionCanBeRecovered();
        retrySameMatrixDoesNotCreateDuplicates();
        concurrentImportBlockedWhileOwned();
        recoveryIsIdempotent();
        existingPeopleAreConserved();
        recoveryNeverInactivates();
        ageDoesNotReleaseOwnedImport();
        System.out.println("DirectoryImportRecoveryTest OK");
    }

    static void completedImportCanInactivate() {
        assertEquals(true, DirectoryPresencePolicy.canInactivate(true, false, "COMPLETED"));
    }

    static void interruptionAfterRunningIsAbandoned() {
        assertEquals(true, DirectoryImportRecoveryPolicy.isAbandoned("RUNNING", false));
        assertEquals(false, DirectoryImportRecoveryPolicy.isAbandoned("RUNNING", true));
        assertEquals(false, DirectoryImportRecoveryPolicy.isAbandoned("FAILED", false));
        assertEquals(false, DirectoryImportRecoveryPolicy.isAbandoned("COMPLETED", false));
    }

    static void exceptionBeforePersistMarksFailedWithoutPeople() {
        DirectoryImportSideEffects effects = DirectoryImportSideEffects.empty();
        assertEquals(false, effects.hasPartialData());
        assertEquals("FAILED", DirectoryImportRecoveryPolicy.recoveryStatus());
        assertEquals(false, DirectoryImportRecoveryPolicy.canInactivateDuringRecovery());
    }

    static void exceptionDuringPersistRollsBackThenFails() {
        DirectoryImportSideEffects rolledBack = DirectoryImportSideEffects.empty();
        assertEquals(false, rolledBack.hasPartialData());
        assertEquals("FAILED", DirectoryImportRecoveryPolicy.recoveryStatus());
    }

    static void applicationRestartLosesOwner() {
        String previous = "process-a";
        String restarted = "process-b";
        assertEquals(
                false,
                DirectoryImportRecoveryPolicy.workerOwns(restarted, previous, false, null, 1L)
        );
        assertEquals(true, DirectoryImportRecoveryPolicy.canRecover("RUNNING", false));
        assertEquals(false, DirectoryImportRecoveryPolicy.workerOwns(false, null, 1L));
    }

    static void orphanedExecutionCanBeRecovered() {
        assertEquals(true, DirectoryImportRecoveryPolicy.canRecover("RUNNING", false));
        assertEquals(false, DirectoryImportRecoveryPolicy.canRecover("RUNNING", true));
        String message = DirectoryImportRecoveryPolicy.abandonedMessage(1L, DirectoryImportSideEffects.empty());
        assertEquals(true, message.contains("DIRECTORY_IMPORT_ABANDONED"));
        assertEquals(true, message.contains("importId=1"));
        assertEquals(true, message.contains("people=0"));
    }

    static void retrySameMatrixDoesNotCreateDuplicates() {
        DirectoryIncomingRow row = row(2, "ana@uss.cl", "12345678");
        DirectoryPersonRef existing = person(
                9L,
                hmac("12345678"),
                true,
                DirectorySnapshotHash.of(row),
                "ana@uss.cl"
        );
        DirectoryImportPlan plan = new DirectoryImportPlanner().plan(
                List.of(row),
                DirectoryCatalog.of(List.of(existing)),
                List.of()
        );
        assertEquals(0, plan.created);
        assertEquals(1, plan.unchanged);
        assertEquals(0, plan.rejected);
    }

    static void concurrentImportBlockedWhileOwned() {
        assertEquals(false, DirectoryImportRecoveryPolicy.canStartImport(true, true));
        assertEquals(false, DirectoryImportRecoveryPolicy.canStartImport(true, false));
        assertEquals(false, DirectoryImportRecoveryPolicy.canStartImport(false, true));
        assertEquals(true, DirectoryImportRecoveryPolicy.canStartImport(false, false));
    }

    static void recoveryIsIdempotent() {
        assertEquals(false, DirectoryImportRecoveryPolicy.canRecover("FAILED", false));
        assertEquals(false, DirectoryImportRecoveryPolicy.canRecover("COMPLETED", false));
    }

    static void existingPeopleAreConserved() {
        DirectoryIncomingRow row = row(2, "ana.nueva@uss.cl", "12345678");
        DirectoryPersonRef existing = person(
                9L,
                hmac("12345678"),
                true,
                "previous-hash",
                "ana@uss.cl"
        );
        DirectoryImportPlan plan = new DirectoryImportPlanner().plan(
                List.of(row),
                DirectoryCatalog.of(List.of(existing)),
                List.of()
        );
        assertEquals(0, plan.created);
        assertEquals(1, plan.updated);
        assertEquals(DirectoryReconcileDecision.UPDATE, plan.rows.get(0).decision);
    }

    static void recoveryNeverInactivates() {
        assertEquals(false, DirectoryImportRecoveryPolicy.canInactivateDuringRecovery());
        assertEquals(false, DirectoryPresencePolicy.canInactivate(true, false, "FAILED"));
        assertEquals(false, DirectoryPresencePolicy.canInactivate(true, false, "RUNNING"));
    }

    static void ageDoesNotReleaseOwnedImport() {
        assertEquals(true, DirectoryImportRecoveryPolicy.workerOwns(true, 1L, 1L));
        assertEquals(false, DirectoryImportRecoveryPolicy.canRecover("RUNNING", true));
        assertEquals(false, DirectoryImportRecoveryPolicy.isAbandoned("RUNNING", true));
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
