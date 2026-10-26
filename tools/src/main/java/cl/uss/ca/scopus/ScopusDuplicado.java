package cl.uss.ca.scopus;

import java.util.ArrayList;
import java.util.List;

public class ScopusDuplicado {
    private final String scopusId;
    private final List<PersonaScopus> personas = new ArrayList<>();

    public ScopusDuplicado(String scopusId) {
        this.scopusId = scopusId;
    }

    public String getScopusId() {
        return scopusId;
    }

    public List<PersonaScopus> getPersonas() {
        return personas;
    }

    public void agregarPersona(PersonaScopus persona) {
        if (persona != null && !personas.contains(persona)) {
            personas.add(persona);
        }
    }

    public List<PersonaScopus> personasConUnSoloId() {
        List<PersonaScopus> resultado = new ArrayList<>();
        for (PersonaScopus persona : personas) {
            if (persona.cantidadScopusIds() == 1) {
                resultado.add(persona);
            }
        }
        return resultado;
    }

    public List<PersonaScopus> personasConMultiplesIds() {
        List<PersonaScopus> resultado = new ArrayList<>();
        for (PersonaScopus persona : personas) {
            if (persona.cantidadScopusIds() > 1) {
                resultado.add(persona);
            }
        }
        return resultado;
    }

    public boolean tienePatronUnoVsMultiples() {
        return !personasConUnSoloId().isEmpty() && !personasConMultiplesIds().isEmpty();
    }

    public boolean todosConUnSoloId() {
        if (personas.isEmpty()) {
            return false;
        }
        return personasConUnSoloId().size() == personas.size();
    }

    public boolean todosConMultiplesIds() {
        if (personas.isEmpty()) {
            return false;
        }
        return personasConMultiplesIds().size() == personas.size();
    }
}
