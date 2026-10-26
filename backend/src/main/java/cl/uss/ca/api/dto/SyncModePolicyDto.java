package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class SyncModePolicyDto {
    public String entity;
    public List<String> supportedSyncModes;
    public List<String> availableSyncModes;
    public boolean hasValidBaseline;
    public String recommendedSyncMode;
    public OffsetDateTime lastFullSyncAt;
    public OffsetDateTime lastSuccessfulFullSyncAt;
    public SyncRunDto lastExecution;
}
