package cl.uss.ca.service;

import cl.uss.ca.api.dto.EventAdminDto;
import cl.uss.ca.api.dto.EventAdminListDto;
import cl.uss.ca.api.dto.EventPublicDetailDto;
import cl.uss.ca.api.dto.EventRegistrationDto;
import cl.uss.ca.api.dto.EventRegistrationListDto;
import cl.uss.ca.api.dto.EventOrganizingUnitDto;
import cl.uss.ca.api.dto.EventWriteDto;
import cl.uss.ca.api.dto.EventsCatalogDto;
import cl.uss.ca.api.dto.EventsModuleStatusDto;
import cl.uss.ca.events.EventDateTimes;
import cl.uss.ca.events.EventLanguage;
import cl.uss.ca.events.EventMapper;
import cl.uss.ca.events.EventModality;
import cl.uss.ca.events.EventPublicationStatus;
import cl.uss.ca.events.EventPublicAccess;
import cl.uss.ca.events.EventOrganizingUnits;
import cl.uss.ca.events.EventRules;
import cl.uss.ca.events.EventSlug;
import cl.uss.ca.exception.NotFoundException;
import cl.uss.ca.exception.ValidationException;
import cl.uss.ca.repository.DirectoryRepository;
import cl.uss.ca.repository.EventOrganizingUnitRepository;
import cl.uss.ca.repository.EventRecord;
import cl.uss.ca.repository.EventRepository;
import cl.uss.ca.repository.EventSignupRecord;
import cl.uss.ca.repository.EventSignupRepository;
import cl.uss.ca.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class EventService {

    @Inject
    EventRepository eventRepository;

    @Inject
    EventSignupRepository signupRepository;

    @Inject
    DirectoryRepository directoryRepository;

    @Inject
    EventMailer eventMailer;

    @Inject
    EventOrganizingUnitRepository organizingUnitRepository;

    public EventsModuleStatusDto status() {
        EventsModuleStatusDto dto = new EventsModuleStatusDto();
        dto.tablesAvailable = eventRepository.available();
        dto.directoryAvailable = directoryRepository.available();
        dto.publishedCount = eventRepository.countPublished();
        if (!dto.tablesAvailable) {
            dto.message = "Events tables are not installed yet. Directory remains the shared people catalog.";
        } else if (!dto.directoryAvailable) {
            dto.message = "Events tables are present. USS directory is not available.";
        } else {
            dto.message = "Events module is connected to uss-ca and the shared USS directory.";
        }
        return dto;
    }

    public EventsCatalogDto catalog() {
        EventsCatalogDto dto = new EventsCatalogDto();
        dto.module = status();
        LocalDateTime now = LocalDateTime.now();
        for (EventRecord record : eventRepository.findPublished()) {
            dto.items.add(EventMapper.summary(record, now));
        }
        return dto;
    }

    public EventPublicDetailDto getPublished(String slug) {
        requireTables();
        EventRecord record = eventRepository.findBySlug(slug);
        EventPublicAccess.requireVisible(record);
        return EventMapper.detail(record, eventMailer.publicBase(), LocalDateTime.now());
    }

    public EventAdminListDto listAdmin(String status, String type, String query) {
        requireTables();
        EventAdminListDto dto = new EventAdminListDto();
        LocalDateTime now = LocalDateTime.now();
        String publicBase = eventMailer.publicBase();
        List<EventRecord> records = eventRepository.listAdmin(status, type, query);
        for (EventRecord record : records) {
            dto.items.add(EventMapper.admin(record, publicBase, now));
        }
        dto.total = dto.items.size();
        return dto;
    }

    public EventAdminDto getAdmin(long id) {
        requireTables();
        return EventMapper.admin(eventRepository.requireById(id), eventMailer.publicBase(), LocalDateTime.now());
    }

    public EventAdminDto create(EventWriteDto body, CurrentUser user) {
        requireTables();
        EventRecord record = fromWrite(body, null);
        record.createdBy = user == null ? null : user.email;
        long id = eventRepository.insert(record);
        return getAdmin(id);
    }

    public EventAdminDto update(long id, EventWriteDto body) {
        requireTables();
        EventRecord current = eventRepository.requireById(id);
        EventRecord record = fromWrite(body, current);
        record.id = id;
        eventRepository.update(record);
        return getAdmin(id);
    }

    public EventAdminDto publish(long id) {
        requireTables();
        EventRecord current = eventRepository.requireById(id);
        EventWriteDto body = toWrite(current);
        EventRules.throwFirst(body, true);
        current.publicationStatus = EventPublicationStatus.PUBLISHED.name();
        eventRepository.update(current);
        return getAdmin(id);
    }

    public EventAdminDto unpublish(long id) {
        requireTables();
        EventRecord current = eventRepository.requireById(id);
        if (EventPublicationStatus.FINISHED.name().equals(current.publicationStatus)
                || EventPublicationStatus.CANCELLED.name().equals(current.publicationStatus)) {
            throw new ValidationException("EVENT_STATUS_LOCKED", "Un evento finalizado o cancelado no se despublica; edite el estado");
        }
        current.publicationStatus = EventPublicationStatus.DRAFT.name();
        eventRepository.update(current);
        return getAdmin(id);
    }

    public EventAdminDto finish(long id, CurrentUser user) {
        requireTables();
        EventRecord current = eventRepository.requireById(id);
        current.publicationStatus = EventPublicationStatus.FINISHED.name();
        eventRepository.update(current);
        return getAdmin(id);
    }

    public EventAdminDto cancel(long id, CurrentUser user) {
        requireTables();
        EventRecord current = eventRepository.requireById(id);
        current.publicationStatus = EventPublicationStatus.CANCELLED.name();
        eventRepository.update(current);
        return getAdmin(id);
    }

    public EventRegistrationListDto registrations(long eventId) {
        requireTables();
        EventRecord event = eventRepository.requireById(eventId);
        EventRegistrationListDto dto = new EventRegistrationListDto();
        dto.eventId = event.id;
        dto.title = event.title;
        dto.capacity = event.capacity;
        dto.confirmedCount = event.confirmedCount;
        for (EventSignupRecord record : signupRepository.listByEvent(eventId)) {
            dto.items.add(EventMapper.registration(record));
        }
        return dto;
    }

    public void requireTables() {
        if (!eventRepository.available()) {
            throw new ValidationException(
                    "EVENTS_TABLES_MISSING",
                    "Las tablas de Events aún no están instaladas. Ejecute backend/sql/proposed-events-phase1-2.sql"
            );
        }
    }

    private EventRecord fromWrite(EventWriteDto body, EventRecord current) {
        if (body == null) {
            throw new ValidationException("EVENT_REQUIRED", "El evento es obligatorio");
        }
        EventPublicationStatus parsed = current == null
                ? EventPublicationStatus.DRAFT
                : EventPublicationStatus.parse(current.publicationStatus);
        EventPublicationStatus currentStatus = parsed == null ? EventPublicationStatus.DRAFT : parsed;
        EventPublicationStatus status = EventRules.parseStatus(body.publicationStatus, currentStatus);
        if (currentStatus == EventPublicationStatus.FINISHED || currentStatus == EventPublicationStatus.CANCELLED) {
            status = currentStatus;
        }
        boolean publish = status == EventPublicationStatus.PUBLISHED;
        EventRules.throwFirst(body, publish);
        EventRecord record = current == null ? new EventRecord() : current;
        record.title = EventRules.requireTitle(body.title);
        record.titleEn = EventRules.optionalText(body.titleEn, EventRules.TITLE_MAX, "EVENT_TITLE_TOO_LONG");
        record.description = EventRules.optionalText(body.description, EventRules.DESCRIPTION_MAX, "EVENT_DESCRIPTION_TOO_LONG");
        record.descriptionEn = EventRules.optionalText(body.descriptionEn, EventRules.DESCRIPTION_MAX, "EVENT_DESCRIPTION_TOO_LONG");
        record.primaryLanguage = EventLanguage.normalize(body.primaryLanguage);
        record.eventType = EventRules.optionalType(body.eventType).name();
        record.startsAt = EventDateTimes.parse(body.startsAt);
        record.endsAt = EventDateTimes.parse(body.endsAt);
        record.timezone = EventRules.timezone(body.timezone);
        EventModality modality = EventRules.optionalModality(body.modality);
        record.modality = modality == null ? null : modality.name();
        record.locationName = EventRules.optionalText(body.locationName, EventRules.LOCATION_MAX, "EVENT_LOCATION_TOO_LONG");
        record.campusName = EventRules.optionalText(body.campusName, EventRules.CAMPUS_MAX, "EVENT_CAMPUS_TOO_LONG");
        record.virtualAccess = EventRules.optionalText(body.virtualAccess, EventRules.VIRTUAL_ACCESS_MAX, "EVENT_VIRTUAL_ACCESS_TOO_LONG");
        record.allowRegistrationDuringEvent = body.allowRegistrationDuringEvent;
        if (!eventRepository.hasVirtualAccessColumn() && (modality == EventModality.VIRTUAL || modality == EventModality.HYBRID)) {
            record.locationName = EventRules.encodeVirtualFallback(record.locationName, record.virtualAccess);
        }
        EventOrganizingUnits.Resolved unit = EventOrganizingUnits.resolve(body.organizingUnitCode, body.organizingUnit);
        if (unit.invalid) {
            throw new ValidationException("EVENT_UNIT_INVALID", "La unidad organizadora no es válida");
        }
        record.organizingUnitCode = unit.code;
        record.organizingUnit = EventRules.optionalText(unit.storedText, EventRules.UNIT_MAX, "EVENT_UNIT_TOO_LONG");
        record.publicationStatus = status.name();
        record.registrationOpensAt = EventDateTimes.parse(body.registrationOpensAt);
        record.registrationClosesAt = EventDateTimes.parse(body.registrationClosesAt);
        EventRules.validateWindow(
                record.startsAt,
                record.endsAt,
                record.registrationOpensAt,
                record.registrationClosesAt,
                record.allowRegistrationDuringEvent,
                record.timezone
        );
        record.capacity = EventRules.optionalCapacity(body.capacity);
        record.blankSignatureRows = EventRules.blankRows(body.blankSignatureRows);
        record.publicSlug = uniqueSlug(
                body.publicSlug,
                record.title,
                current == null ? null : current.id,
                current == null ? null : current.publicSlug
        );
        return record;
    }

    public List<EventOrganizingUnitDto> organizingUnits() {
        List<EventOrganizingUnitDto> items = new java.util.ArrayList<>();
        for (EventOrganizingUnits.Unit unit : organizingUnitRepository.listActive()) {
            EventOrganizingUnitDto dto = new EventOrganizingUnitDto();
            dto.code = unit.code;
            dto.nameEs = unit.nameEs;
            dto.nameEn = unit.nameEn;
            dto.other = unit.isOther();
            dto.active = unit.active;
            dto.sortOrder = unit.sortOrder;
            items.add(dto);
        }
        return items;
    }

    public List<String> campusSuggestions() {
        if (!directoryRepository.available()) {
            return List.of();
        }
        return directoryRepository.listCampusNames();
    }

    private String uniqueSlug(String requested, String title, Long excludeId, String existing) {
        if ((requested == null || requested.isBlank()) && existing != null && !existing.isBlank()) {
            return existing;
        }
        String sanitized = EventSlug.sanitize(requested);
        if (requested != null && !requested.isBlank() && EventSlug.isValid(sanitized)) {
            if (eventRepository.slugTaken(sanitized, excludeId)) {
                throw new ValidationException("EVENT_SLUG_TAKEN", "La URL pública ya está en uso");
            }
            return sanitized;
        }
        String base = EventSlug.fromTitle(title);
        for (int attempt = 1; attempt <= 50; attempt++) {
            String candidate = EventSlug.withSuffix(base, attempt);
            if (!eventRepository.slugTaken(candidate, excludeId)) {
                return candidate;
            }
        }
        throw new ValidationException("EVENT_SLUG_TAKEN", "No fue posible generar una URL pública única");
    }

    private static EventWriteDto toWrite(EventRecord record) {
        String[] placeAccess = EventRules.placeAndAccess(record.modality, record.locationName, record.virtualAccess);
        EventWriteDto dto = new EventWriteDto();
        dto.title = record.title;
        dto.titleEn = record.titleEn;
        dto.description = record.description;
        dto.descriptionEn = record.descriptionEn;
        dto.primaryLanguage = EventLanguage.normalize(record.primaryLanguage);
        dto.eventType = record.eventType;
        dto.startsAt = EventDateTimes.format(record.startsAt);
        dto.endsAt = EventDateTimes.format(record.endsAt);
        dto.timezone = record.timezone;
        dto.modality = record.modality;
        dto.locationName = placeAccess[0];
        dto.campusName = record.campusName;
        dto.virtualAccess = placeAccess[1];
        dto.organizingUnit = record.organizingUnit;
        dto.organizingUnitCode = record.organizingUnitCode;
        dto.registrationOpensAt = EventDateTimes.format(record.registrationOpensAt);
        dto.registrationClosesAt = EventDateTimes.format(record.registrationClosesAt);
        dto.allowRegistrationDuringEvent = record.allowRegistrationDuringEvent;
        return dto;
    }
}
