package cl.uss.ca.api.dto;

import java.math.BigDecimal;

public class OrganizationPersonDto {
    public Long personId;
    public Long personPureId;
    public String personUuid;
    public String firstName;
    public String lastName;
    public String displayName;
    public String jobTitle;
    public String employmentType;
    public BigDecimal fte;
    public String startDate;
    public String endDate;
    public Boolean primaryAssociation;
    public Boolean personActiveInPure;
}
