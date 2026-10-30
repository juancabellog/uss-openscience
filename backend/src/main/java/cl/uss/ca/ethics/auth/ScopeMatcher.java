package cl.uss.ca.ethics.auth;

/**
 * El alcance real lo decide la asignación, no la categoría de la capability.
 */
public final class ScopeMatcher {
    public boolean matches(RoleAssignment assignment, AuthorizationContext context, EthicsResourceIndex index) {
        if (assignment == null || assignment.scopeType() == null || index == null) {
            return false;
        }
        EthicsResourceIndex.Resolved resolved = index.resolve(context);
        if (resolved.mismatch()) {
            return false;
        }
        return switch (assignment.scopeType()) {
            case GLOBAL -> true;
            case COMMITTEE -> assignment.scopeId() != null && assignment.scopeId().equals(resolved.committee());
            case PROJECT -> assignment.scopeId() != null && assignment.scopeId().equals(resolved.projectId());
            case PROCESS -> assignment.scopeId() != null && assignment.scopeId().equals(resolved.processId());
        };
    }
}
