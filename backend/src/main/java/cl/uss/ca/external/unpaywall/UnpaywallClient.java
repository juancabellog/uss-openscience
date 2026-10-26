package cl.uss.ca.external.unpaywall;

public interface UnpaywallClient {
    void assertConfigured();

    boolean isConfigured();

    UnpaywallFetchResult fetchByDoi(String doi);
}
