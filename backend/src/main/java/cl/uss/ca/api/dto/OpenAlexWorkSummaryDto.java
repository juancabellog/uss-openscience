package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class OpenAlexWorkSummaryDto {
    public Long id;
    public String openalexWorkId;
    public String doi;
    public String normalizedDoi;
    public String displayName;
    public Integer publicationYear;
    public String workType;
    public Integer citedByCount;
    public Integer authorshipsCount;
    public Boolean authorshipsTruncated;
    public Boolean rawJsonTruncated;
    public String lastOutcome;
    public OffsetDateTime lastSyncAt;
    public String publicationTitle;
    public int researchOutputCount;
    public final List<OpenAlexLinkedOutputDto> researchOutputs = new ArrayList<>();
}
