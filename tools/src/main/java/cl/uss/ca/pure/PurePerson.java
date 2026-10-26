package cl.uss.ca.pure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class PurePerson {
    private Long pureId;
    private String uuid;
    private String firstName;
    private String lastName;
    private String orcid;
    private boolean orcidAuthenticated;
    private String email;
    private LocalDate fechaIngreso;
    private String cargo;
    private String titulo;
    private OffsetDateTime fechaCreacionPure;
    private OffsetDateTime fechaModificacionPure;
    private List<StaffAssociation> asociaciones = new ArrayList<>();
    private List<AcademicTitle> titulos = new ArrayList<>();

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getOrcid() {
        return orcid;
    }

    public void setOrcid(String orcid) {
        this.orcid = orcid;
    }

    public boolean isOrcidAuthenticated() {
        return orcidAuthenticated;
    }

    public void setOrcidAuthenticated(boolean orcidAuthenticated) {
        this.orcidAuthenticated = orcidAuthenticated;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(LocalDate fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public OffsetDateTime getFechaCreacionPure() {
        return fechaCreacionPure;
    }

    public void setFechaCreacionPure(OffsetDateTime fechaCreacionPure) {
        this.fechaCreacionPure = fechaCreacionPure;
    }

    public OffsetDateTime getFechaModificacionPure() {
        return fechaModificacionPure;
    }

    public void setFechaModificacionPure(OffsetDateTime fechaModificacionPure) {
        this.fechaModificacionPure = fechaModificacionPure;
    }

    public List<StaffAssociation> getAsociaciones() {
        return asociaciones;
    }

    public void setAsociaciones(List<StaffAssociation> asociaciones) {
        this.asociaciones = asociaciones == null ? new ArrayList<>() : asociaciones;
    }

    public List<AcademicTitle> getTitulos() {
        return titulos;
    }

    public void setTitulos(List<AcademicTitle> titulos) {
        this.titulos = titulos == null ? new ArrayList<>() : titulos;
    }
}
