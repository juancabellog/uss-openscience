package cl.uss.ca.identity;

import cl.uss.ca.auth.ActivationOrigin;
import cl.uss.ca.auth.TokenPreviewStatus;
import cl.uss.ca.service.AccountMailer;
import cl.uss.ca.service.PublicAppUrl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

public final class ActivationContractTest {
    private ActivationContractTest() {
    }

    public static void main(String[] args) throws Exception {
        nonPureInvitationRequiresProfile();
        testAllowlistFollowsTheSameNonPureActivation();
        ethicsInvitationOpensTheEthicsApp();
        passwordOnlyIsRejectedForLocalProfile();
        completeLocalProfileIsAcceptedByTheDecision();
        pureInvitationDoesNotRequireAnEditableProfile();
        existingNamedAccountDoesNotAskAgain();
        tokenStates();
        activationPersistsLocalPersonWithoutOpenScience();
        System.out.println("ActivationContractTest OK");
    }

    private static void nonPureInvitationRequiresProfile() {
        check(ActivationProfileDecision.profileRequired(true, false, true), "a non-PURE Ethics invitation requires a profile");
        check(ActivationProfileDecision.profileRequired(true, false, false), "the profile stays required even if a name was stored empty-or-not");
    }

    private static void testAllowlistFollowsTheSameNonPureActivation() throws Exception {
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("juancabellog@gmail.com"), "the test address is not an institutional domain");
        check(!new EthicsTestInstitutionalEmails("").eligible("juancabellog@gmail.com"), "without the allowlist the test address never starts onboarding");
        check(!new EthicsTestInstitutionalEmails("juancabellog@gmail.com").eligible("persona@gmail.com"), "another gmail stays outside the institutional flow");
        EthicsTestInstitutionalEmails listed = new EthicsTestInstitutionalEmails("juancabellog@gmail.com");
        check(listed.eligible("JUANCABELLOG@GMAIL.COM"), "the exact test address is eligible");
        check(InstitutionalOnboarding.NEW_INSTITUTIONAL_PERSON.equals(InstitutionalOnboarding.classify(false, false)), "no account and no PURE is the new institutional person");
        check(ActivationProfileDecision.profileRequired(true, false, true), "that Ethics invitation requires a profile before activation");
        String auth = Files.readString(Path.of("src/main/java/cl/uss/ca/service/AuthService.java"));
        int preview = auth.indexOf("public ActivationPreviewDto previewToken");
        int issue = auth.indexOf("public boolean issueEthicsInvitation");
        String get = auth.substring(preview, issue);
        check(get.contains("ActivationProfileDecision.profileRequired"), "GET /auth/activate uses the same profile rule as the activation POST");
        check(get.contains("dto.ethicsInvitation = invitation.ethics"), "GET tells the screen this token belongs to Ethics");
        int activate = auth.indexOf("public AuthUserDto activate(");
        int reset = auth.indexOf("public GenericMessageDto resetPassword");
        String post = auth.substring(activate, reset);
        check(post.contains("ActivationProfileDecision.passwordAloneRejected"), "POST AUTH_PROFILE_REQUIRED uses that same decision");
    }

    private static void ethicsInvitationOpensTheEthicsApp() {
        String ethics = AccountMailer.activationUrl(
                ActivationOrigin.ETHICS_INVITE,
                "http://localhost:4200",
                "http://localhost:4500",
                "token",
                "es");
        check(ethics.startsWith("http://localhost:4500/activar-cuenta?token="), "an Ethics invitation opens the Ethics app");
        check(!ethics.contains("/auth/activate-account"), "an Ethics invitation does not return the person to OpenScience");
        String openscience = AccountMailer.activationUrl(
                ActivationOrigin.SELF_REGISTRATION,
                "http://localhost:4200",
                "http://localhost:4500",
                "token",
                "es");
        check(openscience.startsWith("http://localhost:4200/auth/activate-account?token="), "an OpenScience registration stays on OpenScience");
        String production = PublicAppUrl.ethicsBase("http://localhost:4500", "https://apps.cienciavida.org/openscience");
        check("https://apps.cienciavida.org/ethics".equals(production), "a public OpenScience host does not send a localhost Ethics link");
        String link = AccountMailer.activationUrl(ActivationOrigin.ETHICS_INVITE, "https://apps.cienciavida.org/openscience", production, "token", "es");
        check(link.startsWith("https://apps.cienciavida.org/ethics/activar-cuenta?token="), "the production invitation opens Ethics");
    }

    private static void passwordOnlyIsRejectedForLocalProfile() {
        check(ActivationProfileDecision.passwordAloneRejected(true, false, false, true), "password alone is AUTH_PROFILE_REQUIRED");
    }

    private static void completeLocalProfileIsAcceptedByTheDecision() {
        check(!ActivationProfileDecision.passwordAloneRejected(true, false, true, true), "a complete local profile can activate");
    }

    private static void pureInvitationDoesNotRequireAnEditableProfile() {
        check(!ActivationProfileDecision.profileRequired(true, true, true), "PURE stays read-only even when a name is blank");
        check(!ActivationProfileDecision.passwordAloneRejected(true, true, false, true), "PURE activation is password only");
    }

    private static void existingNamedAccountDoesNotAskAgain() {
        check(!ActivationProfileDecision.profileRequired(false, false, false), "an existing named account does not ask for a profile");
        check(!ActivationProfileDecision.passwordAloneRejected(false, false, false, false), "an existing named account is not a profile error");
    }

    private static void tokenStates() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-03T15:00:00Z");
        check(TokenPreviewStatus.classify(null, now.plusHours(1), now, "PENDING_ACTIVATION") == TokenPreviewStatus.VALID, "a fresh token is valid");
        check(TokenPreviewStatus.classify(null, now.minusMinutes(1), now, "PENDING_ACTIVATION") == TokenPreviewStatus.EXPIRED, "an expired token is rejected");
        check(TokenPreviewStatus.classify(now, now.plusHours(1), now, "PENDING_ACTIVATION") == TokenPreviewStatus.USED, "a used token is rejected");
        check(TokenPreviewStatus.classify(null, null, now, "PENDING_ACTIVATION") == TokenPreviewStatus.EXPIRED, "a token without expiry is rejected");
        check(TokenPreviewStatus.classify(null, now.plusHours(1), now, "ACTIVE") == TokenPreviewStatus.ALREADY_ACTIVE, "an active account does not go through registration again");
    }

    private static void activationPersistsLocalPersonWithoutOpenScience() throws Exception {
        String auth = Files.readString(Path.of("src/main/java/cl/uss/ca/service/AuthService.java"));
        String onboarding = Files.readString(Path.of("src/main/java/cl/uss/ca/ethics/directory/EthicsOnboardingService.java"));
        int activate = auth.indexOf("public AuthUserDto activate(");
        int reset = auth.indexOf("public GenericMessageDto resetPassword");
        String body = auth.substring(activate, reset);
        check(body.indexOf("AUTH_PROFILE_REQUIRED") < body.indexOf("accessRepository.activate("), "profile is checked before the account is activated");
        check(body.indexOf("accessRepository.activate(") < body.indexOf("markTokenUsed"), "the token is consumed after activation");
        check(!body.contains("OPENSCIENCE"), "activation does not grant OpenScience");
        int listener = onboarding.indexOf("public void onActivated");
        int welcome = onboarding.indexOf("private void welcome");
        String after = onboarding.substring(listener, welcome);
        check(after.contains("insertInstitutionalPerson(") && after.contains("null,"), "a missing PURE person is stored locally");
        check(after.contains("pure.id"), "a PURE person keeps its id");
        check(after.indexOf("ensureEthicsActive") < after.indexOf("insertInstitutionalPerson("), "Ethics access is enabled before the person row");
        check(after.contains("ensureEthicsActive") && after.contains("\"RESEARCHER\""), "Ethics becomes active and RESEARCHER is ensured");
        check(!after.contains("OPENSCIENCE") && !onboarding.contains("INSERT INTO") , "this service does not write pure_person");
        String repository = Files.readString(Path.of("src/main/java/cl/uss/ca/ethics/auth/EthicsAdminRepository.java"));
        check(repository.contains("ensurePersonKindColumn") && repository.contains("columnExists(\"person\", \"person_kind\")"), "a person table without person_kind still accepts the insert");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
