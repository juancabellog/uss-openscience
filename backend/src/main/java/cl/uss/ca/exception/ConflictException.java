package cl.uss.ca.exception;

import java.time.OffsetDateTime;

public class ConflictException extends RuntimeException {
    private final String code;
    private final String entity;
    private final String activeSyncId;
    private final OffsetDateTime startedAt;
    private final String startedBy;

    public ConflictException(String code, String message) {
        this(code, message, null, null, null, null);
    }

    public ConflictException(
            String code,
            String message,
            String entity,
            String activeSyncId,
            OffsetDateTime startedAt,
            String startedBy
    ) {
        super(message);
        this.code = code;
        this.entity = entity;
        this.activeSyncId = activeSyncId;
        this.startedAt = startedAt;
        this.startedBy = startedBy;
    }

    public String getCode() {
        return code;
    }

    public String getEntity() {
        return entity;
    }

    public String getActiveSyncId() {
        return activeSyncId;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public String getStartedBy() {
        return startedBy;
    }
}
