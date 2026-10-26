package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class UnpaywallStatsDto {
    public boolean configured;
    public boolean tablesAvailable;
    public long uniqueDois;
    public long evaluated;
    public long oa;
    public long nonOa;
    public long repositoryCopy;
    public long notFound;
    public Long lastProcessed;
    public Long lastErrors;
    public Long lastNotFound;
    public Long lastOa;
    public Long lastNonOa;
    public OffsetDateTime lastExecution;
    public String lastStatus;
    public final List<UnpaywallStatusCountDto> oaStatusDistribution = new ArrayList<>();
}
