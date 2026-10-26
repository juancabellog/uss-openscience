package cl.uss.ca.external.crossref;

public interface CrossrefClient {
    void assertConfigured();

    boolean isConfigured();

    boolean isEnabled();

    CrossrefFetchResult fetchWork(String doi);

    /**
     * Prepared for a later journals sync. Not used by the DOI work job.
     */
    default String journalUrl(String issn) {
        return null;
    }

    /**
     * Prepared bibliographic search for missing-DOI discovery. Does not write PURE.
     */
    default CrossrefFetchResult searchBibliographic(String query) {
        return CrossrefFetchResult.failure(
                CrossrefStatus.DISABLED,
                0,
                "CROSSREF_SEARCH_NOT_STARTED",
                "DOI candidate discovery is prepared and not started automatically"
        );
    }
}
