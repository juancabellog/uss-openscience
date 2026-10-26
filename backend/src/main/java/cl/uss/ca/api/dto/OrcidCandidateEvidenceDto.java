package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OrcidCandidateEvidenceDto {
    public Long id;
    public Long researchOutputId;
    public String doi;
    public String localAuthor;
    public Integer localPosition;
    public String orcidAuthor;
    public Integer orcidPosition;
    public Boolean nameMatch;
    public Boolean positionMatch;
    public String note;
    public String personFullName;
    public String contributorFullName;
    public String personFirstName;
    public String personLastName;
    public String contributorFirstName;
    public String contributorLastName;
    public Boolean namePartitionDifferent;
    public List<String> reasonCodes = new ArrayList<>();
}
