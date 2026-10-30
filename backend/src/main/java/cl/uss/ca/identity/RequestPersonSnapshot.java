package cl.uss.ca.identity;

/** Copia tomada al envío formal. Un cambio posterior en PURE no la reescribe. */
public record RequestPersonSnapshot(
        String requestId,
        String personId,
        String firstName,
        String lastName,
        String email,
        String institution,
        String campus,
        String faculty,
        String unit,
        String position,
        String capturedAt
) {
}
