package cl.uss.ca.ethics.auth;

import cl.uss.ca.api.dto.EthicsMeDto;
import cl.uss.ca.api.dto.EthicsPermissionsDto;
import cl.uss.ca.api.dto.EthicsRoleDto;
import cl.uss.ca.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Autorización de ética. El nombre del rol no se consulta: solo agrupa capabilities.
 */
@ApplicationScoped
public class AuthorizationService {
    private static final Set<String> REVIEWER_OPERATION = Set.of(
            "EDIT_OWN_REVIEW",
            "SUBMIT_OWN_REVIEW",
            "ACCEPT_REVIEW_ASSIGNMENT",
            "REJECT_REVIEW_ASSIGNMENT"
    );
    private final RoleDefinitionProvider definitions;
    private final RoleAssignmentProvider assignments;
    private final EthicsUserDirectory users;
    private final EthicsWorkflowPolicy workflow = new EthicsWorkflowPolicy();

    /**
     * Lo usa el proxy de Weld. La instancia real entra por el constructor con dependencias.
     */
    protected AuthorizationService() {
        this.definitions = null;
        this.assignments = null;
        this.users = null;
    }

    @Inject
    public AuthorizationService(RoleDefinitionProvider definitions, RoleAssignmentProvider assignments) {
        this(definitions, assignments, new EthicsUserDirectory());
    }

    AuthorizationService(RoleDefinitionProvider definitions, RoleAssignmentProvider assignments, EthicsUserDirectory users) {
        this.definitions = definitions;
        this.assignments = assignments;
        this.users = users;
    }

    public boolean can(String userId, String capability, AuthorizationContext context) {
        if (userId == null || capability == null || capability.isBlank() || !definitions.knownCapability(capability)) {
            return false;
        }
        if (!potential(userId, context).contains(capability)) {
            return false;
        }
        return workflow.allowsNow(capability, context == null ? null : context.workflowState());
    }

    public Set<String> getEffectiveCapabilities(String userId, AuthorizationContext context) {
        Set<String> granted = potential(userId, context);
        String state = context == null ? null : context.workflowState();
        if (state == null) {
            return granted;
        }
        Set<String> effective = new LinkedHashSet<>();
        for (String capability : granted) {
            if (workflow.allowsNow(capability, state)) {
                effective.add(capability);
            }
        }
        return Set.copyOf(effective);
    }

    public EthicsMeDto profile(CurrentUser user) {
        EthicsMeDto dto = new EthicsMeDto();
        if (user == null) {
            dto.roles = List.of();
            return dto;
        }
        dto.id = user.id;
        dto.name = user.displayName;
        dto.email = user.email;
        String key = assignmentKey(user);
        dto.roles = key == null ? List.of() : rolesOf(key);
        return dto;
    }

    public EthicsPermissionsDto permissions(CurrentUser user, AuthorizationContext context) {
        EthicsPermissionsDto dto = new EthicsPermissionsDto();
        if (user == null) {
            dto.capabilities = List.of();
            return dto;
        }
        String key = assignmentKey(user);
        dto.capabilities = key == null ? List.of() : List.copyOf(getEffectiveCapabilities(key, context));
        return dto;
    }

    public List<RoleAssignment> history(String userId) {
        return assignments.getAssignmentsForUser(userId);
    }

    private Set<String> potential(String userId, AuthorizationContext context) {
        AuthorizationContext safe = context == null ? AuthorizationContext.empty() : context;
        LocalDate today = LocalDate.now();
        Set<String> granted = new LinkedHashSet<>();
        Set<String> historical = new LinkedHashSet<>();
        for (RoleAssignment assignment : assignments.getAssignmentsForUserAndContext(userId, safe)) {
            definitions.find(assignment.roleId()).ifPresent(role -> {
                if (assignment.operativeOn(today)) {
                    granted.addAll(role.capabilities());
                } else if ("REVIEWER".equals(assignment.roleId()) && assignment.status() == AssignmentStatus.COMPLETED) {
                    historical.addAll(role.capabilities());
                }
            });
        }
        historical.removeAll(REVIEWER_OPERATION);
        historical.removeAll(granted);
        granted.addAll(historical);
        return Set.copyOf(granted);
    }

    /**
     * La identidad sale del id de la sesión. El correo y un perfil elegido en el navegador no cambian el usuario.
     */
    private String assignmentKey(CurrentUser user) {
        return user.id == null || user.id.isBlank() ? null : user.id;
    }

    private List<EthicsRoleDto> rolesOf(String userId) {
        LocalDate today = LocalDate.now();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (RoleAssignment assignment : assignments.getAssignmentsForUser(userId)) {
            if (assignment.operativeOn(today)) {
                ids.add(assignment.roleId());
            }
        }
        List<EthicsRoleDto> roles = new ArrayList<>();
        for (String id : ids) {
            definitions.find(id).ifPresent(role -> {
                EthicsRoleDto dto = new EthicsRoleDto();
                dto.id = role.id();
                dto.idLabel = role.idLabel();
                roles.add(dto);
            });
        }
        return roles;
    }
}
