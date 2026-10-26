package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class PersonOrcidWorksDto {
    public long personId;
    public String orcid;
    public Boolean orcidValid;
    public Boolean orcidSyncPending;
    public OffsetDateTime lastOrcidSyncAt;
    public String lastOrcidSyncStatus;
    public int workGroupCount;
    public int workAssertionCount;
    public int matchedByDoi;
    public int orcidOnly;
    public int noDoi;
    public int pureOnly;
    public int ambiguous;
    public List<OrcidWorkGroupDto> groups = new ArrayList<>();
    public List<OrcidWorkDto> works = new ArrayList<>();
}
