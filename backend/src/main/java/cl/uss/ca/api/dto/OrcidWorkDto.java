package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrcidWorkDto {
    public Long id;
    public Long workGroupId;
    public Long putCode;
    public String orcid;
    public String workType;
    public String title;
    public String subtitle;
    public String journalTitle;
    public Integer publicationYear;
    public Integer publicationMonth;
    public Integer publicationDay;
    public String url;
    public String visibility;
    public String sourceName;
    public String sourceOrcidPath;
    public String sourceClientIdPath;
    public String assertionOriginName;
    public String citationType;
    public String doi;
    public String eid;
    public Boolean detailLoaded;
    public Boolean activeInOrcid;
    public OffsetDateTime lastSeenInOrcidAt;
    public String matchStatus;
    public int contributorCount;
    public List<OrcidWorkExternalIdDto> externalIds = new ArrayList<>();
    public List<OrcidWorkContributorDto> contributors = new ArrayList<>();
}
