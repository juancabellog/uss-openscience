package cl.uss.ca.ethics.auth;

import cl.uss.ca.auth.EmailNormalizer;

/**
 * Habilita Ethics, RESEARCHER y ADMINISTRATOR sobre una cuenta OpenScience que ya existe.
 * Si el correo no tiene cuenta, no crea una.
 */
public final class EthicsAdminBootstrap {
    public static final String NOT_FOUND = "Bootstrap admin user not found.";
    public static final String EMAIL = "juan.cabello@uss.cl";

    private EthicsAdminBootstrap() {
    }

    public static AdminAccount ensure(String email, EthicsAdminDirectory directory) {
        String normalized = EmailNormalizer.normalize(email);
        if (normalized == null) {
            return null;
        }
        EthicsAdminDirectory.AdminAccount account = directory.findAccount(normalized);
        if (account == null) {
            return null;
        }
        if (directory.ensureEthicsActive(account.id())) {
            directory.audit(account.id(), "ETHICS_ACCESS_ENABLED", "ETHICS");
        }
        if (directory.ensureStructuralRole(account.id(), "RESEARCHER")) {
            directory.audit(account.id(), "ETHICS_ROLE_ASSIGNED", "RESEARCHER");
        }
        if (directory.ensureStructuralRole(account.id(), "ADMINISTRATOR")) {
            directory.audit(account.id(), "ETHICS_ROLE_ASSIGNED", "ADMINISTRATOR");
        }
        return new AdminAccount(account.id(), account.personId(), account.purePersonId());
    }

    public record AdminAccount(long id, Long personId, Long purePersonId) {
    }
}
