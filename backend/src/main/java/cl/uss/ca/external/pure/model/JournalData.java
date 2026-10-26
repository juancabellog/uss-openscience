package cl.uss.ca.external.pure.model;

public class JournalData {
    private String uuid;
    private Long titlePureId;
    private String title;
    private Long issnPureId;
    private String issn;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Long getTitlePureId() {
        return titlePureId;
    }

    public void setTitlePureId(Long titlePureId) {
        this.titlePureId = titlePureId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getIssnPureId() {
        return issnPureId;
    }

    public void setIssnPureId(Long issnPureId) {
        this.issnPureId = issnPureId;
    }

    public String getIssn() {
        return issn;
    }

    public void setIssn(String issn) {
        this.issn = issn;
    }
}
