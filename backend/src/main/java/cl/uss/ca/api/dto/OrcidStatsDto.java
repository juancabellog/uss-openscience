package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class OrcidStatsDto {
    public boolean configured;
    public boolean tablesAvailable;
    public long peopleWithOrcid;
    public long peopleSynced;
    public long workGroupCount;
    public long workAssertionCount;
    /** Conceptual ORCID works. Same as workGroupCount. Not assertion count. */
    public long works;
    public Long lastProcessed;
    public Long lastErrors;
    public OffsetDateTime lastExecution;
    public String lastStatus;
}
