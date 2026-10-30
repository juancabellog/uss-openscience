package cl.uss.ca.ethics.auth;

import java.util.Set;

/**
 * Separa la reasignación ordinaria de la excepcional.
 * REASSIGN_REVIEWER no autoriza una etapa avanzada.
 * Un expediente cerrado no se reabre con ninguna de las dos.
 */
public final class ReviewerReassignmentRule {
    public static final String ORDINARY = "REASSIGN_REVIEWER";
    public static final String ADVANCED = "REASSIGN_REVIEWER_ADVANCED_STAGE";

    /** La revisión todavía está en su etapa operativa. */
    public static final Set<String> ORDINARY_CASE = Set.of(
            "NOTIFIED", "ADMISSIBILITY", "RETURNED", "PENDING_ASSIGNMENT", "PENDING_ACCEPTANCE",
            "IN_REVIEW", "PENDING_SESSION", "PENDING_DOCUMENTS");

    /**
     * El expediente ya salió de la revisión operativa: observaciones,
     * respuesta del investigador o resolución todavía abierta.
     */
    public static final Set<String> ADVANCED_CASE = Set.of(
            "WITH_OBSERVATIONS", "AWAITING_RESEARCHER", "PENDING_RESOLUTION", "PENDING_PRESIDENT_VALIDATION");

    public static final Set<String> CLOSED_CASE = Set.of("APPROVED", "REJECTED", "EXEMPT", "WITHDRAWN");

    private ReviewerReassignmentRule() {
    }

    public static boolean advanced(String caseStatus, String assignmentStatus, boolean reviewSubmitted) {
        if (reviewSubmitted || "COMPLETED".equals(assignmentStatus)) {
            return true;
        }
        return caseStatus != null && ADVANCED_CASE.contains(caseStatus);
    }

    /**
     * Capability que hay que volver a comprobar. {@code null} significa que el cambio no está abierto.
     */
    public static String requiredCapability(String caseStatus, String assignmentStatus, boolean reviewSubmitted) {
        if (caseStatus == null || CLOSED_CASE.contains(caseStatus)) {
            return null;
        }
        if (advanced(caseStatus, assignmentStatus, reviewSubmitted)) {
            return ADVANCED;
        }
        return ORDINARY_CASE.contains(caseStatus) ? ORDINARY : null;
    }
}
