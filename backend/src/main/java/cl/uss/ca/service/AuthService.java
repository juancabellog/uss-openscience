package cl.uss.ca.service;

import cl.uss.ca.api.dto.ActivationPreviewDto;
import cl.uss.ca.api.dto.AuthPermissionDto;
import cl.uss.ca.api.dto.AuthPublicConfigDto;
import cl.uss.ca.api.dto.AuthUserDto;
import cl.uss.ca.api.dto.GenericMessageDto;
import cl.uss.ca.api.dto.RegisterRequestDto;
import cl.uss.ca.api.dto.RegisterResultDto;
import cl.uss.ca.auth.ActivationOrigin;
import cl.uss.ca.auth.AllowedEmailDomains;
import cl.uss.ca.auth.AuthRateLimiter;
import cl.uss.ca.auth.EffectivePermission;
import cl.uss.ca.auth.EmailNormalizer;
import cl.uss.ca.auth.PasswordHasher;
import cl.uss.ca.auth.PasswordPolicy;
import cl.uss.ca.auth.PersonNamePolicy;
import cl.uss.ca.auth.SecureTokens;
import cl.uss.ca.auth.SelfRegistrationDefaultRole;
import cl.uss.ca.auth.TokenPolicy;
import cl.uss.ca.auth.TokenPreviewStatus;
import cl.uss.ca.auth.UserStatus;
import cl.uss.ca.ethics.auth.EthicsAccountActivated;
import cl.uss.ca.exception.UnauthorizedException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.identity.InstitutionalCatalog;
import cl.uss.ca.mail.MailLocale;
import cl.uss.ca.identity.InstitutionalOnboarding;
import cl.uss.ca.repository.AccessRepository;
import cl.uss.ca.repository.AccessRoleRecord;
import cl.uss.ca.repository.AccessUserRecord;
import cl.uss.ca.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class AuthService {
    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    public static final String PURPOSE_ACTIVATION = "ACTIVATION";
    public static final String PURPOSE_RESET = "PASSWORD_RESET";
    public static final String COOKIE = "OS_SESSION";
    public static final String GENERIC_LOGIN = "Correo o contraseña incorrectos.";
    public static final String GENERIC_FORGOT =
            "Si existe una cuenta asociada a ese correo, recibirá un enlace para restablecer su contraseña.";
    public static final String REGISTER_CREATED =
            "Su cuenta fue creada. Revise su correo para definir su contraseña y habilitar la cuenta.";
    public static final String REGISTER_MAIL_FAILED =
            "Su cuenta fue creada, pero no fue posible enviar el correo de activación. Intente reenviar la invitación más tarde.";
    public static final String REGISTER_EXISTS_ACTIVE =
            "Ya existe una cuenta asociada a este correo. Inicie sesión o utilice '¿Olvidó su contraseña?'.";
    public static final String REGISTER_EXISTS_PENDING =
            "No fue necesario crear otra cuenta. Puede solicitar el reenvío de la invitación.";
    public static final String REGISTER_EXISTS_UNAVAILABLE =
            "No fue posible crear la cuenta. Intente nuevamente.";
    public static final String REGISTER_RATE_LIMITED =
            "No fue posible crear la cuenta. Intente nuevamente.";

    @Inject
    AccessRepository accessRepository;

    @Inject
    PasswordPolicy passwordPolicy;

    @Inject
    AccountMailer accountMailer;

    @Inject
    Instance<EthicsAccountActivated> ethicsActivation;

    @Inject
    AuthRateLimiter rateLimiter;

    @Inject
    AllowedEmailDomains allowedEmailDomains;

    @Inject
    @ConfigProperty(name = "openscience.auth.password.iterations", defaultValue = "210000")
    int iterations;

    @Inject
    @ConfigProperty(name = "openscience.auth.activation.ttlHours", defaultValue = "24")
    int activationTtlHours;

    @Inject
    @ConfigProperty(name = "openscience.auth.reset.ttlMinutes", defaultValue = "60")
    int resetTtlMinutes;

    @Inject
    @ConfigProperty(name = "openscience.auth.session.hours", defaultValue = "12")
    int sessionHours;

    @Inject
    @ConfigProperty(name = "openscience.auth.lockout.threshold", defaultValue = "8")
    int lockoutThreshold;

    @Inject
    @ConfigProperty(name = "openscience.auth.lockout.minutes", defaultValue = "15")
    int lockoutMinutes;

    @Inject
    @ConfigProperty(name = "openscience.auth.rateLimit.login", defaultValue = "20")
    int loginLimit;

    @Inject
    @ConfigProperty(name = "openscience.auth.rateLimit.forgot", defaultValue = "8")
    int forgotLimit;

    @Inject
    @ConfigProperty(name = "openscience.auth.registration.defaultRole", defaultValue = "CONSULTA")
    String selfRegistrationDefaultRole;

    public AuthUserDto login(String email, String password, String clientKey) {
        accessRepository.assertAvailable();
        if (!rateLimiter.allow("login:" + clientKey, loginLimit, 15 * 60_000L)) {
            throw new ValidationException("AUTH_RATE_LIMITED", GENERIC_LOGIN);
        }
        AccessUserRecord user = accessRepository.findUserByEmail(email);
        if (user == null) {
            throw new UnauthorizedException("AUTH_INVALID", GENERIC_LOGIN);
        }
        if (user.isDeleted()) {
            throw new UnauthorizedException("AUTH_INVALID", GENERIC_LOGIN);
        }
        accessRepository.unlockIfExpired(user);
        if (UserStatus.PENDING_ACTIVATION.name().equals(user.status)) {
            throw new UnauthorizedException("AUTH_PENDING", "La cuenta aún no está habilitada.");
        }
        if (UserStatus.DISABLED.name().equals(user.status)) {
            throw new UnauthorizedException("AUTH_INVALID", GENERIC_LOGIN);
        }
        if (UserStatus.LOCKED.name().equals(user.status)) {
            throw new UnauthorizedException("AUTH_LOCKED", "La cuenta está temporalmente bloqueada.");
        }
        if (user.passwordHash == null || !PasswordHasher.matches(password == null ? new char[0] : password.toCharArray(), user.passwordHash)) {
            accessRepository.recordLoginFailure(user.id, lockoutThreshold, lockoutMinutes);
            throw new UnauthorizedException("AUTH_INVALID", GENERIC_LOGIN);
        }
        accessRepository.recordLoginSuccess(user.id);
        accessRepository.recordEvent("USER_LOGIN", user.id, user.id, null, null);
        return toDto(accessRepository.findUserById(user.id));
    }

    public String createSession(long userId) {
        String raw = SecureTokens.rawToken();
        accessRepository.insertSession(
                userId,
                SecureTokens.hash(raw),
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(Math.max(1, sessionHours))
        );
        return raw;
    }

    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || !accessRepository.available()) {
            return;
        }
        accessRepository.revokeSession(SecureTokens.hash(rawToken));
    }

    public CurrentUser resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || !accessRepository.available()) {
            return null;
        }
        AccessRepository.SessionRecord session = accessRepository.findSession(SecureTokens.hash(rawToken));
        if (session == null || session.revokedAt != null || session.expiresAt == null
                || session.expiresAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            return null;
        }
        AccessUserRecord user = accessRepository.findUserById(session.userId);
        if (user == null || user.isDeleted() || !UserStatus.ACTIVE.name().equals(user.status)) {
            return null;
        }
        accessRepository.touchSession(session.id);
        return toCurrent(user);
    }

    public AuthUserDto me(CurrentUser user) {
        if (user == null) {
            throw new UnauthorizedException("AUTH_REQUIRED", "Authentication is required");
        }
        if (user.numericId != null && accessRepository.available()) {
            AccessUserRecord stored = accessRepository.findUserById(user.numericId);
            if (stored != null) {
                return toDto(stored);
            }
        }
        AuthUserDto dto = new AuthUserDto();
        dto.id = user.id;
        dto.displayName = user.displayName;
        dto.email = user.email;
        dto.status = user.status;
        dto.preferredLanguage = user.preferredLanguage;
        dto.roles.addAll(user.roles);
        for (EffectivePermission permission : user.permissions) {
            dto.effectivePermissions.add(AuthPermissionDto.of(permission.module.name(), permission.permission.name()));
        }
        return dto;
    }

    public GenericMessageDto forgotPassword(String email, String clientKey) {
        return forgotPassword(email, clientKey, null);
    }

    public GenericMessageDto forgotPassword(String email, String clientKey, String flowLocale) {
        accessRepository.assertAvailable();
        if (!rateLimiter.allow("forgot:" + clientKey, forgotLimit, 15 * 60_000L)) {
            return GenericMessageDto.of("AUTH_FORGOT_ACCEPTED", GENERIC_FORGOT);
        }
        AccessUserRecord user = accessRepository.findUserByEmail(email);
        if (user != null && !user.isDeleted() && UserStatus.ACTIVE.name().equals(user.status)) {
            rememberFlowLanguage(user, flowLocale);
            issueReset(user);
        }
        return GenericMessageDto.of("AUTH_FORGOT_ACCEPTED", GENERIC_FORGOT);
    }

    public ActivationPreviewDto previewToken(String rawToken, String purpose) {
        accessRepository.assertAvailable();
        ActivationPreviewDto dto = new ActivationPreviewDto();
        dto.purpose = purpose;
        String hash = SecureTokens.hash(rawToken);
        if (hash == null) {
            dto.valid = false;
            dto.status = TokenPreviewStatus.INVALID.name();
            return dto;
        }
        AccessRepository.TokenRecord token = accessRepository.findValidToken(hash, purpose);
        if (token == null) {
            dto.valid = false;
            dto.status = TokenPreviewStatus.INVALID.name();
            return dto;
        }
        AccessUserRecord user = accessRepository.findUserById(token.userId);
        if (user != null && user.isDeleted()) {
            dto.valid = false;
            dto.status = TokenPreviewStatus.INVALID.name();
            return dto;
        }
        TokenPreviewStatus status = TokenPreviewStatus.classify(
                token.usedAt,
                token.expiresAt,
                OffsetDateTime.now(ZoneOffset.UTC),
                user == null ? null : user.status
        );
        dto.status = status.name();
        dto.valid = status == TokenPreviewStatus.VALID;
        dto.maskedEmail = mask(user == null ? null : user.email);
        if (user != null) {
            dto.preferredLanguage = MailLocale.resolve(user.preferredLanguage, null);
        }
        if (dto.valid && user != null) {
            dto.profileRequired = InstitutionalOnboarding.profileRequired(user.firstName, user.lastName);
            dto.givenName = user.firstName == null || user.firstName.isBlank() ? null : user.firstName;
            dto.familyName = user.lastName == null || user.lastName.isBlank() ? null : user.lastName;
        }
        return dto;
    }

    public boolean issueEthicsInvitation(AccessUserRecord user) {
        return issueActivation(user, ActivationOrigin.ETHICS_INVITE);
    }

    public AuthUserDto activate(String rawToken, String password) {
        return activate(rawToken, password, null, null, null, null, null);
    }

    public AuthUserDto activate(
            String rawToken,
            String password,
            String givenName,
            String familyName,
            String campus,
            String faculty,
            String positionTitle
    ) {
        accessRepository.assertAvailable();
        AccessRepository.TokenRecord token = validToken(rawToken, PURPOSE_ACTIVATION);
        if (token == null) {
            throw new ValidationException("AUTH_TOKEN_INVALID", "El enlace de activación no es válido o ha expirado.");
        }
        AccessUserRecord user = accessRepository.findUserById(token.userId);
        if (user == null || user.isDeleted() || UserStatus.DISABLED.name().equals(user.status)) {
            throw new ValidationException("AUTH_TOKEN_INVALID", "El enlace de activación no es válido o ha expirado.");
        }
        if (UserStatus.ACTIVE.name().equals(user.status)) {
            throw new ValidationException("AUTH_ALREADY_ACTIVE", "Esta cuenta ya fue activada.");
        }
        boolean needsProfile = InstitutionalOnboarding.profileRequired(user.firstName, user.lastName);
        String given = PersonNamePolicy.normalize(givenName);
        String family = PersonNamePolicy.normalize(familyName);
        if (needsProfile && (!PersonNamePolicy.isAcceptable(given) || !PersonNamePolicy.isAcceptable(family)
                || !InstitutionalCatalog.accepts(campus, faculty, positionTitle))) {
            throw new ValidationException("AUTH_PROFILE_REQUIRED", "Complete los datos institucionales y la contraseña.");
        }
        passwordPolicy.validate(password);
        if (needsProfile) {
            accessRepository.updateUser(user.id, given, family, user.preferredLanguage == null ? "es" : user.preferredLanguage);
        }
        accessRepository.activate(user.id, PasswordHasher.hash(password.toCharArray(), iterations));
        accessRepository.markTokenUsed(token.id);
        accessRepository.invalidateTokens(user.id, PURPOSE_ACTIVATION);
        accessRepository.recordEvent("USER_ACTIVATED", user.id, user.id, null, null);
        if (ethicsActivation != null) {
            String storedGiven = needsProfile ? given : user.firstName;
            String storedFamily = needsProfile ? family : user.lastName;
            ethicsActivation.forEach(listener -> listener.onActivated(
                    user.id, storedGiven, storedFamily, campus, faculty, positionTitle));
        }
        return toDto(accessRepository.findUserById(user.id));
    }

    public GenericMessageDto resetPassword(String rawToken, String password) {
        accessRepository.assertAvailable();
        passwordPolicy.validate(password);
        AccessRepository.TokenRecord token = validToken(rawToken, PURPOSE_RESET);
        if (token == null) {
            throw new ValidationException("AUTH_TOKEN_INVALID", "El enlace no es válido o ha expirado.");
        }
        AccessUserRecord user = accessRepository.findUserById(token.userId);
        if (user == null || user.isDeleted() || UserStatus.DISABLED.name().equals(user.status)) {
            throw new ValidationException("AUTH_TOKEN_INVALID", "El enlace no es válido o ha expirado.");
        }
        accessRepository.updatePassword(user.id, PasswordHasher.hash(password.toCharArray(), iterations));
        accessRepository.markTokenUsed(token.id);
        accessRepository.invalidateTokens(user.id, PURPOSE_RESET);
        accessRepository.revokeUserSessions(user.id);
        if (UserStatus.LOCKED.name().equals(user.status) || UserStatus.PENDING_ACTIVATION.name().equals(user.status)) {
            accessRepository.updateStatus(user.id, UserStatus.ACTIVE);
        }
        accessRepository.recordEvent("PASSWORD_RESET_COMPLETED", user.id, user.id, null, null);
        return GenericMessageDto.of("AUTH_PASSWORD_UPDATED", "Su contraseña fue actualizada correctamente.");
    }

    public AuthPublicConfigDto publicConfig() {
        return AuthPublicConfigDto.of(true, allowedEmailDomains.registrationUssOnly());
    }

    public RegisterResultDto register(RegisterRequestDto body, String clientKey) {
        accessRepository.assertAvailable();
        if (!rateLimiter.allow("register:" + clientKey, forgotLimit, 15 * 60_000L)) {
            throw new ValidationException("AUTH_RATE_LIMITED", REGISTER_RATE_LIMITED);
        }
        String firstName = PersonNamePolicy.normalize(body == null ? null : body.firstName);
        String lastName = PersonNamePolicy.normalize(body == null ? null : body.lastName);
        if (!PersonNamePolicy.isAcceptable(firstName) || !PersonNamePolicy.isAcceptable(lastName)) {
            throw new ValidationException("AUTH_USER_INVALID", "Nombre y apellido son obligatorios.");
        }
        String email = EmailNormalizer.normalize(body == null ? null : body.email);
        if (!EmailNormalizer.looksLikeEmail(email)) {
            throw new ValidationException("AUTH_EMAIL_INVALID", "El correo electrónico no es válido.");
        }
        if (!allowedEmailDomains.acceptsForRegistration(email)) {
            throw new ValidationException(
                    "AUTH_EMAIL_DOMAIN",
                    "Para crear una cuenta utilice su correo institucional USS."
            );
        }
        accessRepository.recordEvent("SELF_REGISTRATION_REQUESTED", null, null, null, email);
        AccessUserRecord existing = accessRepository.findUserByEmail(email);
        if (existing != null) {
            return duplicateRegistration(existing);
        }
        AccessRoleRecord defaultRole = requireSelfRegistrationRole();
        AccessUserRecord user = new AccessUserRecord();
        user.email = email;
        user.emailNormalized = email;
        user.firstName = firstName;
        user.lastName = lastName;
        user.passwordHash = null;
        user.status = UserStatus.PENDING_ACTIVATION.name();
        user.authProvider = "LOCAL";
        user.preferredLanguage = "es";
        user.createdBy = null;
        long id = accessRepository.insertUser(user);
        accessRepository.addUserRole(id, defaultRole.id);
        AccessUserRecord stored = accessRepository.findUserById(id);
        accessRepository.recordEvent("USER_CREATED", null, id, null, null);
        accessRepository.recordEvent("ROLE_ASSIGNED", null, id, defaultRole.id, defaultRole.code);
        boolean sent = issueActivation(stored, ActivationOrigin.SELF_REGISTRATION);
        return RegisterResultDto.of(
                sent ? "AUTH_REGISTER_CREATED" : "AUTH_REGISTER_MAIL_FAILED",
                sent ? REGISTER_CREATED : REGISTER_MAIL_FAILED,
                RegisterResultDto.OUTCOME_CREATED,
                sent,
                !sent,
                mask(email)
        );
    }

    public GenericMessageDto resendActivationPublic(String email, String clientKey) {
        accessRepository.assertAvailable();
        if (!rateLimiter.allow("resend:" + clientKey, forgotLimit, 15 * 60_000L)) {
            return GenericMessageDto.of("AUTH_RESEND_ACCEPTED", GENERIC_FORGOT);
        }
        AccessUserRecord user = accessRepository.findUserByEmail(email);
        if (user != null && !user.isDeleted() && UserStatus.PENDING_ACTIVATION.name().equals(user.status)) {
            issueActivation(user, ActivationOrigin.SELF_REGISTRATION);
        }
        return GenericMessageDto.of("AUTH_RESEND_ACCEPTED", GENERIC_FORGOT);
    }

    boolean issueActivation(AccessUserRecord user) {
        return issueActivation(user, ActivationOrigin.ADMIN_INVITE);
    }

    boolean issueActivation(AccessUserRecord user, ActivationOrigin origin) {
        if (user == null || user.isDeleted()) {
            return false;
        }
        accessRepository.invalidateTokens(user.id, PURPOSE_ACTIVATION);
        String raw = SecureTokens.rawToken();
        OffsetDateTime expires = OffsetDateTime.now(ZoneOffset.UTC).plusHours(Math.max(1, activationTtlHours));
        accessRepository.insertToken(user.id, PURPOSE_ACTIVATION, SecureTokens.hash(raw), expires);
        try {
            accountMailer.sendActivation(user, raw, expires, origin == null ? ActivationOrigin.ADMIN_INVITE : origin);
            accessRepository.recordEvent("ACTIVATION_EMAIL_SENT", null, user.id, null, null);
            return true;
        } catch (RuntimeException ex) {
            accessRepository.recordEvent("ACTIVATION_EMAIL_FAILED", null, user.id, null, null);
            return false;
        }
    }

    public static String duplicateOutcome(String status) {
        if (UserStatus.ACTIVE.name().equals(status)) {
            return RegisterResultDto.OUTCOME_EXISTS_ACTIVE;
        }
        if (UserStatus.PENDING_ACTIVATION.name().equals(status)) {
            return RegisterResultDto.OUTCOME_EXISTS_PENDING;
        }
        return RegisterResultDto.OUTCOME_EXISTS_UNAVAILABLE;
    }

    AccessRoleRecord requireSelfRegistrationRole() {
        String code = SelfRegistrationDefaultRole.code(selfRegistrationDefaultRole);
        if (SelfRegistrationDefaultRole.forbidsAutomaticAssignment(code)) {
            LOG.severe("Self-registration aborted: configured default role " + code
                    + " cannot be auto-assigned. PLATFORM_ADMIN was not used as fallback.");
            throw new ValidationException(SelfRegistrationDefaultRole.UNSAFE_CODE, REGISTER_EXISTS_UNAVAILABLE);
        }
        AccessRoleRecord role = accessRepository.findRoleByCode(code);
        if (!SelfRegistrationDefaultRole.isAssignable(role)) {
            LOG.severe("Self-registration aborted: default role " + code
                    + " is missing or inactive. No user was created. PLATFORM_ADMIN was not assigned.");
            throw new ValidationException(SelfRegistrationDefaultRole.MISSING_CODE, REGISTER_EXISTS_UNAVAILABLE);
        }
        return role;
    }

    private RegisterResultDto duplicateRegistration(AccessUserRecord existing) {
        if (existing.isDeleted()) {
            return RegisterResultDto.of(
                    "AUTH_REGISTER_UNAVAILABLE",
                    REGISTER_EXISTS_UNAVAILABLE,
                    RegisterResultDto.OUTCOME_EXISTS_UNAVAILABLE,
                    false,
                    false,
                    mask(existing.email)
            );
        }
        String outcome = duplicateOutcome(existing.status);
        if (RegisterResultDto.OUTCOME_EXISTS_ACTIVE.equals(outcome)) {
            return RegisterResultDto.of(
                    "AUTH_REGISTER_EXISTS",
                    REGISTER_EXISTS_ACTIVE,
                    outcome,
                    false,
                    false,
                    mask(existing.email)
            );
        }
        if (RegisterResultDto.OUTCOME_EXISTS_PENDING.equals(outcome)) {
            return RegisterResultDto.of(
                    "AUTH_REGISTER_PENDING",
                    REGISTER_EXISTS_PENDING,
                    outcome,
                    false,
                    true,
                    mask(existing.email)
            );
        }
        return RegisterResultDto.of(
                "AUTH_REGISTER_UNAVAILABLE",
                REGISTER_EXISTS_UNAVAILABLE,
                outcome,
                false,
                false,
                mask(existing.email)
        );
    }

    private void rememberFlowLanguage(AccessUserRecord user, String flowLocale) {
        if (user == null || user.preferredLanguage != null && !user.preferredLanguage.isBlank()) {
            return;
        }
        if (flowLocale == null || flowLocale.isBlank()) {
            return;
        }
        String language = MailLocale.resolve(null, flowLocale);
        accessRepository.updatePreferredLanguage(user.id, language);
        user.preferredLanguage = language;
    }

    private void issueReset(AccessUserRecord user) {
        accessRepository.invalidateTokens(user.id, PURPOSE_RESET);
        String raw = SecureTokens.rawToken();
        OffsetDateTime expires = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(Math.max(5, resetTtlMinutes));
        accessRepository.insertToken(user.id, PURPOSE_RESET, SecureTokens.hash(raw), expires);
        try {
            accountMailer.sendReset(user, raw, expires);
            accessRepository.recordEvent("PASSWORD_RESET_REQUESTED", user.id, user.id, null, null);
        } catch (RuntimeException ignored) {
            // Same public response whether mail failed or not.
        }
    }

    private AccessRepository.TokenRecord validToken(String rawToken, String purpose) {
        String hash = SecureTokens.hash(rawToken);
        if (hash == null) {
            return null;
        }
        AccessRepository.TokenRecord token = accessRepository.findValidToken(hash, purpose);
        if (token == null || !TokenPolicy.isUsable(token.usedAt, token.expiresAt, OffsetDateTime.now(ZoneOffset.UTC))) {
            return null;
        }
        return token;
    }

    CurrentUser toCurrent(AccessUserRecord user) {
        CurrentUser current = new CurrentUser();
        current.id = String.valueOf(user.id);
        current.numericId = user.id;
        current.displayName = ((user.firstName == null ? "" : user.firstName) + " "
                + (user.lastName == null ? "" : user.lastName)).trim();
        current.email = user.email;
        current.status = user.status;
        current.preferredLanguage = user.preferredLanguage;
        for (AccessRoleRecord role : user.roles) {
            current.roles.add(role.code);
        }
        current.role = current.roles.isEmpty() ? "" : current.roles.get(0);
        if (!user.isDeleted()) {
            current.permissions.addAll(accessRepository.effectivePermissions(user.id));
        }
        return current;
    }

    AuthUserDto toDto(AccessUserRecord user) {
        AuthUserDto dto = new AuthUserDto();
        dto.id = String.valueOf(user.id);
        dto.firstName = user.firstName;
        dto.lastName = user.lastName;
        dto.displayName = ((user.firstName == null ? "" : user.firstName) + " "
                + (user.lastName == null ? "" : user.lastName)).trim();
        dto.email = user.email;
        dto.status = user.status;
        dto.preferredLanguage = user.preferredLanguage;
        for (AccessRoleRecord role : user.roles) {
            dto.roles.add(role.code);
        }
        Set<EffectivePermission> permissions = accessRepository.effectivePermissions(user.id);
        for (EffectivePermission permission : permissions) {
            dto.effectivePermissions.add(AuthPermissionDto.of(permission.module.name(), permission.permission.name()));
        }
        return dto;
    }

    static String mask(String email) {
        String normalized = EmailNormalizer.normalize(email);
        if (normalized == null) {
            return null;
        }
        int at = normalized.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return normalized.charAt(0) + "***" + normalized.substring(at);
    }
}
