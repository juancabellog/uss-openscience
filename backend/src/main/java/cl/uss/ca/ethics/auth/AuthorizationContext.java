package cl.uss.ca.ethics.auth;

/**
 * Contexto de una pregunta de autorización. Ningún campo es obligatorio:
 * si falta el que el alcance necesita, la respuesta es denegar.
 */
public record AuthorizationContext(
        String committeeType,
        String projectId,
        String committeeCaseId,
        String processId,
        String workflowState
) {
    public static AuthorizationContext empty() {
        return new AuthorizationContext(null, null, null, null, null);
    }

    public AuthorizationContext withWorkflowState(String workflowState) {
        return new AuthorizationContext(committeeType, projectId, committeeCaseId, processId, workflowState);
    }
}
