package cl.uss.ca.ethics.directory;

import cl.uss.ca.ethics.auth.AssignmentStatus;
import cl.uss.ca.ethics.auth.AuthorizationContext;
import cl.uss.ca.ethics.auth.AuthorizationService;
import cl.uss.ca.ethics.auth.EthicsUserCatalog;
import cl.uss.ca.ethics.auth.EthicsUserCatalog.Account;
import cl.uss.ca.ethics.auth.RoleAssignment;
import cl.uss.ca.ethics.auth.RoleAssignmentProvider;
import cl.uss.ca.ethics.auth.RoleDefinitionProvider;
import cl.uss.ca.ethics.auth.ScopeType;
import cl.uss.ca.ethics.auth.YamlRoleDefinitionProvider;

import java.time.LocalDate;
import java.util.List;

public final class EthicsUserCatalogTest {
    private EthicsUserCatalogTest() {
    }

    public static void main(String[] args) throws Exception {
        Account juan = account("41", "Juan", "Cabello", null, null, "juan.cabello@uss.cl", "ACTIVE", "INSTITUTIONAL", 10L, "ACTIVE",
                List.of(role("41", "RESEARCHER", AssignmentStatus.ACTIVE), role("41", "ADMINISTRATOR", AssignmentStatus.ACTIVE)));
        Account external = account("7", "Alberto", "Soto", null, null, "alberto.soto@externo.cl", "ACTIVE", "EXTERNAL", null, "ACTIVE",
                List.of(role("7", "REVIEWER", AssignmentStatus.ACTIVE)));
        Account historical = account("8", "Ana", "Lagos", null, null, "ana.lagos@externo.cl", "ACTIVE", "EXTERNAL", null, "INACTIVE",
                List.of(role("8", "REVIEWER", AssignmentStatus.COMPLETED)));
        Account openScience = account("15", "Maria", "Rojas", null, null, "maria.rojas@uss.cl", "ACTIVE", "INSTITUTIONAL", null, null, List.of());
        Account gmail = account("16", "Pedro", "Gmail", null, null, "pedro@gmail.com", "ACTIVE", null, null, null, List.of());
        List<Account> directory = List.of(juan, external, historical, openScience, gmail);

        EthicsUserCatalog.Query juanQuery = query("juan");
        check(EthicsUserCatalog.select(directory, juanQuery).stream().anyMatch(item -> "41".equals(item.id())), "A. juan matches the given name");
        check(EthicsUserCatalog.select(directory, query("JUAN")).stream().anyMatch(item -> "41".equals(item.id())), "A. the search is case-insensitive");
        check(EthicsUserCatalog.select(directory, query("cabello")).stream().anyMatch(item -> "41".equals(item.id())), "B. cabello matches the family name");
        check(EthicsUserCatalog.select(directory, query("juan.cabello@uss.cl")).stream().anyMatch(item -> "41".equals(item.id())), "C. the full email matches");
        check(EthicsUserCatalog.select(directory, query("juan.cab")).stream().anyMatch(item -> "41".equals(item.id())), "D. a fragment of the email matches");
        check(EthicsUserCatalog.select(directory, query("cabel")).stream().anyMatch(item -> "41".equals(item.id())), "a partial family name matches");

        List<Account> institutional = EthicsUserCatalog.select(directory, query("", "INSTITUTIONAL", "ALL", "ALL"));
        check(institutional.stream().allMatch(item -> "INSTITUTIONAL".equals(EthicsUserCatalog.personKind(item.personKind(), item.email()))), "E. the institutional filter excludes external people");
        check(institutional.stream().noneMatch(item -> "alberto.soto@externo.cl".equals(item.email())), "E. Alberto is not institutional");
        List<Account> onlyExternal = EthicsUserCatalog.select(directory, query("", "EXTERNAL", "ALL", "ALL"));
        check(onlyExternal.stream().allMatch(item -> "EXTERNAL".equals(EthicsUserCatalog.personKind(item.personKind(), item.email()))), "F. the external filter keeps only external people");
        check(onlyExternal.stream().noneMatch(item -> "juan.cabello@uss.cl".equals(item.email())), "F. an institutional account is excluded");

        List<Account> withEthics = EthicsUserCatalog.select(directory, query("", "ALL", "ALL", "YES"));
        check(withEthics.stream().allMatch(item -> "ACTIVE".equals(item.ethicsStatus())), "G. Ethics access yes keeps only an active application");
        check(withEthics.stream().anyMatch(item -> "41".equals(item.id())), "G. the active administrator remains");
        check(withEthics.stream().noneMatch(item -> "15".equals(item.id())), "G. an OpenScience account without Ethics is excluded");

        List<Account> found = EthicsUserCatalog.select(directory, query("maria.rojas@uss.cl"));
        check(found.size() == 1 && !EthicsUserCatalog.ethicsRelated(found.get(0)) && found.get(0).ethicsStatus() == null, "H. an explicit search finds an OpenScience account without Ethics");
        check(EthicsUserCatalog.select(directory, query("")).stream().noneMatch(item -> "15".equals(item.id())), "the default list does not dump every OpenScience account");

        check(EthicsUserCatalog.select(directory, query("camila")).isEmpty(), "I. a demo name that is not stored is absent");
        check(EthicsUserCatalog.select(directory, query("pilar")).isEmpty(), "I. another demo name is absent");

        RoleDefinitionProvider roles = YamlRoleDefinitionProvider.loadClasspath();
        AuthorizationService researcher = new AuthorizationService(roles, assignments("90", "RESEARCHER"));
        check(!researcher.can("90", "MANAGE_USERS", AuthorizationContext.empty()), "J. without MANAGE_USERS the directory is denied");
        AuthorizationService administrator = new AuthorizationService(roles, assignments("41", "ADMINISTRATOR"));
        check(administrator.can("41", "MANAGE_USERS", AuthorizationContext.empty()), "J. MANAGE_USERS comes from the assignment, not the email");

        List<EthicsUserCatalog.VisibleRole> juanRoles = EthicsUserCatalog.visibleRoles(juan.assignments(), LocalDate.now());
        check(juanRoles.stream().anyMatch(role -> "RESEARCHER".equals(role.id()) && !role.contextual()), "Juan keeps the researcher role");
        check(juanRoles.stream().anyMatch(role -> "ADMINISTRATOR".equals(role.id())), "Juan keeps administration");
        check(EthicsUserCatalog.visibleRoles(historical.assignments(), LocalDate.now()).stream().noneMatch(role -> "REVIEWER".equals(role.id())), "a completed review is not a current reviewer function");
        check(EthicsUserCatalog.visibleRoles(external.assignments(), LocalDate.now()).stream().anyMatch(role -> "REVIEWER".equals(role.id()) && role.contextual()), "an active review is a contextual function");
        check("LOCAL".equals(EthicsUserCatalog.dataSource(null)) && "PURE".equals(EthicsUserCatalog.dataSource(10L)), "data source follows purePersonId");
        check("INSTITUTIONAL".equals(EthicsUserCatalog.personKind(null, "maria.rojas@uss.cl")), "an institutional email without PURE stays institutional");
        check("EXTERNAL".equals(EthicsUserCatalog.personKind(null, "pedro@gmail.com")), "a non-USS email is external");
        check("  Juan.Cabello@USS.CL  ".trim().toLowerCase().equals(EthicsUserCatalog.normalizeSearch("  Juan.Cabello@USS.CL  "))
                && "juan.cabello@uss.cl".equals(juan.email()), "normalization does not rewrite the stored email");

        String resource = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cl/uss/ca/api/EthicsAuthorizationResource.java"));
        String service = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cl/uss/ca/ethics/directory/EthicsDirectoryService.java"));
        String page = java.nio.file.Files.readString(java.nio.file.Path.of("../projects/ethics-uss/src/app/features/administration/users.page.ts"));
        check(resource.contains("assertMayManage") && service.contains("\"MANAGE_USERS\""), "the endpoint requires MANAGE_USERS");
        check(!resource.contains("equals(\"ADMINISTRATOR\")") && !service.contains("equals(\"ADMINISTRATOR\")"), "the endpoint does not authorize by role name");
        check(!page.contains("EthicsStore") && !page.contains("DEMO_USERS") && !page.contains("UserAdminService"), "the users screen does not read the prototype store");

        System.out.println("EthicsUserCatalogTest OK");
    }

    private static EthicsUserCatalog.Query query(String search) {
        return query(search, "ALL", "ALL", "ALL");
    }

    private static EthicsUserCatalog.Query query(String search, String type, String status, String access) {
        return new EthicsUserCatalog.Query(search, type, status, access, 1, 25);
    }

    private static Account account(
            String id, String given, String family, String second, String preferred, String email,
            String accountStatus, String kind, Long purePersonId, String ethicsStatus, List<RoleAssignment> assignments
    ) {
        return new Account(id, given, family, second, preferred, email, accountStatus, kind, purePersonId, ethicsStatus, assignments);
    }

    private static RoleAssignment role(String userId, String roleId, AssignmentStatus status) {
        return new RoleAssignment("r-" + userId + roleId, userId, roleId, ScopeType.GLOBAL, "*", status, null, null, null, null, null, null);
    }

    private static RoleAssignmentProvider assignments(String userId, String roleId) {
        RoleAssignment row = role(userId, roleId, AssignmentStatus.ACTIVE);
        return new RoleAssignmentProvider() {
            @Override
            public List<RoleAssignment> getAssignmentsForUser(String id) {
                return userId.equals(id) ? List.of(row) : List.of();
            }

            @Override
            public List<RoleAssignment> getAssignmentsForUserAndContext(String id, AuthorizationContext context) {
                return getAssignmentsForUser(id);
            }
        };
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
