package cl.uss.ca.ethics.auth;

import java.util.List;

/**
 * Lectura futura de ethics_role_assignment. No está registrada en CDI.
 * La tabla es de Ethics: no lleva application_id porque el rol no es de OpenScience.
 * Reemplaza a {@link DemoYamlRoleAssignmentProvider} dentro del compuesto;
 * {@link AuthorizationService} no cambia.
 */
public final class DatabaseRoleAssignmentRepository implements RoleAssignmentProvider {
    @Override
    public List<RoleAssignment> getAssignmentsForUser(String userId) {
        throw new UnsupportedOperationException("ETHICS_ROLE_ASSIGNMENT_TABLE_PENDING");
    }

    @Override
    public List<RoleAssignment> getAssignmentsForUserAndContext(String userId, AuthorizationContext context) {
        throw new UnsupportedOperationException("ETHICS_ROLE_ASSIGNMENT_TABLE_PENDING");
    }
}
