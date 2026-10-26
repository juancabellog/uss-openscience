package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class OpenAlexAuthorSummaryDto {
    public Long id;
    public String openalexAuthorId;
    public String displayName;
    public String orcid;
    public Integer worksCount;
    public Integer citedByCount;
    public String lastOutcome;
    public OffsetDateTime lastSyncAt;
    public String openalexUrl;
    public int linkedPersonCount;
}
