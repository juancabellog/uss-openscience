package cl.uss.ca.ethics.directory;

import cl.uss.ca.api.dto.EthicsOnboardingDto;
import cl.uss.ca.api.dto.GenericMessageDto;
import cl.uss.ca.api.dto.PurePersonIdentity;
import cl.uss.ca.auth.EmailNormalizer;
import cl.uss.ca.auth.UserStatus;
import cl.uss.ca.ethics.auth.EthicsAccountActivated;
import cl.uss.ca.ethics.auth.EthicsAdminDirectory;
import cl.uss.ca.ethics.auth.EthicsAdminRepository;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.identity.InstitutionalDomains;
import cl.uss.ca.identity.InstitutionalOnboarding;
import cl.uss.ca.mail.MailLocale;
import cl.uss.ca.repository.AccessRepository;
import cl.uss.ca.repository.AccessUserRecord;
import cl.uss.ca.repository.PersonRepository;
import cl.uss.ca.security.CurrentUser;
import cl.uss.ca.service.AccountMailer;
import cl.uss.ca.service.AuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Alta institucional por correo. Reutiliza {@code app_user} y el token de activación de OpenScience.
 * No crea una segunda cuenta ni entrega la contraseña a Administración.
 */
@ApplicationScoped
public class EthicsOnboardingService implements EthicsAccountActivated {
    public static final String USE_LOGIN = "ETHICS_USE_LOGIN";
    public static final String INVITE_SENT = "ETHICS_INVITE_SENT";
    public static final String NOT_INSTITUTIONAL = "NOT_INSTITUTIONAL_DOMAIN";

    @Inject
    EthicsDirectoryService directory;

    @Inject
    EthicsAdminRepository admin;

    @Inject
    PersonRepository people;

    @Inject
    AccessRepository accounts;

    @Inject
    AuthService auth;

    @Inject
    AccountMailer mailer;

    public EthicsOnboardingDto resolve(String email) {
        String normalized = requireInstitutional(email);
        EthicsAdminDirectory.AdminAccount account = admin.findAccount(normalized);
        if (account != null) {
            return existing(directory.get(account.id()), invitationOf(account.id()));
        }
        PurePersonIdentity pure = people.findIdentityByEmail(normalized);
        if (pure != null && pure.id != null) {
            return pure(normalized, pure);
        }
        EthicsOnboardingDto created = new EthicsOnboardingDto();
        created.kind = InstitutionalOnboarding.NEW_INSTITUTIONAL_PERSON;
        created.email = normalized;
        return created;
    }

    public EthicsOnboardingDto invite(CurrentUser actor, String email) {
        return invite(actor, email, null);
    }

    public EthicsOnboardingDto invite(CurrentUser actor, String email, String flowLocale) {
        EthicsOnboardingDto resolved = resolve(email);
        if (InstitutionalOnboarding.EXISTING_USER.equals(resolved.kind)) {
            AccessUserRecord current = accounts.findUserByEmail(resolved.email);
            if (current != null && !current.isDeleted() && UserStatus.PENDING_ACTIVATION.name().equals(current.status)) {
                return send(current, resolved, actor);
            }
            throw new ValidationException("ACCOUNT_EXISTS", "El usuario ya tiene cuenta. Habilite Ethics en lugar de invitarlo.");
        }
        String normalized = resolved.email;
        AccessUserRecord existing = accounts.findUserByEmail(normalized);
        if (existing != null && !existing.isDeleted() && UserStatus.ACTIVE.name().equals(existing.status)) {
            throw new ValidationException("ACCOUNT_EXISTS", "El usuario ya tiene cuenta. Habilite Ethics en lugar de invitarlo.");
        }
        if (existing != null && !existing.isDeleted() && UserStatus.PENDING_ACTIVATION.name().equals(existing.status)) {
            return send(existing, resolved, actor);
        }
        AccessUserRecord user = new AccessUserRecord();
        user.email = email == null ? normalized : email.trim();
        user.emailNormalized = normalized;
        user.firstName = text(resolved.givenName);
        user.lastName = text(resolved.familyName);
        user.status = UserStatus.PENDING_ACTIVATION.name();
        user.authProvider = "LOCAL";
        user.preferredLanguage = MailLocale.resolve(null, flowLocale);
        user.createdBy = actorId(actor);
        long id;
        try {
            id = accounts.insertUser(user);
        } catch (DatabaseException ex) {
            AccessUserRecord raced = accounts.findUserByEmail(normalized);
            if (raced != null && UserStatus.PENDING_ACTIVATION.name().equals(raced.status)) {
                return send(raced, resolved, actor);
            }
            throw ex;
        }
        user.id = id;
        linkPure(id, resolved, actor);
        return send(user, resolved, actor);
    }

    public GenericMessageDto publicStart(String email, String flowLocale) {
        if (!InstitutionalDomains.isAllowedInstitutionalEmail(email)) {
            throw new ValidationException(NOT_INSTITUTIONAL, InstitutionalDomains.SELF_REGISTRATION_DENIED);
        }
        String normalized = EmailNormalizer.normalize(email);
        AccessUserRecord existing = accounts.findUserByEmail(normalized);
        if (existing != null && !existing.isDeleted() && UserStatus.ACTIVE.name().equals(existing.status)) {
            return GenericMessageDto.of(USE_LOGIN, "Si ya tienes una cuenta, utiliza Iniciar sesión.");
        }
        if (existing != null && !existing.isDeleted() && UserStatus.PENDING_ACTIVATION.name().equals(existing.status)) {
            admin.markEthicsPending(existing.id);
            rememberFlowLanguage(existing, flowLocale);
            boolean sent = auth.issueEthicsInvitation(existing);
            admin.audit(null, existing.id, "USER_INVITATION_SENT", "ethics invitation");
            return GenericMessageDto.of(INVITE_SENT, "Si el correo es institucional, recibirás los pasos para continuar.", sent);
        }
        if (existing != null) {
            return GenericMessageDto.of(USE_LOGIN, "Si ya tienes una cuenta, utiliza Iniciar sesión.");
        }
        EthicsOnboardingDto invited = invite(null, email, flowLocale);
        return GenericMessageDto.of(
                INVITE_SENT,
                "Si el correo es institucional, recibirás los pasos para continuar.",
                invited.emailSent
        );
    }

    @Override
    public void onActivated(long userId, String givenName, String familyName, String campus, String faculty, String positionTitle) {
        if (!"PENDING".equals(admin.ethicsApplicationStatus(userId))) {
            return;
        }
        AccessUserRecord user = accounts.findUserById(userId);
        if (user == null) {
            return;
        }
        if (InstitutionalOnboarding.profileRequired(givenName, familyName)) {
            return;
        }
        Long personId = admin.insertInstitutionalPerson(
                null,
                user.email,
                givenName,
                familyName,
                campus,
                faculty,
                positionTitle,
                null
        );
        if (personId != null) {
            admin.linkPerson(userId, personId);
        }
        boolean enabled = admin.ensureEthicsActive(userId);
        boolean researcher = false;
        if (InstitutionalDomains.isAllowedInstitutionalEmail(user.email)) {
            researcher = admin.ensureStructuralRole(userId, "RESEARCHER");
        }
        admin.audit(userId, "USER_REGISTERED", "institutional account activated");
        if (enabled) {
            admin.audit(userId, "ETHICS_ACCESS_ENABLED", "ethics access enabled after activation");
        }
        if (researcher) {
            admin.audit(userId, "RESEARCHER_ASSIGNED", "default researcher");
        }
        welcome(userId, user.email, (givenName + " " + familyName).trim());
    }

    private EthicsOnboardingDto send(AccessUserRecord user, EthicsOnboardingDto resolved, CurrentUser actor) {
        admin.markEthicsPending(user.id);
        if (InstitutionalOnboarding.PURE_PERSON.equals(resolved.kind)) {
            linkPure(user.id, resolved, actor);
        }
        boolean sent = auth.issueEthicsInvitation(user);
        admin.audit(actorId(actor), user.id, "USER_INVITATION_SENT", "ethics invitation");
        resolved.invitation = invitationOf(user.id);
        resolved.emailSent = sent;
        return resolved;
    }

    private void linkPure(long userId, EthicsOnboardingDto resolved, CurrentUser actor) {
        if (!InstitutionalOnboarding.PURE_PERSON.equals(resolved.kind)) {
            return;
        }
        PurePersonIdentity pure = people.findIdentityByEmail(resolved.email);
        if (pure == null || pure.id == null) {
            return;
        }
        Long personId = admin.insertInstitutionalPerson(
                pure.id,
                resolved.email,
                text(pure.firstName),
                text(pure.lastName),
                null,
                null,
                pure.position,
                actorId(actor)
        );
        if (personId != null) {
            admin.linkPerson(userId, personId);
        }
    }

    private void welcome(long userId, String email, String name) {
        try {
            AccessUserRecord stored = accounts.findUserById(userId);
            String language = stored == null ? null : stored.preferredLanguage;
            mailer.sendEthicsWelcome(email, name, language);
        } catch (RuntimeException ex) {
            admin.audit(userId, "ETHICS_WELCOME_FAILED", "welcome email failed");
        }
    }

    private EthicsOnboardingDto existing(cl.uss.ca.api.dto.EthicsDirectoryUserDto user, String invitation) {
        EthicsOnboardingDto dto = new EthicsOnboardingDto();
        dto.kind = InstitutionalOnboarding.EXISTING_USER;
        dto.user = user;
        dto.email = user.email;
        dto.givenName = user.givenName;
        dto.familyName = user.familyName;
        dto.ethicsAccess = user.ethicsAccess;
        dto.accountStatus = user.accountStatus;
        dto.invitation = invitation;
        return dto;
    }

    private static EthicsOnboardingDto pure(String email, PurePersonIdentity pure) {
        EthicsOnboardingDto dto = new EthicsOnboardingDto();
        dto.kind = InstitutionalOnboarding.PURE_PERSON;
        dto.email = email;
        dto.givenName = pure.firstName;
        dto.familyName = pure.lastName;
        dto.position = pure.position;
        return dto;
    }

    private String invitationOf(long userId) {
        AccessUserRecord user = accounts.findUserById(userId);
        if (user == null || user.isDeleted()) {
            return null;
        }
        if (UserStatus.ACTIVE.name().equals(user.status)) {
            return "ACCEPTED";
        }
        if (UserStatus.DISABLED.name().equals(user.status)) {
            return "CANCELLED";
        }
        if (!UserStatus.PENDING_ACTIVATION.name().equals(user.status)) {
            return null;
        }
        AccessRepository.TokenRecord token = accounts.latestToken(userId, AuthService.PURPOSE_ACTIVATION);
        if (token == null || token.usedAt != null || token.expiresAt == null
                || !token.expiresAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            return "EXPIRED";
        }
        return "PENDING";
    }

    private void rememberFlowLanguage(AccessUserRecord user, String flowLocale) {
        if (user == null || user.preferredLanguage != null && !user.preferredLanguage.isBlank()) {
            return;
        }
        if (flowLocale == null || flowLocale.isBlank()) {
            return;
        }
        String language = MailLocale.resolve(null, flowLocale);
        accounts.updatePreferredLanguage(user.id, language);
        user.preferredLanguage = language;
    }

    private static String requireInstitutional(String email) {
        if (!InstitutionalDomains.isAllowedInstitutionalEmail(email)) {
            throw new ValidationException(NOT_INSTITUTIONAL, InstitutionalDomains.SELF_REGISTRATION_DENIED);
        }
        String normalized = EmailNormalizer.normalize(email);
        if (!EmailNormalizer.looksLikeEmail(normalized)) {
            throw new ValidationException("EMAIL_INVALID", "Indique un correo válido.");
        }
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static Long actorId(CurrentUser actor) {
        if (actor == null || actor.id == null || actor.id.isBlank() || !actor.id.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.valueOf(actor.id);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
