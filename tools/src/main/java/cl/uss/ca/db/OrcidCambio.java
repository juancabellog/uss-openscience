package cl.uss.ca.db;

public class OrcidCambio {
    private final String uuid;
    private final String nombre;
    private final String orcidAnterior;
    private final String orcidNuevo;

    public OrcidCambio(String uuid, String nombre, String orcidAnterior, String orcidNuevo) {
        this.uuid = uuid == null ? "" : uuid;
        this.nombre = nombre == null ? "" : nombre;
        this.orcidAnterior = orcidAnterior == null ? "" : orcidAnterior;
        this.orcidNuevo = orcidNuevo == null ? "" : orcidNuevo;
    }

    public String getUuid() {
        return uuid;
    }

    public String getNombre() {
        return nombre;
    }

    public String getOrcidAnterior() {
        return orcidAnterior;
    }

    public String getOrcidNuevo() {
        return orcidNuevo;
    }
}
