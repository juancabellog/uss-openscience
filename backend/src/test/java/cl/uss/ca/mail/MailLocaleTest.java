package cl.uss.ca.mail;

import cl.uss.ca.auth.ActivationOrigin;

public final class MailLocaleTest {
    private MailLocaleTest() {
    }

    public static void main(String[] args) {
        AccountMailCatalog.MailContent spanish = AccountMailCatalog.welcome("es", "Juan");
        AccountMailCatalog.MailContent english = AccountMailCatalog.welcome("en", "Juan");
        check(spanish.subject().contains("Acceso habilitado") && spanish.text().contains("Se ha habilitado tu acceso"), "A. es preference uses Spanish");
        check(!spanish.text().contains("Your access"), "A. Spanish body stays Spanish");
        check(english.subject().contains("Access enabled") && english.text().contains("Your access to the Project Ethics platform"), "B. en preference uses English");
        check(!english.text().contains("Se ha habilitado"), "B. English body stays English");
        check("en".equals(MailLocale.resolve("en", "es")), "C. stored preference wins over the flow locale");
        check(AccountMailCatalog.activation("en", ActivationOrigin.ETHICS_INVITE, "Ana", "https://example.test/a", "2026").subject().contains("Activate your Project Ethics"), "C. invitation follows the recipient language");
        check("en".equals(MailLocale.resolve(null, "en")), "D. a new flow without a stored preference keeps its locale");
        check("en".equals(MailLocale.resolve("  ", "en-US")), "D. a blank preference does not discard the flow locale");
        check("es".equals(MailLocale.resolve(null, null)), "E. missing preference and locale fall back to Spanish");
        check("es".equals(MailLocale.resolve("fr", null)), "E. an unsupported language falls back to Spanish");
        AccountMailCatalog.MailContent external = AccountMailCatalog.welcome("en", "External reviewer");
        check(external.subject().equals(english.subject()) && external.text().contains("Your access"), "F. an external reviewer uses preferred language, not the email domain");
        AccountMailCatalog.MailContent activation = AccountMailCatalog.activation("en", ActivationOrigin.ETHICS_INVITE, "Ana", "https://example.test/token", "2026");
        check(activation.subject().startsWith("Activate") && activation.text().contains("set your password") && activation.html().contains("Activate account"), "G. subject and body share English");
        check(!activation.html().contains("Activar cuenta") && !activation.text().contains("Estimado"), "G. English activation does not mix Spanish");
        AccountMailCatalog.MailContent reset = AccountMailCatalog.reset("es", "Ana", "https://example.test/r", "2026");
        check(reset.subject().contains("Restablecimiento") && reset.text().contains("restablecer la contraseña") && !reset.text().contains("Reset your"), "G. Spanish reset stays in one language");

        System.out.println("MailLocaleTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
