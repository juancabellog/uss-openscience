package cl.uss.ca.external.pure.model;

public class KeywordData {
    public static final String TYPE_STRUCTURED = "STRUCTURED";
    public static final String TYPE_FREE = "FREE";

    private Long containerPureId;
    private String keywordType;
    private String state;
    private String origin;
    private String keywordUri;
    private String keywordEs;
    private String keywordEn;
    private String locale;
    private String freeKeyword;

    public Long getContainerPureId() {
        return containerPureId;
    }

    public void setContainerPureId(Long containerPureId) {
        this.containerPureId = containerPureId;
    }

    public String getKeywordType() {
        return keywordType;
    }

    public void setKeywordType(String keywordType) {
        this.keywordType = keywordType;
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

    public String getKeywordEs() {
        return keywordEs;
    }

    public void setKeywordEs(String keywordEs) {
        this.keywordEs = keywordEs;
    }

    public String getKeywordEn() {
        return keywordEn;
    }

    public void setKeywordEn(String keywordEn) {
        this.keywordEn = keywordEn;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getFreeKeyword() {
        return freeKeyword;
    }

    public void setFreeKeyword(String freeKeyword) {
        this.freeKeyword = freeKeyword;
    }
}
