package cl.uss.ca.ethics.auth;

import java.util.List;

/**
 * Origen de asignaciones. Hoy es el YAML demo; mañana puede ser
 * {@link DatabaseRoleAssignmentRepository} sin cambiar {@link AuthorizationService}.
 */
public interface RoleAssignmentProvider {
    List<RoleAssignment> getAssignmentsForUser(String userId);

    List<RoleAssignment> getAssignmentsForUserAndContext(String userId, AuthorizationContext context);
}
