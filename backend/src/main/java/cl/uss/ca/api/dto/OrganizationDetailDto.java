package cl.uss.ca.api.dto;

public class OrganizationDetailDto extends OrganizationSummaryDto {
    public String typeDiscriminator;
    public String createdDatePure;
    public String version;
    public String systemName;
    public Long parentId;
    public Boolean parentResolved;
    public Boolean parentActiveInPure;
}
