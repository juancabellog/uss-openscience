package cl.uss.ca.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ProjectParticipantDto {
    public Long id;
    public Long pureId;
    public String typeDiscriminator;
    public String firstName;
    public String lastName;
    public String displayName;
    public String roleUri;
    public String roleTermEn;
    public String roleTermEs;
    public Boolean associationHidden;
    public String personUuid;
    public Long personId;
    public List<ProjectOrganizationDto> organizations = new ArrayList<>();
}
