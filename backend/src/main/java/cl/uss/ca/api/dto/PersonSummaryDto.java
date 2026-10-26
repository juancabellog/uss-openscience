package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class PersonSummaryDto {
    public Long id;
    public Long pureId;
    public String uuid;
    public String firstName;
    public String lastName;
    public String fullName;
    public String email;
    public String position;
    public String orcid;
    public Boolean orcidAuthenticated;
    public List<String> scopusIds = new ArrayList<>();
    /** COUNT(*) of Scopus ID rows, including duplicates. Not COUNT(DISTINCT). */
    public Integer scopusIdCount;
    public Integer publicationCount;
    public String organization;
    public Boolean activeInPure;
    public String createdBy;
    public String modifiedBy;
    public Integer scopusHindex;
    public Integer scopusCitations;
}
