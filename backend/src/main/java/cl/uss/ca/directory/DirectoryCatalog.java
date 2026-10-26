package cl.uss.ca.directory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DirectoryCatalog {
    public final Map<String, DirectoryPersonRef> byRut = new HashMap<>();
    public final Map<String, DirectoryPersonRef> byEmail = new HashMap<>();
    public final Map<Long, DirectoryPersonRef> byId = new HashMap<>();
    public final Set<Long> activeIds = new HashSet<>();

    public void add(DirectoryPersonRef person) {
        if (person == null) {
            return;
        }
        byId.put(person.id, person);
        if (person.rutHmac != null) {
            byRut.put(person.rutHmac, person);
        }
        for (String email : person.emails) {
            byEmail.put(email, person);
        }
        if (person.activeInDirectory) {
            activeIds.add(person.id);
        }
    }

    public static DirectoryCatalog of(Collection<DirectoryPersonRef> people) {
        DirectoryCatalog catalog = new DirectoryCatalog();
        if (people != null) {
            for (DirectoryPersonRef person : people) {
                catalog.add(person);
            }
        }
        return catalog;
    }
}
