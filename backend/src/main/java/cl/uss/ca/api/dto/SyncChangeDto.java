package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class SyncChangeDto {
    public Long id;
    public String syncId;
    public String entity;
    public Long localRecordId;
    public Long pureId;
    public String recordUuid;
    public String recordKey;
    public String changeType;
    public String changeScope;
    public String description;
    public String oldValue;
    public String newValue;
    public OffsetDateTime createdAt;
}
