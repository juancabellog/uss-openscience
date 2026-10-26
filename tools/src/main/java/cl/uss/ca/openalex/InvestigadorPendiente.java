package cl.uss.ca.openalex;

import java.util.ArrayList;
import java.util.List;

public class InvestigadorPendiente {
    private final Long personId;
    private final String firstName;
    private final String lastName;
    private final String cargo;
    private final List<PublicacionPendiente> publicaciones = new ArrayList<>();

    public InvestigadorPendiente(Long personId, String firstName, String lastName, String cargo) {
        this.personId = personId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.cargo = cargo;
    }

    public Long getPersonId() {
        return personId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getCargo() {
        return cargo;
    }

    public String getNombreCompleto() {
        return ResearcherMatchingService.nombreCompleto(firstName, lastName);
    }

    public List<PublicacionPendiente> getPublicaciones() {
        return publicaciones;
    }

    public void agregarPublicacion(PublicacionPendiente publicacion) {
        if (publicacion != null) {
            publicaciones.add(publicacion);
        }
    }
}
