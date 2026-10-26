package cl.uss.ca.events;

import cl.uss.ca.api.dto.EventAdminDto;
import cl.uss.ca.api.dto.EventPublicDetailDto;
import cl.uss.ca.api.dto.EventPublicSummaryDto;
import cl.uss.ca.api.dto.EventRegistrationDto;
import cl.uss.ca.repository.EventRecord;
import cl.uss.ca.repository.EventSignupRecord;

import java.time.LocalDateTime;

public final class EventMapper {
    private EventMapper() {
    }

    public static EventAdminDto admin(EventRecord record, String publicBase, LocalDateTime now) {
        EventAdminDto dto = new EventAdminDto();
        fillAdmin(dto, record, publicBase, now);
        return dto;
    }

    public static EventPublicSummaryDto summary(EventRecord record, LocalDateTime now) {
        EventPublicSummaryDto dto = new EventPublicSummaryDto();
        fillSummary(dto, record, now);
        return dto;
    }

    public static EventPublicDetailDto detail(EventRecord record, String publicBase, LocalDateTime now) {
        EventPublicDetailDto dto = new EventPublicDetailDto();
        fillSummary(dto, record, now);
        dto.description = record.description;
        dto.timezone = record.timezone;
        String[] placeAccess = EventRules.placeAndAccess(record.modality, record.locationName, record.virtualAccess);
        dto.locationName = placeAccess[0];
        dto.campusName = record.campusName;
        dto.virtualAccess = placeAccess[1];
        fillUnit(dto, record);
        dto.registrationOpensAt = EventDateTimes.format(record.registrationOpensAt);
        dto.registrationClosesAt = EventDateTimes.format(record.registrationClosesAt);
        dto.capacity = record.capacity;
        dto.remainingCapacity = EventRegistrationPolicy.remainingCapacity(record.capacity, record.confirmedCount);
        dto.registrationUrl = registrationUrl(publicBase, record.publicSlug);
        return dto;
    }

    public static EventRegistrationDto registration(EventSignupRecord record) {
        EventRegistrationDto dto = new EventRegistrationDto();
        dto.id = record.id;
        dto.publicCode = record.publicCode;
        dto.seqNo = record.seqNo;
        dto.status = record.status;
        dto.email = record.emailNormalized;
        dto.displayName = record.displayName;
        dto.facultyOrInstitution = record.facultyOrInstitution;
        dto.campusName = record.campusName;
        dto.positionTitle = record.positionTitle;
        dto.affiliationOrigin = record.affiliationOrigin;
        dto.directoryPersonUuid = record.directoryPersonUuid;
        dto.createdAt = EventDateTimes.format(record.createdAt);
        return dto;
    }

    public static String publicUrl(String publicBase, String slug) {
        return EventPublicLinks.eventUrl(publicBase, slug);
    }

    public static String registrationUrl(String publicBase, String slug) {
        return EventPublicLinks.registrationUrl(publicBase, slug);
    }

    private static void fillAdmin(EventAdminDto dto, EventRecord record, String publicBase, LocalDateTime now) {
        dto.id = record.id;
        dto.publicSlug = record.publicSlug;
        dto.publicUrl = publicUrl(publicBase, record.publicSlug);
        dto.registrationUrl = registrationUrl(publicBase, record.publicSlug);
        dto.title = record.title;
        dto.titleEn = record.titleEn;
        dto.description = record.description;
        dto.descriptionEn = record.descriptionEn;
        dto.primaryLanguage = EventLanguage.normalize(record.primaryLanguage);
        dto.eventType = record.eventType;
        EventType type = EventType.parse(record.eventType);
        dto.eventTypeLabel = type == null ? record.eventType : type.labelEs();
        dto.startsAt = EventDateTimes.format(record.startsAt);
        dto.endsAt = EventDateTimes.format(record.endsAt);
        dto.timezone = record.timezone;
        dto.modality = record.modality;
        EventModality modality = EventModality.parse(record.modality);
        dto.modalityLabel = modality == null ? record.modality : modality.labelEs();
        String[] placeAccess = EventRules.placeAndAccess(record.modality, record.locationName, record.virtualAccess);
        dto.locationName = placeAccess[0];
        dto.campusName = record.campusName;
        dto.virtualAccess = placeAccess[1];
        dto.allowRegistrationDuringEvent = record.allowRegistrationDuringEvent;
        fillUnit(dto, record);
        dto.publicationStatus = record.publicationStatus;
        dto.registrationOpensAt = EventDateTimes.format(record.registrationOpensAt);
        dto.registrationClosesAt = EventDateTimes.format(record.registrationClosesAt);
        dto.registrationOpen = EventRegistrationPolicy.isOpen(
                EventPublicationStatus.parse(record.publicationStatus),
                now,
                record.registrationOpensAt,
                record.registrationClosesAt
        );
        dto.capacity = record.capacity;
        dto.confirmedCount = record.confirmedCount;
        dto.remainingCapacity = EventRegistrationPolicy.remainingCapacity(record.capacity, record.confirmedCount);
        dto.blankSignatureRows = record.blankSignatureRows;
        dto.createdBy = record.createdBy;
        dto.createdAt = EventDateTimes.format(record.createdAt);
        dto.updatedAt = EventDateTimes.format(record.updatedAt);
    }

    private static void fillSummary(EventPublicSummaryDto dto, EventRecord record, LocalDateTime now) {
        dto.id = record.id;
        dto.slug = record.publicSlug;
        dto.title = record.title;
        dto.titleEn = record.titleEn;
        dto.descriptionEn = record.descriptionEn;
        dto.primaryLanguage = EventLanguage.normalize(record.primaryLanguage);
        dto.timezone = record.timezone;
        dto.eventType = record.eventType;
        EventType type = EventType.parse(record.eventType);
        dto.eventTypeLabel = type == null ? record.eventType : type.labelEs();
        dto.startsAt = EventDateTimes.format(record.startsAt);
        dto.endsAt = EventDateTimes.format(record.endsAt);
        dto.modality = record.modality;
        EventModality modality = EventModality.parse(record.modality);
        dto.modalityLabel = modality == null ? record.modality : modality.labelEs();
        dto.locationName = EventRules.placeAndAccess(record.modality, record.locationName, record.virtualAccess)[0];
        dto.registrationOpen = EventRegistrationPolicy.isOpen(
                EventPublicationStatus.parse(record.publicationStatus),
                now,
                record.registrationOpensAt,
                record.registrationClosesAt
        );
        dto.publicationStatus = record.publicationStatus;
    }

    private static void fillUnit(EventPublicDetailDto dto, EventRecord record) {
        dto.organizingUnitCode = EventOrganizingUnits.effectiveCode(record.organizingUnitCode, record.organizingUnit);
        dto.organizingUnit = EventOrganizingUnits.display(record.organizingUnitCode, record.organizingUnit, EventLanguage.ES);
        dto.organizingUnitEn = EventOrganizingUnits.display(record.organizingUnitCode, record.organizingUnit, EventLanguage.EN);
    }

    private static void fillUnit(EventAdminDto dto, EventRecord record) {
        dto.organizingUnitCode = EventOrganizingUnits.effectiveCode(record.organizingUnitCode, record.organizingUnit);
        dto.organizingUnit = EventOrganizingUnits.display(record.organizingUnitCode, record.organizingUnit, EventLanguage.ES);
        dto.organizingUnitEn = EventOrganizingUnits.display(record.organizingUnitCode, record.organizingUnit, EventLanguage.EN);
    }
}
