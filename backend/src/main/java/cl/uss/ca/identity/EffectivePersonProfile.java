package cl.uss.ca.identity;

/** Perfil que ve el formulario. No es la fila cruda de {@code pure_person}. */
public record EffectivePersonProfile(
        String personId,
        Long purePersonId,
        PersonSource source,
        String firstName,
        String lastName,
        String email,
        String institution,
        String campus,
        String faculty,
        String unit,
        String position
) {
}
