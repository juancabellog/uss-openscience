package cl.uss.ca.scopus.importer;

import java.util.List;

public record ScopusPublicationCsvRow(
        long fila,
        String eid,
        String doi,
        String doiNormalized,
        String title,
        Integer publicationYear,
        String sourceTitle,
        String volume,
        String issue,
        String articleNumber,
        String pageStart,
        String pageEnd,
        Integer citedBy,
        String link,
        String issn,
        String isbn,
        String coden,
        String pubmedId,
        String documentType,
        String publicationStage,
        String openAccess,
        String source,
        String authorsRaw,
        String authorFullNamesRaw,
        String authorIdsRaw,
        String affiliationsRaw,
        String authorsWithAffiliationsRaw,
        String authorKeywordsRaw,
        String indexKeywordsRaw,
        String fundingDetails,
        String fundingTexts,
        String abstractText,
        String correspondenceAddress,
        String editors,
        List<String> authors,
        List<String> authorFullNames,
        List<String> authorIds,
        List<String> authorsWithAffiliations,
        List<String> affiliations,
        List<String> authorKeywords,
        List<String> indexKeywords,
        List<ScopusPublicationAuthorItem> authorItems,
        boolean authorsAligned,
        List<String> warnings
) {
    public boolean tieneEid() {
        return eid != null;
    }

    public boolean tieneDoi() {
        return doiNormalized != null;
    }

    public boolean tieneClave() {
        return tieneEid() || tieneDoi();
    }
}
