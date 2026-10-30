package cl.uss.ca.ethics.directory;

import cl.uss.ca.api.dto.EthicsDirectoryRoleDto;
import cl.uss.ca.api.dto.EthicsDirectoryUserDto;
import cl.uss.ca.api.dto.EthicsProfileWriteDto;
import cl.uss.ca.api.dto.PageResponse;
import cl.uss.ca.ethics.auth.AuthorizationContext;
import cl.uss.ca.ethics.auth.AuthorizationService;
import cl.uss.ca.ethics.auth.EthicsAdminRepository;
import cl.uss.ca.ethics.auth.EthicsRoleRevocation;
import cl.uss.ca.ethics.auth.EthicsUserCatalog;
import cl.uss.ca.ethics.auth.RoleAssignment;
import cl.uss.ca.ethics.auth.RoleDefinitionProvider;
import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.UnauthorizedException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.AccessRepository;
import cl.uss.ca.repository.AccessUserRecord;
import cl.uss.ca.security.CurrentUser;
import cl.uss.ca.security.CurrentUserService;
import cl.uss.ca.service.AccountMailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Directorio administrativo de Ethics. La autorización es {@code MANAGE_USERS}, no el nombre del rol.
 */
@ApplicationScoped
public class EthicsDirectoryService {
    private static final Set<String> STRUCTURAL = Set.of(
            "RESEARCHER", "ADMINISTRATOR", "COMMITTEE_PRESIDENT", "EXECUTIVE_SECRETARY");

    @Inject
    EthicsDirectoryRepository directory;

    @Inject
    EthicsAdminRepository admin;

    @Inject
    AuthorizationService authorization;

    @Inject
    RoleDefinitionProvider definitions;

    @Inject
    CurrentUserService currentUser;

    @Inject
    AccountMailer mailer;

    @Inject
    AccessRepository accounts;

    public void assertMayManage(CurrentUser user) {
        if (user == null || user.id == null || user.id.isBlank()) {
            throw new UnauthorizedException("AUTH_REQUIRED", "Authentication is required");
        }
        if (!authorization.can(user.id, "MANAGE_USERS", AuthorizationContext.empty())) {
            throw new ForbiddenException("MANAGE_USERS_REQUIRED", "No tiene permiso para administrar usuarios.");
        }
    }

    public PageResponse<EthicsDirectoryUserDto> list(EthicsUserCatalog.Query query) {
        EthicsDirectoryRepository.DirectoryPage page = directory.search(query);
        List<EthicsDirectoryUserDto> items = page.items().stream().map(this::toDto).toList();
        return PageResponse.of(items, page.page(), page.size(), page.total());
    }

    public EthicsDirectoryUserDto get(long userId) {
        return toDto(directory.require(userId));
    }

    public EthicsDirectoryUserDto setAccess(long userId, boolean active, boolean confirmed) {
        EthicsUserCatalog.Account account = directory.require(userId);
        if (!active && directory.activeReviewer(userId) && !confirmed) {
            throw new ValidationException(
                    "ACTIVE_REVIEW",
                    "Este revisor tiene una revisión activa. Deshabilitar Ethics no reasigna esa revisión."
            );
        }
        if (active) {
            boolean enabled = admin.ensureEthicsActive(userId);
            boolean researcher = false;
            if ("INSTITUTIONAL".equals(EthicsUserCatalog.personKind(account.personKind(), account.email()))) {
                researcher = admin.ensureStructuralRole(userId, "RESEARCHER");
            }
            if (enabled) {
                audited(userId, "ETHICS_ACCESS_ENABLED", "ethics access enabled");
                if ("ACTIVE".equals(account.accountStatus())) {
                    welcome(userId, account.email(), (blank(account.givenName()) + " " + blank(account.familyName())).trim());
                }
            }
            if (researcher) {
                audited(userId, "RESEARCHER_ASSIGNED", "default researcher");
            }
            audited(userId, "ENABLE_ETHICS", "ethics access enabled");
        } else {
            directory.deactivateEthics(userId);
            audited(userId, "DISABLE_ETHICS", "ethics access disabled");
        }
        return get(userId);
    }

    public EthicsDirectoryUserDto grantRole(long userId, String roleId) {
        if (roleId == null || !STRUCTURAL.contains(roleId)) {
            throw new ValidationException("ROLE_NOT_STRUCTURAL", "Esa función no se asigna desde la administración de usuarios.");
        }
        EthicsUserCatalog.Account account = directory.require(userId);
        if (!"INSTITUTIONAL".equals(EthicsUserCatalog.personKind(account.personKind(), account.email()))) {
            throw new ForbiddenException(
                    "EXTERNAL_ROLE_DENIED",
                    "Una persona externa no puede ser investigador, secretario, presidente ni administrador."
            );
        }
        if (heldGlobally(account, roleId)) {
            throw new ValidationException("ROLE_ALREADY_ASSIGNED", "El usuario ya tiene ese rol en el mismo alcance.");
        }
        admin.ensureStructuralRole(userId, roleId);
        EthicsDirectoryUserDto granted = get(userId);
        if (!visibleGlobally(granted, roleId)) {
            throw new ValidationException("ROLE_NOT_ASSIGNED", "No se pudo dejar vigente esa función.");
        }
        audited(userId, "GRANT_ETHICS_ROLE", roleId);
        return granted;
    }

    public EthicsDirectoryUserDto revokeRole(long userId, long assignmentId) {
        EthicsUserCatalog.Account account = directory.require(userId);
        RoleAssignment assignment = directory.findAssignment(userId, assignmentId);
        int otherAdminRows = otherAdminRows(account, assignment);
        long otherAdmins = assignment != null && "ADMINISTRATOR".equals(assignment.roleId())
                ? directory.otherActiveAdministrators(userId)
                : 1;
        String denial = EthicsRoleRevocation.decide(
                assignment,
                "ACTIVE".equalsIgnoreCase(account.accountStatus()),
                "ACTIVE".equalsIgnoreCase(account.ethicsStatus()),
                otherAdminRows,
                otherAdmins,
                LocalDate.now()
        );
        if ("ASSIGNMENT_NOT_FOUND".equals(denial)) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND", "La asignación no existe.");
        }
        if ("ROLE_NOT_REMOVABLE".equals(denial)) {
            throw new ForbiddenException("ROLE_NOT_REMOVABLE", "Este rol no se quita desde la administración de usuarios.");
        }
        if ("ROLE_ALREADY_REVOKED".equals(denial)) {
            throw new ValidationException("ROLE_ALREADY_REVOKED", "Este rol ya estaba revocado.");
        }
        if ("LAST_ADMINISTRATOR".equals(denial)) {
            throw new ValidationException(
                    "LAST_ADMINISTRATOR",
                    "No es posible quitar este rol porque es el último administrador activo de Ética de Proyectos."
            );
        }
        if (denial != null) {
            throw new ForbiddenException(denial, "No se puede quitar este rol.");
        }
        if (!directory.revokeAssignment(userId, assignmentId, actorId())) {
            throw new ValidationException("ROLE_ALREADY_REVOKED", "Este rol ya estaba revocado.");
        }
        audited(userId, EthicsRoleRevocation.EVENT, EthicsRoleRevocation.auditDetail(assignment));
        return get(userId);
    }

    public EthicsDirectoryUserDto updateLocal(long userId, EthicsProfileWriteDto body) {
        EthicsUserCatalog.Account account = directory.require(userId);
        if (account.purePersonId() != null) {
            throw new ValidationException("PURE_LOCKED", "Los datos PURE no pueden modificarse");
        }
        if (!"EXTERNAL".equals(account.personKind())) {
            throw new ValidationException("LOCAL_INSTITUTIONAL", "El correo es institucional USS. No hay ficha PURE, así que los datos quedan en la persona local.");
        }
        directory.updateLocalNames(userId, body == null ? "" : body.givenName, body == null ? "" : body.familyName);
        audited(userId, "UPDATE_LOCAL_PROFILE", "local external profile updated");
        return get(userId);
    }

    private EthicsDirectoryUserDto toDto(EthicsUserCatalog.Account account) {
        EthicsDirectoryUserDto dto = new EthicsDirectoryUserDto();
        dto.id = account.id();
        dto.givenName = blank(account.givenName());
        dto.familyName = blank(account.familyName());
        dto.displayName = (dto.givenName + " " + dto.familyName).trim();
        dto.email = blank(account.email());
        dto.personKind = EthicsUserCatalog.personKind(account.personKind(), account.email());
        dto.purePersonId = account.purePersonId();
        dto.dataSource = EthicsUserCatalog.dataSource(account.purePersonId());
        dto.accountStatus = blank(account.accountStatus());
        dto.ethicsAccess = "ACTIVE".equalsIgnoreCase(account.ethicsStatus());
        for (EthicsUserCatalog.VisibleAssignment role : EthicsUserCatalog.visibleAssignments(account.assignments(), LocalDate.now())) {
            var definition = definitions.find(role.roleId());
            if (definition.isEmpty()) {
                continue;
            }
            EthicsDirectoryRoleDto item = new EthicsDirectoryRoleDto();
            item.id = role.roleId();
            item.assignmentId = role.assignmentId();
            item.idLabel = definition.get().idLabel();
            item.contextual = role.contextual();
            item.removable = role.removable();
            item.scopeType = role.scopeType();
            item.scopeId = role.scopeId();
            item.scopeLabel = role.scopeLabel();
            dto.roles.add(item);
        }
        return dto;
    }

    private static boolean visibleGlobally(EthicsDirectoryUserDto user, String roleId) {
        if (user.roles == null) {
            return false;
        }
        for (EthicsDirectoryRoleDto role : user.roles) {
            if (roleId.equals(role.id)
                    && !role.contextual
                    && "GLOBAL".equals(role.scopeType)
                    && "*".equals(role.scopeId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean heldGlobally(EthicsUserCatalog.Account account, String roleId) {
        if (account.assignments() == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        for (RoleAssignment row : account.assignments()) {
            if (row.operativeOn(today)
                    && roleId.equals(row.roleId())
                    && row.scopeType() == cl.uss.ca.ethics.auth.ScopeType.GLOBAL
                    && "*".equals(row.scopeId())) {
                return true;
            }
        }
        return false;
    }

    private static int otherAdminRows(EthicsUserCatalog.Account account, RoleAssignment assignment) {
        if (account.assignments() == null || assignment == null || !"ADMINISTRATOR".equals(assignment.roleId())) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        int count = 0;
        for (RoleAssignment row : account.assignments()) {
            if (row.operativeOn(today)
                    && "ADMINISTRATOR".equals(row.roleId())
                    && !assignment.id().equals(row.id())) {
                count++;
            }
        }
        return count;
    }

    private void welcome(long userId, String email, String name) {
        if (mailer == null || email == null || email.isBlank()) {
            return;
        }
        try {
            AccessUserRecord stored = accounts == null ? null : accounts.findUserById(userId);
            mailer.sendEthicsWelcome(email, name, stored == null ? null : stored.preferredLanguage);
        } catch (RuntimeException ex) {
            audited(userId, "ETHICS_WELCOME_FAILED", "welcome email failed");
        }
    }

    private void audited(long targetUserId, String action, String detail) {
        admin.audit(actorId(), targetUserId, action, detail);
    }

    private Long actorId() {
        CurrentUser user = currentUser == null ? null : currentUser.current();
        if (user == null || user.id == null || user.id.isBlank() || !user.id.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.valueOf(user.id);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
