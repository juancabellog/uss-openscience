package cl.uss.ca.ethics.auth;

import java.util.List;
import java.util.Set;

/**
 * Definición de un rol. No incluye personas ni asignaciones.
 * Las categorías agrupan capabilities; no son el alcance.
 */
public record RoleDefinition(
        String id,
        String idLabel,
        List<String> global,
        List<String> committee,
        List<String> project,
        List<String> process
) {
    public Set<String> capabilities() {
        return Set.copyOf(all());
    }

    public List<String> all() {
        return java.util.stream.Stream.of(global, committee, project, process)
                .flatMap(List::stream)
                .toList();
    }
}
