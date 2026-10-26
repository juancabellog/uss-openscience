package cl.uss.ca.external.pure.model;

public class OutputLink {
    private Long pureId;
    private String url;
    private String descriptionEs;
    private String descriptionEn;
    private String linkTypeUri;
    private String linkTypeEs;
    private String linkTypeEn;

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescriptionEs() {
        return descriptionEs;
    }

    public void setDescriptionEs(String descriptionEs) {
        this.descriptionEs = descriptionEs;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public String getLinkTypeUri() {
        return linkTypeUri;
    }

    public void setLinkTypeUri(String linkTypeUri) {
        this.linkTypeUri = linkTypeUri;
    }

    public String getLinkTypeEs() {
        return linkTypeEs;
    }

    public void setLinkTypeEs(String linkTypeEs) {
        this.linkTypeEs = linkTypeEs;
    }

    public String getLinkTypeEn() {
        return linkTypeEn;
    }

    public void setLinkTypeEn(String linkTypeEn) {
        this.linkTypeEn = linkTypeEn;
    }
}
