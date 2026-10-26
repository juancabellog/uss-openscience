package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class DspaceStatsDto {
    public boolean configured;
    public boolean enabled;
    public boolean tablesAvailable;
    public String dspaceVersion;
    public long items;
    public long communities;
    public long collections;
    public long openAccess;
    public long metadataOnly;
    public long restricted;
    public long embargo;
    public long fulltextPublic;
    public long matched;
    public long candidates;
    public long linkedItems;
    public long reviewItems;
    public long unmatchedItems;
    public long missingItems;
    public long errors;
    public long sharedDois;
    public long sharedDoiItems;
    public long withFile;
    public long withoutFile;
    public long unknownAccess;
    public long bitstreamCount;
    public Long lastProcessed;
    public Long lastErrors;
    public OffsetDateTime lastExecution;
    public String lastStatus;
}
