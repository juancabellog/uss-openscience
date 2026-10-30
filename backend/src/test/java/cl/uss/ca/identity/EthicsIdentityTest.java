package cl.uss.ca.identity;

import cl.uss.ca.ethics.auth.AssignmentStatus;
import cl.uss.ca.ethics.auth.AuthorizationContext;
import cl.uss.ca.ethics.auth.AuthorizationService;
import cl.uss.ca.ethics.auth.EthicsResourceIndex;
import cl.uss.ca.ethics.auth.RoleAssignment;
import cl.uss.ca.ethics.auth.RoleAssignmentProvider;
import cl.uss.ca.ethics.auth.ScopeMatcher;
import cl.uss.ca.ethics.auth.ScopeType;
import cl.uss.ca.ethics.auth.YamlRoleDefinitionProvider;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EthicsIdentityTest {
    private EthicsIdentityTest() {
    }

    public static void main(String[] args) {
        pureProfileUsesInstitutionalValuesWhenLocalIsEmpty();
        pureLinkedPersonIgnoresLocalValuesAndRejectsUpdates();
        emptyPureAttributeCannotBeFilledFromEthics();
        externalPersonStaysOutOfPure();
        externalPersonCanBeEditedLocally();
        newEthicsAccessReceivesOneResearcher();
        applicationAccessIsIndependent();
        researcherStaysWhenReviewerIsContextual();
        historicalSnapshotIgnoresLaterPureChanges();
        creatingAPersonDoesNotGrantResearcher();
        System.out.println("EthicsIdentityTest OK");
    }

    private static void pureProfileUsesInstitutionalValuesWhenLocalIsEmpty() {
        RecordingPure pure = new RecordingPure();
        pure.put(PurePersonView.institutional(10L, "Juan", "Pérez", "Juan.Perez@uss.cl", "Docente"));
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), pure);
        SharedPerson person = service.registerPerson(linked(10L));
        EffectivePersonProfile profile = service.profile(person.id());
        check(profile.source() == PersonSource.PURE, "a linked person is PURE");
        check("Juan".equals(profile.firstName()) && "Pérez".equals(profile.lastName()), "names come from pure_person");
        check("Juan.Perez@uss.cl".equals(profile.email()), "the institutional email keeps its original casing");
        check("Docente".equals(profile.position()), "cargo is the only position column");
        check(profile.campus() == null && profile.faculty() == null && profile.unit() == null, "pure_person has no campus, faculty or unit column");
        check(pure.writes() == 0, "reading pure_person does not write it");
    }

    private static void pureLinkedPersonIgnoresLocalValuesAndRejectsUpdates() {
        RecordingPure pure = new RecordingPure();
        pure.put(new PurePersonView(10L, "Juan", "Pérez", "juan.perez@uss.cl", "Docente", null, "Facultad A", null));
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), pure);
        SharedPerson person = service.registerPerson(new SharedPerson(
                null, 10L, null, null, null, null, null, null, "Facultad B", null, "Investigador", PersonKind.INSTITUTIONAL
        ));
        EffectivePersonProfile profile = service.profile(person.id());
        check("Facultad A".equals(profile.faculty()), "a linked person keeps the institutional faculty");
        check("Docente".equals(profile.position()), "cargo stays authoritative over a local position");
        check("Juan".equals(profile.firstName()), "the name comes from pure_person");
        check(profile.institution() == null, "a local institution is not an override");
        try {
            service.updateLocalPerson(person.id(), new SharedPerson(
                    person.id(), 10L, null, null, null, null, null, null, "Facultad B", null, "Investigador", PersonKind.INSTITUTIONAL
            ));
            throw new AssertionError("a pure-linked update must be rejected");
        } catch (IdentityRuleException ex) {
            check("PURE_PERSON_READONLY".equals(ex.code()), "the rejection identifies the pure source");
            check(ex.getMessage().contains("PURE") && ex.getMessage().contains("Ética de Proyectos"), "the message is understandable");
        }
        SharedPerson stored = service.store().person(person.id());
        check("Facultad B".equals(stored.faculty()) && "Investigador".equals(stored.position()), "the rejected update does not rewrite the stored local columns");
        check("Facultad A".equals(service.profile(person.id()).faculty()), "the effective profile still ignores the legacy local faculty");
        check("Facultad A".equals(pure.findById(10L).faculty()) && "Docente".equals(pure.findById(10L).position()), "pure_person stays unchanged");
        check(pure.writes() == 0, "ethics does not write pure_person");
    }

    private static void emptyPureAttributeCannotBeFilledFromEthics() {
        RecordingPure pure = new RecordingPure();
        pure.put(PurePersonView.institutional(10L, "Juan", "Pérez", "juan.perez@uss.cl", null));
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), pure);
        SharedPerson person = service.registerPerson(linked(10L));
        check(service.profile(person.id()).position() == null, "an empty cargo stays empty");
        try {
            service.updateLocalPerson(person.id(), new SharedPerson(
                    person.id(), 10L, null, null, null, null, null, null, null, null, "Investigador", PersonKind.INSTITUTIONAL
            ));
            throw new AssertionError("an empty pure attribute cannot be completed locally");
        } catch (IdentityRuleException ex) {
            check("PURE_PERSON_READONLY".equals(ex.code()), "filling an empty pure attribute is rejected");
        }
        check(service.store().person(person.id()).position() == null, "the empty attribute is not stored as a local override");
        check(pure.findById(10L).position() == null && pure.writes() == 0, "pure_person stays empty and untouched");
    }

    private static void externalPersonStaysOutOfPure() {
        RecordingPure pure = new RecordingPure();
        pure.failOnRead = true;
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), pure);
        SharedPerson alberto = service.registerPerson(new SharedPerson(
                null, null, "Alberto", "Soto", " Alberto@Externo.CL ", null, "Universidad Austral", null, null, null, "Docente", PersonKind.EXTERNAL
        ));
        check(service.store().accountCount() == 0, "a person can exist without a user account");
        check(service.ethicsRoles("nobody").isEmpty(), "a person without a user has no ethics role");
        check(alberto.purePersonId() == null && alberto.emailNormalized().equals("alberto@externo.cl"), "email is normalized for search and the person stays local");
        check(" Alberto@Externo.CL ".trim().equals(alberto.email()), "the stored email keeps the entered casing");
        EffectivePersonProfile profile = service.profile(alberto.id());
        check(profile.source() == PersonSource.LOCAL && "Alberto".equals(profile.firstName()), "the effective profile uses the local person");
        SharedPerson duplicateSignal = service.registerPerson(new SharedPerson(
                null, null, "Otro", "Alberto", "alberto@externo.cl", null, null, null, null, null, null, PersonKind.EXTERNAL
        ));
        check(service.store().personCount() == 2, "the same email does not merge or delete the existing person");
        check(service.store().findPersonByEmail(" alberto@externo.cl ").id().equals(alberto.id()), "normalized email finds the first person");
        check(!duplicateSignal.id().equals(alberto.id()), "the second person keeps its own id");
        check(pure.writes() == 0, "an external person is not inserted into pure_person");

        try {
            service.registerInstitutional(alberto.email(), "Alberto", "Soto", "self", "2026-10-29T12:00:00Z", null);
            throw new AssertionError("an external email must not self-register");
        } catch (IdentityRuleException ex) {
            check("ETHICS_SELF_REGISTRATION_DENIED".equals(ex.code()), "self-registration rejects an external domain");
        }
        service.registerAccount("u-ext", alberto.email(), "Alberto", "Soto", alberto.id());
        service.createExternalReviewer(true, alberto.email(), "Alberto", "Soto", "Universidad Austral", "Docente", "u-admin", "2026-10-29T12:00:00Z");
        service.createExternalReviewer(true, alberto.email(), "Alberto", "Soto", "Universidad Austral", "Docente", "u-admin", "2026-10-29T12:05:00Z");
        List<RoleAssignment> roles = service.ethicsRoles("u-ext");
        check(roles.isEmpty(), "an external reviewer does not receive researcher");
        check(service.applicationActive("u-ext", ApplicationCode.ETHICS), "ethics access is active");
        check(!service.applicationActive("u-ext", ApplicationCode.OPENSCIENCE), "an external account does not receive OpenScience");
        check(service.store().person(alberto.id()).purePersonId() == null, "granting ethics does not invent a pure_person link");
    }

    private static void externalPersonCanBeEditedLocally() {
        RecordingPure pure = new RecordingPure();
        pure.failOnRead = true;
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), pure);
        SharedPerson person = service.registerPerson(new SharedPerson(
                null, null, "Alberto", "Soto", "alberto@externo.cl", null, "Universidad Austral", null, null, null, "Docente", PersonKind.EXTERNAL
        ));
        SharedPerson updated = service.updateLocalPerson(person.id(), new SharedPerson(
                person.id(), null, null, null, "alberto.soto@externo.cl", null, "Hospital Regional", null, null, null, "Investigador", PersonKind.EXTERNAL
        ));
        check(updated.purePersonId() == null, "editing a local person does not create a pure link");
        check("Hospital Regional".equals(updated.institution()), "institution is stored locally");
        check("alberto.soto@externo.cl".equals(updated.email()), "email is stored locally");
        check("Investigador".equals(updated.position()), "position is stored locally");
        EffectivePersonProfile profile = service.profile(person.id());
        check(profile.source() == PersonSource.LOCAL && "Hospital Regional".equals(profile.institution()), "the effective profile reads the local update");
        check("alberto.soto@externo.cl".equals(profile.email()) && "Investigador".equals(profile.position()), "email and position stay local");
        check(pure.writes() == 0, "editing an external person does not write pure_person");
    }

    private static void newEthicsAccessReceivesOneResearcher() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        service.registerAccount("u-new", "Nueva.Persona@uss.cl", "Nueva", "Persona", null);
        service.grantEthicsAccess("u-new", "u-admin", "2026-10-29T12:00:00Z", null);
        service.grantEthicsAccess("u-new", "u-admin", "2026-10-29T12:10:00Z", null);
        List<RoleAssignment> roles = service.ethicsRoles("u-new");
        check(roles.size() == 1, "the second access does not create another researcher assignment");
        check(roles.get(0).status() == AssignmentStatus.ACTIVE && "RESEARCHER".equals(roles.get(0).roleId()), "the default role is active researcher");
        UserApplication access = service.store().application("u-new", ApplicationCode.ETHICS);
        check(access.status() == ApplicationAccessStatus.ACTIVE, "ethics access is active");
        check(service.store().applicationsOf("u-new").size() == 1, "ethics access is a single row");
        SharedPerson person = service.store().person(service.store().account("u-new").personId());
        check(person.purePersonId() == null && "Nueva".equals(person.firstName()), "without pure_person the person is completed locally");
        check(person.linkedToPure() == false, "a missing institutional row does not block ethics");
    }

    private static void applicationAccessIsIndependent() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        service.registerAccount("u-both", "ambos@uss.cl", "Ambos", "Accesos", null);
        service.grantApplication("u-both", ApplicationCode.OPENSCIENCE, "u-admin", "2026-10-29T12:00:00Z");
        service.grantEthicsAccess("u-both", "u-admin", "2026-10-29T12:00:00Z", null);
        String personId = service.store().account("u-both").personId();
        service.captureSnapshot("req-both", personId, "2026-10-29T12:01:00Z");
        service.revokeApplication("u-both", ApplicationCode.ETHICS, "u-admin", "2026-10-29T13:00:00Z");
        check(service.applicationActive("u-both", ApplicationCode.OPENSCIENCE), "revoking ethics keeps OpenScience");
        check(!service.applicationActive("u-both", ApplicationCode.ETHICS), "ethics access is revoked");
        check(service.store().person(personId) != null && service.store().account("u-both") != null, "revoke does not delete the person or the account");
        check(service.store().snapshot("req-both") != null, "revoke does not delete a submitted snapshot");
        check(service.ethicsRoles("u-both").size() == 1, "revoke does not delete the researcher assignment");

        service.registerAccount("u-back", "vuelta@uss.cl", "Vuelta", "Acceso", null);
        service.grantApplication("u-back", ApplicationCode.OPENSCIENCE, "u-admin", "2026-10-29T12:00:00Z");
        service.grantEthicsAccess("u-back", "u-admin", "2026-10-29T12:00:00Z", null);
        service.revokeApplication("u-back", ApplicationCode.OPENSCIENCE, "u-admin", "2026-10-29T13:00:00Z");
        check(service.applicationActive("u-back", ApplicationCode.ETHICS), "revoking OpenScience keeps ethics");
        check(!service.applicationActive("u-back", ApplicationCode.OPENSCIENCE), "OpenScience access is revoked");
    }

    private static void researcherStaysWhenReviewerIsContextual() {
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), id -> null);
        UserAccountRef account = service.createExternalReviewer(true, "alberto.soto@externo.cl", "Alberto", "Soto", "Universidad Austral", "Docente", "u-admin", "2026-10-29T12:00:00Z");
        service.store().addEthicsRole(new RoleAssignment(
                "rev-ext",
                account.id(),
                "REVIEWER",
                ScopeType.PROCESS,
                "CECUA-2026-004:R1",
                AssignmentStatus.ACTIVE,
                null,
                null,
                "u-admin",
                "2026-10-29T12:30:00Z",
                "u-admin",
                "2026-10-29T12:30:00Z"
        ));
        List<RoleAssignment> roles = service.ethicsRoles(account.id());
        check(roles.stream().noneMatch(item -> "RESEARCHER".equals(item.roleId())), "an external reviewer does not receive researcher");
        check(roles.stream().anyMatch(item -> "REVIEWER".equals(item.roleId())), "reviewer exists only after the contextual assignment");
        AuthorizationService authorization = new AuthorizationService(
                YamlRoleDefinitionProvider.loadClasspath(),
                new FixedAssignments(roles)
        );
        Set<String> onAssignedRound = authorization.getEffectiveCapabilities(account.id(), new AuthorizationContext(null, null, null, "CECUA-2026-004:R1", null));
        Set<String> onAnotherRound = authorization.getEffectiveCapabilities(account.id(), new AuthorizationContext(null, null, null, "CECUA-2026-004:R2", null));
        check(onAssignedRound.contains("VIEW_OWN_REVIEW") && !onAssignedRound.contains("CREATE_REQUEST"), "the assigned round grants reviewer work and not researcher");
        check(onAnotherRound.isEmpty(), "another round does not keep a global researcher role");
        check(roles.stream().noneMatch(item -> "EXTERNAL_REVIEWER".equals(item.roleId())), "being external does not create a reviewer role");
    }

    private static void historicalSnapshotIgnoresLaterPureChanges() {
        RecordingPure pure = new RecordingPure();
        pure.put(new PurePersonView(10L, "Juan", "Pérez", "juan.perez@uss.cl", "Docente", null, "Facultad A", null));
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), pure);
        SharedPerson person = service.registerPerson(linked(10L));
        RequestPersonSnapshot snapshot = service.captureSnapshot("req-2026", person.id(), "2026-10-29T12:00:00Z");
        pure.put(new PurePersonView(10L, "Juan", "Pérez", "juan.perez@uss.cl", "Docente", null, "Facultad B", null));
        RequestPersonSnapshot again = service.captureSnapshot("req-2026", person.id(), "2028-01-01T00:00:00Z");
        check("Facultad A".equals(snapshot.faculty()) && "Facultad A".equals(again.faculty()), "a later pure change does not rewrite the submitted snapshot");
        check("Facultad B".equals(service.profile(person.id()).faculty()), "a new profile read sees the current institutional faculty");
        check(pure.writes() == 0, "the snapshot does not write pure_person");
    }

    private static void creatingAPersonDoesNotGrantResearcher() {
        RecordingPure pure = new RecordingPure();
        pure.put(PurePersonView.institutional(22L, "Ana", "Soto", "ana.soto@uss.cl", "Docente"));
        EthicsAccessService service = new EthicsAccessService(new InMemoryIdentityStore(), pure);
        SharedPerson person = service.registerPerson(linked(22L));
        check(service.store().accountCount() == 0, "importing a pure-linked person does not create a user");
        check(service.ethicsRoles(person.id()).isEmpty(), "a pure_person link does not grant researcher");
        check(service.store().application(person.id(), ApplicationCode.ETHICS) == null, "a person without a user has no ethics access");
        check(pure.writes() == 0, "linking by an existing pure id does not insert another pure row");
    }

    private static SharedPerson linked(long purePersonId) {
        return new SharedPerson(null, purePersonId, null, null, null, null, null, null, null, null, null, PersonKind.INSTITUTIONAL);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingPure implements PurePersonLookup {
        private final Map<Long, PurePersonView> rows = new LinkedHashMap<>();
        private boolean failOnRead;
        private int writes;

        void put(PurePersonView person) {
            rows.put(person.id(), person);
        }

        int writes() {
            return writes;
        }

        @Override
        public PurePersonView findById(long purePersonId) {
            if (failOnRead) {
                throw new AssertionError("an external person must not read pure_person");
            }
            return rows.get(purePersonId);
        }
    }

    private static final class FixedAssignments implements RoleAssignmentProvider {
        private final List<RoleAssignment> rows;
        private final ScopeMatcher matcher = new ScopeMatcher();
        private final EthicsResourceIndex resources = EthicsResourceIndex.prototype();

        private FixedAssignments(List<RoleAssignment> rows) {
            this.rows = List.copyOf(rows);
        }

        @Override
        public List<RoleAssignment> getAssignmentsForUser(String userId) {
            List<RoleAssignment> found = new ArrayList<>();
            for (RoleAssignment row : rows) {
                if (row.userId().equals(userId)) {
                    found.add(row);
                }
            }
            return found;
        }

        @Override
        public List<RoleAssignment> getAssignmentsForUserAndContext(String userId, AuthorizationContext context) {
            LocalDate today = LocalDate.now();
            return getAssignmentsForUser(userId).stream()
                    .filter(item -> item.operativeOn(today))
                    .filter(item -> matcher.matches(item, context, resources))
                    .toList();
        }
    }
}
