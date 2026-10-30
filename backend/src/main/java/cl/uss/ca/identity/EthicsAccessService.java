package cl.uss.ca.identity;

import cl.uss.ca.ethics.auth.AssignmentStatus;
import cl.uss.ca.ethics.auth.RoleAssignment;
import cl.uss.ca.ethics.auth.ScopeType;

import java.util.List;
import java.util.Set;

/**
 * Alta de acceso a Ethics y perfil efectivo.
 * Crear una persona no crea cuenta ni rol. PURE no se modifica.
 */
public final class EthicsAccessService {
    private static final Set<String> STRUCTURAL_ROLES = Set.of(
            "RESEARCHER", "ADMINISTRATOR", "COMMITTEE_PRESIDENT", "EXECUTIVE_SECRETARY");
    public static final String SAME_CREDENTIALS = "ETHICS_ENABLED_SAME_CREDENTIALS";
    public static final String EXTERNAL_INVITED = "EXTERNAL_REVIEWER_ENABLED";

    private final InMemoryIdentityStore store;
    private final PurePersonLookup purePeople;

    public EthicsAccessService(InMemoryIdentityStore store, PurePersonLookup purePeople) {
        this.store = store;
        this.purePeople = purePeople == null ? id -> null : purePeople;
    }

    public InMemoryIdentityStore store() {
        return store;
    }

    /** Persona de directorio. No abre cuenta, no asigna RESEARCHER y no toca PURE. */
    public SharedPerson registerPerson(SharedPerson draft) {
        return store.insertPerson(draft);
    }

    public UserAccountRef registerAccount(String id, String email, String givenName, String familyName, String personId) {
        String presented = EmailAddresses.presentation(email);
        UserAccountRef account = store.insertAccount(new UserAccountRef(
                id,
                presented,
                EmailAddresses.normalize(presented),
                blankToNull(givenName),
                blankToNull(familyName),
                null
        ));
        if (personId != null && !personId.isBlank()) {
            return store.linkPerson(id, personId);
        }
        return account;
    }

    public UserApplication grantApplication(String userId, ApplicationCode application, String actor, String at) {
        if (application == ApplicationCode.ETHICS) {
            return grantEthicsAccess(userId, actor, at, null);
        }
        return activate(userId, application, actor, at);
    }

    /**
     * Entrada a Ethics de una cuenta ya autenticada.
     * Ethics activo se respeta. Un dominio institucional sin fila se aprovisiona.
     * Un correo externo sin habilitación administrativa no crea acceso, persona ni rol.
     */
    public UserApplication enterEthics(String userId, String actor, String at) {
        UserAccountRef account = store.account(userId);
        if (account == null) {
            throw new IdentityRuleException("AUTH_REQUIRED", "Authentication is required");
        }
        UserApplication current = store.application(userId, ApplicationCode.ETHICS);
        if (current != null && current.status().open()) {
            return current;
        }
        if (current == null && InstitutionalDomains.isAllowedInstitutionalEmail(account.email())) {
            return grantEthicsAccess(userId, actor, at, null);
        }
        throw new IdentityRuleException("ETHICS_ACCESS_DENIED", InstitutionalDomains.SELF_REGISTRATION_DENIED);
    }

    /**
     * Primer acceso institucional a Ethics.
     * Reutiliza la cuenta, no pide otra contraseña y no duplica RESEARCHER.
     * Un correo externo no se autoaprovisiona.
     */
    public UserApplication grantEthicsAccess(String userId, String actor, String at, PurePersonView reliablePure) {
        UserAccountRef account = requireAccount(userId);
        if (!InstitutionalDomains.isAllowedInstitutionalEmail(account.email())) {
            throw new IdentityRuleException("ETHICS_SELF_REGISTRATION_DENIED", InstitutionalDomains.SELF_REGISTRATION_DENIED);
        }
        boolean already = applicationActive(userId, ApplicationCode.ETHICS);
        UserApplication access = activate(userId, ApplicationCode.ETHICS, actor, at);
        ensureInstitutionalPerson(userId, reliablePure);
        ensureResearcher(userId, actor, at);
        if (!already) {
            store.addNotice(new AccessNotice(account.email(), SAME_CREDENTIALS, at));
        }
        return access;
    }

    /**
     * Alta institucional. Si la cuenta ya existe, no crea otra.
     */
    public UserAccountRef registerInstitutional(String email, String givenName, String familyName, String actor, String at, PurePersonView reliablePure) {
        if (!InstitutionalDomains.isAllowedInstitutionalEmail(email)) {
            throw new IdentityRuleException("ETHICS_SELF_REGISTRATION_DENIED", InstitutionalDomains.SELF_REGISTRATION_DENIED);
        }
        UserAccountRef existing = store.findAccountByEmail(email);
        if (existing != null) {
            grantEthicsAccess(existing.id(), actor, at, reliablePure);
            return store.account(existing.id());
        }
        String id = store.nextPublic("user");
        UserAccountRef created = registerAccount(id, email, givenName, familyName, null);
        grantEthicsAccess(created.id(), actor, at, reliablePure);
        return store.account(created.id());
    }

    public UserApplication enableInstitutionalEthics(boolean manageUsers, String userId, String actor, String at, PurePersonView reliablePure) {
        requireManageUsers(manageUsers);
        return grantEthicsAccess(userId, actor, at, reliablePure);
    }

    /**
     * Revisor externo. No recibe RESEARCHER. No se inserta en PURE.
     * Un correo de dominio USS se resuelve por el flujo institucional.
     */
    public UserAccountRef createExternalReviewer(
            boolean manageUsers,
            String email,
            String givenName,
            String familyName,
            String institution,
            String position,
            String actor,
            String at
    ) {
        requireManageUsers(manageUsers);
        if (InstitutionalDomains.domainOf(email) == null) {
            throw new IdentityRuleException("EMAIL_INVALID", "Indique un correo válido.");
        }
        if (InstitutionalDomains.isAllowedInstitutionalEmail(email)) {
            throw new IdentityRuleException(
                    "INSTITUTIONAL_EMAIL",
                    "Este correo pertenece a un dominio USS. Use el alta institucional."
            );
        }
        UserAccountRef existing = store.findAccountByEmail(email);
        if (existing != null) {
            SharedPerson person = existing.personId() == null ? null : store.person(existing.personId());
            if (person != null && person.kind() == PersonKind.INSTITUTIONAL) {
                throw new IdentityRuleException("USER_ALREADY_INSTITUTIONAL", "El usuario ya existe como persona institucional.");
            }
            if (person == null) {
                SharedPerson created = store.insertPerson(externalPerson(null, givenName, familyName, email, institution, position));
                store.linkPerson(existing.id(), created.id());
            }
            boolean already = applicationActive(existing.id(), ApplicationCode.ETHICS);
            activate(existing.id(), ApplicationCode.ETHICS, actor, at);
            if (!already) {
                store.addNotice(new AccessNotice(existing.email(), EXTERNAL_INVITED, at));
            }
            return store.account(existing.id());
        }
        SharedPerson person = store.insertPerson(externalPerson(null, givenName, familyName, email, institution, position));
        UserAccountRef created = registerAccount(store.nextPublic("user"), email, givenName, familyName, person.id());
        activate(created.id(), ApplicationCode.ETHICS, actor, at);
        store.addNotice(new AccessNotice(created.email(), EXTERNAL_INVITED, at));
        return store.account(created.id());
    }

    public RoleAssignment grantStructuralRole(boolean manageUsers, String userId, String roleId, String actor, String at) {
        requireManageUsers(manageUsers);
        if (!STRUCTURAL_ROLES.contains(roleId)) {
            throw new IdentityRuleException("ROLE_NOT_STRUCTURAL", "Esa función no se asigna desde la administración de usuarios.");
        }
        UserAccountRef account = requireAccount(userId);
        SharedPerson person = account.personId() == null ? null : store.person(account.personId());
        if (person == null || person.kind() != PersonKind.INSTITUTIONAL) {
            throw new IdentityRuleException(
                    "EXTERNAL_ROLE_DENIED",
                    "Una persona externa no puede recibir investigador, presidencia, secretaría ni administración."
            );
        }
        for (RoleAssignment assignment : store.ethicsRolesOf(userId)) {
            if (roleId.equals(assignment.roleId())) {
                return assignment;
            }
        }
        RoleAssignment created = new RoleAssignment(
                null, userId, roleId, ScopeType.GLOBAL, "*", AssignmentStatus.ACTIVE,
                null, null, actor, at, actor, at
        );
        return store.addEthicsRole(created);
    }

    /**
     * Revocar Ethics no toca otras aplicaciones ni borra la cuenta, la persona ni el historial.
     * Si hay una revisión activa y no hay confirmación, no hace nada.
     */
    public UserApplication revokeEthics(boolean manageUsers, String userId, String actor, String at, boolean activeReview, boolean confirmed) {
        requireManageUsers(manageUsers);
        if (activeReview && !confirmed) {
            throw new IdentityRuleException(
                    "ETHICS_DISABLE_NEEDS_CONFIRMATION",
                    "Este revisor tiene una revisión activa. Deshabilitar Ethics no reasigna esa revisión."
            );
        }
        return revokeApplication(userId, ApplicationCode.ETHICS, actor, at);
    }

    private UserApplication activate(String userId, ApplicationCode application, String actor, String at) {
        requireAccount(userId);
        UserApplication current = store.application(userId, application);
        return store.saveApplication(new UserApplication(
                current == null ? null : current.id(),
                userId,
                application,
                ApplicationAccessStatus.ACTIVE,
                at,
                actor,
                at,
                actor
        ));
    }

    public UserApplication revokeApplication(String userId, ApplicationCode application, String actor, String at) {
        UserApplication current = store.application(userId, application);
        if (current == null) {
            throw new IllegalStateException("no application access to revoke");
        }
        return store.saveApplication(new UserApplication(
                current.id(),
                userId,
                application,
                ApplicationAccessStatus.REVOKED,
                current.createdAt(),
                current.createdBy(),
                at,
                actor
        ));
    }

    public boolean applicationActive(String userId, ApplicationCode application) {
        UserApplication access = store.application(userId, application);
        return access != null && access.status().open();
    }

    public EffectivePersonProfile profile(String personId) {
        SharedPerson person = store.person(personId);
        if (person == null) {
            throw new IllegalArgumentException("unknown person: " + personId);
        }
        if (!person.linkedToPure()) {
            return PersonProfileResolver.resolve(person, null);
        }
        return PersonProfileResolver.resolve(person, purePeople.findById(person.purePersonId()));
    }

    /**
     * Actualiza datos locales de una persona externa.
     * Una persona vinculada a PURE se rechaza: Ethics no escribe pure_person ni un override local.
     */
    public SharedPerson updateLocalPerson(String personId, SharedPerson patch) {
        SharedPerson current = store.person(personId);
        if (current == null) {
            throw new IllegalArgumentException("unknown person: " + personId);
        }
        if (current.linkedToPure()) {
            throw new IdentityRuleException(
                    "PURE_PERSON_READONLY",
                    "Los datos de esta persona provienen de PURE y no pueden modificarse desde Ética de Proyectos."
            );
        }
        return store.replacePerson(new SharedPerson(
                current.id(),
                null,
                first(patch == null ? null : patch.firstName(), current.firstName()),
                first(patch == null ? null : patch.lastName(), current.lastName()),
                first(patch == null ? null : patch.email(), current.email()),
                null,
                first(patch == null ? null : patch.institution(), current.institution()),
                first(patch == null ? null : patch.campus(), current.campus()),
                first(patch == null ? null : patch.faculty(), current.faculty()),
                first(patch == null ? null : patch.unit(), current.unit()),
                first(patch == null ? null : patch.position(), current.position()),
                current.kind()
        ));
    }

    public RequestPersonSnapshot captureSnapshot(String requestId, String personId, String at) {
        RequestPersonSnapshot existing = store.snapshot(requestId);
        if (existing != null) {
            return existing;
        }
        EffectivePersonProfile profile = profile(personId);
        return store.saveSnapshot(new RequestPersonSnapshot(
                requestId,
                profile.personId(),
                profile.firstName(),
                profile.lastName(),
                profile.email(),
                profile.institution(),
                profile.campus(),
                profile.faculty(),
                profile.unit(),
                profile.position(),
                at
        ));
    }

    public List<RoleAssignment> ethicsRoles(String userId) {
        return store.ethicsRolesOf(userId);
    }

    private void ensureResearcher(String userId, String actor, String at) {
        UserAccountRef account = requireAccount(userId);
        SharedPerson person = account.personId() == null ? null : store.person(account.personId());
        if (person == null || person.kind() != PersonKind.INSTITUTIONAL) {
            throw new IdentityRuleException(
                    "EXTERNAL_RESEARCHER_DENIED",
                    "Una persona externa no puede ser investigadora en Ética de Proyectos."
            );
        }
        for (RoleAssignment assignment : store.ethicsRolesOf(userId)) {
            if ("RESEARCHER".equals(assignment.roleId())) {
                return;
            }
        }
        store.addEthicsRole(InMemoryIdentityStore.researcher(userId, actor, at));
    }

    private void ensureInstitutionalPerson(String userId, PurePersonView reliablePure) {
        UserAccountRef account = requireAccount(userId);
        if (account.personId() != null) {
            return;
        }
        SharedPerson created;
        if (reliablePure != null) {
            SharedPerson existing = store.findPersonByPureId(reliablePure.id());
            if (existing != null) {
                store.linkPerson(userId, existing.id());
                return;
            }
            created = store.insertPerson(new SharedPerson(
                    null, reliablePure.id(), null, null, null, null, null, null, null, null, null, PersonKind.INSTITUTIONAL
            ));
        } else {
            created = store.insertPerson(new SharedPerson(
                    null, null, account.givenName(), account.familyName(), account.email(), null,
                    null, null, null, null, null, PersonKind.INSTITUTIONAL
            ));
        }
        store.linkPerson(userId, created.id());
    }

    private static SharedPerson externalPerson(String id, String givenName, String familyName, String email, String institution, String position) {
        return new SharedPerson(
                id, null, givenName, familyName, email, null, institution, null, null, null, position, PersonKind.EXTERNAL
        );
    }

    private static void requireManageUsers(boolean manageUsers) {
        if (!manageUsers) {
            throw new IdentityRuleException("MANAGE_USERS_REQUIRED", "No tiene permiso para administrar usuarios.");
        }
    }

    private UserAccountRef requireAccount(String userId) {
        UserAccountRef account = store.account(userId);
        if (account == null) {
            throw new IllegalArgumentException("unknown account: " + userId);
        }
        return account;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String first(String incoming, String current) {
        if (incoming == null || incoming.isBlank()) {
            return current;
        }
        return incoming.trim();
    }
}
