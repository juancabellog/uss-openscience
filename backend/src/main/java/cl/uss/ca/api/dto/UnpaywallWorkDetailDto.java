package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class UnpaywallWorkDetailDto extends UnpaywallWorkSummaryDto {
    public String genre;
    public String publishedDate;
    public String journalIssns;
    public String journalIssnL;
    public Boolean journalIsOa;
    public Integer lastHttpStatus;
    public String lastErrorCode;
    public String lastErrorMessage;
    public final List<UnpaywallLocationDto> locations = new ArrayList<>();
}
