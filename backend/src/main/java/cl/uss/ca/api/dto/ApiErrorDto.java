package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class ApiErrorDto {
    public OffsetDateTime timestamp;
    public int status;
    public String code;
    public String message;
    public String path;
    public String entity;
    public String activeSyncId;
    public OffsetDateTime startedAt;
    public String startedBy;
}
