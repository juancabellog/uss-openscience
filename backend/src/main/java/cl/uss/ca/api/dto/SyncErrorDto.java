package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class SyncErrorDto {
    public Long id;
    public String syncId;
    public String entity;
    public Long localRecordId;
    public Long pureId;
    public String uuid;
    public String recordKey;
    public String stage;
    public String errorCode;
    public String errorMessage;
    public OffsetDateTime createdAt;
}
