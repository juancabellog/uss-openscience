package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class CrossrefStatsDto {
    public boolean configured;
    public boolean enabled;
    public boolean tablesAvailable;
    public long uniqueDois;
    public long evaluated;
    public long found;
    public long otherAgency;
    public long notFound;
    public long invalid;
    public long withDifferences;
    public Long lastProcessed;
    public Long lastErrors;
    public OffsetDateTime lastExecution;
    public String lastStatus;
    public long withOrcid;
    public long withFunding;
    public long withRor;
    public long withUpdate;
}
