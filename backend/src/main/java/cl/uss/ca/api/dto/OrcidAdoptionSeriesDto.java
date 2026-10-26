package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OrcidAdoptionSeriesDto {
    public String metric;
    public String definition;
    public String universe;
    public String availableFrom;
    public String from;
    public String to;
    public final List<OrcidAdoptionMonthDto> months = new ArrayList<>();
}
