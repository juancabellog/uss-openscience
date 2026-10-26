package cl.uss.ca.orcid.email;

public class RegistroOrcid {
    private final String id;
    private final String uuid;
    private final String nombre;
    private final String cargo;
    private final String orcid;
    private final String doi;
    private final String posicion;
    private final String nombreOrcid;
    private final String ok;
    private final String afiliacion;
    private final String doisEnComun;
    private String email;
    private String gender;

    public RegistroOrcid(String id, String uuid, String nombre, String cargo, String orcid,
                         String doi, String posicion, String nombreOrcid, String ok,
                         String afiliacion, String doisEnComun) {
        this.id = nuloAVacio(id);
        this.uuid = nuloAVacio(uuid);
        this.nombre = nuloAVacio(nombre);
        this.cargo = nuloAVacio(cargo);
        this.orcid = nuloAVacio(orcid);
        this.doi = nuloAVacio(doi);
        this.posicion = nuloAVacio(posicion);
        this.nombreOrcid = nuloAVacio(nombreOrcid);
        this.ok = nuloAVacio(ok);
        this.afiliacion = nuloAVacio(afiliacion);
        this.doisEnComun = nuloAVacio(doisEnComun);
        this.email = "";
        this.gender = "";
    }

    public String getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = nuloAVacio(email);
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = nuloAVacio(gender);
    }

    private static String nuloAVacio(String value) {
        return value == null ? "" : value.trim();
    }
}
