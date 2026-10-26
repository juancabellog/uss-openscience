package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class CrossrefAuthorDto {
    public int sequenceNo;
    public String givenName;
    public String familyName;
    public String fullName;
    public String orcid;
    public Boolean authenticatedOrcid;
    public final List<CrossrefAffiliationDto> affiliations = new ArrayList<>();
}
