package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocalizedText {

    @JsonProperty("es_CL")
    private String esCl;

    @JsonProperty("en_US")
    private String enUs;

    @JsonProperty("en_GB")
    private String enGb;

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

    public String getEnGb() {
        return enGb;
    }

    public void setEnGb(String enGb) {
        this.enGb = enGb;
    }

    public String preferred() {
        if (esCl != null && !esCl.isBlank()) {
            return esCl.trim();
        }
        if (enUs != null && !enUs.isBlank()) {
            return enUs.trim();
        }
        if (enGb != null && !enGb.isBlank()) {
            return enGb.trim();
        }
        return null;
    }
}
