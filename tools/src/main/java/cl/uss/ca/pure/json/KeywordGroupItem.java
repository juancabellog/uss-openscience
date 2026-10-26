package cl.uss.ca.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KeywordGroupItem {
    private Long pureId;
    private String typeDiscriminator;
    private String logicalName;
    private LocalizedText name;
    private List<KeywordContainerItem> keywordContainers;
    private JsonNode keywords;

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

    public LocalizedText getName() {
        return name;
    }

    public void setName(LocalizedText name) {
        this.name = name;
    }

    public List<KeywordContainerItem> getKeywordContainers() {
        return keywordContainers;
    }

    public void setKeywordContainers(List<KeywordContainerItem> keywordContainers) {
        this.keywordContainers = keywordContainers;
    }

    public JsonNode getKeywords() {
        return keywords;
    }

    public void setKeywords(JsonNode keywords) {
        this.keywords = keywords;
    }
}
