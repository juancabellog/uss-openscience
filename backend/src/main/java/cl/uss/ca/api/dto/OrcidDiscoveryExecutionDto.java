package cl.uss.ca.api.dto;

public class OrcidDiscoveryExecutionDto {
    public Long id;
    public String status;
    public String startedAt;
    public String finishedAt;
    public String startedBy;
    public int totalPeople;
    public int processedPeople;
    public int peopleWithCandidates;
    public int strongMatchCount;
    public int manualReviewCount;
    public int noCandidateCount;
    public int tooManyCount;
    public int conflictCount;
    public int errorCount;
}
