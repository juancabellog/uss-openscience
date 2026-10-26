package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class PersonStatsDto {
    public long total;
    public long totalPeople;
    public List<PositionCountDto> byPosition = new ArrayList<>();
    public long withoutPosition;
    public long researchers;
    public ScopusStatsDto scopus = new ScopusStatsDto();
    public long withScopusHindex;
}
