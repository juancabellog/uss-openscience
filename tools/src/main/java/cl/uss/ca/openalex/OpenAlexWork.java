package cl.uss.ca.openalex;

import java.util.ArrayList;
import java.util.List;

public class OpenAlexWork {
    private String doi;
    private String title;
    private List<OpenAlexAuthor> authors = new ArrayList<>();

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<OpenAlexAuthor> getAuthors() {
        return authors;
    }

    public void setAuthors(List<OpenAlexAuthor> authors) {
        this.authors = authors == null ? new ArrayList<>() : authors;
    }
}
