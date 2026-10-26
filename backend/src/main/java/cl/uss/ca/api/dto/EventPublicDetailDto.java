package cl.uss.ca.api.dto;

public class EventPublicDetailDto extends EventPublicSummaryDto {
    public String description;
    public String eventType;
    public String eventTypeLabel;
    public String endsAt;
    public String timezone;
    public String modality;
    public String modalityLabel;
    public String locationName;
    public String campusName;
    public String virtualAccess;
    public String organizingUnit;
    public String organizingUnitEn;
    public String organizingUnitCode;
    public String registrationOpensAt;
    public String registrationClosesAt;
    public Integer capacity;
    public Integer remainingCapacity;
    public String registrationUrl;
}
