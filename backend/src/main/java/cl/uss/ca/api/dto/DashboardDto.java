package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class DashboardDto {
    public OffsetDateTime lastUpdated;
    public DashboardMetricsDto metrics;
    public DataQualitySummaryDto dataQuality;
    public List<IntegrationStatusDto> integrations;
}
