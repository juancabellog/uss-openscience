package cl.uss.ca.ethics.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Al arrancar, habilita a los correos configurados si ya tienen cuenta OpenScience.
 */
@ApplicationScoped
public class EthicsAdminBootstrapService {
    private static final Logger LOG = Logger.getLogger(EthicsAdminBootstrapService.class.getName());

    @Inject
    EthicsAdminRepository repository;

    @Inject
    @ConfigProperty(name = "ethics.bootstrap.adminEmails", defaultValue = EthicsAdminBootstrap.EMAIL)
    String adminEmails;

    public void ensureConfigured() {
        if (adminEmails == null || adminEmails.isBlank()) {
            return;
        }
        if (!repository.accountsAvailable()) {
            LOG.warning(EthicsAdminBootstrap.NOT_FOUND);
            return;
        }
        try {
            repository.ensureSchema();
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Ethics admin bootstrap skipped: " + ex.getMessage(), ex);
            return;
        }
        for (String raw : adminEmails.split(",")) {
            String email = raw.trim();
            if (email.isEmpty()) {
                continue;
            }
            EthicsAdminBootstrap.AdminAccount account = EthicsAdminBootstrap.ensure(email, repository);
            if (account == null) {
                LOG.warning(EthicsAdminBootstrap.NOT_FOUND);
                continue;
            }
            LOG.info("Ethics bootstrap admin id=" + account.id()
                    + " personId=" + account.personId()
                    + " purePersonId=" + account.purePersonId());
        }
    }
}
