package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class AuditEntryDto {
    public Long id;
    public OffsetDateTime timestamp;
    public String user;
    public String entityType;
    public String entityId;
    public String operation;
    public String source;
    public String result;
    public String status;
}
