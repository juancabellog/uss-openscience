package cl.uss.ca.external.pure.model;

public class JournalIdentifierData {
    private Long pureId;
    private String typeDiscriminator;
    private String idSource;
    private String value;
    private String typeUri;
    private String typeEn;
    private String typeEs;

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

    public String getIdSource() {
        return idSource;
    }

    public void setIdSource(String idSource) {
        this.idSource = idSource;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getTypeUri() {
        return typeUri;
    }

    public void setTypeUri(String typeUri) {
        this.typeUri = typeUri;
    }

    public String getTypeEn() {
        return typeEn;
    }

    public void setTypeEn(String typeEn) {
        this.typeEn = typeEn;
    }

    public String getTypeEs() {
        return typeEs;
    }

    public void setTypeEs(String typeEs) {
        this.typeEs = typeEs;
    }
}
