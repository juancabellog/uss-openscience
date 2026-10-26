package cl.uss.ca.repository;

import java.time.LocalDateTime;

public class EventRecord {
    public long id;
    public String publicSlug;
    public String title;
    public String titleEn;
    public String description;
    public String descriptionEn;
    public String primaryLanguage;
    public String eventType;
    public LocalDateTime startsAt;
    public LocalDateTime endsAt;
    public String timezone;
    public String modality;
    public String locationName;
    public String campusName;
    public String virtualAccess;
    public boolean allowRegistrationDuringEvent;
    public String organizingUnit;
    public String organizingUnitCode;
    public String publicationStatus;
    public LocalDateTime registrationOpensAt;
    public LocalDateTime registrationClosesAt;
    public Integer capacity;
    public int blankSignatureRows;
    public String createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public int confirmedCount;
}
