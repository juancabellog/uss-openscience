package cl.uss.ca.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LinkItem {
    private Long pureId;
    private String url;
    private LocalizedText description;
    private ClassificationRef linkType;

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

    public LocalizedText getDescription() {
        return description;
    }

    public void setDescription(LocalizedText description) {
        this.description = description;
    }

    public ClassificationRef getLinkType() {
        return linkType;
    }

    public void setLinkType(ClassificationRef linkType) {
        this.linkType = linkType;
    }
}
