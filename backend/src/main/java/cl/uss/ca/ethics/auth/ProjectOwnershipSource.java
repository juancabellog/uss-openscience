package cl.uss.ca.ethics.auth;

import java.util.List;

/**
 * Investigador responsable ya expresado por el proyecto.
 * No es una segunda copia del equipo del proyecto.
 */
public interface ProjectOwnershipSource {
    List<OwnedProject> ownedBy(String userId);

    record OwnedProject(String userId, String projectId) {
    }
}
