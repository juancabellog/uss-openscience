package cl.uss.ca.ethics.auth;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asignaciones estructurales del YAML demo. REVIEWER y RESEARCHER no salen de aquí:
 * vienen de la asignación de revisor y del investigador responsable del proyecto.
 */
public final class DemoYamlRoleAssignmentProvider implements RoleAssignmentProvider {
    private static final Logger LOG = Logger.getLogger(DemoYamlRoleAssignmentProvider.class.getName());
    private final List<RoleAssignment> assignments;

    public DemoYamlRoleAssignmentProvider(List<RoleAssignment> assignments) {
        this.assignments = List.copyOf(assignments);
        LOG.info("Ethics demo structural assignments: " + this.assignments.size());
    }

    @Override
    public List<RoleAssignment> getAssignmentsForUser(String userId) {
        if (userId == null) {
            return List.of();
        }
        return assignments.stream().filter(item -> userId.equals(item.userId())).toList();
    }

    @Override
    public List<RoleAssignment> getAssignmentsForUserAndContext(String userId, AuthorizationContext context) {
        return getAssignmentsForUser(userId);
    }
}

final class DemoAssignmentFile {
    private static final Logger LOG = Logger.getLogger(DemoAssignmentFile.class.getName());

    private DemoAssignmentFile() {
    }

    static List<RoleAssignment> parse(String yaml, RoleDefinitionProvider roles, EthicsResourceIndex resources, EthicsUserDirectory users) {
        Map<String, Object> root = YamlValues.parseMap(yaml);
        if (!"2".equals(YamlValues.text(root.get("version")))) {
            throw new EthicsAuthException("ETHICS_ASSIGNMENTS_VERSION", "demo-role-assignments-V2.yml version must be 2");
        }
        List<Object> rows = YamlValues.list(root.get("assignments"), "assignments");
        List<RoleAssignment> parsed = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Object row : rows) {
            Map<String, Object> body = YamlValues.map(row, "assignment");
            String id = YamlValues.text(body.get("id"));
            String userKey = YamlValues.text(body.get("userKey"));
            String roleId = YamlValues.text(body.get("roleId"));
            ScopeType scope = ScopeType.parse(YamlValues.text(body.get("scopeType")));
            String scopeId = normalizeScope(scope, YamlValues.text(body.get("scopeId")), resources);
            AssignmentStatus status = AssignmentStatus.parse(YamlValues.text(body.get("status")));
            String userId = users.resolveKey(userKey);
            if (id == null || id.isBlank()) {
                problems.add("assignment without id");
                continue;
            }
            if (userId == null) {
                problems.add(id + " userKey " + userKey + " does not match an ethics user");
                continue;
            }
            if ("COMMITTEE_MEMBER".equals(roleId)) {
                problems.add(id + " roleId COMMITTEE_MEMBER was removed and is not converted to REVIEWER");
                continue;
            }
            if (roles.find(roleId).isEmpty()) {
                problems.add(id + " roleId " + roleId + " is not defined");
                continue;
            }
            if (scope == null) {
                problems.add(id + " has an invalid scopeType");
                continue;
            }
            if (scopeId == null) {
                problems.add(id + " scopeId is not a known " + scope);
                continue;
            }
            if (status == null) {
                problems.add(id + " has an invalid status");
                continue;
            }
            parsed.add(new RoleAssignment(
                    id,
                    userId,
                    roleId,
                    scope,
                    scopeId,
                    status,
                    date(body.get("validFrom"), id, "validFrom", problems),
                    date(body.get("validUntil"), id, "validUntil", problems),
                    null,
                    null,
                    null,
                    null
            ));
        }
        if (!problems.isEmpty()) {
            for (String problem : problems) {
                LOG.log(Level.SEVERE, "Ethics demo assignment rejected: {0}", problem);
            }
            throw new EthicsAuthException("ETHICS_ASSIGNMENTS_INVALID", String.join("; ", problems));
        }
        return List.copyOf(parsed);
    }

    private static String normalizeScope(ScopeType scope, String scopeId, EthicsResourceIndex resources) {
        if (scope == null) {
            return null;
        }
        if (scope == ScopeType.GLOBAL) {
            return "*".equals(scopeId) || scopeId == null || scopeId.isBlank() ? "*" : null;
        }
        if (scopeId == null || scopeId.isBlank()) {
            return null;
        }
        if (scope == ScopeType.COMMITTEE) {
            return resources.knownCommittee(scopeId) ? scopeId : null;
        }
        if (scope == ScopeType.PROJECT) {
            return resources.canonicalProjectId(scopeId);
        }
        if (scope == ScopeType.PROCESS) {
            return resources.knownProcess(scopeId) ? scopeId : null;
        }
        return null;
    }

    private static LocalDate date(Object raw, String id, String field, List<String> problems) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(raw));
        } catch (Exception ex) {
            problems.add(id + " " + field + " is not a date");
            return null;
        }
    }
}

/**
 * Personas del prototipo ethics-uss. No crea usuarios de OpenScience.
 */
final class EthicsUserDirectory {
    private final Map<String, String> keyToId = new LinkedHashMap<>();

    EthicsUserDirectory() {
        add("u-admin", "camila.herrera", "camila.herrera@uss.cl");
        add("u-pres-cecua", "pilar.nunez", "pilar.nunez@uss.cl");
        add("u-sec-cecua", "rodrigo.pino", "rodrigo.pino@uss.cl");
        add("u-rev-4", "ignacio.bravo", "ignacio.bravo@uss.cl");
        add("u-rev-5", "sofia.lagos", "sofia.lagos@uss.cl");
        add("u-rev-ext", "alberto.soto", "alberto.soto@externo.cl");
        add("u-inv-1", "juan.perez", "juan.perez@uss.cl");
    }

    String resolveKey(String userKey) {
        if (userKey == null) {
            return null;
        }
        return keyToId.get(userKey.trim().toLowerCase());
    }

    String assignmentKey(String userId, String email) {
        String byId = resolveKey(userId);
        if (byId != null) {
            return byId;
        }
        return resolveKey(email);
    }

    private void add(String id, String key, String email) {
        keyToId.put(id.toLowerCase(), id);
        keyToId.put(key.toLowerCase(), id);
        keyToId.put(email.toLowerCase(), id);
    }
}
