package cl.uss.ca.scopus;

import java.util.List;

public record AuthorLookupResult(
        AuthorLookupStatus status,
        Long scopusAuthorId,
        List<Long> idsEncontrados
) {
    public static AuthorLookupResult foundByAuthId(long id) {
        return new AuthorLookupResult(AuthorLookupStatus.FOUND_BY_AUTH_ID, id, List.of(id));
    }

    public static AuthorLookupResult foundByName(long id) {
        return new AuthorLookupResult(AuthorLookupStatus.FOUND_BY_NAME, id, List.of(id));
    }

    public static AuthorLookupResult notFound() {
        return new AuthorLookupResult(AuthorLookupStatus.NOT_FOUND, null, List.of());
    }

    public static AuthorLookupResult ambiguousName(List<Long> ids) {
        return new AuthorLookupResult(AuthorLookupStatus.AMBIGUOUS_NAME, null, List.copyOf(ids));
    }

    public boolean encontrado() {
        return status == AuthorLookupStatus.FOUND_BY_AUTH_ID
                || status == AuthorLookupStatus.FOUND_BY_NAME;
    }
}
