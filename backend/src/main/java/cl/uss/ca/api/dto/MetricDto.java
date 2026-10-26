package cl.uss.ca.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class MetricDto {
    public String code;
    public BigDecimal value;
    public String unit;
    public boolean available;
    public Long numerator;
    public Long denominator;
    public String source;
    public OffsetDateTime calculatedAt;
    public String unavailableReason;
}
