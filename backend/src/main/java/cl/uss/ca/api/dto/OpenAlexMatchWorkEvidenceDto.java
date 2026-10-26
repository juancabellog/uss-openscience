package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OpenAlexMatchWorkEvidenceDto {
    public Long id;
    public Long authorshipId;
    public Integer sourceOrder;
    public String rawAuthorName;
    public String authorDisplayName;
    public String openalexAuthorId;
    public String authorPosition;
    public String affiliationDisplay;
    public String ror;
    public Boolean nameCompatible;
    public Boolean namePartitionDifferent;
    public String positionCode;
    public String orcidPure;
    public String orcidOpenAlex;
    public boolean ussAffiliation;
    public String reasonCodes;
    public final List<String> reasonCodeList = new ArrayList<>();
}
