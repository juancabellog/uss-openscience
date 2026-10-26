package cl.uss.ca.api.dto;

import java.math.BigDecimal;

public class MetricObservationDto {
    public String collectionId;
    public String sourceUri;
    public String sourceTermEs;
    public String sourceTermEn;
    public String categoryUri;
    public String categoryTermEs;
    public String categoryTermEn;
    public Integer metricYear;
    public String metricId;
    public String valueKind;
    public Long integerValue;
    public BigDecimal decimalValue;
    public String stringValue;
    public String lastSyncedAt;
}
