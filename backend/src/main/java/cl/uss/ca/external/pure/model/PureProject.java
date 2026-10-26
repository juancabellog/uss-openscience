package cl.uss.ca.external.pure.model;

import cl.uss.ca.external.pure.LocalizedTexts;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class PureProject {
    private Long pureId;
    private String uuid;
    private String typeDiscriminator;
    private String titleEs;
    private String titleEn;
    private String acronym;
    private String createdBy;
    private OffsetDateTime createdDatePure;
    private String modifiedBy;
    private OffsetDateTime modifiedDatePure;
    private String portalUrl;
    private String version;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private String typeUri;
    private String typeEn;
    private String typeEs;
    private String workflowStep;
    private String workflowDescriptionEn;
    private String workflowDescriptionEs;
    private String visibilityKey;
    private String visibilityEn;
    private String visibilityEs;
    private String managingOrganizationUuid;
    private String systemName;
    private final List<String> prettyUrlIdentifiers = new ArrayList<>();
    private final List<ProjectParticipantData> participants = new ArrayList<>();
    private final List<ProjectDescriptionData> descriptions = new ArrayList<>();
    private final List<ProjectIdentifierData> identifiers = new ArrayList<>();
    private final List<ProjectNatureTypeData> natureTypes = new ArrayList<>();
    private final List<ProjectOrganizationData> organizations = new ArrayList<>();

    public String displayTitle() {
        String preferred = LocalizedTexts.trimToNull(titleEs);
        if (preferred != null) {
            return preferred;
        }
        preferred = LocalizedTexts.trimToNull(titleEn);
        if (preferred != null) {
            return preferred;
        }
        return LocalizedTexts.trimToNull(acronym);
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

    public String getTypeDiscriminator() {
        return typeDiscriminator;
    }

    public void setTypeDiscriminator(String typeDiscriminator) {
        this.typeDiscriminator = typeDiscriminator;
    }

    public String getTitleEs() {
        return titleEs;
    }

    public void setTitleEs(String titleEs) {
        this.titleEs = titleEs;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getAcronym() {
        return acronym;
    }

    public void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedDatePure() {
        return createdDatePure;
    }

    public void setCreatedDatePure(OffsetDateTime createdDatePure) {
        this.createdDatePure = createdDatePure;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public OffsetDateTime getModifiedDatePure() {
        return modifiedDatePure;
    }

    public void setModifiedDatePure(OffsetDateTime modifiedDatePure) {
        this.modifiedDatePure = modifiedDatePure;
    }

    public String getPortalUrl() {
        return portalUrl;
    }

    public void setPortalUrl(String portalUrl) {
        this.portalUrl = portalUrl;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public LocalDate getPeriodStartDate() {
        return periodStartDate;
    }

    public void setPeriodStartDate(LocalDate periodStartDate) {
        this.periodStartDate = periodStartDate;
    }

    public LocalDate getPeriodEndDate() {
        return periodEndDate;
    }

    public void setPeriodEndDate(LocalDate periodEndDate) {
        this.periodEndDate = periodEndDate;
    }

    public String getTypeUri() {
        return typeUri;
    }

    public void setTypeUri(String typeUri) {
        this.typeUri = typeUri;
    }

    public String getTypeEn() {
        return typeEn;
    }

    public void setTypeEn(String typeEn) {
        this.typeEn = typeEn;
    }

    public String getTypeEs() {
        return typeEs;
    }

    public void setTypeEs(String typeEs) {
        this.typeEs = typeEs;
    }

    public String getWorkflowStep() {
        return workflowStep;
    }

    public void setWorkflowStep(String workflowStep) {
        this.workflowStep = workflowStep;
    }

    public String getWorkflowDescriptionEn() {
        return workflowDescriptionEn;
    }

    public void setWorkflowDescriptionEn(String workflowDescriptionEn) {
        this.workflowDescriptionEn = workflowDescriptionEn;
    }

    public String getWorkflowDescriptionEs() {
        return workflowDescriptionEs;
    }

    public void setWorkflowDescriptionEs(String workflowDescriptionEs) {
        this.workflowDescriptionEs = workflowDescriptionEs;
    }

    public String getVisibilityKey() {
        return visibilityKey;
    }

    public void setVisibilityKey(String visibilityKey) {
        this.visibilityKey = visibilityKey;
    }

    public String getVisibilityEn() {
        return visibilityEn;
    }

    public void setVisibilityEn(String visibilityEn) {
        this.visibilityEn = visibilityEn;
    }

    public String getVisibilityEs() {
        return visibilityEs;
    }

    public void setVisibilityEs(String visibilityEs) {
        this.visibilityEs = visibilityEs;
    }

    public String getManagingOrganizationUuid() {
        return managingOrganizationUuid;
    }

    public void setManagingOrganizationUuid(String managingOrganizationUuid) {
        this.managingOrganizationUuid = managingOrganizationUuid;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public List<String> getPrettyUrlIdentifiers() {
        return prettyUrlIdentifiers;
    }

    public List<ProjectParticipantData> getParticipants() {
        return participants;
    }

    public List<ProjectDescriptionData> getDescriptions() {
        return descriptions;
    }

    public List<ProjectIdentifierData> getIdentifiers() {
        return identifiers;
    }

    public List<ProjectNatureTypeData> getNatureTypes() {
        return natureTypes;
    }

    public List<ProjectOrganizationData> getOrganizations() {
        return organizations;
    }
}
