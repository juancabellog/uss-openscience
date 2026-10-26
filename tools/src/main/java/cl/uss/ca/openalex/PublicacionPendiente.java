package cl.uss.ca.openalex;

public class PublicacionPendiente {
    private final String doi;
    private final String title;

    public PublicacionPendiente(String doi, String title) {
        this.doi = doi;
        this.title = title;
    }

    public String getDoi() {
        return doi;
    }

    public String getTitle() {
        return title;
    }
}
