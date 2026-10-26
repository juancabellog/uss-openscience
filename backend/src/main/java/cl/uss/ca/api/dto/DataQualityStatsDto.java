package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class DataQualityStatsDto {
    public long openFindings;
    public long affectedRecords;
    public long correctionAvailable;
    public long manualReview;
    public long viewOnly;
    public long rulesWithFindings;
    public List<DataQualityRuleCountDto> ruleCounts = new ArrayList<>();
}
