package cl.uss.ca.identity;

/** Cuenta compartida. El nombre institucional vive en {@link SharedPerson}, no se copia aquí como perfil. */
public record UserAccountRef(
        String id,
        String email,
        String emailNormalized,
        String givenName,
        String familyName,
        String personId
) {
}
