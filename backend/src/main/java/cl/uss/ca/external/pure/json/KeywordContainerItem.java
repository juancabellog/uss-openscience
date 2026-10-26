package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KeywordContainerItem {
    private Long pureId;
    private String state;
    private String origin;
    private ClassificationRef structuredKeyword;
    private List<FreeKeywordLocaleItem> freeKeywords;

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public ClassificationRef getStructuredKeyword() {
        return structuredKeyword;
    }

    public void setStructuredKeyword(ClassificationRef structuredKeyword) {
        this.structuredKeyword = structuredKeyword;
    }

    public List<FreeKeywordLocaleItem> getFreeKeywords() {
        return freeKeywords;
    }

    public void setFreeKeywords(List<FreeKeywordLocaleItem> freeKeywords) {
        this.freeKeywords = freeKeywords;
    }
}
