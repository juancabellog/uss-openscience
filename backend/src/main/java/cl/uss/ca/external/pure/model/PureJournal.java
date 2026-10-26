package cl.uss.ca.external.pure.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class PureJournal {
    private Long pureId;
    private String uuid;
    private String createdBy;
    private OffsetDateTime createdDatePure;
    private String modifiedBy;
    private OffsetDateTime modifiedDatePure;
    private String version;
    private String publisherUuid;
    private String publisherSystemName;
    private String typeUri;
    private String typeEn;
    private String typeEs;
    private String workflowStep;
    private String workflowDescriptionEn;
    private String workflowDescriptionEs;
    private String systemName;
    private final List<JournalTitleData> titles = new ArrayList<>();
    private final List<JournalIssnData> issns = new ArrayList<>();
    private final List<JournalIdentifierData> identifiers = new ArrayList<>();
    private final List<JournalKeywordGroupData> keywordGroups = new ArrayList<>();

    public String displayTitle() {
        return titles.isEmpty() ? null : titles.get(0).getTitle();
    }

    public String displayIssn() {
        return issns.isEmpty() ? null : issns.get(0).getIssn();
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPublisherUuid() {
        return publisherUuid;
    }

    public void setPublisherUuid(String publisherUuid) {
        this.publisherUuid = publisherUuid;
    }

    public String getPublisherSystemName() {
        return publisherSystemName;
    }

    public void setPublisherSystemName(String publisherSystemName) {
        this.publisherSystemName = publisherSystemName;
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

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public List<JournalTitleData> getTitles() {
        return titles;
    }

    public List<JournalIssnData> getIssns() {
        return issns;
    }

    public List<JournalIdentifierData> getIdentifiers() {
        return identifiers;
    }

    public List<JournalKeywordGroupData> getKeywordGroups() {
        return keywordGroups;
    }
}
