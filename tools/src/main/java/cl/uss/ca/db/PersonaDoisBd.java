package cl.uss.ca.db;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class PersonaDoisBd {
    private final String uuid;
    private final Set<String> dois;

    public PersonaDoisBd(String uuid, Set<String> dois) {
        this.uuid = uuid;
        this.dois = dois == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(dois));
    }

    public static PersonaDoisBd noEncontrada() {
        return new PersonaDoisBd(null, Set.of());
    }

    public String getUuid() {
        return uuid;
    }

    public Set<String> getDois() {
        return dois;
    }

    public boolean encontrada() {
        return uuid != null && !uuid.isBlank();
    }
}
