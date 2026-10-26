package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ScopusStatsDto {
    public long without;
    public long one;
    public long two;
    public long threeOrMore;
    public int maxCount;
    public List<ScopusCountDto> distribution = new ArrayList<>();
}
