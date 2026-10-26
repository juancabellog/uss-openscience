package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class AuditEventDto {
    public String eventId;
    public String eventType;
    public String executionId;
    public OffsetDateTime startedAt;
    public OffsetDateTime finishedAt;
    public Long durationMs;
    public String actorKind;
    public String actorId;
    public String actorLabel;
    public String entity;
    public String operation;
    public String source;
    public String syncMode;
    public String status;
    public Integer totalExpected;
    public long processed;
    public long created;
    public long updated;
    public long unchanged;
    public long reactivated;
    public long markedInactive;
    public long errorCount;
    public String errorCode;
    public String errorMessage;
    public String sourceSyncExecutionId;
    public Boolean recoveryAvailable;
    public String recoveryType;
    public String recoveryBlockedReason;
    public Long pendingAuthors;
}
