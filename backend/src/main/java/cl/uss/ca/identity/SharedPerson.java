package cl.uss.ca.identity;

/**
 * Persona compartida por OpenScience. No es una cuenta y no es una fila de {@code pure_person}.
 * Si {@code purePersonId} no es nulo, {@link PersonProfileResolver} ignora estas columnas.
 */
public record SharedPerson(
        String id,
        Long purePersonId,
        String firstName,
        String lastName,
        String email,
        String emailNormalized,
        String institution,
        String campus,
        String faculty,
        String unit,
        String position,
        PersonKind kind
) {
    public SharedPerson {
        if (kind == null) {
            throw new IllegalArgumentException("person kind is required");
        }
    }

    public boolean linkedToPure() {
        return purePersonId != null;
    }
}
