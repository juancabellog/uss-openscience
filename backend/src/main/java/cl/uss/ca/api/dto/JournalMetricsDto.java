package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class JournalMetricsDto {
    public Long journalId;
    public String uuid;
    public MetricFetchStateDto scopusJournal;
    public MetricFetchStateDto scopusCitescore;
    public JournalCiteScoreSummaryDto latestCiteScore;
    public JournalQuartileSummaryDto latestQuartiles;
    public List<MetricObservationDto> citeScoreHistory = new ArrayList<>();
    public List<JournalQuartileCategoryDto> quartileHistory = new ArrayList<>();
    public List<MetricObservationDto> quartileRows = new ArrayList<>();
    public List<MetricObservationDto> otherScopusJournal = new ArrayList<>();
    public int quartileTotal;
    public int quartilePage;
    public int quartileSize;
}
