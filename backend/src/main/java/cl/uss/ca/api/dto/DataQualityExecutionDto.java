package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class DataQualityExecutionDto {
    public Long id;
    public String status;
    public String triggerType;
    public String scope;
    public String entityType;
    public Long localRecordId;
    public Long syncExecutionId;
    public OffsetDateTime startedAt;
    public OffsetDateTime finishedAt;
    public int totalRecords;
    public int evaluatedRecords;
    public int findingCount;
    public int resolvedCount;
    public int errorCount;
    public String startedBy;
    public String errorCode;
    public String errorMessage;
    public Integer durationSeconds;
}
