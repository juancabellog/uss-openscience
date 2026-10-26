package cl.uss.ca.sync;

import cl.uss.ca.api.dto.SyncRunDto;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

public class SyncRunState {
    public String syncId;
    public final String entity;
    public final String startedBy;
    public final OffsetDateTime startedAt = OffsetDateTime.now();
    public volatile String status = "PENDING";
    public volatile OffsetDateTime finishedAt;
    public volatile OffsetDateTime heartbeatAt = startedAt;
    public volatile Integer totalExpected;
    public final AtomicLong processed = new AtomicLong();
    public final AtomicLong created = new AtomicLong();
    public final AtomicLong updated = new AtomicLong();
    public final AtomicLong unchanged = new AtomicLong();
    public final AtomicLong reactivated = new AtomicLong();
    public final AtomicLong markedInactive = new AtomicLong();
    public final AtomicLong errors = new AtomicLong();
    public final AtomicLong notFound = new AtomicLong();
    public final AtomicLong oa = new AtomicLong();
    public final AtomicLong nonOa = new AtomicLong();
    public volatile String errorCode;
    public volatile String message;
    public volatile boolean fatal;
    public volatile SyncMode syncMode = SyncMode.FULL;
    public volatile String phase;
    public volatile Integer authorsTotal;
    public final AtomicLong authorsProcessed = new AtomicLong();
    public final AtomicLong authorsCreated = new AtomicLong();
    public final AtomicLong authorsUpdated = new AtomicLong();
    public final AtomicLong invalid = new AtomicLong();
    public volatile String sourceSyncExecutionId;
    public volatile String cancelledBy;

    public SyncRunState(String entity, String startedBy) {
        this.entity = entity;
        this.startedBy = startedBy;
    }

    public SyncRunDto toDto() {
        SyncRunDto dto = new SyncRunDto();
        dto.syncId = syncId;
        dto.entity = entity;
        dto.status = status;
        dto.startedBy = startedBy;
        dto.startedAt = startedAt;
        dto.finishedAt = finishedAt;
        dto.heartbeatAt = heartbeatAt;
        dto.totalExpected = totalExpected;
        dto.processed = processed.get();
        dto.fetched = processed.get() + errors.get();
        dto.created = created.get();
        dto.updated = updated.get();
        dto.unchanged = unchanged.get();
        dto.reactivated = reactivated.get();
        dto.markedInactive = markedInactive.get();
        dto.errors = errors.get();
        dto.notFound = notFound.get();
        dto.oa = oa.get();
        dto.nonOa = nonOa.get();
        dto.errorCode = errorCode;
        dto.message = message;
        dto.syncMode = syncMode == null ? null : syncMode.name();
        dto.phase = phase;
        dto.authorsTotal = authorsTotal;
        dto.authorsProcessed = authorsProcessed.get();
        dto.authorsCreated = authorsCreated.get();
        dto.authorsUpdated = authorsUpdated.get();
        dto.invalidCount = invalid.get();
        dto.sourceSyncExecutionId = sourceSyncExecutionId;
        return dto;
    }

    public boolean isFull() {
        return syncMode == null || syncMode.isFull();
    }

    public boolean isIncremental() {
        return syncMode != null && syncMode.isIncremental();
    }

    public boolean isRecovery() {
        return syncMode != null && syncMode.isRecovery();
    }
}
