package cl.uss.ca.ethics.auth;

import cl.uss.ca.ethics.auth.EthicsUserCatalog.VisibleAssignment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public final class EthicsRoleRevocationTest {
    private EthicsRoleRevocationTest() {
    }

    public static void main(String[] args) throws Exception {
        LocalDate today = LocalDate.now();
        RoleAssignment researcher = row("10", "41", "RESEARCHER", ScopeType.GLOBAL, "*", AssignmentStatus.ACTIVE);
        RoleAssignment admin = row("11", "41", "ADMINISTRATOR", ScopeType.GLOBAL, "*", AssignmentStatus.ACTIVE);
        check(EthicsRoleRevocation.decide(admin, true, true, 0, 1, today) == null, "A. another active administrator allows removing ADMINISTRATOR");
        check(EthicsRoleRevocation.decide(researcher, true, true, 0, 0, today) == null, "A. RESEARCHER stays after that removal and is itself removable");

        check("LAST_ADMINISTRATOR".equals(EthicsRoleRevocation.decide(admin, true, true, 0, 0, today)), "B. the last active administrator cannot be removed");
        check(EthicsRoleRevocation.decide(admin, true, true, 1, 0, today) == null, "B. another administrator assignment on the same user still leaves an administrator");

        String resource = Files.readString(Path.of("src/main/java/cl/uss/ca/api/EthicsAuthorizationResource.java"));
        String service = Files.readString(Path.of("src/main/java/cl/uss/ca/ethics/directory/EthicsDirectoryService.java"));
        check(resource.contains("@DELETE") && resource.contains("/users/{id}/roles/{assignmentId}") && resource.contains("assertMayManage"), "C. revocation requires MANAGE_USERS on the assignment endpoint");
        check(!resource.contains("roles/{roleId}") && service.contains("revokeRole"), "C. the endpoint does not revoke by role id");

        RoleAssignment cec = row("21", "8", "COMMITTEE_PRESIDENT", ScopeType.COMMITTEE, "CEC", AssignmentStatus.ACTIVE);
        RoleAssignment cecua = row("22", "8", "COMMITTEE_PRESIDENT", ScopeType.COMMITTEE, "CECUA", AssignmentStatus.ACTIVE);
        check(EthicsRoleRevocation.decide(cecua, true, true, 0, 0, today) == null, "D. a committee presidency can be revoked");
        List<VisibleAssignment> chips = EthicsUserCatalog.visibleAssignments(List.of(cec, cecua), today);
        check(chips.size() == 2, "D. each committee scope stays visible");
        check("CEC".equals(chips.get(0).scopeLabel()) && "21".equals(chips.get(0).assignmentId()), "D. CEC keeps its own assignment");
        check("CECUA".equals(chips.get(1).scopeLabel()) && "22".equals(chips.get(1).assignmentId()), "D. CECUA is a different assignment");
        check(chips.stream().allMatch(VisibleAssignment::removable), "D. both presidencies are removable on their own");

        RoleAssignment reviewer = row("30", "7", "REVIEWER", ScopeType.PROCESS, "CECUA-2026-004:R1", AssignmentStatus.ACTIVE);
        List<VisibleAssignment> review = EthicsUserCatalog.visibleAssignments(List.of(reviewer), today);
        check(review.size() == 1 && review.get(0).contextual() && !review.get(0).removable(), "E. an active reviewer is contextual and not removable here");
        check("ROLE_NOT_REMOVABLE".equals(EthicsRoleRevocation.decide(reviewer, true, true, 0, 1, today)), "E. the backend refuses to revoke REVIEWER from user administration");
        check(EthicsUserCatalog.visibleAssignments(List.of(row("31", "8", "REVIEWER", ScopeType.PROCESS, "P1", AssignmentStatus.COMPLETED)), today).isEmpty(), "E. a completed review is not a current function");

        String detail = EthicsRoleRevocation.auditDetail(cecua);
        check(EthicsRoleRevocation.EVENT.equals("ROLE_REVOKED"), "F. the audit event is ROLE_REVOKED");
        check(detail.contains("assignment=22") && detail.contains("role=COMMITTEE_PRESIDENT") && detail.contains("scopeType=COMMITTEE") && detail.contains("scopeId=CECUA"), "F. the audit keeps assignment, role and scope");
        String repository = Files.readString(Path.of("src/main/java/cl/uss/ca/ethics/directory/EthicsDirectoryRepository.java"));
        check(repository.contains("status = 'REVOKED'") && repository.contains("modified_by") && repository.contains("WHERE id = ? AND user_id = ? AND status = 'ACTIVE'"), "F. revocation records who revoked it and keeps the row");
        check(!repository.contains("DELETE FROM"), "F. the directory does not physically delete assignments");

        RoleAssignment revoked = row("11", "41", "ADMINISTRATOR", ScopeType.GLOBAL, "*", AssignmentStatus.REVOKED);
        check("ROLE_ALREADY_REVOKED".equals(EthicsRoleRevocation.decide(revoked, true, true, 0, 1, today)), "G. a second revocation is a controlled error");

        List<VisibleAssignment> global = EthicsUserCatalog.visibleAssignments(List.of(researcher, admin), today);
        check(global.stream().allMatch(item -> item.scopeLabel() == null && item.removable()), "a single global role does not invent a scope suffix and can be removed");
        check(EthicsRoleRevocation.decide(admin, false, true, 0, 0, today) == null, "an inactive account is not the last active administrator");

        String page = Files.readString(Path.of("../projects/ethics-uss/src/app/features/administration/users.page.ts"));
        String shell = Files.readString(Path.of("../projects/ethics-uss/src/app/core/layout/app-shell.component.ts"));
        String guard = Files.readString(Path.of("../projects/ethics-uss/src/app/core/guards/demo-auth.guard.ts"));
        check(page.contains("revokeRole") && page.contains("refreshOwnContext") && page.contains("session.refresh"), "the screen revokes by assignment and refreshes the signed-in context");
        check(page.contains("actorId !== targetId"), "editing another user does not refresh the signed-in menu");
        check(guard.contains("'/usuarios', capability: 'MANAGE_USERS'") && page.contains("capabilityForUrl"), "losing MANAGE_USERS leaves the users route");
        check(shell.contains("caps.has('CREATE_REQUEST')") && shell.contains("navigation.newApplication"), "Nueva solicitud follows CREATE_REQUEST");
        check(shell.contains("caps.has('VIEW_PROJECTS')") && shell.contains("navigation.projects"), "Proyectos follows VIEW_PROJECTS");
        check(shell.contains("caps.has('VIEW_NOTIFICATIONS')") && !shell.contains("caps.has('VIEW_NOTIFICATIONS') && caps.has('CREATE_REQUEST')"), "Notificaciones follows VIEW_NOTIFICATIONS");
        check(shell.contains("caps.has('MANAGE_USERS')") && !shell.contains("role === 'RESEARCHER'") && !shell.contains("isResearcher"), "the menu does not branch on a role name");

        System.out.println("EthicsRoleRevocationTest OK");
    }

    private static RoleAssignment row(String id, String userId, String roleId, ScopeType scope, String scopeId, AssignmentStatus status) {
        return new RoleAssignment(id, userId, roleId, scope, scopeId, status, null, null, null, null, null, null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
