package cl.uss.ca.ethics.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Carga la configuración al crear el bean. Un YAML inválido impide arrancar con permisos parciales.
 */
@ApplicationScoped
public class EthicsAuthorizationConfig {
    private final RoleDefinitionProvider definitions;
    private final RoleAssignmentProvider assignments;

    /** Lo usa el proxy de Weld. La instancia real entra por el constructor con dependencias. */
    protected EthicsAuthorizationConfig() {
        this.definitions = null;
        this.assignments = null;
    }

    @Inject
    public EthicsAuthorizationConfig(EthicsAdminRepository persisted) {
        this.definitions = YamlRoleDefinitionProvider.loadClasspath();
        this.assignments = CompositeRoleAssignmentProvider.persisted(persisted);
    }

    @Produces
    @ApplicationScoped
    public RoleDefinitionProvider roleDefinitions() {
        return definitions;
    }

    @Produces
    @ApplicationScoped
    public RoleAssignmentProvider roleAssignments() {
        return assignments;
    }
}
