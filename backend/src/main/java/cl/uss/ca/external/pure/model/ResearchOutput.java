package cl.uss.ca.external.pure.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ResearchOutput {
    private Long pureId;
    private String uuid;
    private String typeDiscriminator;
    private String title;
    private String typeUri;
    private String typeEs;
    private String typeEn;
    private String categoryUri;
    private String categoryEs;
    private String categoryEn;
    private Boolean peerReview;
    private String languageUri;
    private String languageEs;
    private String languageEn;
    private Integer submissionYear;
    private Integer totalNumberOfContributors;
    private String managingOrganizationUuid;
    private String pages;
    private String volume;
    private String journalNumber;
    private Integer numberOfPages;
    private String abstractEs;
    private String abstractEn;
    private String portalUrl;
    private String visibilityKey;
    private String visibilityEs;
    private String visibilityEn;
    private String workflowStep;
    private String workflowEs;
    private String workflowEn;
    private String createdBy;
    private OffsetDateTime createdDate;
    private String modifiedBy;
    private OffsetDateTime modifiedDate;
    private String version;
    private List<OutputStatus> statuses = new ArrayList<>();
    private List<OutputContributor> contributors = new ArrayList<>();
    private List<ElectronicVersion> electronicVersions = new ArrayList<>();
    private List<OutputIdentifier> identifiers = new ArrayList<>();
    private List<OutputLink> links = new ArrayList<>();
    private JournalData journal;
    private Long journalAssociationPureId;
    private List<String> externalOrganizationUuids = new ArrayList<>();
    private List<KeywordGroupData> keywordGroups = new ArrayList<>();
    private int identifiersDuplicadosOmitidos;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTypeUri() {
        return typeUri;
    }

    public void setTypeUri(String typeUri) {
        this.typeUri = typeUri;
    }

    public String getTypeEs() {
        return typeEs;
    }

    public void setTypeEs(String typeEs) {
        this.typeEs = typeEs;
    }

    public String getTypeEn() {
        return typeEn;
    }

    public void setTypeEn(String typeEn) {
        this.typeEn = typeEn;
    }

    public String getCategoryUri() {
        return categoryUri;
    }

    public void setCategoryUri(String categoryUri) {
        this.categoryUri = categoryUri;
    }

    public String getCategoryEs() {
        return categoryEs;
    }

    public void setCategoryEs(String categoryEs) {
        this.categoryEs = categoryEs;
    }

    public String getCategoryEn() {
        return categoryEn;
    }

    public void setCategoryEn(String categoryEn) {
        this.categoryEn = categoryEn;
    }

    public Boolean getPeerReview() {
        return peerReview;
    }

    public void setPeerReview(Boolean peerReview) {
        this.peerReview = peerReview;
    }

    public String getLanguageUri() {
        return languageUri;
    }

    public void setLanguageUri(String languageUri) {
        this.languageUri = languageUri;
    }

    public String getLanguageEs() {
        return languageEs;
    }

    public void setLanguageEs(String languageEs) {
        this.languageEs = languageEs;
    }

    public String getLanguageEn() {
        return languageEn;
    }

    public void setLanguageEn(String languageEn) {
        this.languageEn = languageEn;
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

    public String getManagingOrganizationUuid() {
        return managingOrganizationUuid;
    }

    public void setManagingOrganizationUuid(String managingOrganizationUuid) {
        this.managingOrganizationUuid = managingOrganizationUuid;
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

    public String getAbstractEs() {
        return abstractEs;
    }

    public void setAbstractEs(String abstractEs) {
        this.abstractEs = abstractEs;
    }

    public String getAbstractEn() {
        return abstractEn;
    }

    public void setAbstractEn(String abstractEn) {
        this.abstractEn = abstractEn;
    }

    public String getPortalUrl() {
        return portalUrl;
    }

    public void setPortalUrl(String portalUrl) {
        this.portalUrl = portalUrl;
    }

    public String getVisibilityKey() {
        return visibilityKey;
    }

    public void setVisibilityKey(String visibilityKey) {
        this.visibilityKey = visibilityKey;
    }

    public String getVisibilityEs() {
        return visibilityEs;
    }

    public void setVisibilityEs(String visibilityEs) {
        this.visibilityEs = visibilityEs;
    }

    public String getVisibilityEn() {
        return visibilityEn;
    }

    public void setVisibilityEn(String visibilityEn) {
        this.visibilityEn = visibilityEn;
    }

    public String getWorkflowStep() {
        return workflowStep;
    }

    public void setWorkflowStep(String workflowStep) {
        this.workflowStep = workflowStep;
    }

    public String getWorkflowEs() {
        return workflowEs;
    }

    public void setWorkflowEs(String workflowEs) {
        this.workflowEs = workflowEs;
    }

    public String getWorkflowEn() {
        return workflowEn;
    }

    public void setWorkflowEn(String workflowEn) {
        this.workflowEn = workflowEn;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public OffsetDateTime getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(OffsetDateTime modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<OutputStatus> getStatuses() {
        return statuses;
    }

    public void setStatuses(List<OutputStatus> statuses) {
        this.statuses = statuses == null ? new ArrayList<>() : statuses;
    }

    public List<OutputContributor> getContributors() {
        return contributors;
    }

    public void setContributors(List<OutputContributor> contributors) {
        this.contributors = contributors == null ? new ArrayList<>() : contributors;
    }

    public List<ElectronicVersion> getElectronicVersions() {
        return electronicVersions;
    }

    public void setElectronicVersions(List<ElectronicVersion> electronicVersions) {
        this.electronicVersions = electronicVersions == null ? new ArrayList<>() : electronicVersions;
    }

    public List<OutputIdentifier> getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(List<OutputIdentifier> identifiers) {
        this.identifiers = identifiers == null ? new ArrayList<>() : identifiers;
    }

    public List<OutputLink> getLinks() {
        return links;
    }

    public void setLinks(List<OutputLink> links) {
        this.links = links == null ? new ArrayList<>() : links;
    }

    public JournalData getJournal() {
        return journal;
    }

    public void setJournal(JournalData journal) {
        this.journal = journal;
    }

    public Long getJournalAssociationPureId() {
        return journalAssociationPureId;
    }

    public void setJournalAssociationPureId(Long journalAssociationPureId) {
        this.journalAssociationPureId = journalAssociationPureId;
    }

    public List<String> getExternalOrganizationUuids() {
        return externalOrganizationUuids;
    }

    public void setExternalOrganizationUuids(List<String> externalOrganizationUuids) {
        this.externalOrganizationUuids = externalOrganizationUuids == null
                ? new ArrayList<>() : externalOrganizationUuids;
    }

    public List<KeywordGroupData> getKeywordGroups() {
        return keywordGroups;
    }

    public void setKeywordGroups(List<KeywordGroupData> keywordGroups) {
        this.keywordGroups = keywordGroups == null ? new ArrayList<>() : keywordGroups;
    }

    public int getIdentifiersDuplicadosOmitidos() {
        return identifiersDuplicadosOmitidos;
    }

    public void setIdentifiersDuplicadosOmitidos(int identifiersDuplicadosOmitidos) {
        this.identifiersDuplicadosOmitidos = identifiersDuplicadosOmitidos;
    }

    public boolean tieneDoi() {
        return electronicVersions.stream()
                .anyMatch(version -> version.getDoi() != null && !version.getDoi().isBlank());
    }

    public boolean esContributorInterno(OutputContributor contributor) {
        return contributor != null && contributor.esInterno();
    }
}
