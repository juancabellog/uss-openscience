package cl.uss.ca.ethics.auth;

import java.util.Map;
import java.util.Set;

/**
 * Una capability es una posibilidad. El estado del expediente decide si ahora se puede usar.
 * Las reglas salen del workflow ya implementado en ethics-uss; no viven en el YAML.
 */
public final class EthicsWorkflowPolicy {
    private static final Set<String> ASSIGNMENT = Set.of(
            "NOTIFIED", "ADMISSIBILITY", "RETURNED", "PENDING_ASSIGNMENT", "PENDING_ACCEPTANCE");
    private static final Set<String> REVIEW = Set.of(
            "IN_REVIEW", "PENDING_SESSION", "WITH_OBSERVATIONS", "AWAITING_RESEARCHER");
    /**
     * REASSIGN_REVIEWER solo en la etapa operativa de la revisión.
     * REASSIGN_REVIEWER_ADVANCED_STAGE solo cuando el expediente ya avanzó.
     * Un caso cerrado no entra en ninguno de los dos conjuntos.
     */
    private static final Set<String> OPEN_REASSIGN = ReviewerReassignmentRule.ORDINARY_CASE;
    private static final Map<String, Set<String>> ACTIONS = Map.ofEntries(
            Map.entry("UPLOAD_DOCUMENT", Set.of("AWAITING_RESEARCHER")),
            Map.entry("UPLOAD_DOCUMENTS", Set.of("AWAITING_RESEARCHER")),
            Map.entry("UPLOAD_DOCUMENT_VERSION", Set.of("AWAITING_RESEARCHER")),
            Map.entry("RESPOND_OBSERVATIONS", Set.of("AWAITING_RESEARCHER")),
            Map.entry("RESPOND_TO_OBSERVATIONS", Set.of("AWAITING_RESEARCHER")),
            Map.entry("EDIT_OWN_REVIEW", Set.of("IN_REVIEW", "PENDING_ACCEPTANCE")),
            Map.entry("SUBMIT_OWN_REVIEW", Set.of("IN_REVIEW", "PENDING_ACCEPTANCE")),
            Map.entry("ACCEPT_REVIEW_ASSIGNMENT", Set.of("PENDING_ACCEPTANCE")),
            Map.entry("REJECT_REVIEW_ASSIGNMENT", Set.of("PENDING_ACCEPTANCE")),
            Map.entry("ASSIGN_REVIEWERS", ASSIGNMENT),
            Map.entry("REASSIGN_REVIEWER", OPEN_REASSIGN),
            Map.entry("REASSIGN_REVIEWER_ADVANCED_STAGE", ReviewerReassignmentRule.ADVANCED_CASE),
            Map.entry("CONSOLIDATE_OBSERVATIONS", REVIEW),
            Map.entry("EDIT_CONSOLIDATED_OBSERVATIONS", REVIEW),
            Map.entry("SAVE_CONSOLIDATED_OBSERVATIONS", REVIEW),
            Map.entry("PUBLISH_OBSERVATIONS", REVIEW),
            Map.entry("PREPARE_RESOLUTION", Set.of("PENDING_RESOLUTION")),
            Map.entry("VALIDATE_RESOLUTION", Set.of("PENDING_PRESIDENT_VALIDATION")),
            Map.entry("RETURN_RESOLUTION", Set.of("PENDING_PRESIDENT_VALIDATION"))
    );

    public boolean stateSensitive(String capability) {
        return ACTIONS.containsKey(capability);
    }

    /**
     * Sin estado, una acción no se concede. Con estado, solo en los estados que el workflow ya permite.
     */
    public boolean allowsNow(String capability, String workflowState) {
        Set<String> states = ACTIONS.get(capability);
        if (states == null) {
            return true;
        }
        return workflowState != null && states.contains(workflowState);
    }
}
