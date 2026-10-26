package cl.uss.ca.pure.ro;

import java.util.ArrayList;
import java.util.List;

public class KeywordGroupData {
    private Long pureId;
    private String typeDiscriminator;
    private String logicalName;
    private String nameEs;
    private String nameEn;
    private List<KeywordData> keywords = new ArrayList<>();

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

    public String getLogicalName() {
        return logicalName;
    }

    public void setLogicalName(String logicalName) {
        this.logicalName = logicalName;
    }

    public String getNameEs() {
        return nameEs;
    }

    public void setNameEs(String nameEs) {
        this.nameEs = nameEs;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public List<KeywordData> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<KeywordData> keywords) {
        this.keywords = keywords == null ? new ArrayList<>() : keywords;
    }
}
