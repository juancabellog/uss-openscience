package cl.uss.ca.external.pure.model;

public class ElectronicVersion {
    private Long pureId;
    private String typeDiscriminator;
    private String doi;
    private String accessTypeUri;
    private String accessTypeEs;
    private String accessTypeEn;
    private String versionTypeUri;
    private String versionTypeEs;
    private String versionTypeEn;

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

    public String getAccessTypeUri() {
        return accessTypeUri;
    }

    public void setAccessTypeUri(String accessTypeUri) {
        this.accessTypeUri = accessTypeUri;
    }

    public String getAccessTypeEs() {
        return accessTypeEs;
    }

    public void setAccessTypeEs(String accessTypeEs) {
        this.accessTypeEs = accessTypeEs;
    }

    public String getAccessTypeEn() {
        return accessTypeEn;
    }

    public void setAccessTypeEn(String accessTypeEn) {
        this.accessTypeEn = accessTypeEn;
    }

    public String getVersionTypeUri() {
        return versionTypeUri;
    }

    public void setVersionTypeUri(String versionTypeUri) {
        this.versionTypeUri = versionTypeUri;
    }

    public String getVersionTypeEs() {
        return versionTypeEs;
    }

    public void setVersionTypeEs(String versionTypeEs) {
        this.versionTypeEs = versionTypeEs;
    }

    public String getVersionTypeEn() {
        return versionTypeEn;
    }

    public void setVersionTypeEn(String versionTypeEn) {
        this.versionTypeEn = versionTypeEn;
    }
}
