package cl.uss.ca.ethics.auth;

import cl.uss.ca.auth.EmailNormalizer;
import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.UnauthorizedException;
import cl.uss.ca.identity.InstitutionalDomains;
import cl.uss.ca.security.CurrentUser;
import cl.uss.ca.security.CurrentUserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Una sesión de OpenScience no abre Ethics.
 * Solo entra quien ya tiene ETHICS activo, o un dominio institucional que se aprovisiona al entrar.
 */
@ApplicationScoped
public class EthicsEntryService {
    @Inject
    EthicsAdminRepository repository;

    @Inject
    CurrentUserService currentUserService;

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
            return;
        }
        if (InstitutionalDomains.isAllowedInstitutionalEmail(email)) {
            if (repository.ensureEthicsActive(account.id())) {
                repository.audit(account.id(), "ETHICS_ACCESS_ENABLED", "ETHICS");
            }
            if (repository.ensureStructuralRole(account.id(), "RESEARCHER")) {
                repository.audit(account.id(), "ETHICS_ROLE_ASSIGNED", "RESEARCHER");
            }
            return;
        }
        throw new ForbiddenException("ETHICS_ACCESS_DENIED", InstitutionalDomains.SELF_REGISTRATION_DENIED);
    }
}
