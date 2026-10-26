package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ResearcherSummaryDto {
    public Long id;
    public String uuid;
    public String firstName;
    public String lastName;
    public String fullName;
    public String email;
    public String position;
    public String faculty;
    public String orcid;
    public Boolean orcidAuthenticated;
    public List<String> scopusIds = new ArrayList<>();
    public Integer publicationCount;
    public String dataQualityStatus;
}
