package cl.uss.ca.scopus.importer;

public record ScopusPublicationAuthorItem(
        int position,
        String authId,
        String authorNameShort,
        String authorNameFull,
        String authorAffiliationsRaw,
        String fullNameAuthId,
        boolean authIdMismatch
) {
}
