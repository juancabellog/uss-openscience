package cl.uss.ca.service;

import cl.uss.ca.api.dto.AdminRoleDto;
import cl.uss.ca.api.dto.AdminRoleWriteDto;
import cl.uss.ca.api.dto.AuthPermissionDto;
import cl.uss.ca.auth.AppModule;
import cl.uss.ca.auth.LastAdminPolicy;
import cl.uss.ca.auth.AppPermission;
import cl.uss.ca.exception.ForbiddenException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.AccessPermissionGrant;
import cl.uss.ca.repository.AccessRepository;
import cl.uss.ca.repository.AccessRoleRecord;
import cl.uss.ca.security.CurrentUser;
import cl.uss.ca.security.CurrentUserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class RoleAdminService {

    @Inject
    AccessRepository accessRepository;

    @Inject
    CurrentUserService currentUserService;

    public List<AdminRoleDto> list(boolean includeInactive) {
        accessRepository.assertAvailable();
        List<AdminRoleDto> items = new ArrayList<>();
        for (AccessRoleRecord role : accessRepository.listRoles(includeInactive)) {
            items.add(toDto(role));
        }
        return items;
    }

    public AdminRoleDto get(long id) {
        accessRepository.assertAvailable();
        AccessRoleRecord role = accessRepository.findRole(id);
        if (role == null) {
            throw new NotFoundException("ROLE_NOT_FOUND", "Role was not found");
        }
        return toDto(role);
    }

    public AdminRoleDto create(AdminRoleWriteDto body) {
        accessRepository.assertAvailable();
        validate(body, true);
        AccessRoleRecord role = new AccessRoleRecord();
        role.code = normalizeCode(body.code);
        role.name = body.name.trim();
        role.description = blankToNull(body.description);
        role.active = body.active == null || body.active;
        role.systemRole = false;
        long id = accessRepository.insertRole(role);
        accessRepository.replaceRolePermissions(id, normalizeGrants(body.permissions));
        accessRepository.recordEvent("ROLE_CREATED", actorId(), null, id, null);
        return get(id);
    }

    public AdminRoleDto update(long id, AdminRoleWriteDto body) {
        accessRepository.assertAvailable();
        validate(body, false);
        AccessRoleRecord current = accessRepository.findRole(id);
        if (current == null) {
            throw new NotFoundException("ROLE_NOT_FOUND", "Role was not found");
        }
        boolean nextActive = body.active == null ? current.active : body.active;
        List<AccessPermissionGrant> grants = normalizeGrants(body.permissions);
        protectLastAdminRole(current, nextActive, grants);
        accessRepository.updateRole(id, body.name.trim(), blankToNull(body.description), nextActive);
        accessRepository.replaceRolePermissions(id, grants);
        accessRepository.recordEvent("ROLE_PERMISSION_UPDATED", actorId(), null, id, null);
        return get(id);
    }

    public AdminRoleDto duplicate(long id) {
        AdminRoleDto source = get(id);
        AdminRoleWriteDto body = new AdminRoleWriteDto();
        body.code = source.code + "_COPY";
        body.name = source.name + " (copia)";
        body.description = source.description;
        body.active = true;
        body.permissions = source.permissions;
        return create(body);
    }

    public AdminRoleDto disable(long id) {
        AccessRoleRecord current = accessRepository.findRole(id);
        if (current == null) {
            throw new NotFoundException("ROLE_NOT_FOUND", "Role was not found");
        }
        protectLastAdminRole(current, false, current.grants);
        accessRepository.updateRole(id, current.name, current.description, false);
        accessRepository.recordEvent("ROLE_UPDATED", actorId(), null, id, "disabled");
        return get(id);
    }

    private void protectLastAdminRole(AccessRoleRecord current, boolean nextActive, List<AccessPermissionGrant> grants) {
        if (!accessRepository.roleGrantsAdmin(current.id) || !current.active) {
            return;
        }
        boolean stillAdmin = nextActive && grants.stream()
                .anyMatch(grant -> "ADMINISTRATION".equals(grant.moduleCode) && "ADMINISTER".equals(grant.permissionCode));
        if (!LastAdminPolicy.canDemoteAdminRole(
                true,
                stillAdmin,
                accessRepository.countActiveAdministrators(),
                current.userCount
        )) {
            throw new ForbiddenException("LAST_ADMIN", "Cannot remove administration access from the last active administrator role");
        }
    }

    private void validate(AdminRoleWriteDto body, boolean create) {
        if (body == null || blank(body.name)) {
            throw new ValidationException("AUTH_ROLE_INVALID", "Role name is required");
        }
        if (create && (blank(body.code) || !body.code.trim().matches("[A-Za-z][A-Za-z0-9_]{1,62}"))) {
            throw new ValidationException("AUTH_ROLE_CODE_INVALID", "Role code must be alphanumeric");
        }
    }

    private List<AccessPermissionGrant> normalizeGrants(List<AuthPermissionDto> permissions) {
        Set<String> keys = new LinkedHashSet<>();
        List<AccessPermissionGrant> grants = new ArrayList<>();
        if (permissions == null) {
            return grants;
        }
        for (AuthPermissionDto item : permissions) {
            if (item == null) {
                continue;
            }
            AppModule module = AppModule.from(item.module);
            AppPermission permission = AppPermission.from(item.permission);
            if (module == null || permission == null) {
                continue;
            }
            if (permission != AppPermission.CONSULT && permission != AppPermission.ADMINISTER) {
                continue;
            }
            addGrant(grants, keys, module, permission);
            if (permission == AppPermission.ADMINISTER) {
                addGrant(grants, keys, module, AppPermission.CONSULT);
            }
        }
        return grants;
    }

    private static void addGrant(
            List<AccessPermissionGrant> grants,
            Set<String> keys,
            AppModule module,
            AppPermission permission
    ) {
        String key = module.name() + ":" + permission.name();
        if (keys.add(key)) {
            AccessPermissionGrant grant = new AccessPermissionGrant();
            grant.moduleCode = module.name();
            grant.permissionCode = permission.name();
            grants.add(grant);
        }
    }

    private AdminRoleDto toDto(AccessRoleRecord role) {
        AdminRoleDto dto = new AdminRoleDto();
        dto.id = role.id;
        dto.code = role.code;
        dto.name = role.name;
        dto.description = role.description;
        dto.active = role.active;
        dto.systemRole = role.systemRole;
        dto.userCount = role.userCount;
        for (AccessPermissionGrant grant : role.grants) {
            dto.permissions.add(AuthPermissionDto.of(grant.moduleCode, grant.permissionCode));
        }
        return dto;
    }

    private Long actorId() {
        CurrentUser actor = currentUserService.currentOrNull();
        return actor == null ? null : actor.numericId;
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
