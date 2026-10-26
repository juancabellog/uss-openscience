package cl.uss.ca.external.pure.model;

public class OutputStatus {
    private Long pureId;
    private Boolean currentStatus;
    private String statusUri;
    private String statusEs;
    private String statusEn;
    private Integer publicationYear;
    private Integer publicationMonth;
    private Integer publicationDay;

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public Boolean getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(Boolean currentStatus) {
        this.currentStatus = currentStatus;
    }

    public String getStatusUri() {
        return statusUri;
    }

    public void setStatusUri(String statusUri) {
        this.statusUri = statusUri;
    }

    public String getStatusEs() {
        return statusEs;
    }

    public void setStatusEs(String statusEs) {
        this.statusEs = statusEs;
    }

    public String getStatusEn() {
        return statusEn;
    }

    public void setStatusEn(String statusEn) {
        this.statusEn = statusEn;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public void setPublicationYear(Integer publicationYear) {
        this.publicationYear = publicationYear;
    }

    public Integer getPublicationMonth() {
        return publicationMonth;
    }

    public void setPublicationMonth(Integer publicationMonth) {
        this.publicationMonth = publicationMonth;
    }

    public Integer getPublicationDay() {
        return publicationDay;
    }

    public void setPublicationDay(Integer publicationDay) {
        this.publicationDay = publicationDay;
    }
}
