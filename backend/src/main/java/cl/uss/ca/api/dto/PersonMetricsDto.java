package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class PersonMetricsDto {
    public Long personId;
    public String uuid;
    public MetricFetchStateDto hIndex;
    public PersonHindexSummaryDto scopusAllYears;
    public List<MetricObservationDto> observations = new ArrayList<>();
}
