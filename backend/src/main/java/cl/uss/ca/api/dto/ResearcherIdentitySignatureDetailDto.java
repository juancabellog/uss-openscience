package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ResearcherIdentitySignatureDetailDto {
    public long personId;
    public String fullName;
    public String position;
    public int evaluatedPublications;
    public int distinctSignatureCount;
    public List<ResearcherIdentitySignatureVariantDto> variants = new ArrayList<>();
}
