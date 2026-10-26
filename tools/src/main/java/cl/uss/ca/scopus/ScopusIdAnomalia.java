package cl.uss.ca.scopus;

public record ScopusIdAnomalia(
        long personId,
        String uuid,
        String nombre,
        String scopusIdRepetido,
        int cantidadScopusIds,
        String todosScopusIds
) {
}
