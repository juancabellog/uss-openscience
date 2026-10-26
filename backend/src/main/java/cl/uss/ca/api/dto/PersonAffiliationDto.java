package cl.uss.ca.api.dto;

import java.math.BigDecimal;

public class PersonAffiliationDto {
    public Long personId;
    public Long pureId;
    public String organizationUuid;
    public Long organizationId;
    public String nameEs;
    public String nameEn;
    public String typeEs;
    public String typeEn;
    public Boolean organizationActiveInPure;
    public boolean resolved;
    public String employmentType;
    public String email;
    public String startDate;
    public String endDate;
    public Boolean primaryAssociation;
    public String contractType;
    public BigDecimal fte;
    public String jobDescription;
    public String jobTitle;
    public String staffType;
}
