package cl.uss.ca.orcid;

public class OrcidExcelFila {
    private final int numeroFilaExcel;
    private final String id;
    private final String nombre;
    private final String cargo;
    private final String orcid;
    private final String doi;
    private final String posicion;

    public OrcidExcelFila(int numeroFilaExcel, String id, String nombre, String cargo,
                          String orcid, String doi, String posicion) {
        this.numeroFilaExcel = numeroFilaExcel;
        this.id = nuloAVacio(id);
        this.nombre = nuloAVacio(nombre);
        this.cargo = nuloAVacio(cargo);
        this.orcid = nuloAVacio(orcid);
        this.doi = nuloAVacio(doi);
        this.posicion = nuloAVacio(posicion);
    }

    public int getNumeroFilaExcel() {
        return numeroFilaExcel;
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCargo() {
        return cargo;
    }

    public String getOrcid() {
        return orcid;
    }

    public String getDoi() {
        return doi;
    }

    public String getPosicion() {
        return posicion;
    }

    private static String nuloAVacio(String value) {
        return value == null ? "" : value;
    }
}
