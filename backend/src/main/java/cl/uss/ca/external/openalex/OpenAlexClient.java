package cl.uss.ca.external.openalex;

public interface OpenAlexClient {
    void assertConfigured();

    boolean isConfigured();

    boolean isEnabled();

    OpenAlexFetchResult fetchWork(String doi);

    OpenAlexFetchResult fetchAuthor(String openAlexAuthorId);
}
