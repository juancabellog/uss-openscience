package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OpenAlexAuthorshipDto {
    public Integer sourceOrder;
    public String authorPosition;
    public boolean corresponding;
    public String rawAuthorName;
    public String rawAffiliationString;
    public String openalexAuthorId;
    public String authorDisplayName;
    public String authorOrcid;
    public String observedOrcid;
    public String openalexUrl;
    public final List<OpenAlexInstitutionDto> institutions = new ArrayList<>();
}
