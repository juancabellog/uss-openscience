package cl.uss.ca.directory;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Phase 0 directory identity tests. Not executed by the agent.
 * Fixtures are synthetic; they do not use rows from the academic matrix.
 */
public final class DirectoryImportIdentityTest {
    private static final byte[] PEPPER = "test-directory-pepper".getBytes(StandardCharsets.UTF_8);

    private DirectoryImportIdentityTest() {
    }

    public static void main(String[] args) {
        emailNormalization();
        empresaDuplicateIsNotAlternate();
        empresaDistinctBecomesAlternate();
        rutNormalizeAndHmacStable();
        facultyPrefixIsStripped();
        displayNameSkipsBlanks();
        createWhenRutAndEmailAreNew();
        emailChangeDoesNotCreatePerson();
        inactiveRutMatchReactivates();
        emailOwnedByOtherRutIsAmbiguous();
        rutOwnedByOtherEmailIsAmbiguous();
        namesAreNeverIdentity();
        missingRutDoesNotCreate();
        invalidEmailIsIssue();
        System.out.println("DirectoryImportIdentityTest OK");
    }

    static void emailNormalization() {
        assertEquals("ana.perez@uss.cl", DirectoryEmail.normalize("  Ana.Perez@USS.CL "));
        assertEquals(true, DirectoryEmail.isUsable("ana.perez@uss.cl"));
        assertEquals(false, DirectoryEmail.isUsable("not-an-email"));
    }

    static void empresaDuplicateIsNotAlternate() {
        assertEquals(null, DirectoryReconciler.alternateEmpresaEmail("ana.perez@uss.cl", "ana.perez@uss.cl"));
    }

    static void empresaDistinctBecomesAlternate() {
        assertEquals(
                "ana.perez.alt@uss.cl",
                DirectoryReconciler.alternateEmpresaEmail("Ana.Perez.Alt@USS.CL", "ana.perez@uss.cl")
        );
    }

    static void rutNormalizeAndHmacStable() {
        String body = "12345678";
        String normalized = body + DirectoryRut.checkDigit(body);
        assertEquals(normalized, DirectoryRut.normalize("12.345.678-" + DirectoryRut.checkDigit(body)));
        assertEquals(true, DirectoryRut.isValid(normalized));
        String hmac = DirectoryRut.hmac(normalized, PEPPER);
        assertEquals(64, hmac.length());
        assertEquals(hmac, DirectoryRut.hmac(normalized, PEPPER));
    }

    static void facultyPrefixIsStripped() {
        assertEquals("c. Facultad de Medicina", DirectoryFaculty.normalizeRaw(" c. Facultad de Medicina "));
        assertEquals("Facultad de Medicina", DirectoryFaculty.label("c. Facultad de Medicina"));
        assertEquals("Rectoría", DirectoryFaculty.label("j. Rectoría"));
    }

    static void displayNameSkipsBlanks() {
        assertEquals("Ana Pérez", DirectoryDisplayName.of("Ana", "  ", "Pérez", null));
    }

    static void createWhenRutAndEmailAreNew() {
        DirectoryIncomingRow row = row("ana.perez@uss.cl", hmac("12345678"), "hash-1");
        DirectoryReconcileResult result = DirectoryReconciler.decide(row, null, null);
        assertEquals(DirectoryReconcileDecision.CREATE, result.decision);
    }

    static void emailChangeDoesNotCreatePerson() {
        DirectoryPersonRef existing = person(7L, hmac("12345678"), true, "hash-old", "ana.perez@uss.cl");
        DirectoryIncomingRow row = row("ana.perez.nuevo@uss.cl", hmac("12345678"), "hash-new");
        DirectoryReconcileResult result = DirectoryReconciler.decide(row, existing, null);
        assertEquals(DirectoryReconcileDecision.UPDATE, result.decision);
        assertEquals(7L, result.existing.id);
    }

    static void inactiveRutMatchReactivates() {
        DirectoryPersonRef existing = person(7L, hmac("12345678"), false, "hash-1", "ana.perez@uss.cl");
        DirectoryIncomingRow row = row("ana.perez@uss.cl", hmac("12345678"), "hash-1");
        DirectoryReconcileResult result = DirectoryReconciler.decide(row, existing, existing);
        assertEquals(DirectoryReconcileDecision.REACTIVATE, result.decision);
    }

    static void emailOwnedByOtherRutIsAmbiguous() {
        DirectoryPersonRef owner = person(1L, hmac("11111111"), true, "hash-a", "ana.perez@uss.cl");
        DirectoryIncomingRow row = row("ana.perez@uss.cl", hmac("12345678"), "hash-b");
        DirectoryReconcileResult result = DirectoryReconciler.decide(row, null, owner);
        assertEquals(DirectoryReconcileDecision.ISSUE, result.decision);
        assertEquals("EMAIL_RUT_CONFLICT", result.issueCode);
    }

    static void rutOwnedByOtherEmailIsAmbiguous() {
        DirectoryPersonRef byRut = person(1L, hmac("12345678"), true, "hash-a", "ana@uss.cl");
        DirectoryPersonRef byEmail = person(2L, hmac("87654321"), true, "hash-b", "otra@uss.cl");
        DirectoryIncomingRow row = row("otra@uss.cl", hmac("12345678"), "hash-c");
        DirectoryReconcileResult result = DirectoryReconciler.decide(row, byRut, byEmail);
        assertEquals(DirectoryReconcileDecision.ISSUE, result.decision);
        assertEquals("RUT_EMAIL_CONFLICT", result.issueCode);
    }

    static void namesAreNeverIdentity() {
        DirectoryPersonRef existing = person(7L, hmac("12345678"), true, "hash-1", "ana.perez@uss.cl");
        DirectoryIncomingRow samePersonNewName = new DirectoryIncomingRow(
                2,
                "ana.perez@uss.cl",
                rut("12345678"),
                hmac("12345678"),
                "María",
                null,
                "Pérez",
                null,
                "Bellavista",
                "c. Facultad de Medicina",
                "Docente",
                "ana.perez@uss.cl",
                "hash-2"
        );
        DirectoryReconcileResult result = DirectoryReconciler.decide(samePersonNewName, existing, existing);
        assertEquals(DirectoryReconcileDecision.UPDATE, result.decision);
        assertEquals(7L, result.existing.id);
    }

    static void missingRutDoesNotCreate() {
        DirectoryIncomingRow row = new DirectoryIncomingRow(
                3, "ana@uss.cl", null, null, "Ana", null, "Pérez", null,
                "Bellavista", "c. Facultad de Medicina", "Docente", "ana@uss.cl", "hash-x"
        );
        DirectoryReconcileResult result = DirectoryReconciler.decide(row, null, null);
        assertEquals(DirectoryReconcileDecision.ISSUE, result.decision);
        assertEquals("MISSING_RUT", result.issueCode);
    }

    static void invalidEmailIsIssue() {
        DirectoryIncomingRow row = new DirectoryIncomingRow(
                4, "x", rut("12345678"), hmac("12345678"), "Ana", null, "Pérez", null,
                "Bellavista", "c. Facultad de Medicina", "Docente", "not-an-email", "hash-y"
        );
        DirectoryReconcileResult result = DirectoryReconciler.decide(row, null, null);
        assertEquals(DirectoryReconcileDecision.ISSUE, result.decision);
        assertEquals("INVALID_EMAIL", result.issueCode);
    }

    private static DirectoryIncomingRow row(String email, String rutHmac, String hash) {
        return new DirectoryIncomingRow(
                1,
                email,
                rut("12345678"),
                rutHmac,
                "Ana",
                null,
                "Pérez",
                null,
                "Bellavista",
                "c. Facultad de Medicina",
                "Docente",
                email,
                hash
        );
    }

    private static DirectoryPersonRef person(
            long id,
            String rutHmac,
            boolean active,
            String hash,
            String email
    ) {
        return new DirectoryPersonRef(id, "11111111-1111-1111-1111-111111111111", rutHmac, active, hash, Set.of(email));
    }

    private static String rut(String body) {
        return body + DirectoryRut.checkDigit(body);
    }

    private static String hmac(String body) {
        return DirectoryRut.hmac(rut(body), PEPPER);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
