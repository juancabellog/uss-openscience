package cl.uss.ca.identity;

import cl.uss.ca.ethics.auth.AssignmentStatus;
import cl.uss.ca.ethics.auth.RoleAssignment;
import cl.uss.ca.ethics.auth.ScopeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado en memoria del modelo compartido. El SQL propuesto todavía no se ejecuta;
 * esta clase es la que prueban los tests y la que un repositorio JDBC puede reemplazar.
 */
public final class InMemoryIdentityStore {
    private final Map<String, SharedPerson> people = new LinkedHashMap<>();
    private final Map<String, UserAccountRef> accounts = new LinkedHashMap<>();
    private final Map<String, UserApplication> applications = new LinkedHashMap<>();
    private final Map<String, RoleAssignment> ethicsRoles = new LinkedHashMap<>();
    private final Map<String, RequestPersonSnapshot> snapshots = new LinkedHashMap<>();
    private final List<AccessNotice> notices = new ArrayList<>();
    private int sequence = 1;

    public SharedPerson person(String id) {
        return people.get(id);
    }

    public List<SharedPerson> people() {
        return List.copyOf(people.values());
    }

    public SharedPerson findPersonByPureId(Long purePersonId) {
        if (purePersonId == null) {
            return null;
        }
        for (SharedPerson person : people.values()) {
            if (purePersonId.equals(person.purePersonId())) {
                return person;
            }
        }
        return null;
    }

    public SharedPerson findPersonByEmail(String email) {
        String normalized = EmailAddresses.normalize(email);
        if (normalized == null) {
            return null;
        }
        for (SharedPerson person : people.values()) {
            if (normalized.equals(person.emailNormalized())) {
                return person;
            }
        }
        return null;
    }

    public SharedPerson insertPerson(SharedPerson draft) {
        String id = draft.id() == null || draft.id().isBlank() ? next("person") : draft.id();
        if (people.containsKey(id)) {
            throw new IllegalStateException("person already exists: " + id);
        }
        SharedPerson stored = copy(id, draft);
        people.put(id, stored);
        return stored;
    }

    /** Reemplaza una persona local. No crea filas nuevas ni toca pure_person. */
    public SharedPerson replacePerson(SharedPerson person) {
        if (person.id() == null || !people.containsKey(person.id())) {
            throw new IllegalArgumentException("unknown person: " + person.id());
        }
        SharedPerson stored = copy(person.id(), person);
        people.put(person.id(), stored);
        return stored;
    }

    public UserAccountRef account(String id) {
        return accounts.get(id);
    }

    public UserAccountRef findAccountByEmail(String email) {
        String normalized = EmailAddresses.normalize(email);
        if (normalized == null) {
            return null;
        }
        for (UserAccountRef account : accounts.values()) {
            if (normalized.equals(account.emailNormalized())) {
                return account;
            }
        }
        return null;
    }

    public void addNotice(AccessNotice notice) {
        notices.add(notice);
    }

    public List<AccessNotice> notices() {
        return List.copyOf(notices);
    }

    public UserAccountRef insertAccount(UserAccountRef account) {
        if (account.id() == null || accounts.containsKey(account.id())) {
            throw new IllegalStateException("account id is missing or duplicated");
        }
        accounts.put(account.id(), account);
        return account;
    }

    public UserAccountRef linkPerson(String userId, String personId) {
        UserAccountRef current = requiredAccount(userId);
        if (!people.containsKey(personId)) {
            throw new IllegalArgumentException("unknown person: " + personId);
        }
        UserAccountRef linked = new UserAccountRef(
                current.id(),
                current.email(),
                current.emailNormalized(),
                current.givenName(),
                current.familyName(),
                personId
        );
        accounts.put(userId, linked);
        return linked;
    }

    public UserApplication application(String userId, ApplicationCode application) {
        for (UserApplication item : applications.values()) {
            if (item.userId().equals(userId) && item.application() == application) {
                return item;
            }
        }
        return null;
    }

    public UserApplication saveApplication(UserApplication application) {
        UserApplication previous = application(application.userId(), application.application());
        String id = previous == null ? next("access") : previous.id();
        UserApplication stored = new UserApplication(
                id,
                application.userId(),
                application.application(),
                application.status(),
                previous == null ? application.createdAt() : previous.createdAt(),
                previous == null ? application.createdBy() : previous.createdBy(),
                application.modifiedAt(),
                application.modifiedBy()
        );
        if (previous != null) {
            applications.remove(previous.id());
        }
        applications.put(id, stored);
        return stored;
    }

    public List<UserApplication> applicationsOf(String userId) {
        List<UserApplication> found = new ArrayList<>();
        for (UserApplication item : applications.values()) {
            if (item.userId().equals(userId)) {
                found.add(item);
            }
        }
        return List.copyOf(found);
    }

    public List<RoleAssignment> ethicsRolesOf(String userId) {
        List<RoleAssignment> found = new ArrayList<>();
        for (RoleAssignment item : ethicsRoles.values()) {
            if (item.userId().equals(userId)) {
                found.add(item);
            }
        }
        return List.copyOf(found);
    }

    public RoleAssignment addEthicsRole(RoleAssignment assignment) {
        String id = assignment.id() == null || assignment.id().isBlank() ? next("ethics-role") : assignment.id();
        RoleAssignment stored = new RoleAssignment(
                id,
                assignment.userId(),
                assignment.roleId(),
                assignment.scopeType(),
                assignment.scopeId(),
                assignment.status(),
                assignment.validFrom(),
                assignment.validUntil(),
                assignment.createdBy(),
                assignment.createdAt(),
                assignment.modifiedBy(),
                assignment.modifiedAt()
        );
        ethicsRoles.put(id, stored);
        return stored;
    }

    public RequestPersonSnapshot snapshot(String requestId) {
        return snapshots.get(requestId);
    }

    public RequestPersonSnapshot saveSnapshot(RequestPersonSnapshot snapshot) {
        RequestPersonSnapshot previous = snapshots.get(snapshot.requestId());
        if (previous != null) {
            return previous;
        }
        snapshots.put(snapshot.requestId(), snapshot);
        return snapshot;
    }

    public int personCount() {
        return people.size();
    }

    public int accountCount() {
        return accounts.size();
    }

    private UserAccountRef requiredAccount(String userId) {
        UserAccountRef account = accounts.get(userId);
        if (account == null) {
            throw new IllegalArgumentException("unknown account: " + userId);
        }
        return account;
    }

    private SharedPerson copy(String id, SharedPerson draft) {
        String email = EmailAddresses.presentation(draft.email());
        return new SharedPerson(
                id,
                draft.purePersonId(),
                blankToNull(draft.firstName()),
                blankToNull(draft.lastName()),
                email,
                EmailAddresses.normalize(email),
                blankToNull(draft.institution()),
                blankToNull(draft.campus()),
                blankToNull(draft.faculty()),
                blankToNull(draft.unit()),
                blankToNull(draft.position()),
                draft.kind()
        );
    }

    public String nextPublic(String prefix) {
        return next(prefix);
    }

    private String next(String prefix) {
        return prefix + "-" + sequence++;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    static RoleAssignment researcher(String userId, String actor, String at) {
        return new RoleAssignment(
                null,
                userId,
                "RESEARCHER",
                ScopeType.GLOBAL,
                "*",
                AssignmentStatus.ACTIVE,
                null,
                null,
                actor,
                at,
                actor,
                at
        );
    }
}
