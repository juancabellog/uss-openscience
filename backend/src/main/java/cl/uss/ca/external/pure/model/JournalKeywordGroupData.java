package cl.uss.ca.external.pure.model;

import java.util.ArrayList;
import java.util.List;

public class JournalKeywordGroupData {
    private Long pureId;
    private String typeDiscriminator;
    private String logicalName;
    private String nameEn;
    private String nameEs;
    private final List<JournalClassificationData> classifications = new ArrayList<>();
    private final List<JournalKeywordData> keywords = new ArrayList<>();

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

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getNameEs() {
        return nameEs;
    }

    public void setNameEs(String nameEs) {
        this.nameEs = nameEs;
    }

    public List<JournalClassificationData> getClassifications() {
        return classifications;
    }

    public List<JournalKeywordData> getKeywords() {
        return keywords;
    }
}
