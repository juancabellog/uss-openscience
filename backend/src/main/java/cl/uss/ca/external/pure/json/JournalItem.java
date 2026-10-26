package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JournalItem {
    private Long pureId;
    private String uuid;
    private String createdBy;
    private String createdDate;
    private String modifiedBy;
    private String modifiedDate;
    private String version;
    private String systemName;
    private OrganizationRef publisher;
    private ClassificationRef type;
    private WorkflowItem workflow;
    private List<JournalTitleItem> titles;
    private List<IssnItem> issns;
    private List<IdentifierItem> identifiers;
    private List<KeywordGroupItem> keywordGroups;

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public OrganizationRef getPublisher() {
        return publisher;
    }

    public void setPublisher(OrganizationRef publisher) {
        this.publisher = publisher;
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

    public List<JournalTitleItem> getTitles() {
        return titles;
    }

    public void setTitles(List<JournalTitleItem> titles) {
        this.titles = titles;
    }

    public List<IssnItem> getIssns() {
        return issns;
    }

    public void setIssns(List<IssnItem> issns) {
        this.issns = issns;
    }

    public List<IdentifierItem> getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(List<IdentifierItem> identifiers) {
        this.identifiers = identifiers;
    }

    public List<KeywordGroupItem> getKeywordGroups() {
        return keywordGroups;
    }

    public void setKeywordGroups(List<KeywordGroupItem> keywordGroups) {
        this.keywordGroups = keywordGroups;
    }
}
