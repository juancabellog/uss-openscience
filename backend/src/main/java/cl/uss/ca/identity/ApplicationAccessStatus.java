package cl.uss.ca.identity;

/**
 * Acceso de una cuenta a una aplicación. No es el estado de un RoleAssignment.
 */
public enum ApplicationAccessStatus {
    ACTIVE,
    INACTIVE,
    REVOKED;

    public boolean open() {
        return this == ACTIVE;
    }
}
