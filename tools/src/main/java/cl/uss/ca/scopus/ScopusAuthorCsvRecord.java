package cl.uss.ca.scopus;

import java.util.List;

public record ScopusAuthorCsvRecord(
        String authorName,
        String authId,
        Integer numberDocuments,
        String orcid,
        List<String> subjectAreas
) {
}
