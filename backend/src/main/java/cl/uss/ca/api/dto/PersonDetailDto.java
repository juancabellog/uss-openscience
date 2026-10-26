package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class PersonDetailDto extends PersonSummaryDto {
    public String gender;
    public String primaryId;
    public Boolean orcidSyncPending;
    public java.time.OffsetDateTime lastOrcidSyncAt;
    public String lastOrcidSyncStatus;
    public java.time.OffsetDateTime orcidFirstSeenAt;
    public String orcidFirstSeenSource;
    public List<IdentifierDto> identities = new ArrayList<>();
    public List<PersonAffiliationDto> affiliations = new ArrayList<>();
    public List<ResearchOutputSummaryDto> publications = new ArrayList<>();
    public String openalexAuthorId;
    public String openalexDisplayName;
    public String openalexMatchStatus;
    public String openalexUrl;
    public Integer openalexMatchingWorkCount;
    public Boolean openalexConfirmed;
}
