package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ResearcherIdentityCaseDto {
    public long personId;
    public Long pureId;
    public String firstName;
    public String lastName;
    public String fullName;
    public String position;
    public String orcid;
    public Integer scopusDistinctCount;
    public Integer evaluatedPublications;
    public Integer distinctSignatureCount;
    public String primarySignature;
    public String classification;
    public String employmentOrganizationName;
    public List<String> employmentOrganizationNames = new ArrayList<>();
}
