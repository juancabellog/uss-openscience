package cl.uss.ca.ethics.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EthicsAdminBootstrapTest {
    private EthicsAdminBootstrapTest() {
    }

    public static void main(String[] args) {
        activatesEthicsResearcherAndAdministrator();
        secondRunDoesNotDuplicate();
        existingResearcherOnlyAddsAdministrator();
        existingAdministratorIsNotDuplicated();
        missingAccountIsNotCreated();
        manageUsersFollowsTheRole();
        System.out.println("EthicsAdminBootstrapTest OK");
    }

    private static void activatesEthicsResearcherAndAdministrator() {
        Memory directory = new Memory();
        directory.account = new EthicsAdminDirectory.AdminAccount(41L, "juan.cabello@uss.cl", null, 88L);
        EthicsAdminBootstrap.AdminAccount result = EthicsAdminBootstrap.ensure(" Juan.Cabello@USS.CL ", directory);
        check(result != null && result.id() == 41L && Long.valueOf(88L).equals(result.purePersonId()), "the existing account is reused");
        check(directory.accounts.size() == 1, "a second account is not created");
        check(directory.ethics.contains(41L), "ethics becomes active");
        check(directory.roles.get(41L).contains("RESEARCHER") && directory.roles.get(41L).contains("ADMINISTRATOR"), "researcher and administrator are granted");
        check(directory.audit.stream().noneMatch(item -> item.toLowerCase().contains("password")), "the audit does not store a password");
    }

    private static void secondRunDoesNotDuplicate() {
        Memory directory = new Memory();
        directory.account = new EthicsAdminDirectory.AdminAccount(41L, "juan.cabello@uss.cl", null, null);
        EthicsAdminBootstrap.ensure("juan.cabello@uss.cl", directory);
        int roles = directory.roles.get(41L).size();
        int events = directory.audit.size();
        EthicsAdminBootstrap.ensure("juan.cabello@uss.cl", directory);
        check(directory.roles.get(41L).size() == roles, "a second bootstrap does not duplicate roles");
        check(directory.ethics.size() == 1, "a second bootstrap does not duplicate ethics");
        check(directory.audit.size() == events, "a second bootstrap does not repeat the audit");
    }

    private static void existingResearcherOnlyAddsAdministrator() {
        Memory directory = new Memory();
        directory.account = new EthicsAdminDirectory.AdminAccount(41L, "juan.cabello@uss.cl", 7L, null);
        directory.roles.computeIfAbsent(41L, key -> new ArrayList<>()).add("RESEARCHER");
        EthicsAdminBootstrap.ensure("juan.cabello@uss.cl", directory);
        check(directory.roles.get(41L).stream().filter(role -> "RESEARCHER".equals(role)).count() == 1, "researcher stays single");
        check(directory.roles.get(41L).contains("ADMINISTRATOR"), "administrator is added");
        check(directory.audit.stream().anyMatch(item -> item.contains("ADMINISTRATOR")), "administrator assignment is audited");
        check(directory.audit.stream().noneMatch(item -> item.contains("RESEARCHER")), "an existing researcher is not assigned again");
    }

    private static void existingAdministratorIsNotDuplicated() {
        Memory directory = new Memory();
        directory.account = new EthicsAdminDirectory.AdminAccount(41L, "juan.cabello@uss.cl", null, null);
        directory.roles.computeIfAbsent(41L, key -> new ArrayList<>()).add("ADMINISTRATOR");
        EthicsAdminBootstrap.ensure("juan.cabello@uss.cl", directory);
        check(directory.roles.get(41L).stream().filter(role -> "ADMINISTRATOR".equals(role)).count() == 1, "administrator stays single");
    }

    private static void missingAccountIsNotCreated() {
        Memory directory = new Memory();
        EthicsAdminBootstrap.AdminAccount result = EthicsAdminBootstrap.ensure("juan.cabello@uss.cl", directory);
        check(result == null, EthicsAdminBootstrap.NOT_FOUND);
        check(directory.accounts.isEmpty() && directory.ethics.isEmpty() && directory.roles.isEmpty(), "a missing account is not created");
    }

    private static void manageUsersFollowsTheRole() {
        RoleDefinitionProvider roles = YamlRoleDefinitionProvider.loadClasspath();
        check(roles.find("ADMINISTRATOR").orElseThrow().capabilities().contains("MANAGE_USERS"), "administrator can manage users");
        for (String roleId : List.of("COMMITTEE_PRESIDENT", "EXECUTIVE_SECRETARY", "REVIEWER", "RESEARCHER")) {
            check(!roles.find(roleId).orElseThrow().capabilities().contains("MANAGE_USERS"), roleId + " cannot manage users");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Memory implements EthicsAdminDirectory {
        private final Map<Long, String> accounts = new LinkedHashMap<>();
        private final List<Long> ethics = new ArrayList<>();
        private final Map<Long, List<String>> roles = new LinkedHashMap<>();
        private final List<String> audit = new ArrayList<>();
        private AdminAccount account;

        @Override
        public AdminAccount findAccount(String normalizedEmail) {
            if (account == null || !account.email().equals(normalizedEmail)) {
                return null;
            }
            accounts.putIfAbsent(account.id(), account.email());
            return account;
        }

        @Override
        public boolean ensureEthicsActive(long userId) {
            if (ethics.contains(userId)) {
                return false;
            }
            ethics.add(userId);
            return true;
        }

        @Override
        public boolean ensureStructuralRole(long userId, String roleId) {
            List<String> held = roles.computeIfAbsent(userId, key -> new ArrayList<>());
            if (held.contains(roleId)) {
                return false;
            }
            held.add(roleId);
            return true;
        }

        @Override
        public void audit(long userId, String action, String detail) {
            audit.add(userId + " " + action + " " + detail);
        }
    }
}
