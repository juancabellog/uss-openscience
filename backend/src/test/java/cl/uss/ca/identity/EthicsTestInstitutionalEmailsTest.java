package cl.uss.ca.identity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class EthicsTestInstitutionalEmailsTest {
    private EthicsTestInstitutionalEmailsTest() {
    }

    public static void main(String[] args) throws Exception {
        check(InstitutionalDomains.isAllowedInstitutionalEmail("persona@uss.cl"), "a USS domain stays allowed");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("juancabellog@gmail.com"), "gmail is not an institutional domain");
        check(!InstitutionalDomains.isAllowedInstitutionalEmail("otro@gmail.com"), "another gmail is not an institutional domain");

        EthicsTestInstitutionalEmails empty = new EthicsTestInstitutionalEmails("");
        check(!empty.contains("juancabellog@gmail.com"), "an empty list rejects the test address");
        check(!empty.eligible("juancabellog@gmail.com"), "an empty list does not open the institutional flow");
        check(empty.eligible("persona@uss.cl"), "an empty list still accepts USS");

        EthicsTestInstitutionalEmails listed = new EthicsTestInstitutionalEmails(" JuanCabelloG@gmail.com ");
        check(listed.contains("juancabellog@gmail.com"), "the configured address matches ignoring case and spaces");
        check(listed.eligible("juancabellog@gmail.com"), "the configured address can use the non-PURE institutional flow");
        check(!listed.contains("otro@gmail.com"), "another gmail address stays out");
        check(!listed.eligible("otro@gmail.com"), "another gmail address is not eligible");

        Set<String> rejected = EthicsTestInstitutionalEmails.parse("@gmail.com,*@gmail.com,gmail.com,juancabellog@gmail.com");
        check(rejected.equals(Set.of("juancabellog@gmail.com")), "domains and wildcards are ignored");
        check(!listed.contains("juancabellog+test@gmail.com"), "a plus address is a different email");
        check(InstitutionalOnboarding.NEW_INSTITUTIONAL_PERSON.equals(InstitutionalOnboarding.classify(false, false)), "an eligible person missing from PURE uses the same new institutional flow");

        String onboarding = Files.readString(Path.of("src/main/java/cl/uss/ca/ethics/directory/EthicsOnboardingService.java"));
        check(onboarding.contains("admit(email)"), "public start and invite share one eligibility check");
        check(onboarding.contains("INVITE_RESENT"), "a pending invitation is reported apart from a new one");
        String client = Files.readString(Path.of("../projects/ethics-uss/src/app/core/auth/shared-session-sign-in.ts"));
        int start = client.indexOf("async startInstitutional");
        int register = client.indexOf("async registerInstitutional");
        String body = client.substring(start, register);
        check(!body.contains("isAllowedInstitutionalEmail"), "create account asks the backend before rejecting a domain");
        check(body.contains("ethicsStartOutcome"), "the institutional message comes from the backend code");
        System.out.println("EthicsTestInstitutionalEmailsTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
