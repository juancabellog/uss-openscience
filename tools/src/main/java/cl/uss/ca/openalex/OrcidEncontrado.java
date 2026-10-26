package cl.uss.ca.openalex;

public class OrcidEncontrado {
    private final Long personId;
    private final String nombrePersona;
    private final String cargo;
    private final String orcid;
    private final String doi;
    private final String titulo;
    private final String autorOpenAlex;
    private final Integer posicion;

    public OrcidEncontrado(Long personId, String nombrePersona, String cargo, String orcid,
                           String doi, String titulo, String autorOpenAlex, Integer posicion) {
        this.personId = personId;
        this.nombrePersona = nombrePersona;
        this.cargo = cargo;
        this.orcid = orcid;
        this.doi = doi;
        this.titulo = titulo;
        this.autorOpenAlex = autorOpenAlex;
        this.posicion = posicion;
    }

    public Long getPersonId() {
        return personId;
    }

    public String getNombrePersona() {
        return nombrePersona;
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

    public String getTitulo() {
        return titulo;
    }

    public String getAutorOpenAlex() {
        return autorOpenAlex;
    }

    public Integer getPosicion() {
        return posicion;
    }
}
