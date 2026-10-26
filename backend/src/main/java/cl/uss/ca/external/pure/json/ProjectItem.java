package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectItem {
    private String typeDiscriminator;
    private Long pureId;
    private String uuid;
    private String createdBy;
    private String createdDate;
    private String modifiedBy;
    private String modifiedDate;
    private String portalUrl;
    private List<String> prettyUrlIdentifiers;
    private String version;
    private String acronym;
    private List<ProjectParticipantItem> participants;
    private List<ProjectDescriptionItem> descriptions;
    private DateRange period;
    private List<IdentifierItem> identifiers;
    private List<ClassificationRef> natureTypes;
    private List<OrganizationRef> organizations;
    private OrganizationRef managingOrganization;
    private LocalizedText title;
    private ClassificationRef type;
    private WorkflowItem workflow;
    private VisibilityItem visibility;
    private String systemName;

    public String getTypeDiscriminator() {
        return typeDiscriminator;
    }

    public void setTypeDiscriminator(String typeDiscriminator) {
        this.typeDiscriminator = typeDiscriminator;
    }

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public String getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(String modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getPortalUrl() {
        return portalUrl;
    }

    public void setPortalUrl(String portalUrl) {
        this.portalUrl = portalUrl;
    }

    public List<String> getPrettyUrlIdentifiers() {
        return prettyUrlIdentifiers;
    }

    public void setPrettyUrlIdentifiers(List<String> prettyUrlIdentifiers) {
        this.prettyUrlIdentifiers = prettyUrlIdentifiers;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAcronym() {
        return acronym;
    }

    public void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    public List<ProjectParticipantItem> getParticipants() {
        return participants;
    }

    public void setParticipants(List<ProjectParticipantItem> participants) {
        this.participants = participants;
    }

    public List<ProjectDescriptionItem> getDescriptions() {
        return descriptions;
    }

    public void setDescriptions(List<ProjectDescriptionItem> descriptions) {
        this.descriptions = descriptions;
    }

    public DateRange getPeriod() {
        return period;
    }

    public void setPeriod(DateRange period) {
        this.period = period;
    }

    public List<IdentifierItem> getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(List<IdentifierItem> identifiers) {
        this.identifiers = identifiers;
    }

    public List<ClassificationRef> getNatureTypes() {
        return natureTypes;
    }

    public void setNatureTypes(List<ClassificationRef> natureTypes) {
        this.natureTypes = natureTypes;
    }

    public List<OrganizationRef> getOrganizations() {
        return organizations;
    }

    public void setOrganizations(List<OrganizationRef> organizations) {
        this.organizations = organizations;
    }

    public OrganizationRef getManagingOrganization() {
        return managingOrganization;
    }

    public void setManagingOrganization(OrganizationRef managingOrganization) {
        this.managingOrganization = managingOrganization;
    }

    public LocalizedText getTitle() {
        return title;
    }

    public void setTitle(LocalizedText title) {
        this.title = title;
    }

    public ClassificationRef getType() {
        return type;
    }

    public void setType(ClassificationRef type) {
        this.type = type;
    }

    public WorkflowItem getWorkflow() {
        return workflow;
    }

    public void setWorkflow(WorkflowItem workflow) {
        this.workflow = workflow;
    }

    public VisibilityItem getVisibility() {
        return visibility;
    }

    public void setVisibility(VisibilityItem visibility) {
        this.visibility = visibility;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }
}
