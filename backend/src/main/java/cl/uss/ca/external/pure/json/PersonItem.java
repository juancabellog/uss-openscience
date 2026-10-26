package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PersonItem {

    private Long pureId;
    private String uuid;
    private String createdBy;
    private String createdDate;
    private String modifiedBy;
    private String modifiedDate;
    private PersonName name;
    private String orcid;
    private Boolean orcidAuthenticated;
    private List<StaffOrganizationAssociationItem> staffOrganizationAssociations;
    private List<TitleItem> titles;
    private ClassificationRef gender;
    private List<IdentifierItem> identifiers;

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

    public PersonName getName() {
        return name;
    }

    public void setName(PersonName name) {
        this.name = name;
    }

    public String getOrcid() {
        return orcid;
    }

    public void setOrcid(String orcid) {
        this.orcid = orcid;
    }

    public Boolean getOrcidAuthenticated() {
        return orcidAuthenticated;
    }

    public void setOrcidAuthenticated(Boolean orcidAuthenticated) {
        this.orcidAuthenticated = orcidAuthenticated;
    }

    public List<StaffOrganizationAssociationItem> getStaffOrganizationAssociations() {
        return staffOrganizationAssociations;
    }

    public void setStaffOrganizationAssociations(List<StaffOrganizationAssociationItem> staffOrganizationAssociations) {
        this.staffOrganizationAssociations = staffOrganizationAssociations;
    }

    public List<TitleItem> getTitles() {
        return titles;
    }

    public void setTitles(List<TitleItem> titles) {
        this.titles = titles;
    }

    public ClassificationRef getGender() {
        return gender;
    }

    public void setGender(ClassificationRef gender) {
        this.gender = gender;
    }

    public List<IdentifierItem> getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(List<IdentifierItem> identifiers) {
        this.identifiers = identifiers;
    }
}
