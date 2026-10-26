package cl.uss.ca.service;

import cl.uss.ca.api.dto.AdminUserDeleteResultDto;
import cl.uss.ca.api.dto.AdminUserSummaryDto;
import cl.uss.ca.api.dto.AdminUserWriteDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.auth.AppModule;
import cl.uss.ca.auth.AppPermission;
import cl.uss.ca.auth.EmailNormalizer;
import cl.uss.ca.auth.LastAdminPolicy;
import cl.uss.ca.auth.UserDeletionPolicy;
import cl.uss.ca.auth.UserStatus;
import cl.uss.ca.exception.ConflictException;
import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.AccessRepository;
import cl.uss.ca.repository.AccessRoleRecord;
import cl.uss.ca.repository.AccessUserRecord;
import cl.uss.ca.security.CurrentUser;
import cl.uss.ca.security.CurrentUserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class UserAdminService {
    private static final Logger LOG = Logger.getLogger(UserAdminService.class.getName());

    @Inject
    AccessRepository accessRepository;

    @Inject
    AuthService authService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    @ConfigProperty(name = "openscience.auth.bootstrap.email")
    Optional<String> bootstrapEmail;

    @Inject
    @ConfigProperty(name = "openscience.auth.bootstrap.firstName", defaultValue = "Administrador")
    String bootstrapFirstName;

    @Inject
    @ConfigProperty(name = "openscience.auth.bootstrap.lastName", defaultValue = "OPENSCIENCE")
    String bootstrapLastName;

    public void bootstrapIfNeeded() {
        boolean tables = accessRepository.available();
        long users = tables ? accessRepository.countUsers() : -1;
        String email = resolveBootstrapEmail();
        LOG.info("Auth bootstrap check tables=" + tables + " users=" + users + " emailConfigured=" + (email != null));
        if (!tables) {
            LOG.warning("Auth bootstrap skipped: faltan tablas app_*. Ejecute backend/sql/proposed-auth.sql");
            return;
        }
        if (users > 0) {
            LOG.info("Auth bootstrap skipped: already have users");
            return;
        }
        if (email == null) {
            LOG.warning("Auth bootstrap skipped: openscience.auth.bootstrap.email vacío. En liberty:dev póngalo con puntos en bootstrap.properties");
            return;
        }
        AccessRoleRecord admin = accessRepository.findRoleByCode("PLATFORM_ADMIN");
        if (admin == null) {
            LOG.warning("Auth bootstrap skipped: no existe rol PLATFORM_ADMIN. Ejecute el seed de proposed-auth.sql");
            return;
        }
        AccessUserRecord user = new AccessUserRecord();
        user.email = email;
        user.emailNormalized = email;
        user.firstName = bootstrapFirstName;
        user.lastName = bootstrapLastName;
        user.status = UserStatus.PENDING_ACTIVATION.name();
        user.authProvider = "LOCAL";
        user.preferredLanguage = "es";
        long id = accessRepository.insertUser(user);
        accessRepository.replaceUserRoles(id, List.of(admin.id));
        AccessUserRecord stored = accessRepository.findUserById(id);
        boolean sent = authService.issueActivation(stored);
        accessRepository.recordEvent("USER_CREATED", null, id, null, "bootstrap");
        LOG.info("Auth bootstrap created PENDING_ACTIVATION admin id=" + id
                + " email=" + email
                + " invitationSent=" + sent);
    }

    public PageResponse<AdminUserSummaryDto> list(
            String search, String status, Long roleId, String sort, String direction, int page, int size
    ) {
        accessRepository.assertAvailable();
        PageResponse<AccessUserRecord> result = accessRepository.listUsers(search, status, roleId, sort, direction, page, size);
        List<AdminUserSummaryDto> items = new ArrayList<>();
        for (AccessUserRecord user : result.items) {
            items.add(toSummary(user, false));
        }
        return PageResponse.of(items, result.page, result.size, result.totalElements);
    }

    public AdminUserSummaryDto get(long id) {
        accessRepository.assertAvailable();
        AccessUserRecord user = accessRepository.findUserById(id);
        if (user == null) {
            throw new NotFoundException("USER_NOT_FOUND", "User was not found");
        }
        return toSummary(user, false);
    }

    public AdminUserSummaryDto create(AdminUserWriteDto body) {
        accessRepository.assertAvailable();
        validateWrite(body, true);
        AccessUserRecord existing = accessRepository.findUserByEmail(body.email);
        if (existing != null && existing.isDeleted()) {
            throw new ConflictException(
                    "ACCOUNT_DELETED",
                    "Existe una cuenta eliminada con este correo. Requiere intervención administrativa."
            );
        }
        if (existing != null) {
            throw new ConflictException("EMAIL_EXISTS", "Ya existe un usuario con este correo electrónico.");
        }
        AccessUserRecord user = new AccessUserRecord();
        user.email = body.email.trim();
        user.emailNormalized = EmailNormalizer.normalize(body.email);
        user.firstName = body.firstName.trim();
        user.lastName = body.lastName.trim();
        user.status = UserStatus.PENDING_ACTIVATION.name();
        user.authProvider = "LOCAL";
        user.preferredLanguage = language(body.preferredLanguage);
        CurrentUser actor = currentUserService.currentOrNull();
        user.createdBy = actor == null ? null : actor.numericId;
        long id = accessRepository.insertUser(user);
        accessRepository.replaceUserRoles(id, body.roleIds);
        AccessUserRecord stored = accessRepository.findUserById(id);
        boolean sent = authService.issueActivation(stored);
        accessRepository.recordEvent("USER_CREATED", actorId(), id, null, null);
        return toSummary(stored, sent);
    }

    public AdminUserSummaryDto update(long id, AdminUserWriteDto body) {
        accessRepository.assertAvailable();
        validateWrite(body, false);
        AccessUserRecord user = required(id);
        rejectDeleted(user);
        protectLastAdmin(user, body.roleIds, user.status);
        accessRepository.updateUser(id, body.firstName.trim(), body.lastName.trim(), language(body.preferredLanguage));
        accessRepository.replaceUserRoles(id, body.roleIds);
        accessRepository.recordEvent("ROLE_ASSIGNED", actorId(), id, null, null);
        return toSummary(accessRepository.findUserById(id), false);
    }

    public AdminUserSummaryDto disable(long id) {
        AccessUserRecord user = required(id);
        rejectDeleted(user);
        protectLastAdmin(user, roleIds(user), UserStatus.DISABLED.name());
        accessRepository.updateStatus(id, UserStatus.DISABLED);
        accessRepository.revokeUserSessions(id);
        accessRepository.recordEvent("USER_DISABLED", actorId(), id, null, null);
        return toSummary(accessRepository.findUserById(id), false);
    }

    public AdminUserSummaryDto enable(long id) {
        AccessUserRecord user = required(id);
        rejectDeleted(user);
        UserStatus next = user.passwordHash == null ? UserStatus.PENDING_ACTIVATION : UserStatus.ACTIVE;
        accessRepository.updateStatus(id, next);
        accessRepository.recordEvent("USER_ENABLED", actorId(), id, null, null);
        return toSummary(accessRepository.findUserById(id), false);
    }

    public AdminUserSummaryDto resend(long id) {
        AccessUserRecord user = required(id);
        rejectDeleted(user);
        if (!UserStatus.PENDING_ACTIVATION.name().equals(user.status)) {
            throw new ValidationException("AUTH_NOT_PENDING", "Only pending users can receive an activation invitation");
        }
        boolean sent = authService.issueActivation(user);
        return toSummary(user, sent);
    }

    public AdminUserDeleteResultDto delete(long id) {
        AccessUserRecord user = required(id);
        if (user.isDeleted()) {
            throw new ValidationException(UserDeletionPolicy.ALREADY_DELETED, "The user is already deleted");
        }
        CurrentUser actor = currentUserService.currentOrNull();
        if (actor != null && actor.numericId != null && actor.numericId == id) {
            throw new ForbiddenException(UserDeletionPolicy.CANNOT_DELETE_SELF, "You cannot delete your own account");
        }
        protectLastAdmin(user, roleIds(user), UserDeletionPolicy.DISPLAY_STATUS);
        accessRepository.assertDeletionReady();
        boolean historical = accessRepository.hasHistoricalActivity(user);
        String reason = accessRepository.historicalReason(user);
        String mode = UserDeletionPolicy.mode(historical);
        Long actorId = actor == null ? null : actor.numericId;
        if (historical) {
            accessRepository.softDeleteUser(user.id, actorId, user.emailNormalized, reason);
        } else {
            accessRepository.physicallyDeleteUser(user.id, actorId, user.emailNormalized, reason);
        }
        LOG.info("User deleted id=" + id + " mode=" + mode + " reason=" + reason);
        return AdminUserDeleteResultDto.of(
                UserDeletionPolicy.EVENT_DELETED,
                mode,
                "Usuario eliminado correctamente."
        );
    }

    private AccessUserRecord required(long id) {
        accessRepository.assertAvailable();
        AccessUserRecord user = accessRepository.findUserById(id);
        if (user == null) {
            throw new NotFoundException("USER_NOT_FOUND", "User was not found");
        }
        return user;
    }

    private void rejectDeleted(AccessUserRecord user) {
        if (user.isDeleted()) {
            throw new ValidationException(UserDeletionPolicy.ALREADY_DELETED, "The user is already deleted");
        }
    }

    private void protectLastAdmin(AccessUserRecord user, List<Long> nextRoles, String nextStatus) {
        boolean currentlyAdmin = !user.isDeleted()
                && accessRepository.hasPermission(user.id, AppModule.ADMINISTRATION, AppPermission.ADMINISTER)
                && UserStatus.ACTIVE.name().equals(user.status);
        if (!currentlyAdmin) {
            return;
        }
        boolean stillAdmin = UserStatus.ACTIVE.name().equals(nextStatus)
                && accessRepository.wouldRemainAdministrator(user.id, nextRoles);
        if (!LastAdminPolicy.canRemoveAdminAccess(
                currentlyAdmin,
                stillAdmin,
                accessRepository.countActiveAdministrators()
        )) {
            throw new ForbiddenException("LAST_ADMIN", "Cannot remove the last active platform administrator");
        }
    }

    private void validateWrite(AdminUserWriteDto body, boolean create) {
        if (body == null || blank(body.firstName) || blank(body.lastName)) {
            throw new ValidationException("AUTH_USER_INVALID", "First name and last name are required");
        }
        if (create && !EmailNormalizer.looksLikeEmail(body.email)) {
            throw new ValidationException("AUTH_EMAIL_INVALID", "A valid email is required");
        }
        if (body.roleIds != null) {
            for (Long roleId : body.roleIds) {
                if (roleId != null && accessRepository.findRole(roleId) == null) {
                    throw new ValidationException("AUTH_ROLE_INVALID", "One of the selected roles is not valid");
                }
            }
        }
    }

    private static List<Long> roleIds(AccessUserRecord user) {
        List<Long> ids = new ArrayList<>();
        for (AccessRoleRecord role : user.roles) {
            ids.add(role.id);
        }
        return ids;
    }

    private AdminUserSummaryDto toSummary(AccessUserRecord user, boolean emailSent) {
        AdminUserSummaryDto dto = new AdminUserSummaryDto();
        dto.id = user.id;
        dto.firstName = user.firstName;
        dto.lastName = user.lastName;
        dto.displayName = ((user.firstName == null ? "" : user.firstName) + " "
                + (user.lastName == null ? "" : user.lastName)).trim();
        dto.email = user.email;
        dto.status = user.isDeleted() ? UserDeletionPolicy.DISPLAY_STATUS : user.status;
        dto.deletedAt = user.deletedAt;
        dto.preferredLanguage = user.preferredLanguage;
        dto.lastLoginAt = user.lastLoginAt;
        dto.createdAt = user.createdAt;
        dto.emailSent = emailSent;
        for (AccessRoleRecord role : user.roles) {
            dto.roles.add(role.name);
            dto.roleIds.add(role.id);
        }
        return dto;
    }

    private Long actorId() {
        CurrentUser actor = currentUserService.currentOrNull();
        return actor == null ? null : actor.numericId;
    }

    private static String language(String value) {
        return "en".equalsIgnoreCase(value) ? "en" : "es";
    }

    private String resolveBootstrapEmail() {
        return firstEmail(
                bootstrapEmail == null ? null : bootstrapEmail.orElse(null),
                configValue("openscience.auth.bootstrap.email"),
                configValue("AUTH_BOOTSTRAP_EMAIL"),
                System.getenv("AUTH_BOOTSTRAP_EMAIL"),
                System.getProperty("openscience.auth.bootstrap.email"),
                System.getProperty("AUTH_BOOTSTRAP_EMAIL")
        );
    }

    private static String firstEmail(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = EmailNormalizer.normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String configValue(String key) {
        try {
            return ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
