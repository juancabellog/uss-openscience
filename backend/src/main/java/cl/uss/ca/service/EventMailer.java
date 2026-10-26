package cl.uss.ca.service;

import cl.uss.ca.events.EventLanguage;
import cl.uss.ca.events.EventMailTemplates;
import cl.uss.ca.events.EventPublicLinks;
import cl.uss.ca.mail.MailMessage;
import cl.uss.ca.mail.MailService;
import cl.uss.ca.repository.EventRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.logging.Logger;

@ApplicationScoped
public class EventMailer {
    private static final Logger LOG = Logger.getLogger(EventMailer.class.getName());

    @Inject
    MailService mailService;

    @Inject
    EventPublicUrlSettings publicUrlSettings;

    @Inject
    @ConfigProperty(name = "openscience.events.challengeTtlMinutes", defaultValue = "30")
    int challengeTtlMinutes;

    public boolean configured() {
        return mailService.isConfigured() && publicUrlSettings.resolve() != null;
    }

    public String publicBase() {
        return publicUrlSettings.resolve();
    }

    public void sendVerification(String email, EventRecord event, String rawToken) {
        sendVerification(email, event, rawToken, EventLanguage.ES_CL);
    }

    public void sendVerification(String email, EventRecord event, String rawToken, String language) {
        String base = publicUrlSettings.requireMailableBase();
        String url = EventPublicLinks.verificationUrl(base, event.publicSlug, rawToken);
        EventMailTemplates.MailContent content = EventMailTemplates.verification(
                language,
                localizedTitle(event, language),
                url,
                Math.max(5, challengeTtlMinutes)
        );
        LOG.info("Sending event verification mail eventId=" + event.id + " locale=" + EventLanguage.localeTag(language));
        mailService.send(MailMessage.of(email, content.subject(), content.text(), content.html()));
    }

    public void sendConfirmation(String email, EventRecord event, String displayName, String publicCode) {
        sendConfirmation(email, event, displayName, publicCode, EventLanguage.ES_CL);
    }

    public void sendConfirmation(String email, EventRecord event, String displayName, String publicCode, String language) {
        String base = publicUrlSettings.requireMailableBase();
        String url = EventPublicLinks.confirmationUrl(base, event.publicSlug);
        String name = displayName == null || displayName.isBlank() ? email : displayName;
        EventMailTemplates.MailContent content = EventMailTemplates.confirmation(
                language,
                name,
                localizedTitle(event, language),
                publicCode,
                url
        );
        LOG.info("Sending event confirmation mail eventId=" + event.id + " locale=" + EventLanguage.localeTag(language));
        mailService.send(MailMessage.of(email, content.subject(), content.text(), content.html()));
    }

    public void warnIfLocal() {
        String base = publicBase();
        if (EventPublicLinks.looksDevelopment(base)) {
            LOG.warning("Event registration links will use a local URL. Set EVENTS_PUBLIC_URL or EVENTS_PUBLIC_BASE_URL.");
        }
    }

    private static String localizedTitle(EventRecord event, String language) {
        if (EventLanguage.isEnglish(language) && event.titleEn != null && !event.titleEn.isBlank()) {
            return event.titleEn;
        }
        return event.title;
    }
}
