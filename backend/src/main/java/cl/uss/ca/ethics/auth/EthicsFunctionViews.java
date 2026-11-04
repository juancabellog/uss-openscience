package cl.uss.ca.ethics.auth;

import cl.uss.ca.api.dto.EthicsFunctionViewDto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Proyecta asignaciones ya resueltas para que la interfaz arme espacios de trabajo.
 * No calcula capabilities y no recibe un rol elegido en el navegador.
 */
public final class EthicsFunctionViews {
    private EthicsFunctionViews() {
    }

    public static List<EthicsFunctionViewDto> present(List<RoleAssignment> assignments) {
        LocalDate today = LocalDate.now();
        List<EthicsFunctionViewDto> views = new ArrayList<>();
        if (assignments == null) {
            return views;
        }
        for (RoleAssignment assignment : assignments) {
            if (assignment == null || assignment.roleId() == null || assignment.roleId().isBlank()) {
                continue;
            }
            boolean reviewerHistory = "REVIEWER".equals(assignment.roleId())
                    && assignment.status() == AssignmentStatus.COMPLETED;
            if (!assignment.operativeOn(today) && !reviewerHistory) {
                continue;
            }
            EthicsFunctionViewDto view = new EthicsFunctionViewDto();
            view.roleId = assignment.roleId();
            view.scopeType = assignment.scopeType() == null ? null : assignment.scopeType().name();
            view.scopeId = assignment.scopeId();
            view.status = assignment.status() == null ? null : assignment.status().name();
            views.add(view);
        }
        return List.copyOf(views);
    }
}
