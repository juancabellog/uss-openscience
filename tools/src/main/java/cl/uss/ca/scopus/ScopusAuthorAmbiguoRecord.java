package cl.uss.ca.scopus;

public record ScopusAuthorAmbiguoRecord(
        String archivo,
        String affiliationName,
        String affiliationId,
        String authorName,
        String authId,
        int cantidadCoincidencias,
        String scopusAuthorIdsEncontrados,
        String motivo
) {
}
