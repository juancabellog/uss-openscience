package cl.uss.ca.identity;

/**
 * Lectura de {@code pure_person}. No se escribe desde Ethics.
 *
 * <p>Columnas reales usadas hoy: {@code id}, {@code first_name}, {@code last_name},
 * {@code email}, {@code cargo}. Sede, facultad, unidad y programa no son columnas
 * de esa tabla; quedan nulos salvo que un origen futuro los entregue ya resueltos.
 */
public record PurePersonView(
        long id,
        String firstName,
        String lastName,
        String email,
        String position,
        String campus,
        String faculty,
        String unit
) {
    public static PurePersonView institutional(long id, String firstName, String lastName, String email, String position) {
        return new PurePersonView(id, firstName, lastName, email, position, null, null, null);
    }
}
