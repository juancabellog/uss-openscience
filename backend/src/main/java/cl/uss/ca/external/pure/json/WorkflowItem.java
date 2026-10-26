package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowItem {
    private String step;
    private LocalizedText description;

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    public LocalizedText getDescription() {
        return description;
    }

    public void setDescription(LocalizedText description) {
        this.description = description;
    }
}
