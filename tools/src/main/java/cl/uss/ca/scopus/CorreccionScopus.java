package cl.uss.ca.scopus;

import java.util.LinkedHashSet;
import java.util.Set;

public record CorreccionScopus(
        long personId,
        String uuid,
        String nombre,
        String scopusIdEliminar,
        Set<String> otrosScopusIds
) {
    public CorreccionScopus {
        uuid = uuid == null ? "" : uuid;
        nombre = nombre == null ? "" : nombre;
        scopusIdEliminar = scopusIdEliminar == null ? "" : scopusIdEliminar.trim();
        otrosScopusIds = otrosScopusIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(otrosScopusIds));
    }

    public String otrosScopusIdsTexto() {
        return String.join("; ", otrosScopusIds);
    }
}
