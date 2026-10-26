package cl.uss.ca.service;

import cl.uss.ca.api.dto.DirectoryPersonSummaryDto;
import cl.uss.ca.api.dto.EventAdminRegistrationDto;
import cl.uss.ca.api.dto.EventEmailRequestDto;
import cl.uss.ca.api.dto.EventRegistrationConfirmDto;
import cl.uss.ca.api.dto.EventRegistrationProfileDto;
import cl.uss.ca.api.dto.EventRegistrationReceiptDto;
import cl.uss.ca.api.dto.EventVerificationPreviewDto;
import cl.uss.ca.api.dto.GenericMessageDto;
import cl.uss.ca.security.CurrentUser;
import cl.uss.ca.auth.AuthRateLimiter;
import cl.uss.ca.auth.EmailNormalizer;
import cl.uss.ca.auth.SecureTokens;
import cl.uss.ca.directory.DirectoryEmail;
import cl.uss.ca.events.EventAffiliationOrigin;
import cl.uss.ca.events.EventChallengeState;
import cl.uss.ca.events.EventLanguage;
import cl.uss.ca.events.EventMailTemplates;
import cl.uss.ca.events.EventDateTimes;
import cl.uss.ca.events.EventPublicationStatus;
import cl.uss.ca.events.EventRegistrationPolicy;
import cl.uss.ca.events.EventRegistrationStatus;
import cl.uss.ca.events.EventRules;
import cl.uss.ca.exception.ConflictException;
import cl.uss.ca.exception.DatabaseException;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.UnauthorizedException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.DirectoryRepository;
import cl.uss.ca.repository.EventRecord;
import cl.uss.ca.repository.EventRepository;
import cl.uss.ca.repository.EventSignupRecord;
import cl.uss.ca.repository.EventSignupRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class EventRegistrationService {
    public static final String COOKIE = "OSE_VERIFIED";
    private static final Logger LOG = Logger.getLogger(EventRegistrationService.class.getName());

    @Inject
    EventRepository eventRepository;

    @Inject
    EventSignupRepository signupRepository;

    @Inject
    DirectoryRepository directoryRepository;

    @Inject
    EventMailer eventMailer;

    @Inject
    EventService eventService;

    @Inject
    AuthRateLimiter rateLimiter;

    @Inject
    @ConfigProperty(name = "openscience.events.challengeTtlMinutes", defaultValue = "30")
    int challengeTtlMinutes;

    @Inject
    @ConfigProperty(name = "openscience.events.sessionTtlMinutes", defaultValue = "120")
    int sessionTtlMinutes;

    @Inject
    @ConfigProperty(name = "openscience.events.rateLimit.email", defaultValue = "5")
    int emailRateLimit;

    @Inject
    @ConfigProperty(name = "openscience.events.rateLimit.ip", defaultValue = "20")
    int ipRateLimit;

    public GenericMessageDto request(String slug, EventEmailRequestDto body, String clientKey) {
        eventService.requireTables();
        EventRecord event = published(slug);
        String language = EventLanguage.fromRequest(
                body == null ? null : body.locale,
                body == null ? null : body.language
        );
        String email = requireEmail(body == null ? null : body.email);
        assertRegistrationOpen(event);
        throttle("events-reg-email:" + event.id + ":" + email, emailRateLimit);
        throttle("events-reg-ip:" + String.valueOf(clientKey), ipRateLimit);
        String raw = SecureTokens.rawToken();
        LocalDateTime now = LocalDateTime.now();
        signupRepository.insertChallenge(
                event.id,
                email,
                SecureTokens.hash(raw),
                now.plusMinutes(Math.max(5, challengeTtlMinutes))
        );
        boolean emailSent = false;
        if (eventMailer.configured()) {
            eventMailer.warnIfLocal();
            try {
                eventMailer.sendVerification(email, event, raw, language);
                emailSent = true;
            } catch (ValidationException ex) {
                if ("EVENT_PUBLIC_URL_MISSING".equals(ex.getCode()) || "EVENT_PUBLIC_URL_LOCAL".equals(ex.getCode())) {
                    LOG.severe(ex.getMessage());
                    throw new ValidationException("EVENT_MAIL_FAILED", "No fue posible enviar el enlace de verificación");
                }
                throw ex;
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Event verification mail failed", ex);
                throw new ValidationException("EVENT_MAIL_FAILED", "No fue posible enviar el enlace de verificación");
            }
        } else {
            LOG.warning("Event verification mail skipped: SMTP is not configured");
        }
        return GenericMessageDto.of("EVENT_VERIFICATION_SENT", EventMailTemplates.verificationSent(language), emailSent);
    }

    public EventVerificationPreviewDto inspect(String slug, String rawToken) {
        eventService.requireTables();
        EventRecord event = published(slug);
        EventSignupRepository.Challenge challenge = lookupChallenge(rawToken);
        EventChallengeState state = classify(challenge, event.id);
        EventVerificationPreviewDto dto = new EventVerificationPreviewDto();
        dto.status = state.name();
        dto.usable = state == EventChallengeState.USABLE;
        if (challenge != null && state == EventChallengeState.USABLE) {
            dto.expiresAt = EventDateTimes.format(challenge.expiresAt());
        }
        if (!dto.usable) {
            throw new ValidationException(state.errorCode(), state.errorMessage());
        }
        return dto;
    }

    public EventRegistrationProfileDto consume(String slug, String rawToken) {
        eventService.requireTables();
        EventRecord event = published(slug);
        EventSignupRepository.Challenge challenge = lookupChallenge(rawToken);
        EventChallengeState state = classify(challenge, event.id);
        if (state != EventChallengeState.USABLE || challenge == null) {
            throw new ValidationException(state.errorCode(), state.errorMessage());
        }
        LocalDateTime now = LocalDateTime.now();
        if (!signupRepository.consumeChallenge(challenge.id(), now)) {
            throw new ValidationException(
                    EventChallengeState.USED.errorCode(),
                    EventChallengeState.USED.errorMessage()
            );
        }
        String sessionRaw = SecureTokens.rawToken();
        signupRepository.insertSession(
                event.id,
                challenge.emailNormalized(),
                SecureTokens.hash(sessionRaw),
                now.plusMinutes(Math.max(15, sessionTtlMinutes))
        );
        EventRegistrationProfileDto dto = profile(event, challenge.emailNormalized());
        dto.verified = true;
        dto.sessionToken = sessionRaw;
        return dto;
    }

    public EventRegistrationProfileDto profile(String slug, String rawSession) {
        eventService.requireTables();
        EventRecord event = published(slug);
        EventSignupRepository.VerifiedSession session = requireSession(event.id, rawSession);
        return profile(event, session.emailNormalized());
    }

    public EventRegistrationReceiptDto confirm(String slug, String rawSession, EventRegistrationConfirmDto body) {
        eventService.requireTables();
        EventRecord event = published(slug);
        EventSignupRepository.VerifiedSession session = requireSession(event.id, rawSession);
        String language = EventLanguage.fromRequest(
                body == null ? null : body.locale,
                body == null ? null : body.language
        );
        assertRegistrationOpen(event);
        DirectoryPersonSummaryDto directory = directoryRepository.findActiveByNormalizedEmail(session.emailNormalized());
        boolean matched = directory != null;
        String displayName;
        String faculty;
        String campus;
        String position;
        String origin = EventAffiliationOrigin.fromDirectoryMatch(matched).name();
        String directoryUuid = null;
        if (matched) {
            displayName = EventRules.trimToNull(directory.displayName);
            faculty = EventRules.trimToNull(directory.faculty);
            campus = EventRules.trimToNull(directory.campusName);
            position = EventRules.trimToNull(directory.positionTitle);
            directoryUuid = directory.uuid;
        } else {
            displayName = EventRules.optionalText(body == null ? null : body.displayName, 512, "EVENT_NAME_TOO_LONG");
            faculty = EventRules.optionalText(body == null ? null : body.facultyOrInstitution, 255, "EVENT_FACULTY_TOO_LONG");
            campus = EventRules.optionalText(body == null ? null : body.campusName, 128, "EVENT_CAMPUS_TOO_LONG");
            position = EventRules.optionalText(body == null ? null : body.positionTitle, 255, "EVENT_POSITION_TOO_LONG");
            if (displayName == null || faculty == null || position == null) {
                throw new ValidationException(
                        "EVENT_PROFILE_REQUIRED",
                        "Indique nombre completo, facultad o institución y cargo para completar la inscripción"
                );
            }
        }
        Connection connection = eventRepository.openTransaction();
        boolean previous = false;
        EventSignupRecord stored;
        try {
            previous = connection.getAutoCommit();
            EventRecord locked = eventRepository.lockById(connection, event.id);
            if (locked == null) {
                throw new NotFoundException("EVENT_NOT_FOUND", "Published event not found");
            }
            EventSignupRecord existing = signupRepository.findRegistration(connection, event.id, session.emailNormalized());
            if (existing != null && EventRegistrationStatus.CONFIRMED.name().equals(existing.status)) {
                throw new ConflictException("EVENT_ALREADY_REGISTERED", "Este correo ya está inscrito en el evento");
            }
            int confirmed = eventRepository.countConfirmed(connection, event.id);
            if (!EventRegistrationPolicy.hasCapacity(locked.capacity, confirmed)) {
                throw new ConflictException("EVENT_FULL", "El evento no tiene cupos disponibles");
            }
            long participantId = signupRepository.upsertParticipant(
                    connection,
                    session.emailNormalized(),
                    displayName,
                    faculty,
                    campus,
                    position,
                    origin,
                    directoryUuid
            );
            EventSignupRecord record = new EventSignupRecord();
            record.eventId = event.id;
            record.participantId = participantId;
            record.emailNormalized = session.emailNormalized();
            record.displayName = displayName;
            record.facultyOrInstitution = faculty;
            record.campusName = campus;
            record.positionTitle = position;
            record.affiliationOrigin = origin;
            record.directoryPersonUuid = directoryUuid;
            record.status = EventRegistrationStatus.CONFIRMED.name();
            record.snapshotJson = snapshotJson(session.emailNormalized(), displayName, faculty, campus, position, origin, directoryUuid, language);
            if (existing == null) {
                record.seqNo = signupRepository.nextSeq(connection, event.id);
                record.publicCode = publicCode(event.id, record.seqNo);
                record.id = signupRepository.insertRegistration(connection, record);
                signupRepository.insertHistory(connection, record.id, null, record.status, session.emailNormalized(), "CONFIRM");
            } else {
                record.id = existing.id;
                record.seqNo = existing.seqNo;
                record.publicCode = existing.publicCode;
                signupRepository.reactivateRegistration(connection, record);
                signupRepository.insertHistory(
                        connection,
                        record.id,
                        existing.status,
                        record.status,
                        session.emailNormalized(),
                        "RECONFIRM"
                );
            }
            connection.commit();
            stored = record;
        } catch (SQLException ex) {
            EventRepository.rollback(connection);
            throw new DatabaseException("Failed to confirm registration", ex);
        } catch (RuntimeException ex) {
            EventRepository.rollback(connection);
            throw ex;
        } finally {
            EventRepository.restore(connection, previous);
            EventRepository.close(connection);
        }
        try {
            if (eventMailer.configured()) {
                eventMailer.sendConfirmation(session.emailNormalized(), event, displayName, stored.publicCode, language);
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Event confirmation mail failed", ex);
        }
        EventRegistrationReceiptDto receipt = new EventRegistrationReceiptDto();
        receipt.publicCode = stored.publicCode;
        receipt.eventTitle = EventLanguage.isEnglish(language) && event.titleEn != null && !event.titleEn.isBlank()
                ? event.titleEn
                : event.title;
        receipt.email = session.emailNormalized();
        receipt.displayName = displayName;
        receipt.affiliationOrigin = origin;
        receipt.directoryMatch = matched;
        receipt.message = EventMailTemplates.registrationConfirmed(language);
        return receipt;
    }

    public EventRegistrationReceiptDto adminRegister(long eventId, EventAdminRegistrationDto body, CurrentUser user) {
        eventService.requireTables();
        EventRecord event = eventRepository.requireById(eventId);
        if (EventPublicationStatus.CANCELLED.name().equals(event.publicationStatus)) {
            throw new ConflictException("EVENT_CANCELLED", "No se inscribe en un evento cancelado");
        }
        String email = requireEmail(body == null ? null : body.email);
        DirectoryPersonSummaryDto directory = directoryRepository.findActiveByNormalizedEmail(email);
        boolean matched = directory != null;
        String displayName;
        String faculty;
        String campus;
        String position;
        String origin = EventAffiliationOrigin.fromDirectoryMatch(matched).name();
        String directoryUuid = null;
        if (matched) {
            displayName = EventRules.trimToNull(directory.displayName);
            faculty = EventRules.trimToNull(directory.faculty);
            campus = EventRules.trimToNull(directory.campusName);
            position = EventRules.trimToNull(directory.positionTitle);
            directoryUuid = directory.uuid;
        } else {
            displayName = EventRules.optionalText(body == null ? null : body.displayName, 512, "EVENT_NAME_TOO_LONG");
            faculty = EventRules.optionalText(body == null ? null : body.facultyOrInstitution, 255, "EVENT_FACULTY_TOO_LONG");
            campus = EventRules.optionalText(body == null ? null : body.campusName, 128, "EVENT_CAMPUS_TOO_LONG");
            position = EventRules.optionalText(body == null ? null : body.positionTitle, 255, "EVENT_POSITION_TOO_LONG");
            if (displayName == null || faculty == null || position == null) {
                throw new ValidationException(
                        "EVENT_PROFILE_REQUIRED",
                        "Indique nombre completo, facultad o institución y cargo para completar la inscripción"
                );
            }
        }
        boolean walkIn = body != null && body.walkIn;
        Connection connection = eventRepository.openTransaction();
        boolean previous = false;
        EventSignupRecord stored;
        try {
            previous = connection.getAutoCommit();
            EventRecord locked = eventRepository.lockById(connection, event.id);
            if (locked == null) {
                throw new NotFoundException("EVENT_NOT_FOUND", "Event not found");
            }
            EventSignupRecord existing = signupRepository.findRegistration(connection, event.id, email);
            if (existing != null && EventRegistrationStatus.CONFIRMED.name().equals(existing.status)) {
                throw new ConflictException("EVENT_ALREADY_REGISTERED", "Este correo ya está inscrito en el evento");
            }
            int confirmed = eventRepository.countConfirmed(connection, event.id);
            if (!walkIn && !EventRegistrationPolicy.hasCapacity(locked.capacity, confirmed)) {
                throw new ConflictException("EVENT_FULL", "El evento no tiene cupos disponibles");
            }
            long participantId = signupRepository.upsertParticipant(
                    connection,
                    email,
                    displayName,
                    faculty,
                    campus,
                    position,
                    origin,
                    directoryUuid
            );
            EventSignupRecord record = new EventSignupRecord();
            record.eventId = event.id;
            record.participantId = participantId;
            record.emailNormalized = email;
            record.displayName = displayName;
            record.facultyOrInstitution = faculty;
            record.campusName = campus;
            record.positionTitle = position;
            record.affiliationOrigin = origin;
            record.directoryPersonUuid = directoryUuid;
            record.status = EventRegistrationStatus.CONFIRMED.name();
            record.snapshotJson = snapshotJson(email, displayName, faculty, campus, position, origin, directoryUuid, EventLanguage.ES);
            if (existing == null) {
                record.seqNo = signupRepository.nextSeq(connection, event.id);
                record.publicCode = publicCode(event.id, record.seqNo);
                record.id = signupRepository.insertRegistration(connection, record);
                signupRepository.insertHistory(connection, record.id, null, record.status, actor(user), walkIn ? "WALK_IN" : "ADMIN");
            } else {
                record.id = existing.id;
                record.seqNo = existing.seqNo;
                record.publicCode = existing.publicCode;
                signupRepository.reactivateRegistration(connection, record);
                signupRepository.insertHistory(connection, record.id, existing.status, record.status, actor(user), walkIn ? "WALK_IN" : "ADMIN");
            }
            connection.commit();
            stored = record;
        } catch (SQLException ex) {
            EventRepository.rollback(connection);
            throw new DatabaseException("Failed to confirm registration", ex);
        } catch (RuntimeException ex) {
            EventRepository.rollback(connection);
            throw ex;
        } finally {
            EventRepository.restore(connection, previous);
            EventRepository.close(connection);
        }
        EventRegistrationReceiptDto receipt = new EventRegistrationReceiptDto();
        receipt.publicCode = stored.publicCode;
        receipt.eventTitle = event.title;
        receipt.email = email;
        receipt.displayName = displayName;
        receipt.affiliationOrigin = origin;
        receipt.directoryMatch = matched;
        receipt.message = walkIn
                ? "Persona registrada como asistencia espontánea."
                : "Inscripción agregada por administración.";
        return receipt;
    }

    private static String actor(CurrentUser user) {
        return user == null ? null : user.email;
    }

    private EventRegistrationProfileDto profile(EventRecord event, String email) {
        EventRegistrationProfileDto dto = new EventRegistrationProfileDto();
        dto.email = email;
        dto.verified = true;
        DirectoryPersonSummaryDto directory = directoryRepository.findActiveByNormalizedEmail(email);
        dto.directoryMatch = directory != null;
        if (directory != null) {
            dto.displayName = directory.displayName;
            dto.facultyOrInstitution = directory.faculty;
            dto.campusName = directory.campusName;
            dto.positionTitle = directory.positionTitle;
            dto.affiliationOrigin = EventAffiliationOrigin.DIRECTORY_MATCH.name();
        } else {
            dto.affiliationOrigin = EventAffiliationOrigin.SELF_DECLARED.name();
        }
        EventSignupRecord existing = signupRepository.findRegistration(event.id, email);
        dto.alreadyRegistered = existing != null && EventRegistrationStatus.CONFIRMED.name().equals(existing.status);
        LocalDateTime now = LocalDateTime.now();
        dto.registrationOpen = EventRegistrationPolicy.isOpen(
                EventPublicationStatus.parse(event.publicationStatus),
                now,
                event.registrationOpensAt,
                event.registrationClosesAt
        );
        dto.remainingCapacity = EventRegistrationPolicy.remainingCapacity(event.capacity, event.confirmedCount);
        return dto;
    }

    private EventSignupRepository.Challenge lookupChallenge(String rawToken) {
        String hash = SecureTokens.hash(rawToken);
        if (hash == null) {
            return null;
        }
        return signupRepository.findChallenge(hash);
    }

    private static EventChallengeState classify(EventSignupRepository.Challenge challenge, long eventId) {
        if (challenge == null) {
            return EventChallengeState.INVALID;
        }
        return EventChallengeState.classify(
                challenge.eventId(),
                eventId,
                challenge.expiresAt(),
                challenge.consumedAt(),
                LocalDateTime.now()
        );
    }

    private EventRecord published(String slug) {
        EventRecord record = eventRepository.findPublishedBySlug(slug);
        if (record == null) {
            throw new NotFoundException("EVENT_NOT_FOUND", "Published event not found");
        }
        return record;
    }

    private EventSignupRepository.VerifiedSession requireSession(long eventId, String rawSession) {
        EventSignupRepository.VerifiedSession session = signupRepository.findSession(
                SecureTokens.hash(rawSession),
                LocalDateTime.now()
        );
        if (session == null || session.eventId() != eventId) {
            throw new UnauthorizedException("EVENT_SESSION_REQUIRED", "Verifique su correo para continuar la inscripción");
        }
        return session;
    }

    private void assertRegistrationOpen(EventRecord event) {
        if (!EventRegistrationPolicy.isOpen(
                EventPublicationStatus.parse(event.publicationStatus),
                LocalDateTime.now(),
                event.registrationOpensAt,
                event.registrationClosesAt
        )) {
            throw new ConflictException("EVENT_REGISTRATION_CLOSED", "La inscripción de este evento no está abierta");
        }
        if (!EventRegistrationPolicy.hasCapacity(event.capacity, event.confirmedCount)) {
            throw new ConflictException("EVENT_FULL", "El evento no tiene cupos disponibles");
        }
    }

    private void throttle(String key, int max) {
        if (!rateLimiter.allow(key, max, 15 * 60 * 1000L)) {
            throw new ValidationException("EVENT_RATE_LIMITED", "Hay demasiados intentos. Espere unos minutos e inténtelo de nuevo");
        }
    }

    private static String requireEmail(String raw) {
        String email = DirectoryEmail.normalize(raw);
        if (email == null) {
            email = EmailNormalizer.normalize(raw);
        }
        if (!DirectoryEmail.isUsable(email) && !EmailNormalizer.looksLikeEmail(raw)) {
            throw new ValidationException("EVENT_EMAIL_INVALID", "Indique un correo electrónico válido");
        }
        if (email == null || !email.contains("@")) {
            throw new ValidationException("EVENT_EMAIL_INVALID", "Indique un correo electrónico válido");
        }
        return email;
    }

    private static String publicCode(long eventId, int seq) {
        String suffix = HexFormat.of().formatHex(new byte[] {
                (byte) ThreadLocalRandom.current().nextInt(256),
                (byte) ThreadLocalRandom.current().nextInt(256)
        }).toUpperCase(Locale.ROOT);
        return "EV" + eventId + "-" + seq + "-" + suffix;
    }

    private static String snapshotJson(
            String email,
            String displayName,
            String faculty,
            String campus,
            String position,
            String origin,
            String directoryUuid,
            String preferredLanguage
    ) {
        return """
                {"email":%s,"displayName":%s,"facultyOrInstitution":%s,"campusName":%s,"positionTitle":%s,"affiliationOrigin":%s,"directoryPersonUuid":%s,"preferredLanguage":%s,"capturedAt":%s}
                """.formatted(
                quote(email),
                quote(displayName),
                quote(faculty),
                quote(campus),
                quote(position),
                quote(origin),
                quote(directoryUuid),
                quote(EventLanguage.localeTag(preferredLanguage)),
                quote(EventDateTimes.format(LocalDateTime.now()))
        ).trim();
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
