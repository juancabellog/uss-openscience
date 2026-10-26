package cl.uss.ca.orcid;

import cl.uss.ca.openalex.ResearcherMatchingService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Segunda validación de coherencia de nombre: tokens ORCID ⊆ tokens XLSX.
 *
 * No demuestra identidad absoluta. OpenAlex + DOI ya propusieron el ORCID;
 * aquí solo se comprueba que el nombre público de ORCID.org no introduzca
 * palabras que no existen en el nombre USS del Excel.
 *
 * No se reutiliza {@link ResearcherMatchingService#coinciden} porque admite
 * iniciales (p. ej. "C." ≡ "Carolina") y matching bidireccional. Ante duda,
 * esta clase prefiere NO.
 */
public class OrcidNombreMatcher {

    /**
     * True si todas las palabras significativas del nombre ORCID aparecen
     * en el nombre del Excel. El Excel puede tener apellidos/nombres extra.
     */
    public boolean nombresCoherentes(String nombreExcel, String nombreOrcid) {
        List<String> tokensOrcid = tokenizarNombre(nombreOrcid);
        List<String> tokensExcel = tokenizarNombre(nombreExcel);
        if (tokensOrcid.isEmpty() || tokensExcel.isEmpty()) {
            return false;
        }
        Set<String> excel = new LinkedHashSet<>(tokensExcel);
        for (String token : tokensOrcid) {
            if (!excel.contains(token)) {
                return false;
            }
        }
        return true;
    }

    public String normalizarNombre(String nombre) {
        String t = ResearcherMatchingService.normalize(nombre);
        if (t.isEmpty()) {
            return "";
        }
        t = t.replaceAll("[^a-z0-9\\s]", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    public List<String> tokenizarNombre(String nombre) {
        String normalizado = normalizarNombre(nombre);
        if (normalizado.isEmpty()) {
            return List.of();
        }
        String[] parts = normalizado.split(" ");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part.toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }
}
