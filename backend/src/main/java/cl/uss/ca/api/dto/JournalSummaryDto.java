package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class JournalSummaryDto {
    public Long id;
    public Long pureId;
    public String uuid;
    public String title;
    public List<String> issns = new ArrayList<>();
    public Integer issnCount;
    public Integer publicationCount;
    public String scopusId;
    public String type;
    public String typeEs;
    public String typeEn;
    public String workflowStep;
    public String workflowEs;
    public String workflowEn;
    public Boolean activeInPure;
    public String createdBy;
    public String modifiedBy;
    public java.math.BigDecimal citeScore;
    public Integer citeScoreYear;
    public String quartilesCompact;
    public Integer quartileYear;
    public Integer quartileCount;
}
