package cl.uss.ca.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ElectronicVersionItem {
    private Long pureId;
    private String typeDiscriminator;
    private String doi;
    private ClassificationRef accessType;
    private ClassificationRef versionType;

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public String getTypeDiscriminator() {
        return typeDiscriminator;
    }

    public void setTypeDiscriminator(String typeDiscriminator) {
        this.typeDiscriminator = typeDiscriminator;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public ClassificationRef getAccessType() {
        return accessType;
    }

    public void setAccessType(ClassificationRef accessType) {
        this.accessType = accessType;
    }

    public ClassificationRef getVersionType() {
        return versionType;
    }

    public void setVersionType(ClassificationRef versionType) {
        this.versionType = versionType;
    }
}
