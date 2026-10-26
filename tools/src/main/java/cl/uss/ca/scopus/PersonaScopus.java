package cl.uss.ca.scopus;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PersonaScopus {
    private final long personId;
    private final String uuid;
    private final String firstName;
    private final String lastName;
    private final Set<String> scopusIds = new LinkedHashSet<>();

    public PersonaScopus(long personId, String uuid, String firstName, String lastName) {
        this.personId = personId;
        this.uuid = uuid == null ? "" : uuid;
        this.firstName = firstName == null ? "" : firstName;
        this.lastName = lastName == null ? "" : lastName;
    }

    public void agregarScopusId(String scopusId) {
        if (scopusId != null && !scopusId.isBlank()) {
            scopusIds.add(scopusId.trim());
        }
    }

    public long getPersonId() {
        return personId;
    }

    public String getUuid() {
        return uuid;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getNombre() {
        return (firstName + " " + lastName).trim();
    }

    public Set<String> getScopusIds() {
        return scopusIds;
    }

    public int cantidadScopusIds() {
        return scopusIds.size();
    }

    public String todosScopusIdsTexto() {
        return scopusIds.stream().collect(Collectors.joining("; "));
    }

    public boolean tieneScopusId(String scopusId) {
        return scopusId != null && scopusIds.contains(scopusId.trim());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PersonaScopus persona)) {
            return false;
        }
        return personId == persona.personId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(personId);
    }
}
