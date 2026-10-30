package cl.uss.ca.identity;

import java.nio.file.Files;
import java.nio.file.Path;

public final class EthicsOnboardingTest {
    private EthicsOnboardingTest() {
    }

    public static void main(String[] args) throws Exception {
        check(InstitutionalOnboarding.EXISTING_USER.equals(InstitutionalOnboarding.classify(true, true)), "A. an existing account wins over PURE");
        check(InstitutionalOnboarding.EXISTING_USER.equals(InstitutionalOnboarding.classify(true, false)), "A. an existing account is reused");
        check(InstitutionalOnboarding.PURE_PERSON.equals(InstitutionalOnboarding.classify(false, true)), "B. PURE without an account");
        check(InstitutionalOnboarding.NEW_INSTITUTIONAL_PERSON.equals(InstitutionalOnboarding.classify(false, false)), "C. neither account nor PURE");
        check(!InstitutionalOnboarding.profileRequired("Juan", "Cabello"), "B. PURE names do not ask for a profile");
        check(InstitutionalOnboarding.profileRequired("", "Cabello"), "C. a blank name still needs the registration form");
        check(InstitutionalOnboarding.profileRequired(null, null), "C. missing names need the registration form");

        check(InstitutionalDomains.isAllowedInstitutionalEmail("juan@uss.cl"), "domain uss.cl");
        check(InstitutionalDomains.isAllowedInstitutionalEmail("juan@correo.uss.cl"), "domain correo.uss.cl");
        check(InstitutionalDomains.isAllowedInstitutionalEmail("juan@docente.uss.cl"), "domain docente.uss.cl");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("persona@gmail.com"), "D. gmail is not this flow");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("persona@universidad.cl"), "D. another university is not this flow");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("persona@fakeuss.cl"), "D. endsWith uss.cl is not enough");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("persona@uss.cl.attacker.com"), "D. a suffix is not a USS domain");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("persona@notuss.cl"), "D. a lookalike domain is rejected");

        check(InstitutionalCatalog.accepts(
                "Santiago - Bellavista",
                "Medicina",
                "Investigador"
        ), "catalog values from the project form are accepted");
        check(!InstitutionalCatalog.accepts("Campus libre", "Medicina", "Investigador"), "free-text campus is rejected");
        check(!InstitutionalCatalog.accepts("Santiago - Bellavista", "Facultad inventada", "Investigador"), "free-text faculty is rejected");
        check(!InstitutionalCatalog.accepts("Santiago - Bellavista", "Medicina", "Cargo libre"), "free-text position is rejected");

        String onboarding = Files.readString(Path.of("src/main/java/cl/uss/ca/ethics/directory/EthicsOnboardingService.java"));
        String mailer = Files.readString(Path.of("src/main/java/cl/uss/ca/service/AccountMailer.java"));
        String catalog = Files.readString(Path.of("src/main/java/cl/uss/ca/mail/AccountMailCatalog.java"));
        String auth = Files.readString(Path.of("src/main/java/cl/uss/ca/service/AuthService.java"));
        String users = Files.readString(Path.of("../projects/ethics-uss/src/app/features/administration/users.page.html"));
        String start = Files.readString(Path.of("../projects/ethics-uss/src/app/features/auth/role-select.page.ts"));
        int institutional = users.indexOf("accountType() === 'INSTITUTIONAL'");
        int external = users.indexOf("users.sendRegistrationInvite", institutional);
        String institutionalBlock = users.substring(institutional, external);
        check(institutionalBlock.contains("users.continue") && institutionalBlock.contains("searchingUser"), "admin starts with continue and a lookup");
        check(!institutionalBlock.contains("givenName.set") && !institutionalBlock.contains("familyName.set"), "admin does not ask for names before resolution");
        check(institutionalBlock.contains("sendInvitation") && institutionalBlock.contains("enableEthics"), "admin invites or enables after resolution");

        int accountPanel = start.indexOf("panel() === 'account'");
        int loginPanel = start.indexOf("@else {", accountPanel);
        String publicBlock = start.substring(accountPanel, loginPanel);
        check(publicBlock.contains("institutionalEmail") && !publicBlock.contains("givenName"), "public create-account starts with email");
        check(start.contains("startInstitutional"), "public create-account calls the email resolution");

        check(onboarding.contains("issueEthicsInvitation"), "invitations reuse the OpenScience activation token");
        check(onboarding.contains("RESEARCHER"), "activation assigns RESEARCHER");
        int activated = onboarding.indexOf("public void onActivated");
        int welcomeMethod = onboarding.indexOf("private void welcome");
        String activation = onboarding.substring(activated, welcomeMethod);
        check(activation.contains("insertInstitutionalPerson(") && activation.contains("null,"), "a person missing from PURE is stored with a null PURE id");
        check(onboarding.contains("Si ya tienes una cuenta, utiliza Iniciar sesión."), "public existing account does not describe the account");
        check(!onboarding.contains("password"), "the onboarding service does not handle passwords");
        check(mailer.contains("MailLocale.resolve(user == null ? null : user.preferredLanguage, null)"), "welcome and activation use the recipient language");
        check(catalog.contains("Se ha habilitado tu acceso a la Plataforma de Ética de Proyectos."), "welcome text");
        check(catalog.contains("credenciales de tu cuenta actual"), "welcome reuses the current password");
        check(catalog.contains("Your access to the Project Ethics platform has been enabled."), "welcome also exists in English");
        int welcome = catalog.indexOf("welcome.body");
        String welcomeLine = catalog.substring(welcome, catalog.indexOf("welcome.closing"));
        check(!welcomeLine.contains("token=") && !welcomeLine.toLowerCase().contains("password"), "welcome does not carry a password or token");
        check(auth.contains("issueEthicsInvitation") && auth.contains("InstitutionalCatalog.accepts"), "activation validates the institutional catalog before the password");

        System.out.println("EthicsOnboardingTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
