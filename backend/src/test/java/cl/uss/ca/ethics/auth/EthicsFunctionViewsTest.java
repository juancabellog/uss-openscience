package cl.uss.ca.ethics.auth;

import cl.uss.ca.api.dto.EthicsFunctionViewDto;

import java.util.List;

public final class EthicsFunctionViewsTest {
    private EthicsFunctionViewsTest() {
    }

    public static void main(String[] args) {
        List<EthicsFunctionViewDto> views = EthicsFunctionViews.present(List.of(
                row("a", "COMMITTEE_PRESIDENT", ScopeType.COMMITTEE, "CEC", AssignmentStatus.ACTIVE),
                row("b", "COMMITTEE_PRESIDENT", ScopeType.COMMITTEE, "CECUA", AssignmentStatus.ACTIVE),
                row("c", "COMMITTEE_PRESIDENT", ScopeType.COMMITTEE, "BIOSEGURIDAD", AssignmentStatus.REVOKED),
                row("d", "REVIEWER", ScopeType.PROCESS, "proc-1", AssignmentStatus.COMPLETED),
                row("e", "REVIEWER", ScopeType.PROCESS, "proc-2", AssignmentStatus.REVOKED),
                row("f", "RESEARCHER", ScopeType.GLOBAL, "*", AssignmentStatus.ACTIVE)
        ));
        check(views.size() == 4, "active scopes, one completed review and the researcher stay");
        check(views.stream().anyMatch(item -> "CEC".equals(item.scopeId)), "CEC presidency stays");
        check(views.stream().anyMatch(item -> "CECUA".equals(item.scopeId)), "CECUA presidency stays");
        check(views.stream().noneMatch(item -> "BIOSEGURIDAD".equals(item.scopeId)), "a revoked presidency is not a function");
        check(views.stream().anyMatch(item -> "REVIEWER".equals(item.roleId) && "COMPLETED".equals(item.status)), "a completed review stays visible");
        check(views.stream().noneMatch(item -> "REVOKED".equals(item.status)), "revoked rows are omitted");
        System.out.println("EthicsFunctionViewsTest OK");
    }

    private static RoleAssignment row(String id, String roleId, ScopeType scope, String scopeId, AssignmentStatus status) {
        return new RoleAssignment(id, "user", roleId, scope, scopeId, status, null, null, null, null, null, null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
