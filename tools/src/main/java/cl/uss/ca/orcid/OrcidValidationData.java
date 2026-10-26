package cl.uss.ca.orcid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Datos obtenidos desde ORCID.org, cacheables por ORCID.
 * El resultado Ok NO se cachea: depende del nombre de cada fila del Excel.
 */
public class OrcidValidationData {
    public enum Estado {
        OK,
        NO_ENCONTRADO,
        ERROR,
        VACIO,
        INVALIDO
    }

    private final Estado estado;
    private final String nombreOrcid;
    private final List<String> otherNames;
    private final boolean afiliacionUss;
    private final Set<String> doisOrcid;
    private final String mensaje;

    public OrcidValidationData(Estado estado, String nombreOrcid, List<String> otherNames,
                               boolean afiliacionUss, Set<String> doisOrcid, String mensaje) {
        this.estado = estado;
        this.nombreOrcid = nombreOrcid == null ? "" : nombreOrcid;
        this.otherNames = otherNames == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(otherNames));
        this.afiliacionUss = afiliacionUss;
        this.doisOrcid = doisOrcid == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(doisOrcid));
        this.mensaje = mensaje;
    }

    public static OrcidValidationData vacio() {
        return new OrcidValidationData(Estado.VACIO, "", List.of(), false, Set.of(), "ORCID vacío");
    }

    public static OrcidValidationData invalido(String orcid) {
        return new OrcidValidationData(Estado.INVALIDO, "", List.of(), false, Set.of(),
                "ORCID con formato inválido: " + orcid);
    }

    public static OrcidValidationData noEncontrado(String orcid) {
        return new OrcidValidationData(Estado.NO_ENCONTRADO, "", List.of(), false, Set.of(),
                "ORCID no encontrado: " + orcid);
    }

    public static OrcidValidationData error(String mensaje) {
        return new OrcidValidationData(Estado.ERROR, "", List.of(), false, Set.of(), mensaje);
    }

    public static OrcidValidationData ok(String nombreOrcid, List<String> otherNames,
                                         boolean afiliacionUss, Set<String> doisOrcid) {
        return new OrcidValidationData(Estado.OK, nombreOrcid, otherNames, afiliacionUss, doisOrcid, null);
    }

    public Estado getEstado() {
        return estado;
    }

    public String getNombreOrcid() {
        return nombreOrcid;
    }

    public List<String> getOtherNames() {
        return otherNames;
    }

    public boolean isAfiliacionUss() {
        return afiliacionUss;
    }

    public Set<String> getDoisOrcid() {
        return doisOrcid;
    }

    public String getMensaje() {
        return mensaje;
    }
}
