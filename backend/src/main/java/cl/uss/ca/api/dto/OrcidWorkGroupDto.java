package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OrcidWorkGroupDto {
    public Long id;
    public String orcid;
    public String title;
    public String journalTitle;
    public String workType;
    public Integer publicationYear;
    public String doi;
    public Boolean activeInOrcid;
    public int sourceCount;
    public List<String> sources = new ArrayList<>();
    public String matchStatus;
    public List<OrcidWorkExternalIdDto> externalIds = new ArrayList<>();
    public List<OrcidWorkDto> assertions = new ArrayList<>();
}
