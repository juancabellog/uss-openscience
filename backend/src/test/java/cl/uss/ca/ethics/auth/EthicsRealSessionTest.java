package cl.uss.ca.ethics.auth;

import cl.uss.ca.security.CurrentUser;

import java.time.LocalDate;
import java.util.List;

/**
 * La sesión real no hereda el YAML demo ni un rol enviado por el navegador.
 */
public final class EthicsRealSessionTest {
    private EthicsRealSessionTest() {
    }

    public static void main(String[] args) {
        researcherCannotBecomeAdministrator();
        administratorKeepsManageUsers();
        presidentUsesThePermissionFile();
        reviewerWithoutAssignmentSeesNoReview();
        reviewerSeesOnlyTheAssignedProcess();
        demoSelectorStaysHidden();
        productionNeverShowsTheSelector();
        persistedProviderIgnoresDemoUsers();
        System.out.println("EthicsRealSessionTest OK");
    }

    private static void researcherCannotBecomeAdministrator() {
        AuthorizationService service = service(global("200", "RESEARCHER"));
        CurrentUser session = user("200", "camila.herrera@uss.cl", "Camila Herrera");
        var capabilities = service.permissions(session, AuthorizationContext.empty()).capabilities;
        check(!capabilities.contains("MANAGE_USERS"), "a researcher session does not become administrator");
        check(capabilities.contains("CREATE_REQUEST"), "the researcher assignment remains");
        check("200".equals(service.profile(session).id), "the profile id is the session id");
        check("camila.herrera@uss.cl".equals(service.profile(session).email), "the profile email is the session email");
    }

    private static void administratorKeepsManageUsers() {
        AuthorizationService service = service(global("41", "RESEARCHER"), global("41", "ADMINISTRATOR"));
        var capabilities = service.permissions(user("41", "juan.cabello@uss.cl", "Juan Manuel Cabello"), AuthorizationContext.empty()).capabilities;
        check(capabilities.contains("MANAGE_USERS"), "the administrator assignment grants manage users");
    }

    private static void presidentUsesThePermissionFile() {
        AuthorizationService service = service(global("300", "COMMITTEE_PRESIDENT"));
        var capabilities = service.permissions(user("300", "presidenta@uss.cl", "Presidenta"), AuthorizationContext.empty()).capabilities;
        check(capabilities.contains("REASSIGN_REVIEWER_ADVANCED_STAGE"), "presidency keeps the advanced reassignment");
        check(!capabilities.contains("MANAGE_USERS"), "presidency does not manage users");
    }

    private static void reviewerWithoutAssignmentSeesNoReview() {
        AuthorizationService service = service();
        var capabilities = service.permissions(user("400", "revisor@uss.cl", "Revisor"), AuthorizationContext.empty()).capabilities;
        check(!capabilities.contains("VIEW_OWN_REVIEW"), "without a reviewer assignment there is no review");
        check(!capabilities.contains("CREATE_REQUEST"), "the missing assignment does not grant researcher");
    }

    private static void reviewerSeesOnlyTheAssignedProcess() {
        RoleAssignment assignment = new RoleAssignment(
                "rev-1", "400", "REVIEWER", ScopeType.PROCESS, "CECUA-2026-004:R1", AssignmentStatus.ACTIVE,
                null, null, "41", "2026-10-29T12:00:00Z", "41", "2026-10-29T12:00:00Z"
        );
        AuthorizationService service = service(assignment);
        CurrentUser session = user("400", "revisor@uss.cl", "Revisor");
        var assigned = service.permissions(session, new AuthorizationContext(null, null, null, "CECUA-2026-004:R1", null)).capabilities;
        var other = service.permissions(session, new AuthorizationContext(null, null, null, "CECUA-2026-004:R2", null)).capabilities;
        check(assigned.contains("VIEW_OWN_REVIEW"), "the assigned process is visible");
        check(!other.contains("VIEW_OWN_REVIEW"), "another process stays closed");
    }

    private static void demoSelectorStaysHidden() {
        check(!EthicsDemoMode.selectorVisible(false, false), "demo is off by default");
    }

    private static void productionNeverShowsTheSelector() {
        check(!EthicsDemoMode.selectorVisible(true, true), "production hides the selector");
        check(!EthicsDemoMode.selectorVisible(false, true), "production stays closed");
        check(EthicsDemoMode.selectorVisible(true, false), "an explicit local flag can show it");
    }

    private static void persistedProviderIgnoresDemoUsers() {
        CompositeRoleAssignmentProvider provider = CompositeRoleAssignmentProvider.persisted(new Fixed());
        check(provider.getAssignmentsForUser("u-admin").isEmpty(), "the demo administrator is not authorized");
        check(provider.getAssignmentsForUser("41").isEmpty(), "an empty catalog grants nothing");
    }

    private static AuthorizationService service(RoleAssignment... rows) {
        return new AuthorizationService(YamlRoleDefinitionProvider.loadClasspath(), new Fixed(rows), null);
    }

    private static RoleAssignment global(String userId, String roleId) {
        return new RoleAssignment(
                roleId + "-" + userId, userId, roleId, ScopeType.GLOBAL, "*", AssignmentStatus.ACTIVE,
                null, null, "system", "2026-10-29T12:00:00Z", "system", "2026-10-29T12:00:00Z"
        );
    }

    private static CurrentUser user(String id, String email, String name) {
        CurrentUser user = new CurrentUser();
        user.id = id;
        user.email = email;
        user.displayName = name;
        return user;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixed implements RoleAssignmentProvider {
        private final List<RoleAssignment> rows;
        private final ScopeMatcher matcher = new ScopeMatcher();
        private final EthicsResourceIndex resources = EthicsResourceIndex.prototype();

        private Fixed(RoleAssignment... rows) {
            this.rows = List.of(rows);
        }

        @Override
        public List<RoleAssignment> getAssignmentsForUser(String userId) {
            return rows.stream().filter(item -> item.userId().equals(userId)).toList();
        }

        @Override
        public List<RoleAssignment> getAssignmentsForUserAndContext(String userId, AuthorizationContext context) {
            LocalDate today = LocalDate.now();
            return getAssignmentsForUser(userId).stream()
                    .filter(item -> item.operativeOn(today))
                    .filter(item -> matcher.matches(item, context, resources))
                    .toList();
        }
    }
}
