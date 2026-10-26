package cl.uss.ca.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TitleItem {

    private Long pureId;
    private LocalizedText value;
    private ClassificationRef type;

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public LocalizedText getValue() {
        return value;
    }

    public void setValue(LocalizedText value) {
        this.value = value;
    }

    public ClassificationRef getType() {
        return type;
    }

    public void setType(ClassificationRef type) {
        this.type = type;
    }
}
