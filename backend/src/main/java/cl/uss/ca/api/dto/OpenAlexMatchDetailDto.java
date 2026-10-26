package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OpenAlexMatchDetailDto extends OpenAlexMatchSummaryDto {
    public String conclusion;
    public int worksTotal;
    public int workPage = 1;
    public int workSize = 25;
    public final List<OpenAlexMatchWorkDto> works = new ArrayList<>();
}
