package cl.uss.ca.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ResearchOutputItem {
    private Long pureId;
    private String uuid;
    private String typeDiscriminator;
    private String createdDate;
    private String modifiedDate;
    private String portalUrl;
    private String version;
    private JsonNode title;
    private ClassificationRef type;
    private ClassificationRef category;
    private Boolean peerReview;
    private ClassificationRef language;
    private Integer submissionYear;
    private Integer totalNumberOfContributors;
    private OrganizationRef managingOrganization;
    private String pages;
    private String volume;
    private String journalNumber;
    private Integer numberOfPages;
    private LocalizedText abstractText;
    private VisibilityItem visibility;
    private WorkflowItem workflow;
    private List<PublicationStatusItem> publicationStatuses;
    private List<ContributorItem> contributors;
    private List<ElectronicVersionItem> electronicVersions;
    private List<IdentifierItem> identifiers;
    private List<LinkItem> links;
    private JournalAssociationItem journalAssociation;
    private List<OrganizationRef> externalOrganizations;
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

    public String getTypeDiscriminator() {
        return typeDiscriminator;
    }

    public void setTypeDiscriminator(String typeDiscriminator) {
        this.typeDiscriminator = typeDiscriminator;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public JsonNode getTitle() {
        return title;
    }

    public void setTitle(JsonNode title) {
        this.title = title;
    }

    public ClassificationRef getType() {
        return type;
    }

    public void setType(ClassificationRef type) {
        this.type = type;
    }

    public ClassificationRef getCategory() {
        return category;
    }

    public void setCategory(ClassificationRef category) {
        this.category = category;
    }

    public Boolean getPeerReview() {
        return peerReview;
    }

    public void setPeerReview(Boolean peerReview) {
        this.peerReview = peerReview;
    }

    public ClassificationRef getLanguage() {
        return language;
    }

    public void setLanguage(ClassificationRef language) {
        this.language = language;
    }

    public Integer getSubmissionYear() {
        return submissionYear;
    }

    public void setSubmissionYear(Integer submissionYear) {
        this.submissionYear = submissionYear;
    }

    public Integer getTotalNumberOfContributors() {
        return totalNumberOfContributors;
    }

    public void setTotalNumberOfContributors(Integer totalNumberOfContributors) {
        this.totalNumberOfContributors = totalNumberOfContributors;
    }

    public OrganizationRef getManagingOrganization() {
        return managingOrganization;
    }

    public void setManagingOrganization(OrganizationRef managingOrganization) {
        this.managingOrganization = managingOrganization;
    }

    public String getPages() {
        return pages;
    }

    public void setPages(String pages) {
        this.pages = pages;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getJournalNumber() {
        return journalNumber;
    }

    public void setJournalNumber(String journalNumber) {
        this.journalNumber = journalNumber;
    }

    public Integer getNumberOfPages() {
        return numberOfPages;
    }

    public void setNumberOfPages(Integer numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    public LocalizedText getAbstractText() {
        return abstractText;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("abstract")
    public void setAbstractText(LocalizedText abstractText) {
        this.abstractText = abstractText;
    }

    public VisibilityItem getVisibility() {
        return visibility;
    }

    public void setVisibility(VisibilityItem visibility) {
        this.visibility = visibility;
    }

    public WorkflowItem getWorkflow() {
        return workflow;
    }

    public void setWorkflow(WorkflowItem workflow) {
        this.workflow = workflow;
    }

    public List<PublicationStatusItem> getPublicationStatuses() {
        return publicationStatuses;
    }

    public void setPublicationStatuses(List<PublicationStatusItem> publicationStatuses) {
        this.publicationStatuses = publicationStatuses;
    }

    public List<ContributorItem> getContributors() {
        return contributors;
    }

    public void setContributors(List<ContributorItem> contributors) {
        this.contributors = contributors;
    }

    public List<ElectronicVersionItem> getElectronicVersions() {
        return electronicVersions;
    }

    public void setElectronicVersions(List<ElectronicVersionItem> electronicVersions) {
        this.electronicVersions = electronicVersions;
    }

    public List<IdentifierItem> getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(List<IdentifierItem> identifiers) {
        this.identifiers = identifiers;
    }

    public List<LinkItem> getLinks() {
        return links;
    }

    public void setLinks(List<LinkItem> links) {
        this.links = links;
    }

    public JournalAssociationItem getJournalAssociation() {
        return journalAssociation;
    }

    public void setJournalAssociation(JournalAssociationItem journalAssociation) {
        this.journalAssociation = journalAssociation;
    }

    public List<OrganizationRef> getExternalOrganizations() {
        return externalOrganizations;
    }

    public void setExternalOrganizations(List<OrganizationRef> externalOrganizations) {
        this.externalOrganizations = externalOrganizations;
    }

    public List<KeywordGroupItem> getKeywordGroups() {
        return keywordGroups;
    }

    public void setKeywordGroups(List<KeywordGroupItem> keywordGroups) {
        this.keywordGroups = keywordGroups;
    }
}
