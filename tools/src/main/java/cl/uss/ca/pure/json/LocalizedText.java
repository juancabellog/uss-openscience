package cl.uss.ca.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocalizedText {

    @JsonProperty("es_CL")
    private String esCl;

    @JsonProperty("en_US")
    private String enUs;

    public String getEsCl() {
        return esCl;
    }

    public void setEsCl(String esCl) {
        this.esCl = esCl;
    }

    public String getEnUs() {
        return enUs;
    }

    public void setEnUs(String enUs) {
        this.enUs = enUs;
    }

    public String preferred() {
        if (esCl != null && !esCl.isBlank()) {
            return esCl.trim();
        }
        if (enUs != null && !enUs.isBlank()) {
            return enUs.trim();
        }
        return null;
    }
}
