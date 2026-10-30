package cl.uss.ca.ethics.auth;

import java.time.LocalDate;
import java.util.Set;

/**
 * Revocación de un {@link RoleAssignment} concreto.
 * RESEARCHER global se crea al habilitar Ethics institucional y no se vuelve a imponer
 * en cada autorización. No es irremovible.
 * REVIEWER no se revoca aquí: la función vigente sale de la revisión, no de este listado.
 */
public final class EthicsRoleRevocation {
    public static final String EVENT = "ROLE_REVOKED";
    public static final Set<String> REMOVABLE = Set.of(
            "RESEARCHER", "ADMINISTRATOR", "COMMITTEE_PRESIDENT", "EXECUTIVE_SECRETARY");

    private EthicsRoleRevocation() {
    }

    /**
     * @return código de rechazo, o null si esa asignación se puede revocar
     */
    public static String decide(
            RoleAssignment assignment,
            boolean accountActive,
            boolean ethicsActive,
            int otherOperativeAdminAssignmentsOnUser,
            long otherActiveAdminUsers,
            LocalDate day
    ) {
        if (assignment == null || assignment.id() == null || assignment.id().isBlank()) {
            return "ASSIGNMENT_NOT_FOUND";
        }
        if ("REVIEWER".equals(assignment.roleId()) || !REMOVABLE.contains(assignment.roleId())) {
            return "ROLE_NOT_REMOVABLE";
        }
        if (assignment.status() == AssignmentStatus.REVOKED
                || assignment.status() == AssignmentStatus.COMPLETED
                || !assignment.operativeOn(day)) {
            return "ROLE_ALREADY_REVOKED";
        }
        boolean lastAdministrator = "ADMINISTRATOR".equals(assignment.roleId())
                && accountActive
                && ethicsActive
                && otherOperativeAdminAssignmentsOnUser <= 0
                && otherActiveAdminUsers <= 0;
        if (lastAdministrator) {
            return "LAST_ADMINISTRATOR";
        }
        return null;
    }

    public static String auditDetail(RoleAssignment assignment) {
        String scopeType = assignment.scopeType() == null ? "" : assignment.scopeType().name();
        String scopeId = assignment.scopeId() == null ? "" : assignment.scopeId();
        return "assignment=" + assignment.id()
                + " role=" + assignment.roleId()
                + " scopeType=" + scopeType
                + " scopeId=" + scopeId;
    }
}
