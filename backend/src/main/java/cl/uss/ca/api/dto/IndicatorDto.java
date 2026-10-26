package cl.uss.ca.api.dto;

import java.math.BigDecimal;

public class IndicatorDto {
    public String code;
    public int layer;
    public String group;
    public boolean available;
    public BigDecimal value;
    public String unavailableReason;
}
