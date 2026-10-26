package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class IntegrationStatusDto {
    public String code;
    public String status;
    public String statusReason;
    public OffsetDateTime lastExecution;
    public OffsetDateTime lastSuccessfulExecution;
    public OffsetDateTime lastFullSyncAt;
    public OffsetDateTime lastSuccessfulFullSyncAt;
    public String lastSyncMode;
    public List<String> supportedSyncModes;
    public List<String> availableSyncModes;
    public Boolean hasValidBaseline;
    public String recommendedSyncMode;
    public Long processed;
    public Long errors;
    public List<IntegrationEntitySyncDto> entities;
}
