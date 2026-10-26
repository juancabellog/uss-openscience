package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ProjectDetailDto extends ProjectSummaryDto {
    public String typeDiscriminator;
    public String createdBy;
    public String createdDatePure;
    public String modifiedBy;
    public String modifiedDatePure;
    public String version;
    public String typeUri;
    public String workflowDescriptionEn;
    public String workflowDescriptionEs;
    public String systemName;
    public List<String> prettyUrlIdentifiers = new ArrayList<>();
    public List<ProjectParticipantDto> participants = new ArrayList<>();
    public List<ProjectIdentifierDto> identifiers = new ArrayList<>();
    public List<ProjectDescriptionDto> descriptions = new ArrayList<>();
    public List<ProjectNatureTypeDto> natureTypes = new ArrayList<>();
    public List<ProjectOrganizationDto> organizations = new ArrayList<>();
}
