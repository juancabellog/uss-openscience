package cl.uss.ca.ethics.auth;

/**
 * Cuenta ya existente y asignaciones de Ethics. No crea {@code app_user} ni contraseñas.
 */
public interface EthicsAdminDirectory {
    AdminAccount findAccount(String normalizedEmail);

    /** @return true cuando el acceso pasó a ACTIVE en esta llamada */
    boolean ensureEthicsActive(long userId);

    /** @return true cuando el rol estructural quedó ACTIVE en esta llamada */
    boolean ensureStructuralRole(long userId, String roleId);

    void audit(long userId, String action, String detail);

    record AdminAccount(long id, String email, Long personId, Long purePersonId) {
    }
}
