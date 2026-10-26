package cl.uss.ca.openalex;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class OpenAlexAuthor {
    private String displayName;
    private String rawAuthorName;
    private String openAlexId;
    private String orcid;
    private Integer posicion;

    public List<String> nombresCandidatos() {
        Set<String> nombres = new LinkedHashSet<>();
        if (rawAuthorName != null && !rawAuthorName.isBlank()) {
            nombres.add(rawAuthorName.trim());
        }
        if (displayName != null && !displayName.isBlank()) {
            nombres.add(displayName.trim());
        }
        return new ArrayList<>(nombres);
    }

    public String nombreMostrado() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        if (rawAuthorName != null && !rawAuthorName.isBlank()) {
            return rawAuthorName.trim();
        }
        return "";
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRawAuthorName() {
        return rawAuthorName;
    }

    public void setRawAuthorName(String rawAuthorName) {
        this.rawAuthorName = rawAuthorName;
    }

    public String getOpenAlexId() {
        return openAlexId;
    }

    public void setOpenAlexId(String openAlexId) {
        this.openAlexId = openAlexId;
    }

    public String getOrcid() {
        return orcid;
    }

    public void setOrcid(String orcid) {
        this.orcid = orcid;
    }

    public Integer getPosicion() {
        return posicion;
    }

    public void setPosicion(Integer posicion) {
        this.posicion = posicion;
    }
}
