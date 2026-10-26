package cl.uss.ca.audit;

import cl.uss.ca.api.dto.AuditEventDto;
import cl.uss.ca.api.dto.SyncChangeDto;
import cl.uss.ca.api.dto.SyncErrorDto;
import cl.uss.ca.api.dto.SyncRunDto;

import java.time.Duration;

public final class AuditEvents {
    private AuditEvents() {
    }

    public static AuditEventDto fromSync(SyncRunDto run, String actorLabel) {
        if (run == null) {
            return null;
        }
        AuditEventDto dto = new AuditEventDto();
        dto.eventId = AuditCatalog.eventId(run.syncId);
        dto.eventType = AuditCatalog.TYPE_SYNC;
        dto.executionId = run.syncId;
        dto.startedAt = run.startedAt;
        dto.finishedAt = run.finishedAt;
        dto.durationMs = durationMs(run);
        dto.actorKind = AuditCatalog.actorKind(run.startedBy);
        dto.actorId = run.startedBy == null || run.startedBy.isBlank() ? null : run.startedBy.trim();
        dto.actorLabel = AuditCatalog.actorLabel(run.startedBy, actorLabel);
        dto.entity = run.entity;
        dto.operation = AuditCatalog.operationOf(run.entity, run.syncMode);
        dto.source = AuditCatalog.sourceOf(run.entity);
        dto.syncMode = blankToNull(run.syncMode);
        dto.sourceSyncExecutionId = blankToNull(run.sourceSyncExecutionId);
        dto.status = run.status;
        dto.totalExpected = run.totalExpected;
        dto.processed = run.processed;
        dto.created = run.created;
        dto.updated = run.updated;
        dto.unchanged = run.unchanged;
        dto.reactivated = run.reactivated;
        dto.markedInactive = run.markedInactive;
        dto.errorCount = run.errors;
        dto.errorCode = run.errorCode;
        dto.errorMessage = AuditSanitizer.redact(run.message);
        dto.recoveryAvailable = run.recoveryAvailable;
        dto.recoveryType = run.recoveryType;
        dto.recoveryBlockedReason = run.recoveryBlockedReason;
        dto.pendingAuthors = run.pendingAuthors;
        return dto;
    }

    public static SyncChangeDto sanitize(SyncChangeDto change) {
        if (change == null) {
            return null;
        }
        change.description = AuditSanitizer.redact(change.description);
        change.oldValue = AuditSanitizer.redact(change.oldValue);
        change.newValue = AuditSanitizer.redact(change.newValue);
        change.recordKey = AuditSanitizer.redact(change.recordKey);
        return change;
    }

    public static SyncErrorDto sanitize(SyncErrorDto error) {
        if (error == null) {
            return null;
        }
        error.errorMessage = AuditSanitizer.redact(error.errorMessage);
        error.recordKey = AuditSanitizer.redact(error.recordKey);
        return error;
    }

    private static Long durationMs(SyncRunDto run) {
        if (run.startedAt == null || run.finishedAt == null) {
            return null;
        }
        long millis = Duration.between(run.startedAt, run.finishedAt).toMillis();
        return millis < 0 ? null : millis;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
