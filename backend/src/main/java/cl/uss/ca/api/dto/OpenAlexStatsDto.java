package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class OpenAlexStatsDto {
    public boolean configured;
    public boolean enabled;
    public boolean tablesAvailable;
    public long uniqueDois;
    public long evaluated;
    public long found;
    public long notFound;
    public long invalid;
    public long authors;
    public long personsLinked;
    public long pendingMatches;
    public long confirmedMatches;
    public long conflicts;
    public Long lastProcessed;
    public Long lastErrors;
    public OffsetDateTime lastExecution;
    public String lastStatus;
    public long coverageNumerator;
    public long coverageDenominator;
    public List<SyncRunDto> pendingRecoveries;
}
