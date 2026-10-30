package cl.uss.ca.ethics.auth;

import cl.uss.ca.identity.InstitutionalDomains;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas de la pantalla de usuarios. El nombre guardado no se reescribe:
 * la comparación usa una copia recortada y en minúsculas.
 * Un revisor histórico no se muestra como función vigente.
 */
public final class EthicsUserCatalog {
    public static final List<String> ROLE_ORDER = List.of(
            "RESEARCHER",
            "ADMINISTRATOR",
            "COMMITTEE_PRESIDENT",
            "EXECUTIVE_SECRETARY",
            "REVIEWER"
    );
    private static final Set<String> STRUCTURAL = Set.of(
            "RESEARCHER", "ADMINISTRATOR", "COMMITTEE_PRESIDENT", "EXECUTIVE_SECRETARY");

    private EthicsUserCatalog() {
    }

    public record Account(
            String id,
            String givenName,
            String familyName,
            String secondFamilyName,
            String preferredName,
            String email,
            String accountStatus,
            String personKind,
            Long purePersonId,
            String ethicsStatus,
            List<RoleAssignment> assignments
    ) {
    }

    public record Query(String search, String type, String status, String ethicsAccess, int page, int size) {
    }

    public record VisibleRole(String id, boolean contextual) {
    }

    public record VisibleAssignment(
            String assignmentId,
            String roleId,
            boolean contextual,
            boolean removable,
            String scopeType,
            String scopeId,
            String scopeLabel
    ) {
    }

    public static String normalizeSearch(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static String likePattern(String normalizedSearch) {
        String term = normalizedSearch
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + term + "%";
    }

    public static boolean ethicsRelated(Account account) {
        if (account.ethicsStatus() != null && !account.ethicsStatus().isBlank()) {
            return true;
        }
        return account.assignments() != null && !account.assignments().isEmpty();
    }

    public static boolean matchesSearch(String normalizedSearch, Account account) {
        if (normalizedSearch.isEmpty()) {
            return true;
        }
        for (String field : searchFields(account)) {
            if (field.contains(normalizedSearch)) {
                return true;
            }
        }
        return false;
    }

    public static boolean included(Query query, Account account) {
        String search = normalizeSearch(query.search());
        if (search.isEmpty()) {
            if (!ethicsRelated(account)) {
                return false;
            }
        } else if (!matchesSearch(search, account)) {
            return false;
        }
        return typeMatches(query.type(), account)
                && statusMatches(query.status(), account)
                && accessMatches(query.ethicsAccess(), account);
    }

    public static List<Account> select(List<Account> accounts, Query query) {
        List<Account> matched = new ArrayList<>();
        for (Account account : accounts) {
            if (included(query, account)) {
                matched.add(account);
            }
        }
        int size = query.size() <= 0 ? matched.size() : query.size();
        int page = Math.max(query.page(), 1);
        int from = Math.min((page - 1) * size, matched.size());
        int to = Math.min(from + size, matched.size());
        return List.copyOf(matched.subList(from, to));
    }

    public static List<VisibleRole> visibleRoles(List<RoleAssignment> assignments, LocalDate day) {
        LinkedHashSet<String> structural = new LinkedHashSet<>();
        boolean reviewer = false;
        if (assignments != null) {
            for (RoleAssignment assignment : assignments) {
                if (assignment == null || !assignment.operativeOn(day)) {
                    continue;
                }
                if ("REVIEWER".equals(assignment.roleId())) {
                    reviewer = true;
                } else if (STRUCTURAL.contains(assignment.roleId())) {
                    structural.add(assignment.roleId());
                }
            }
        }
        List<VisibleRole> visible = new ArrayList<>();
        for (String id : ROLE_ORDER) {
            if ("REVIEWER".equals(id)) {
                if (reviewer) {
                    visible.add(new VisibleRole(id, true));
                }
            } else if (structural.contains(id)) {
                visible.add(new VisibleRole(id, false));
            }
        }
        return List.copyOf(visible);
    }

    /**
     * Una fila por asignación vigente. Dos presidencias de comité no se funden en un chip.
     * El alcance global único no agrega sufijo. REVIEWER queda contextual y no se quita aquí.
     */
    public static List<VisibleAssignment> visibleAssignments(List<RoleAssignment> assignments, LocalDate day) {
        List<RoleAssignment> operative = new ArrayList<>();
        if (assignments != null) {
            for (RoleAssignment assignment : assignments) {
                if (assignment == null || assignment.roleId() == null || !assignment.operativeOn(day)) {
                    continue;
                }
                if (STRUCTURAL.contains(assignment.roleId()) || "REVIEWER".equals(assignment.roleId())) {
                    operative.add(assignment);
                }
            }
        }
        List<VisibleAssignment> visible = new ArrayList<>();
        for (String roleId : ROLE_ORDER) {
            List<RoleAssignment> ofRole = new ArrayList<>();
            for (RoleAssignment assignment : operative) {
                if (roleId.equals(assignment.roleId())) {
                    ofRole.add(assignment);
                }
            }
            for (RoleAssignment assignment : ofRole) {
                boolean reviewer = "REVIEWER".equals(roleId);
                visible.add(new VisibleAssignment(
                        assignment.id(),
                        roleId,
                        reviewer,
                        !reviewer,
                        assignment.scopeType() == null ? null : assignment.scopeType().name(),
                        assignment.scopeId(),
                        scopeLabel(ofRole, assignment)
                ));
            }
        }
        return List.copyOf(visible);
    }

    public static String scopeLabel(List<RoleAssignment> sameRole, RoleAssignment assignment) {
        if (assignment == null) {
            return null;
        }
        boolean multiple = sameRole != null && sameRole.size() > 1;
        boolean namedScope = assignment.scopeType() != null && assignment.scopeType() != ScopeType.GLOBAL;
        if (!multiple && !namedScope) {
            return null;
        }
        String scopeId = assignment.scopeId();
        if (scopeId != null && !scopeId.isBlank() && !"*".equals(scopeId)) {
            return scopeId;
        }
        return assignment.scopeType() == null ? null : assignment.scopeType().name();
    }

    public static String personKind(String storedKind, String email) {
        if ("INSTITUTIONAL".equals(storedKind) || "EXTERNAL".equals(storedKind)) {
            return storedKind;
        }
        return InstitutionalDomains.isAllowedInstitutionalEmail(email) ? "INSTITUTIONAL" : "EXTERNAL";
    }

    public static String dataSource(Long purePersonId) {
        return purePersonId == null ? "LOCAL" : "PURE";
    }

    private static boolean typeMatches(String type, Account account) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) {
            return true;
        }
        return type.equalsIgnoreCase(personKind(account.personKind(), account.email()));
    }

    private static boolean statusMatches(String status, Account account) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return true;
        }
        boolean active = "ACTIVE".equalsIgnoreCase(account.accountStatus());
        if ("ACTIVE".equalsIgnoreCase(status)) {
            return active;
        }
        if ("INACTIVE".equalsIgnoreCase(status)) {
            return !active;
        }
        return true;
    }

    private static boolean accessMatches(String access, Account account) {
        if (access == null || access.isBlank() || "ALL".equalsIgnoreCase(access)) {
            return true;
        }
        boolean open = "ACTIVE".equalsIgnoreCase(account.ethicsStatus());
        if ("YES".equalsIgnoreCase(access) || "true".equalsIgnoreCase(access)) {
            return open;
        }
        if ("NO".equalsIgnoreCase(access) || "false".equalsIgnoreCase(access)) {
            return !open;
        }
        return true;
    }

    private static List<String> searchFields(Account account) {
        String given = normalizeSearch(account.givenName());
        String family = normalizeSearch(account.familyName());
        String second = normalizeSearch(account.secondFamilyName());
        String preferred = normalizeSearch(account.preferredName());
        String email = normalizeSearch(account.email());
        String full = (given + " " + family + " " + second).trim().replaceAll(" +", " ");
        List<String> fields = new ArrayList<>();
        fields.add(given);
        fields.add(family);
        fields.add(second);
        fields.add(preferred);
        fields.add(full);
        fields.add(email);
        return fields;
    }
}
