package cl.uss.ca.ethics.auth;

import cl.uss.ca.auth.EmailNormalizer;
import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.UnauthorizedException;
import cl.uss.ca.identity.EthicsTestInstitutionalEmails;
import cl.uss.ca.identity.InstitutionalDomains;
import cl.uss.ca.security.CurrentUser;
import cl.uss.ca.security.CurrentUserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Una sesión de OpenScience no abre Ethics.
 * Solo entra quien ya tiene ETHICS activo, o un dominio institucional que se aprovisiona al entrar.
 */
@ApplicationScoped
public class EthicsEntryService {
    private static final Logger LOG = Logger.getLogger(EthicsEntryService.class.getName());
    @Inject
    EthicsAdminRepository repository;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    EthicsTestInstitutionalEmails testInstitutionalEmails;

    public void assertMayEnter(CurrentUser user) {
        if (currentUserService.isDevMock()) {
            return;
        }
        if (user == null || user.email == null || user.email.isBlank()) {
            throw new UnauthorizedException("AUTH_REQUIRED", "Authentication is required");
        }
        String email = EmailNormalizer.normalize(user.email);
        EthicsAdminDirectory.AdminAccount account = repository.findAccount(email);
        if (account == null) {
            throw new ForbiddenException("ETHICS_ACCESS_DENIED", InstitutionalDomains.SELF_REGISTRATION_DENIED);
        }
        if (repository.ethicsApplicationActive(account.id())) {
            rememberLocalPerson(account);
            return;
        }
        if (testInstitutionalEmails != null && testInstitutionalEmails.eligible(email)) {
            if (repository.ensureEthicsActive(account.id())) {
                repository.audit(account.id(), "ETHICS_ACCESS_ENABLED", "ETHICS");
            }
            if (repository.ensureStructuralRole(account.id(), "RESEARCHER")) {
                repository.audit(account.id(), "ETHICS_ROLE_ASSIGNED", "RESEARCHER");
            }
            rememberLocalPerson(account);
            return;
        }
        throw new ForbiddenException("ETHICS_ACCESS_DENIED", InstitutionalDomains.SELF_REGISTRATION_DENIED);
    }

    private void rememberLocalPerson(EthicsAdminDirectory.AdminAccount account) {
        if (account.purePersonId() != null || account.personId() != null) {
            return;
        }
        try {
            repository.storeAccountPerson(account.id());
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Account person was not stored for user " + account.id(), ex);
        }
    }
}
