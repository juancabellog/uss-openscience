package cl.uss.ca.scopus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PersonaScopusInterna {
    private final long personId;
    private final String uuid;
    private final String nombre;
    private final List<ScopusInternoCandidato> scopus = new ArrayList<>();

    public PersonaScopusInterna(long personId, String uuid, String nombre) {
        this.personId = personId;
        this.uuid = uuid == null ? "" : uuid;
        this.nombre = nombre == null ? "" : nombre;
    }

    public long getPersonId() {
        return personId;
    }

    public String getUuid() {
        return uuid;
    }

    public String getNombre() {
        return nombre;
    }

    public List<ScopusInternoCandidato> getScopus() {
        return scopus;
    }

    public void agregar(ScopusInternoCandidato candidato) {
        if (candidato != null) {
            scopus.add(candidato);
        }
    }

    public Set<String> getScopusIdsDuplicados() {
        Set<String> ids = new LinkedHashSet<>();
        for (ScopusInternoCandidato candidato : scopus) {
            if (candidato.scopusId() != null && !candidato.scopusId().isBlank()) {
                ids.add(candidato.scopusId());
            }
        }
        return ids;
    }
}
