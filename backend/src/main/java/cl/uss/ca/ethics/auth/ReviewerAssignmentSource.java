package cl.uss.ca.ethics.auth;

import java.util.List;

/**
 * Hecho de dominio: una persona revisa un proceso.
 * La implementación demo lee las filas REVIEWER del YAML.
 * La futura implementación leerá ReviewerAssignment y no un segundo registro de rol.
 */
public interface ReviewerAssignmentSource {
    List<ReviewerEngagement> engagementsFor(String userId);

    record ReviewerEngagement(String id, String userId, String processId, AssignmentStatus status) {
    }
}
