package cl.uss.ca.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicationStatusItem {
    private Long pureId;
    private Boolean current;
    private ClassificationRef publicationStatus;
    private PublicationDateItem publicationDate;

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public Boolean getCurrent() {
        return current;
    }

    public void setCurrent(Boolean current) {
        this.current = current;
    }

    public ClassificationRef getPublicationStatus() {
        return publicationStatus;
    }

    public void setPublicationStatus(ClassificationRef publicationStatus) {
        this.publicationStatus = publicationStatus;
    }

    public PublicationDateItem getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(PublicationDateItem publicationDate) {
        this.publicationDate = publicationDate;
    }
}
