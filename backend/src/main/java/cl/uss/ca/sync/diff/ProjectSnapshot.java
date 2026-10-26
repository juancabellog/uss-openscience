package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.ProjectDescriptionData;
import cl.uss.ca.external.pure.model.ProjectIdentifierData;
import cl.uss.ca.external.pure.model.ProjectNatureTypeData;
import cl.uss.ca.external.pure.model.ProjectOrganizationData;
import cl.uss.ca.external.pure.model.ProjectParticipantData;
import cl.uss.ca.external.pure.model.PureProject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProjectSnapshot {
    public Long id;
    public Long pureId;
    public String uuid;
    public Boolean activeInPure;
    public String typeDiscriminator;
    public String titleEs;
    public String titleEn;
    public String acronym;
    public String createdBy;
    public OffsetDateTime createdDatePure;
    public String modifiedBy;
    public OffsetDateTime modifiedDatePure;
    public String portalUrl;
    public String version;
    public LocalDate periodStartDate;
    public LocalDate periodEndDate;
    public String typeUri;
    public String typeEn;
    public String typeEs;
    public String workflowStep;
    public String workflowDescriptionEn;
    public String workflowDescriptionEs;
    public String visibilityKey;
    public String visibilityEn;
    public String visibilityEs;
    public String managingOrganizationUuid;
    public String systemName;
    public final List<String> prettyUrlIdentifiers = new ArrayList<>();
    public final List<ProjectParticipantData> participants = new ArrayList<>();
    public final List<ProjectDescriptionData> descriptions = new ArrayList<>();
    public final List<ProjectIdentifierData> identifiers = new ArrayList<>();
    public final List<ProjectNatureTypeData> natureTypes = new ArrayList<>();
    public final List<ProjectOrganizationData> organizations = new ArrayList<>();

    public PureProject asProject() {
        PureProject project = new PureProject();
        project.setPureId(pureId);
        project.setUuid(uuid);
        project.setTypeDiscriminator(typeDiscriminator);
        project.setTitleEs(titleEs);
        project.setTitleEn(titleEn);
        project.setAcronym(acronym);
        project.setCreatedBy(createdBy);
        project.setCreatedDatePure(createdDatePure);
        project.setModifiedBy(modifiedBy);
        project.setModifiedDatePure(modifiedDatePure);
        project.setPortalUrl(portalUrl);
        project.setVersion(version);
        project.setPeriodStartDate(periodStartDate);
        project.setPeriodEndDate(periodEndDate);
        project.setTypeUri(typeUri);
        project.setTypeEn(typeEn);
        project.setTypeEs(typeEs);
        project.setWorkflowStep(workflowStep);
        project.setWorkflowDescriptionEn(workflowDescriptionEn);
        project.setWorkflowDescriptionEs(workflowDescriptionEs);
        project.setVisibilityKey(visibilityKey);
        project.setVisibilityEn(visibilityEn);
        project.setVisibilityEs(visibilityEs);
        project.setManagingOrganizationUuid(managingOrganizationUuid);
        project.setSystemName(systemName);
        project.getPrettyUrlIdentifiers().addAll(prettyUrlIdentifiers);
        project.getParticipants().addAll(participants);
        project.getDescriptions().addAll(descriptions);
        project.getIdentifiers().addAll(identifiers);
        project.getNatureTypes().addAll(natureTypes);
        project.getOrganizations().addAll(organizations);
        return project;
    }
}
