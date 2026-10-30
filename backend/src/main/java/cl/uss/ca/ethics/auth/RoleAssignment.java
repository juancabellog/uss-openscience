package cl.uss.ca.ethics.auth;

import java.time.LocalDate;

/**
 * Una persona tiene un rol en un alcance. No copia las capabilities del rol.
 */
public record RoleAssignment(
        String id,
        String userId,
        String roleId,
        ScopeType scopeType,
        String scopeId,
        AssignmentStatus status,
        LocalDate validFrom,
        LocalDate validUntil,
        String createdBy,
        String createdAt,
        String modifiedBy,
        String modifiedAt
) {
    public boolean operativeOn(LocalDate day) {
        if (status == null || !status.operative() || day == null) {
            return false;
        }
        if (validFrom != null && day.isBefore(validFrom)) {
            return false;
        }
        return validUntil == null || !day.isAfter(validUntil);
    }
}
