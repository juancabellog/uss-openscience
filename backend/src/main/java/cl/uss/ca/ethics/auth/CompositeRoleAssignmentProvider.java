package cl.uss.ca.ethics.auth;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * Junta asignaciones estructurales, revisores y propiedad del proyecto.
 * AuthorizationService solo ve {@link RoleAssignmentProvider}.
 */
public final class CompositeRoleAssignmentProvider implements RoleAssignmentProvider {
    private static final Logger LOG = Logger.getLogger(CompositeRoleAssignmentProvider.class.getName());
    private final RoleAssignmentProvider structural;
    private final ReviewerAssignmentSource reviewers;
    private final ProjectOwnershipSource ownership;
    private final ScopeMatcher matcher;
    private final EthicsResourceIndex resources;

    public CompositeRoleAssignmentProvider(
            RoleAssignmentProvider structural,
            ReviewerAssignmentSource reviewers,
            ProjectOwnershipSource ownership,
            EthicsResourceIndex resources
    ) {
        this.structural = structural;
        this.reviewers = reviewers;
        this.ownership = ownership;
        this.matcher = new ScopeMatcher();
        this.resources = resources;
    }

    /** Autorización de una sesión real. No lee el YAML demo. */
    public static CompositeRoleAssignmentProvider persisted(RoleAssignmentProvider structural) {
        return new CompositeRoleAssignmentProvider(
                structural,
                userId -> List.of(),
                userId -> List.of(),
                EthicsResourceIndex.prototype()
        );
    }

    public static CompositeRoleAssignmentProvider demo(String assignmentYaml, RoleDefinitionProvider roles) {
        return demo(assignmentYaml, roles, null);
    }

    public static CompositeRoleAssignmentProvider demo(
            String assignmentYaml,
            RoleDefinitionProvider roles,
            RoleAssignmentProvider persisted
    ) {
        EthicsResourceIndex resources = EthicsResourceIndex.prototype();
        EthicsUserDirectory users = new EthicsUserDirectory();
        List<RoleAssignment> rows = DemoAssignmentFile.parse(assignmentYaml, roles, resources, users);
        RoleAssignmentProvider structural = new DemoYamlRoleAssignmentProvider(CompositeRoleAssignmentProvider.structural(rows));
        if (persisted != null) {
            structural = new UnionRoleAssignmentProvider(structural, persisted);
        }
        return new CompositeRoleAssignmentProvider(
                structural,
                new DemoReviewerAssignmentSource(rows),
                new DemoProjectOwnershipSource(),
                resources
        );
    }

    @Override
    public List<RoleAssignment> getAssignmentsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        LinkedHashMap<String, RoleAssignment> merged = new LinkedHashMap<>();
        for (RoleAssignment item : structural.getAssignmentsForUser(userId)) {
            merged.put(key(item), item);
        }
        for (ReviewerAssignmentSource.ReviewerEngagement engagement : reviewers.engagementsFor(userId)) {
            RoleAssignment item = fromReviewer(engagement);
            merged.putIfAbsent(key(item), item);
        }
        for (ProjectOwnershipSource.OwnedProject project : ownership.ownedBy(userId)) {
            RoleAssignment item = fromOwnership(project);
            merged.putIfAbsent(key(item), item);
        }
        return List.copyOf(merged.values());
    }

    @Override
    public List<RoleAssignment> getAssignmentsForUserAndContext(String userId, AuthorizationContext context) {
        LocalDate today = LocalDate.now();
        return getAssignmentsForUser(userId).stream()
                .filter(item -> item.operativeOn(today) || readableReviewer(item, today))
                .filter(item -> matcher.matches(item, context, resources))
                .toList();
    }

    /**
     * Una revisión COMPLETED sigue siendo consultable.
     * No recupera editar, enviar, aceptar ni rechazar.
     */
    private static boolean readableReviewer(RoleAssignment item, LocalDate day) {
        if (!"REVIEWER".equals(item.roleId()) || item.status() != AssignmentStatus.COMPLETED || day == null) {
            return false;
        }
        if (item.validFrom() != null && day.isBefore(item.validFrom())) {
            return false;
        }
        return item.validUntil() == null || !day.isAfter(item.validUntil());
    }

    private static List<RoleAssignment> structural(List<RoleAssignment> rows) {
        List<RoleAssignment> structural = new ArrayList<>();
        for (RoleAssignment row : rows) {
            if ("REVIEWER".equals(row.roleId())) {
                continue;
            }
            if ("RESEARCHER".equals(row.roleId())) {
                LOG.info("Ethics demo RESEARCHER assignment " + row.id() + " follows Project.researcherId and is not granted twice");
                continue;
            }
            structural.add(row);
        }
        return structural;
    }

    private static RoleAssignment fromReviewer(ReviewerAssignmentSource.ReviewerEngagement engagement) {
        return new RoleAssignment(
                engagement.id(),
                engagement.userId(),
                "REVIEWER",
                ScopeType.PROCESS,
                engagement.processId(),
                engagement.status(),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static RoleAssignment fromOwnership(ProjectOwnershipSource.OwnedProject project) {
        return new RoleAssignment(
                "domain-researcher-" + project.projectId(),
                project.userId(),
                "RESEARCHER",
                ScopeType.PROJECT,
                project.projectId(),
                AssignmentStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static String key(RoleAssignment item) {
        return item.roleId() + "|" + item.scopeType() + "|" + item.scopeId() + "|" + item.status();
    }
}

/** Junta el YAML demo con las asignaciones persistidas. No reemplaza una fila ya cargada. */
final class UnionRoleAssignmentProvider implements RoleAssignmentProvider {
    private final RoleAssignmentProvider first;
    private final RoleAssignmentProvider second;

    UnionRoleAssignmentProvider(RoleAssignmentProvider first, RoleAssignmentProvider second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public List<RoleAssignment> getAssignmentsForUser(String userId) {
        LinkedHashMap<String, RoleAssignment> merged = new LinkedHashMap<>();
        for (RoleAssignment item : first.getAssignmentsForUser(userId)) {
            merged.put(item.id() + "|" + item.roleId() + "|" + item.scopeType() + "|" + item.scopeId(), item);
        }
        for (RoleAssignment item : second.getAssignmentsForUser(userId)) {
            merged.putIfAbsent(item.id() + "|" + item.roleId() + "|" + item.scopeType() + "|" + item.scopeId(), item);
        }
        return List.copyOf(merged.values());
    }

    @Override
    public List<RoleAssignment> getAssignmentsForUserAndContext(String userId, AuthorizationContext context) {
        return getAssignmentsForUser(userId);
    }
}
