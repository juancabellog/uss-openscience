package cl.uss.ca.external.pure.model;

public class JournalKeywordData {
    private Long pureId;
    private String state;
    private String origin;
    private String keywordUri;
    private String keywordEn;
    private String keywordEs;

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

    public String getKeywordUri() {
        return keywordUri;
    }

    public void setKeywordUri(String keywordUri) {
        this.keywordUri = keywordUri;
    }

    public String getKeywordEn() {
        return keywordEn;
    }

    public void setKeywordEn(String keywordEn) {
        this.keywordEn = keywordEn;
    }

    public String getKeywordEs() {
        return keywordEs;
    }

    public void setKeywordEs(String keywordEs) {
        this.keywordEs = keywordEs;
    }
}
