package cl.uss.ca.identity;

import cl.uss.ca.ethics.auth.AssignmentStatus;
import cl.uss.ca.ethics.auth.AuthorizationContext;
import cl.uss.ca.ethics.auth.AuthorizationService;
import cl.uss.ca.ethics.auth.RoleAssignment;
import cl.uss.ca.ethics.auth.RoleAssignmentProvider;
import cl.uss.ca.ethics.auth.ScopeType;
import cl.uss.ca.ethics.auth.YamlRoleDefinitionProvider;

import java.util.List;

public final class EthicsExternalAccessTest {
    private EthicsExternalAccessTest() {
    }

    public static void main(String[] args) {
        openscienceAccountCannotEnterEthics();
        eventosAccountCannotEnterEthics();
        administrationReusesTheExternalAccount();
        enabledReviewerWithoutAssignmentSeesNothing();
        assignedReviewerSeesOnlyThatProcess();
        institutionalAccountProvisionsItself();
        System.out.println("EthicsExternalAccessTest OK");
    }

    private static void openscienceAccountCannotEnterEthics() {
        EthicsAccessService service = deniedEntry("externo@gmail.com", ApplicationCode.OPENSCIENCE);
        check(service.applicationActive("u-ext", ApplicationCode.OPENSCIENCE), "openscience stays active");
    }

    private static void eventosAccountCannotEnterEthics() {
        EthicsAccessService service = deniedEntry("externo@gmail.com", ApplicationCode.EVENTOS);
        check(service.applicationActive("u-ext", ApplicationCode.EVENTOS), "eventos stays active");
    }

    private static EthicsAccessService deniedEntry(String email, ApplicationCode other) {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        service.registerAccount("u-ext", email, "Persona", "Externa", null);
        service.grantApplication("u-ext", other, "system", "2026-10-29T12:00:00Z");
        int people = service.store().personCount();
        int accounts = service.store().accountCount();
        try {
            service.enterEthics("u-ext", "self", "2026-10-29T12:05:00Z");
            throw new AssertionError("an external account must not enter ethics");
        } catch (IdentityRuleException ex) {
            check("ETHICS_ACCESS_DENIED".equals(ex.code()), "ethics denies the external account");
        }
        check(service.store().application("u-ext", ApplicationCode.ETHICS) == null, "ethics was not created");
        check(service.ethicsRoles("u-ext").isEmpty(), "no researcher or reviewer was granted");
        check(service.store().personCount() == people, "no person was created");
        check(service.store().accountCount() == accounts, "the account was not duplicated");
        return service;
    }

    private static void administrationReusesTheExternalAccount() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        service.registerAccount("u-ext", "externo@gmail.com", "Persona", "Externa", null);
        service.grantApplication("u-ext", ApplicationCode.OPENSCIENCE, "system", "2026-10-29T12:00:00Z");
        UserAccountRef enabled = service.createExternalReviewer(
                true, "Externo@gmail.com", "Persona", "Externa", "Universidad X", "Docente", "u-admin", "2026-10-29T12:10:00Z"
        );
        check("u-ext".equals(enabled.id()), "the openscience account is reused");
        check(service.store().accountCount() == 1, "a second account is not created");
        check(service.applicationActive("u-ext", ApplicationCode.ETHICS), "ethics is active");
        check(service.applicationActive("u-ext", ApplicationCode.OPENSCIENCE), "openscience stays active");
        check(enabled.personId() != null && service.store().person(enabled.personId()).purePersonId() == null, "the person is local");
        check(service.store().person(enabled.personId()).kind() == PersonKind.EXTERNAL, "the person is external");
        check(service.ethicsRoles("u-ext").isEmpty(), "the external reviewer does not receive researcher");
        service.enterEthics("u-ext", "self", "2026-10-29T12:20:00Z");
        check(service.ethicsRoles("u-ext").isEmpty(), "entering does not add researcher");
    }

    private static void enabledReviewerWithoutAssignmentSeesNothing() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        UserAccountRef account = service.createExternalReviewer(
                true, "externo@gmail.com", "Persona", "Externa", "Universidad X", "Docente", "u-admin", "2026-10-29T12:00:00Z"
        );
        service.enterEthics(account.id(), "self", "2026-10-29T12:05:00Z");
        AuthorizationService authorization = new AuthorizationService(YamlRoleDefinitionProvider.loadClasspath(), new Fixed(List.of()));
        check(authorization.getEffectiveCapabilities(account.id(), AuthorizationContext.empty()).isEmpty(), "without an assignment there is no project access");
    }

    private static void assignedReviewerSeesOnlyThatProcess() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        UserAccountRef account = service.createExternalReviewer(
                true, "externo@gmail.com", "Persona", "Externa", "Universidad X", "Docente", "u-admin", "2026-10-29T12:00:00Z"
        );
        RoleAssignment assignment = new RoleAssignment(
                "rev-1", account.id(), "REVIEWER", ScopeType.PROCESS, "CECUA-2026-004:R1", AssignmentStatus.ACTIVE,
                null, null, "u-admin", "2026-10-29T12:30:00Z", "u-admin", "2026-10-29T12:30:00Z"
        );
        AuthorizationService authorization = new AuthorizationService(YamlRoleDefinitionProvider.loadClasspath(), new Fixed(List.of(assignment)));
        var onRound = authorization.getEffectiveCapabilities(account.id(), new AuthorizationContext(null, null, null, "CECUA-2026-004:R1", null));
        var elsewhere = authorization.getEffectiveCapabilities(account.id(), new AuthorizationContext(null, null, null, "CECUA-2026-004:R2", null));
        check(onRound.contains("VIEW_OWN_REVIEW") && !onRound.contains("CREATE_REQUEST"), "the assignment grants the review");
        check(elsewhere.isEmpty(), "another process stays closed");
        check(service.ethicsRoles(account.id()).isEmpty(), "reviewer is not a stored structural role");
    }

    private static void institutionalAccountProvisionsItself() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        service.registerAccount("u-uss", "usuario@uss.cl", "Usuario", "USS", null);
        service.grantApplication("u-uss", ApplicationCode.OPENSCIENCE, "system", "2026-10-29T12:00:00Z");
        service.enterEthics("u-uss", "self", "2026-10-29T12:05:00Z");
        check(service.applicationActive("u-uss", ApplicationCode.ETHICS), "ethics becomes active");
        check(service.applicationActive("u-uss", ApplicationCode.OPENSCIENCE), "openscience stays active");
        check(service.ethicsRoles("u-uss").stream().anyMatch(item -> "RESEARCHER".equals(item.roleId())), "the institutional user receives researcher");
        check(service.store().accountCount() == 1, "the account is reused");
        service.enterEthics("u-uss", "self", "2026-10-29T12:06:00Z");
        check(service.ethicsRoles("u-uss").size() == 1, "a second entry does not duplicate researcher");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixed implements RoleAssignmentProvider {
        private final List<RoleAssignment> rows;
        private final cl.uss.ca.ethics.auth.ScopeMatcher matcher = new cl.uss.ca.ethics.auth.ScopeMatcher();
        private final cl.uss.ca.ethics.auth.EthicsResourceIndex resources = cl.uss.ca.ethics.auth.EthicsResourceIndex.prototype();

        private Fixed(List<RoleAssignment> rows) {
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
