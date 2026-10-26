package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OrcidCandidateDto {
    public Long id;
    public String orcid;
    public Integer score;
    public String matchLevel;
    public String status;
    public Boolean nameMatch;
    public Integer doiMatchCount;
    public Boolean authorMatch;
    public Boolean positionMatch;
    public Long alreadyUsedPersonId;
    public List<OrcidCandidateEvidenceDto> evidence = new ArrayList<>();
}
