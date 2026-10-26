package cl.uss.ca.audit;

import cl.uss.ca.api.dto.AuditEventDto;
import cl.uss.ca.api.dto.SyncChangeDto;
import cl.uss.ca.api.dto.SyncErrorDto;
import cl.uss.ca.api.dto.SyncRunDto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Mapping of sync_execution into audit events. Not executed by the agent.
 */
public final class AuditEventsTest {
    private AuditEventsTest() {
    }

    public static void main(String[] args) {
        mapsCompleted();
        mapsRunning();
        mapsFailed();
        mapsWarnings();
        zeroCountersAreReal();
        sanitizesChangeAndError();
        systemActorWhenMissing();
        System.out.println("AuditEventsTest OK");
    }

    static void mapsCompleted() {
        AuditEventDto dto = AuditEvents.fromSync(run("44", "COMPLETED", "FULL", 12, 0), "Ana Pérez");
        assert "SYNC:44".equals(dto.eventId);
        assert "SYNC".equals(dto.eventType);
        assert "44".equals(dto.executionId);
        assert "PURE".equals(dto.source);
        assert "SYNC_PERSONS".equals(dto.operation);
        assert "FULL".equals(dto.syncMode);
        assert "COMPLETED".equals(dto.status);
        assert "USER".equals(dto.actorKind);
        assert "Ana Pérez".equals(dto.actorLabel);
        assert dto.durationMs != null && dto.durationMs == 60_000L;
        assert dto.processed == 12;
        assert dto.errorCount == 0;
    }

    static void mapsRunning() {
        SyncRunDto run = run("9", "RUNNING", "INCREMENTAL", 3, 0);
        run.finishedAt = null;
        AuditEventDto dto = AuditEvents.fromSync(run, null);
        assert "RUNNING".equals(dto.status);
        assert "INCREMENTAL".equals(dto.syncMode);
        assert dto.durationMs == null;
        assert dto.processed == 3;
    }

    static void mapsFailed() {
        SyncRunDto run = run("8", "FAILED", "FULL", 2, 4);
        run.message = "apiKey=abc123 failed";
        AuditEventDto dto = AuditEvents.fromSync(run, "7");
        assert "FAILED".equals(dto.status);
        assert dto.errorCount == 4;
        assert dto.errorMessage != null && dto.errorMessage.contains("[REDACTED]");
        assert !dto.errorMessage.contains("abc123");
    }

    static void mapsWarnings() {
        AuditEventDto dto = AuditEvents.fromSync(run("3", "COMPLETED_WITH_WARNINGS", "FULL", 5, 1), null);
        assert "COMPLETED_WITH_WARNINGS".equals(dto.status);
        assert dto.errorCount == 1;
    }

    static void zeroCountersAreReal() {
        SyncRunDto run = run("1", "COMPLETED", "FULL", 0, 0);
        run.created = 0;
        run.updated = 0;
        run.unchanged = 0;
        run.totalExpected = null;
        AuditEventDto dto = AuditEvents.fromSync(run, null);
        assert dto.processed == 0;
        assert dto.created == 0;
        assert dto.errorCount == 0;
        assert dto.totalExpected == null;
    }

    static void sanitizesChangeAndError() {
        SyncChangeDto change = new SyncChangeDto();
        change.oldValue = "token=secret-token";
        change.newValue = "ok";
        change.recordKey = "https://x?api_key=zzz";
        AuditEvents.sanitize(change);
        assert !change.oldValue.contains("secret-token");
        assert "ok".equals(change.newValue);
        assert !change.recordKey.contains("zzz");

        SyncErrorDto error = new SyncErrorDto();
        error.errorMessage = "password=hunter2";
        error.recordKey = "https://x?access_token=aaa";
        AuditEvents.sanitize(error);
        assert !error.errorMessage.contains("hunter2");
        assert !error.recordKey.contains("aaa");
    }

    static void systemActorWhenMissing() {
        SyncRunDto run = run("2", "COMPLETED", "FULL", 1, 0);
        run.startedBy = null;
        AuditEventDto dto = AuditEvents.fromSync(run, null);
        assert "SYSTEM".equals(dto.actorKind);
        assert dto.actorLabel == null;
    }

    private static SyncRunDto run(String id, String status, String mode, long processed, long errors) {
        SyncRunDto dto = new SyncRunDto();
        dto.syncId = id;
        dto.entity = "PEOPLE";
        dto.status = status;
        dto.syncMode = mode;
        dto.startedBy = "7";
        dto.startedAt = OffsetDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC);
        dto.finishedAt = dto.startedAt.plusMinutes(1);
        dto.processed = processed;
        dto.errors = errors;
        return dto;
    }
}
