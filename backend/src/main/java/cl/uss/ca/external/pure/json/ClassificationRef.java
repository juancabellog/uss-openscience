package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassificationRef {

    private String uri;
    private LocalizedText term;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public LocalizedText getTerm() {
        return term;
    }

    public void setTerm(LocalizedText term) {
        this.term = term;
    }

    public String preferredTerm() {
        return term == null ? null : term.preferred();
    }

    public String termEsCl() {
        return term == null ? null : blankToNull(term.getEsCl());
    }

    public String termEnUs() {
        return term == null ? null : blankToNull(term.getEnUs());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
