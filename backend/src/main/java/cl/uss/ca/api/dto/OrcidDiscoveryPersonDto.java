package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class OrcidDiscoveryPersonDto {
    public Long id;
    public Long personId;
    public Long pureId;
    public String uuid;
    public String firstName;
    public String lastName;
    public String fullName;
    public String cargo;
    public String pureOrcid;
    public String foundOrcid;
    public String selectedOrcid;
    public Long bestCandidateId;
    public Long uniqueStrongCandidateId;
    public String personStatus;
    public String statusReason;
    public Integer candidateCount;
    public Integer searchHitCount;
    public Integer doiMatchCount;
    public Long selectedCandidateId;
    public String selectionOrigin;
    public String selectedBy;
    public String selectedAt;
    public String searchGivenName;
    public String searchFamilyName;
    public String lastError;
    public boolean selectable;
    public boolean selected;
    public String selectionDisabledReason;
    public List<OrcidCandidateDto> candidates = new ArrayList<>();
}
