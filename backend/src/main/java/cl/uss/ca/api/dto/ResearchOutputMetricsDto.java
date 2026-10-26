package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ResearchOutputMetricsDto {
    public Long researchOutputId;
    public String uuid;
    public MetricFetchStateDto sciVal;
    public ResearchOutputFwciSummaryDto fwci;
    public List<MetricObservationDto> observations = new ArrayList<>();
}
