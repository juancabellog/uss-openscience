package cl.uss.ca.scopus;

public record ScopusDuplicadoRow(
        long idPerson,
        String uuid,
        String nombreCompleto,
        int cantidadScopus,
        long idPerson2,
        String uuid2,
        String nombreCompleto2,
        int cantidadScopus2,
        String scopusIdRepetido
) {
}
