package cl.uss.ca.orcid;

public class OrcidFilaValidada {
    private final OrcidExcelFila origen;
    private final String uuid;
    private final String nombreOrcid;
    private final String ok;
    private final String afiliacion;
    private final String doisEnComun;
    private final int cantidadDoisBd;
    private final int cantidadDoisOrcid;
    private final int cantidadDoisComunes;

    public OrcidFilaValidada(OrcidExcelFila origen, String uuid, String nombreOrcid, String ok,
                             String afiliacion, String doisEnComun,
                             int cantidadDoisBd, int cantidadDoisOrcid, int cantidadDoisComunes) {
        this.origen = origen;
        this.uuid = uuid == null ? "" : uuid;
        this.nombreOrcid = nombreOrcid == null ? "" : nombreOrcid;
        this.ok = ok;
        this.afiliacion = afiliacion;
        this.doisEnComun = doisEnComun == null ? "" : doisEnComun;
        this.cantidadDoisBd = cantidadDoisBd;
        this.cantidadDoisOrcid = cantidadDoisOrcid;
        this.cantidadDoisComunes = cantidadDoisComunes;
    }

    public OrcidExcelFila getOrigen() {
        return origen;
    }

    public String getUuid() {
        return uuid;
    }

    public String getNombreOrcid() {
        return nombreOrcid;
    }

    public String getOk() {
        return ok;
    }

    public String getAfiliacion() {
        return afiliacion;
    }

    public String getDoisEnComun() {
        return doisEnComun;
    }

    public int getCantidadDoisBd() {
        return cantidadDoisBd;
    }

    public int getCantidadDoisOrcid() {
        return cantidadDoisOrcid;
    }

    public int getCantidadDoisComunes() {
        return cantidadDoisComunes;
    }
}
