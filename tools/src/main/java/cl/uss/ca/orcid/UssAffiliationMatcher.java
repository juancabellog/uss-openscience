package cl.uss.ca.orcid;

import cl.uss.ca.openalex.ResearcherMatchingService;

/**
 * Detecta Universidad San Sebastián en nombres de organización ORCID
 * sin depender de tildes, mayúsculas ni puntuación.
 */
public class UssAffiliationMatcher {

    public boolean esAfiliacionUSS(String nombre) {
        return esUniversidadSanSebastian(nombre);
    }

    public boolean esUniversidadSanSebastian(String organizationName) {
        String normalizado = normalizarOrganizacion(organizationName);
        if (normalizado.isEmpty()) {
            return false;
        }
        return normalizado.contains("universidad san sebastian")
                || normalizado.contains("san sebastian university");
    }

    public boolean algunaEsUniversidadSanSebastian(Iterable<String> organizaciones) {
        if (organizaciones == null) {
            return false;
        }
        for (String nombre : organizaciones) {
            if (esUniversidadSanSebastian(nombre)) {
                return true;
            }
        }
        return false;
    }

    public String normalizarOrganizacion(String nombre) {
        String t = ResearcherMatchingService.normalize(nombre);
        if (t.isEmpty()) {
            return "";
        }
        t = t.replaceAll("[^a-z0-9\\s]", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }
}
