package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class SyncRunDto {
    public String syncId;
    public String entity;
    public String syncMode;
    public String status;
    public String startedBy;
    public OffsetDateTime startedAt;
    public OffsetDateTime finishedAt;
    public OffsetDateTime heartbeatAt;
    public Integer totalExpected;
    public long fetched;
    public long processed;
    public long created;
    public long updated;
    public long unchanged;
    public long reactivated;
    public long markedInactive;
    public long errors;
    public Long notFound;
    public Long oa;
    public Long nonOa;
    public String errorCode;
    public String message;
    public String phase;
    public Integer authorsTotal;
    public Long authorsProcessed;
    public Long authorsCreated;
    public Long authorsUpdated;
    public Long invalidCount;
    public Boolean recoveryAvailable;
    public String recoveryType;
    public String recoveryBlockedReason;
    public Long pendingAuthors;
    public String sourceSyncExecutionId;
}
