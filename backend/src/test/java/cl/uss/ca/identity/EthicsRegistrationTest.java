package cl.uss.ca.identity;

import cl.uss.ca.ethics.auth.AssignmentStatus;
import cl.uss.ca.ethics.auth.AuthorizationContext;
import cl.uss.ca.ethics.auth.AuthorizationService;
import cl.uss.ca.ethics.auth.RoleAssignment;
import cl.uss.ca.ethics.auth.ScopeType;
import cl.uss.ca.ethics.auth.YamlRoleDefinitionProvider;

import java.util.List;

public final class EthicsRegistrationTest {
    private EthicsRegistrationTest() {
    }

    public static void main(String[] args) {
        domains();
        existingInstitutionalReusesTheAccount();
        externalSelfRegistrationIsDenied();
        administratorCreatesExternalReviewer();
        externalReviewerSeesOnlyTheAssignedProcess();
        externalCannotReceiveResearcher();
        pureLinkedDataStaysReadonly();
        revokingEthicsKeepsEventos();
        disablingAnActiveReviewerNeedsConfirmation();
        System.out.println("EthicsRegistrationTest OK");
    }

    private static void domains() {
        check(InstitutionalDomains.isAllowedInstitutionalEmail("persona@uss.cl"), "uss.cl");
        check(InstitutionalDomains.isAllowedInstitutionalEmail("Persona@CORREO.uss.cl"), "correo.uss.cl ignores case");
        check(InstitutionalDomains.isAllowedInstitutionalEmail("persona@docente.uss.cl"), "docente.uss.cl");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("persona@gmail.com"), "gmail");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("persona@universidad.cl"), "another university");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("persona@fakeuss.cl"), "a lookalike domain");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("persona@uss.cl.example.org"), "an extra @ is not a uss address");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("persona@uss.cl.otrodominio.com"), "a suffix is not the uss domain");
    }

    private static void existingInstitutionalReusesTheAccount() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        service.registerAccount("u-eventos", "juan@uss.cl", "Juan", "Pérez", null);
        service.grantApplication("u-eventos", ApplicationCode.EVENTOS, "system", "2026-10-29T12:00:00Z");
        UserAccountRef again = service.registerInstitutional("Juan@uss.cl", "Juan", "Pérez", "self", "2026-10-29T12:05:00Z", null);
        check(again.id().equals("u-eventos"), "an existing account is reused");
        check(service.store().accountCount() == 1, "a second account is not created");
        check(service.applicationActive("u-eventos", ApplicationCode.ETHICS), "ethics becomes active");
        check(service.applicationActive("u-eventos", ApplicationCode.EVENTOS), "eventos stays active");
        List<RoleAssignment> roles = service.ethicsRoles("u-eventos");
        check(roles.size() == 1 && "RESEARCHER".equals(roles.get(0).roleId()), "the institutional user receives one researcher role");
        service.registerInstitutional("juan@uss.cl", "Juan", "Pérez", "self", "2026-10-29T12:06:00Z", null);
        check(service.ethicsRoles("u-eventos").size() == 1, "a second entry does not duplicate researcher");
        check(service.store().notices().stream().anyMatch(item -> EthicsAccessService.SAME_CREDENTIALS.equals(item.template())), "the notice uses the existing credentials");
        check(service.store().notices().stream().noneMatch(item -> item.template().toLowerCase().contains("password")), "the notice does not carry a password");
    }

    private static void externalSelfRegistrationIsDenied() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        service.registerAccount("u-out", "persona@gmail.com", "Persona", "Externa", null);
        try {
            service.grantEthicsAccess("u-out", "self", "2026-10-29T12:00:00Z", null);
            throw new AssertionError("an external account must not provision ethics by itself");
        } catch (IdentityRuleException ex) {
            check("ETHICS_SELF_REGISTRATION_DENIED".equals(ex.code()), "self-provision names the institutional rule");
        }
        check(service.store().application("u-out", ApplicationCode.ETHICS) == null, "ethics was not created");
        check(service.ethicsRoles("u-out").isEmpty(), "researcher was not granted");
    }

    private static void administratorCreatesExternalReviewer() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        try {
            service.createExternalReviewer(false, "alberto@universidad.cl", "Alberto", "Soto", "Universidad Austral", "Docente", "u-admin", "2026-10-29T12:00:00Z");
            throw new AssertionError("without manage users the external reviewer is not created");
        } catch (IdentityRuleException ex) {
            check("MANAGE_USERS_REQUIRED".equals(ex.code()), "the admin action fails closed");
        }
        check(service.store().accountCount() == 0, "a denied call does not create an account");
        UserAccountRef created = service.createExternalReviewer(true, "alberto@universidad.cl", "Alberto", "Soto", "Universidad Austral", "Docente", "u-admin", "2026-10-29T12:00:00Z");
        SharedPerson person = service.store().person(created.personId());
        check(person.kind() == PersonKind.EXTERNAL && person.purePersonId() == null, "the reviewer is a local external person");
        check(service.applicationActive(created.id(), ApplicationCode.ETHICS), "ethics is active");
        check(service.ethicsRoles(created.id()).isEmpty(), "the external reviewer does not receive researcher");
        check(service.store().notices().stream().anyMatch(item -> EthicsAccessService.EXTERNAL_INVITED.equals(item.template())), "an access notice is recorded");
        UserAccountRef second = service.createExternalReviewer(true, "Alberto@universidad.cl", "Alberto", "Soto", "Universidad Austral", "Docente", "u-admin", "2026-10-29T12:10:00Z");
        check(second.id().equals(created.id()) && service.store().accountCount() == 1, "the same email reuses the account");
    }

    private static void externalReviewerSeesOnlyTheAssignedProcess() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        UserAccountRef created = service.createExternalReviewer(true, "alberto@universidad.cl", "Alberto", "Soto", "Universidad Austral", "Docente", "u-admin", "2026-10-29T12:00:00Z");
        AuthorizationService authorization = new AuthorizationService(YamlRoleDefinitionProvider.loadClasspath(), new EthicsIdentityTestAccess(List.of()));
        check(authorization.getEffectiveCapabilities(created.id(), AuthorizationContext.empty()).isEmpty(), "without an assignment there is no review access");
        RoleAssignment assignment = service.store().addEthicsRole(new RoleAssignment(
                null, created.id(), "REVIEWER", ScopeType.PROCESS, "CECUA-2026-004:R1", AssignmentStatus.ACTIVE,
                null, null, "u-admin", "2026-10-29T12:30:00Z", "u-admin", "2026-10-29T12:30:00Z"
        ));
        AuthorizationService assigned = new AuthorizationService(
                YamlRoleDefinitionProvider.loadClasspath(),
                new EthicsIdentityTestAccess(List.of(assignment))
        );
        var onRound = assigned.getEffectiveCapabilities(created.id(), new AuthorizationContext(null, null, null, "CECUA-2026-004:R1", null));
        var elsewhere = assigned.getEffectiveCapabilities(created.id(), new AuthorizationContext(null, null, null, "CECUA-2026-004:R2", null));
        check(onRound.contains("VIEW_OWN_REVIEW") && !onRound.contains("CREATE_REQUEST"), "the assignment grants reviewer work");
        check(elsewhere.isEmpty(), "another process stays closed");
    }

    private static void externalCannotReceiveResearcher() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        UserAccountRef created = service.createExternalReviewer(true, "alberto@universidad.cl", "Alberto", "Soto", "Universidad Austral", "Docente", "u-admin", "2026-10-29T12:00:00Z");
        try {
            service.grantStructuralRole(true, created.id(), "RESEARCHER", "u-admin", "2026-10-29T12:10:00Z");
            throw new AssertionError("an external person must not become a researcher");
        } catch (IdentityRuleException ex) {
            check("EXTERNAL_ROLE_DENIED".equals(ex.code()), "the structural role is denied");
        }
        try {
            service.grantStructuralRole(true, created.id(), "ADMINISTRATOR", "u-admin", "2026-10-29T12:10:00Z");
            throw new AssertionError("an external person must not become an administrator");
        } catch (IdentityRuleException ex) {
            check("EXTERNAL_ROLE_DENIED".equals(ex.code()), "administration is denied");
        }
        check(service.ethicsRoles(created.id()).isEmpty(), "no structural role was stored");
    }

    private static void pureLinkedDataStaysReadonly() {
        Recording pure = new Recording();
        pure.row = new PurePersonView(10L, "Juan", "Pérez", "juan.perez@uss.cl", "Docente", null, "Facultad A", null);
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), pure);
        UserAccountRef account = service.registerInstitutional("juan.perez@uss.cl", "Juan", "Pérez", "self", "2026-10-29T12:00:00Z", pure.row);
        SharedPerson person = service.store().person(account.personId());
        check(person.kind() == PersonKind.INSTITUTIONAL && person.purePersonId() == 10L, "a reliable pure id links the institutional person");
        check("Facultad A".equals(service.profile(person.id()).faculty()), "the profile shows the pure faculty");
        try {
            service.updateLocalPerson(person.id(), new SharedPerson(
                    person.id(), 10L, "Otro", null, null, null, null, null, "Facultad B", null, null, PersonKind.INSTITUTIONAL
            ));
            throw new AssertionError("pure data must stay readonly");
        } catch (IdentityRuleException ex) {
            check("PURE_PERSON_READONLY".equals(ex.code()), "the update is rejected");
        }
        check(pure.writes == 0 && "Facultad A".equals(pure.row.faculty()), "pure_person is not written");
    }

    private static void revokingEthicsKeepsEventos() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        service.registerAccount("u-both", "ambos@uss.cl", "Ambos", "Accesos", null);
        service.grantApplication("u-both", ApplicationCode.EVENTOS, "u-admin", "2026-10-29T12:00:00Z");
        service.grantEthicsAccess("u-both", "u-admin", "2026-10-29T12:00:00Z", null);
        service.revokeEthics(true, "u-both", "u-admin", "2026-10-29T13:00:00Z", false, false);
        check(service.applicationActive("u-both", ApplicationCode.EVENTOS), "eventos stays active");
        check(!service.applicationActive("u-both", ApplicationCode.ETHICS), "ethics is revoked");
        check(service.store().account("u-both") != null, "the account remains");
    }

    private static void disablingAnActiveReviewerNeedsConfirmation() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        UserAccountRef created = service.createExternalReviewer(true, "alberto@universidad.cl", "Alberto", "Soto", "Universidad Austral", "Docente", "u-admin", "2026-10-29T12:00:00Z");
        try {
            service.revokeEthics(true, created.id(), "u-admin", "2026-10-29T13:00:00Z", true, false);
            throw new AssertionError("an active review must not be disabled in silence");
        } catch (IdentityRuleException ex) {
            check("ETHICS_DISABLE_NEEDS_CONFIRMATION".equals(ex.code()), "the warning is explicit");
        }
        check(service.applicationActive(created.id(), ApplicationCode.ETHICS), "ethics stays active until the warning is confirmed");
        service.revokeEthics(true, created.id(), "u-admin", "2026-10-29T13:05:00Z", true, true);
        check(!service.applicationActive(created.id(), ApplicationCode.ETHICS), "a confirmed disable revokes ethics only");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Recording implements PurePersonLookup {
        private PurePersonView row;
        private int writes;

        @Override
        public PurePersonView findById(long purePersonId) {
            return row != null && row.id() == purePersonId ? row : null;
        }
    }

    private static final class EthicsIdentityTestAccess implements cl.uss.ca.ethics.auth.RoleAssignmentProvider {
        private final List<RoleAssignment> rows;
        private final cl.uss.ca.ethics.auth.ScopeMatcher matcher = new cl.uss.ca.ethics.auth.ScopeMatcher();
        private final cl.uss.ca.ethics.auth.EthicsResourceIndex resources = cl.uss.ca.ethics.auth.EthicsResourceIndex.prototype();

        private EthicsIdentityTestAccess(List<RoleAssignment> rows) {
            this.rows = List.copyOf(rows);
        }

        @Override
        public List<RoleAssignment> getAssignmentsForUser(String userId) {
            return rows.stream().filter(item -> item.userId().equals(userId)).toList();
        }

        @Override
        public List<RoleAssignment> getAssignmentsForUserAndContext(String userId, AuthorizationContext context) {
            java.time.LocalDate today = java.time.LocalDate.now();
            return getAssignmentsForUser(userId).stream()
                    .filter(item -> item.operativeOn(today))
                    .filter(item -> matcher.matches(item, context, resources))
                    .toList();
        }
    }
}
