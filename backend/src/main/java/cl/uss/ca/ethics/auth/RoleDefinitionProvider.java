package cl.uss.ca.ethics.auth;

import java.util.Collection;
import java.util.Optional;

public interface RoleDefinitionProvider {
    Collection<RoleDefinition> definitions();

    Optional<RoleDefinition> find(String roleId);

    boolean knownCapability(String capability);
}
