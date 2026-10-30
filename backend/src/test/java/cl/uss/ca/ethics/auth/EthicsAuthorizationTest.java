package cl.uss.ca.ethics.auth;

import java.util.List;
import java.util.Set;

public final class EthicsAuthorizationTest {
    private EthicsAuthorizationTest() {
    }

    public static void main(String[] args) {
        RoleDefinitionProvider roles = YamlRoleDefinitionProvider.loadClasspath();
        String assignments = YamlRoleDefinitionProvider.read("/ethics/demo-role-assignments-V2.yml");
        String permissions = YamlRoleDefinitionProvider.read("/ethics/permissions-V4.yml");
        AuthorizationService service = service(roles, assignments);

        check(!permissions.contains("userKey"), "permissions yaml contains users");
        check(!permissions.contains("Revisor") && !permissions.contains("Reviewer"), "permissions yaml contains translations");
        check(!permissions.contains("effectiveCapabilities"), "permissions yaml contains a policy string");
        check(roles.definitions().size() == 5, "exactly five roles");
        check(roles.find("COMMITTEE_MEMBER").isEmpty(), "COMMITTEE_MEMBER is not a role");
        check(Set.of("ADMINISTRATOR", "COMMITTEE_PRESIDENT", "EXECUTIVE_SECRETARY", "REVIEWER", "RESEARCHER")
                .equals(roles.definitions().stream().map(RoleDefinition::id).collect(java.util.stream.Collectors.toSet())),
                "the configurable roles are the five of permissions-V4");
        check("roles.reviewer".equals(roles.find("REVIEWER").orElseThrow().idLabel()), "reviewer id_label");
        check("roles.administrator".equals(roles.find("ADMINISTRATOR").orElseThrow().idLabel()), "administrator id_label");
        check(!assignments.contains("COMMITTEE_MEMBER:") && !assignments.contains("roleId: COMMITTEE_MEMBER"), "demo assignments do not grant COMMITTEE_MEMBER");
        check(!assignments.contains("camila.herrera"), "demo user keys still use display aliases");
        check(!assignments.contains("userKey: u-rev-5"), "Sofia is not granted a role in the demo yaml");

        RoleDefinition administrator = roles.find("ADMINISTRATOR").orElseThrow();
        Set<String> camila = service.getEffectiveCapabilities("u-admin", new AuthorizationContext("CECUA", null, null, null, null));
        check(camila.equals(administrator.capabilities()), "Camila receives the ADMINISTRATOR capabilities");
        check(camila.contains("SUBMIT_REQUEST"), "Camila can prepare a request inside a valid context");
        check(camila.contains("VIEW_SAVED_CONSOLIDATED_OBSERVATIONS"), "Camila sees a saved consolidation");
        check(camila.contains("EDIT_CONSOLIDATED_OBSERVATIONS") && camila.contains("SAVE_CONSOLIDATED_OBSERVATIONS"), "Camila edits and saves consolidation");
        check(camila.contains("VALIDATE_RESOLUTION") && camila.contains("RETURN_RESOLUTION"), "Camila validates and returns a resolution");
        check(!camila.contains("VIEW_ALL_PROJECTS"), "the old reduced capability is not the source");
        var camilaAssignment = service.history("u-admin").stream()
                .filter(item -> "ADMINISTRATOR".equals(item.roleId()))
                .findFirst()
                .orElseThrow();
        check(camilaAssignment.scopeType() == ScopeType.GLOBAL && "*".equals(camilaAssignment.scopeId()), "Camila assignment is global");
        check(camilaAssignment.status() == AssignmentStatus.ACTIVE, "Camila assignment is active");

        Set<String> pilarCecua = service.getEffectiveCapabilities("u-pres-cecua", new AuthorizationContext("CECUA", null, null, null, null));
        RoleDefinition president = roles.find("COMMITTEE_PRESIDENT").orElseThrow();
        check(pilarCecua.equals(president.capabilities()), "Pilar receives the president capabilities inside CECUA");
        check(pilarCecua.contains("VALIDATE_RESOLUTION") && pilarCecua.contains("RETURN_RESOLUTION"), "Pilar validates inside CECUA");
        check(pilarCecua.contains("VIEW_ADMISSIBILITY") && pilarCecua.contains("ASSIGN_REVIEWERS"), "Pilar sees admissibility and assignment");
        check(pilarCecua.contains("EDIT_CONSOLIDATED_OBSERVATIONS") && pilarCecua.contains("SAVE_CONSOLIDATED_OBSERVATIONS"), "Pilar edits consolidation");
        check(!pilarCecua.contains("SUBMIT_REQUEST") && !pilarCecua.contains("VIEW_CONFIGURATION"), "Pilar does not receive administration-only capabilities");
        check(service.getEffectiveCapabilities("u-pres-cecua", new AuthorizationContext("CEC", null, null, null, null)).isEmpty(), "Pilar does not inherit CEC");
        check(service.getEffectiveCapabilities("u-pres-cecua", new AuthorizationContext("BIOSEGURIDAD", null, null, null, null)).isEmpty(), "Pilar does not inherit biosafety");

        AuthorizationContext cecua = new AuthorizationContext("CECUA", null, null, null, null);
        Set<String> rodrigo = service.getEffectiveCapabilities("u-sec-cecua", cecua);
        RoleDefinition secretary = roles.find("EXECUTIVE_SECRETARY").orElseThrow();
        check(rodrigo.equals(secretary.capabilities()), "Rodrigo receives the secretary capabilities inside CECUA");
        check(camila.contains("MANAGE_USERS"), "administration manages users");
        check(!pilarCecua.contains("MANAGE_USERS"), "the president does not manage users");
        check(!rodrigo.contains("MANAGE_USERS"), "the secretary does not manage users");
        check(service.can("u-admin", "MANAGE_USERS", AuthorizationContext.empty()), "managing users does not depend on a case state");
        check(!service.can("u-pres-cecua", "MANAGE_USERS", cecua), "the president cannot manage users");
        check(rodrigo.contains("ASSIGN_REVIEWERS"), "Rodrigo assigns reviewers");
        check(rodrigo.contains("REASSIGN_REVIEWER"), "Rodrigo reassigns reviewers");
        check(rodrigo.contains("EDIT_CONSOLIDATED_OBSERVATIONS") && rodrigo.contains("SAVE_CONSOLIDATED_OBSERVATIONS"), "Rodrigo edits and saves consolidation");
        check(rodrigo.contains("PREPARE_RESOLUTION") && rodrigo.contains("PUBLISH_OBSERVATIONS"), "Rodrigo prepares a resolution and publishes observations");
        check(!rodrigo.contains("VALIDATE_RESOLUTION") && !rodrigo.contains("RETURN_RESOLUTION"), "Rodrigo does not validate or return a resolution");
        check(service.getEffectiveCapabilities("u-sec-cecua", new AuthorizationContext("CEC", null, null, null, null)).isEmpty(), "Rodrigo stays in CECUA");
        check(service.can("u-sec-cecua", "ASSIGN_REVIEWERS", cecua.withWorkflowState("ADMISSIBILITY")), "assignment is open in admissibility");
        check(!service.can("u-sec-cecua", "ASSIGN_REVIEWERS", cecua.withWorkflowState("IN_REVIEW")), "assignment is closed in review");
        check(!service.can("u-sec-cecua", "ASSIGN_REVIEWERS", cecua), "an action without workflow state is denied");
        check(service.can("u-sec-cecua", "REASSIGN_REVIEWER", cecua.withWorkflowState("IN_REVIEW")), "ordinary reassignment stays open during review");
        check(service.can("u-admin", "REASSIGN_REVIEWER", cecua.withWorkflowState("IN_REVIEW")), "administration keeps ordinary reassignment");
        check(!service.can("u-sec-cecua", "REASSIGN_REVIEWER", cecua.withWorkflowState("PENDING_RESOLUTION")), "ordinary reassignment does not cover resolution preparation");
        check(!service.can("u-sec-cecua", "REASSIGN_REVIEWER_ADVANCED_STAGE", cecua.withWorkflowState("PENDING_RESOLUTION")), "the secretary does not receive the advanced reassignment");
        check(!service.can("u-admin", "REASSIGN_REVIEWER_ADVANCED_STAGE", cecua.withWorkflowState("PENDING_RESOLUTION")), "administration does not receive the advanced reassignment");
        check(service.can("u-pres-cecua", "REASSIGN_REVIEWER", cecua.withWorkflowState("IN_REVIEW")), "the president keeps ordinary reassignment");
        check(service.can("u-pres-cecua", "REASSIGN_REVIEWER_ADVANCED_STAGE", cecua.withWorkflowState("PENDING_RESOLUTION")), "the president can reassign at an advanced stage");
        check(!service.can("u-pres-cecua", "REASSIGN_REVIEWER", cecua.withWorkflowState("PENDING_RESOLUTION")), "ordinary reassignment does not replace the advanced capability");
        check(!service.can("u-rev-4", "REASSIGN_REVIEWER_ADVANCED_STAGE", cecua.withWorkflowState("PENDING_RESOLUTION")), "a reviewer cannot reassign at an advanced stage");
        check(!service.can("u-inv-1", "REASSIGN_REVIEWER_ADVANCED_STAGE", cecua.withWorkflowState("AWAITING_RESEARCHER")), "a researcher cannot reassign at an advanced stage");
        check("REASSIGN_REVIEWER".equals(ReviewerReassignmentRule.requiredCapability("IN_REVIEW", "ACCEPTED", false)), "an accepted review in progress is ordinary");
        check("REASSIGN_REVIEWER_ADVANCED_STAGE".equals(ReviewerReassignmentRule.requiredCapability("IN_REVIEW", "COMPLETED", false)), "a completed assignment is advanced even during review");
        check(ReviewerReassignmentRule.requiredCapability("APPROVED", "COMPLETED", true) == null, "a closed approval is not reassigned");
        check(!service.can("u-sec-cecua", "REASSIGN_REVIEWER", cecua.withWorkflowState("APPROVED")), "a closed approval is not reassigned");

        AuthorizationContext roundOne = new AuthorizationContext(null, null, null, "CECUA-2026-004:R1", null);
        AuthorizationContext roundTwo = new AuthorizationContext(null, null, null, "CECUA-2026-004:R2", null);
        Set<String> ignacioRoundOne = service.getEffectiveCapabilities("u-rev-4", roundOne);
        RoleDefinition reviewer = roles.find("REVIEWER").orElseThrow();
        check(ignacioRoundOne.equals(reviewer.capabilities()), "Ignacio receives reviewer capabilities only on his process");
        check(ignacioRoundOne.contains("VIEW_OWN_REVIEW"), "Ignacio reviews R1");
        check(!ignacioRoundOne.contains("VIEW_COMMITTEE_SESSIONS"), "Ignacio does not see committee sessions");
        check(!ignacioRoundOne.contains("VIEW_REVIEWER_POOL"), "Ignacio does not see the reviewer pool");
        check(!ignacioRoundOne.contains("VIEW_ALL_REVIEWS"), "Ignacio does not see the other review");
        check(!ignacioRoundOne.contains("VIEW_REVIEWER_ASSIGNMENTS"), "Ignacio does not see the other assignment");
        check(ignacioRoundOne.contains("VIEW_PUBLISHED_OBSERVATIONS") && ignacioRoundOne.contains("VIEW_RESEARCHER_RESPONSE"), "Ignacio sees published observations and the response");
        Set<String> ignacioRoundTwo = service.getEffectiveCapabilities("u-rev-4", roundTwo);
        check(ignacioRoundTwo.isEmpty(), "Ignacio has no access to a process without his assignment");
        check(service.getEffectiveCapabilities("u-rev-4", cecua).isEmpty(), "Ignacio does not receive CECUA by being a reviewer");
        check(service.profile(user("u-rev-4", "ignacio.bravo@uss.cl", "Dr. Ignacio Bravo")).roles.stream()
                .noneMatch(role -> "COMMITTEE_MEMBER".equals(role.id)), "Ignacio profile has no committee member role");
        check(service.profile(user("u-rev-4", "ignacio.bravo@uss.cl", "Dr. Ignacio Bravo")).roles.stream()
                .anyMatch(role -> "REVIEWER".equals(role.id) && "roles.reviewer".equals(role.idLabel)), "Ignacio profile is reviewer");

        Set<String> sofia = service.getEffectiveCapabilities("u-rev-5", roundOne);
        check(sofia.isEmpty(), "Sofia has no yaml role grant");
        check(service.profile(user("u-rev-5", "sofia.lagos@uss.cl", "Dra. Sofía Lagos")).roles.isEmpty(), "Sofia has no active structural role");
        check(!sofia.contains("EDIT_OWN_REVIEW") && !sofia.contains("SUBMIT_OWN_REVIEW"), "Sofia has no operational review capabilities from yaml");

        Set<String> alberto = service.getEffectiveCapabilities("u-rev-ext", roundOne);
        check(alberto.equals(reviewer.capabilities()), "Alberto uses the same reviewer capabilities");
        check(alberto.contains("VIEW_OWN_REVIEW") && alberto.contains("VIEW_PUBLISHED_OBSERVATIONS"), "Alberto sees his review and published observations");
        check(!alberto.contains("VIEW_COMMITTEE_SESSIONS"), "Alberto does not join committee sessions");
        check(!alberto.contains("VIEW_ALL_REVIEWS") && !alberto.contains("VIEW_REVIEWER_ASSIGNMENTS"), "Alberto does not see the other review");
        check(service.getEffectiveCapabilities("u-rev-ext", new AuthorizationContext(null, "prj-1", null, null, null)).isEmpty(), "Alberto does not see another project");
        check(service.getEffectiveCapabilities("u-rev-ext", cecua).isEmpty(), "Alberto does not receive the whole committee");
        check(roles.find("EXTERNAL_REVIEWER").isEmpty(), "external reviewer is not a role");

        AuthorizationContext juanProject = new AuthorizationContext(null, "prj-2", null, null, null);
        Set<String> juan = service.getEffectiveCapabilities("u-inv-1", juanProject);
        RoleDefinition researcher = roles.find("RESEARCHER").orElseThrow();
        check(juan.equals(researcher.capabilities()), "Juan receives the researcher capabilities on his project");
        check(juan.contains("SUBMIT_REQUEST"), "Juan can submit his request");
        check(juan.contains("VIEW_PUBLISHED_OBSERVATIONS") && juan.contains("VIEW_RESEARCHER_RESPONSE"), "Juan sees published observations and his response");
        check(juan.contains("VIEW_EXPIRATIONS") && juan.contains("VIEW_POST_APPROVAL_PROCESSES"), "Juan sees expirations and later processes");
        check(juan.contains("START_AMENDMENT") && juan.contains("START_FOLLOW_UP") && juan.contains("START_RENEWAL"), "Juan can start a later process");
        check(!juan.contains("VIEW_ALL_REVIEWS") && !juan.contains("EDIT_CONSOLIDATED_OBSERVATIONS"), "Juan does not see private reviews or a draft consolidation");
        check(service.can("u-inv-1", "VIEW_DOCUMENTS", new AuthorizationContext(null, "ETI-2026-002", null, null, null)), "project code resolves to the same project");
        check(service.can("u-inv-1", "VIEW_DOCUMENTS", new AuthorizationContext(null, "prj-1", null, null, null)), "ownership covers Juan's other projects");
        check(!service.can("u-inv-1", "UPLOAD_DOCUMENTS", new AuthorizationContext(null, "prj-2", null, null, "IN_REVIEW")), "upload is denied while in review");
        check(service.can("u-inv-1", "UPLOAD_DOCUMENTS", new AuthorizationContext(null, "prj-2", null, null, "AWAITING_RESEARCHER")), "upload is allowed when answering");
        Set<String> juanReview = service.getEffectiveCapabilities("u-inv-1", new AuthorizationContext(null, "prj-2", null, null, "IN_REVIEW"));
        check(juanReview.contains("VIEW_DOCUMENTS") && !juanReview.contains("UPLOAD_DOCUMENTS"), "workflow removes only the blocked action");
        check(service.history("u-inv-1").stream().noneMatch(item -> "demo-assignment-juan-researcher-project".equals(item.id())), "researcher yaml row is not a second grant");

        String completed = completeAlberto(assignments);
        AuthorizationService closed = service(roles, completed);
        Set<String> albertoClosed = closed.getEffectiveCapabilities("u-rev-ext", roundOne);
        check(albertoClosed.contains("VIEW_OWN_REVIEW"), "a completed review stays readable");
        check(albertoClosed.contains("VIEW_PUBLISHED_OBSERVATIONS"), "a completed review still sees published observations");
        check(!albertoClosed.contains("EDIT_OWN_REVIEW"), "a completed review cannot be edited");
        check(!albertoClosed.contains("SUBMIT_OWN_REVIEW"), "a completed review cannot be submitted");
        check(!albertoClosed.contains("ACCEPT_REVIEW_ASSIGNMENT") && !albertoClosed.contains("REJECT_REVIEW_ASSIGNMENT"), "a completed review cannot be accepted or rejected");
        check(closed.history("u-rev-ext").stream().anyMatch(item -> item.status() == AssignmentStatus.COMPLETED), "completed assignment stays in history");

        check(!service.can("u-rev-ext", "NOT_A_CAPABILITY", roundOne), "unknown capability is denied");
        check(!service.can("missing-user", "VIEW_DOCUMENTS", roundOne), "unknown user is denied");
        try {
            YamlRoleDefinitionProvider.parse("version: 4\nroles:\n  REVIEWER:\n    capabilities:\n      global: []\n      committee: []\n      project: []\n      process:\n        - VIEW_OWN_REVIEW\n");
            throw new AssertionError("missing id_label must fail");
        } catch (EthicsAuthException ex) {
            check("ETHICS_ROLE_LABEL".equals(ex.code()), "missing id_label is reported");
        }

        check(service.profile(user("42", "otra.persona@uss.cl", "Otra")).roles.isEmpty(), "unmapped person gets no ethics roles");
        check(service.permissions(user("42", "juan.cabello@uss.cl", "Juan"), AuthorizationContext.empty()).capabilities.isEmpty(), "the email alone does not grant ethics");
        AuthorizationService persisted = new AuthorizationService(roles, new FixedAssignments(List.of(
                new RoleAssignment("boot-admin", "1001", "ADMINISTRATOR", ScopeType.GLOBAL, "*", AssignmentStatus.ACTIVE, null, null, null, null, null, null),
                new RoleAssignment("boot-researcher", "1001", "RESEARCHER", ScopeType.GLOBAL, "*", AssignmentStatus.ACTIVE, null, null, null, null, null, null)
        )));
        check(persisted.permissions(user("1001", "otro@uss.cl", "Juan"), AuthorizationContext.empty()).capabilities.contains("MANAGE_USERS"), "the assignment grants manage users");
        check(!persisted.permissions(user("1002", "juan.cabello@uss.cl", "Juan"), AuthorizationContext.empty()).capabilities.contains("MANAGE_USERS"), "another id with the same email is not the administrator");

        EthicsWorkflowPolicy policy = new EthicsWorkflowPolicy();
        check(policy.allowsNow("REASSIGN_REVIEWER", "IN_REVIEW"), "ordinary reassignment stays available during review");
        check(!policy.allowsNow("REASSIGN_REVIEWER", "AWAITING_RESEARCHER"), "ordinary reassignment stops when the researcher must answer");
        check(!policy.allowsNow("REASSIGN_REVIEWER", "PENDING_RESOLUTION"), "ordinary reassignment stops while the resolution is prepared");
        check(policy.allowsNow("REASSIGN_REVIEWER_ADVANCED_STAGE", "AWAITING_RESEARCHER"), "the advanced capability matches the researcher response stage");
        check(policy.allowsNow("REASSIGN_REVIEWER_ADVANCED_STAGE", "PENDING_RESOLUTION"), "the advanced capability matches resolution preparation");
        check(!policy.allowsNow("REASSIGN_REVIEWER_ADVANCED_STAGE", "IN_REVIEW"), "the advanced capability does not replace the ordinary one");
        check(!policy.allowsNow("REASSIGN_REVIEWER", "APPROVED"), "a closed approval is not reassigned");
        check(!policy.allowsNow("REASSIGN_REVIEWER_ADVANCED_STAGE", "APPROVED"), "a closed approval is not reassigned with the advanced capability");
        check(policy.allowsNow("SAVE_CONSOLIDATED_OBSERVATIONS", "IN_REVIEW"), "saving a consolidation follows the review states");
        check(!policy.allowsNow("SAVE_CONSOLIDATED_OBSERVATIONS", "APPROVED"), "a closed case does not save a consolidation");

        System.out.println("EthicsAuthorizationTest OK");
    }

    private static AuthorizationService service(RoleDefinitionProvider roles, String assignments) {
        return new AuthorizationService(roles, CompositeRoleAssignmentProvider.demo(assignments, roles));
    }

    private static String completeAlberto(String yaml) {
        int user = yaml.indexOf("userKey: u-rev-ext");
        int status = yaml.indexOf("status: ACTIVE", user);
        if (user < 0 || status < 0) {
            throw new AssertionError("Alberto assignment was not found");
        }
        return yaml.substring(0, status) + "status: COMPLETED" + yaml.substring(status + "status: ACTIVE".length());
    }

    private static cl.uss.ca.security.CurrentUser user(String id, String email, String name) {
        cl.uss.ca.security.CurrentUser user = new cl.uss.ca.security.CurrentUser();
        user.id = id;
        user.email = email;
        user.displayName = name;
        return user;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FixedAssignments implements RoleAssignmentProvider {
        private final List<RoleAssignment> rows;

        private FixedAssignments(List<RoleAssignment> rows) {
            this.rows = List.copyOf(rows);
        }

        @Override
        public List<RoleAssignment> getAssignmentsForUser(String userId) {
            return rows.stream().filter(item -> item.userId().equals(userId)).toList();
        }

        @Override
        public List<RoleAssignment> getAssignmentsForUserAndContext(String userId, AuthorizationContext context) {
            return getAssignmentsForUser(userId);
        }
    }
}
