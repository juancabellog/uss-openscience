package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FreeKeywordLocaleItem {
    private String locale;
    private List<String> freeKeywords;

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public List<String> getFreeKeywords() {
        return freeKeywords;
    }

    public void setFreeKeywords(List<String> freeKeywords) {
        this.freeKeywords = freeKeywords;
    }
}
