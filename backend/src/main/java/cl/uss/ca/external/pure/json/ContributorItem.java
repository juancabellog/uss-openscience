package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ContributorItem {
    private Long pureId;
    private String typeDiscriminator;
    private PersonName name;
    private ClassificationRef role;
    private OrganizationRef person;
    private OrganizationRef externalPerson;
    private Boolean hidden;
    private Boolean correspondingAuthor;
    private Boolean associationHidden;
    private List<OrganizationRef> externalOrganizations;

    public Long getPureId() {
        return pureId;
    }

    public void setPureId(Long pureId) {
        this.pureId = pureId;
    }

    public String getTypeDiscriminator() {
        return typeDiscriminator;
    }

    public void setTypeDiscriminator(String typeDiscriminator) {
        this.typeDiscriminator = typeDiscriminator;
    }

    public PersonName getName() {
        return name;
    }

    public void setName(PersonName name) {
        this.name = name;
    }

    public ClassificationRef getRole() {
        return role;
    }

    public void setRole(ClassificationRef role) {
        this.role = role;
    }

    public OrganizationRef getPerson() {
        return person;
    }

    public void setPerson(OrganizationRef person) {
        this.person = person;
    }

    public OrganizationRef getExternalPerson() {
        return externalPerson;
    }

    public void setExternalPerson(OrganizationRef externalPerson) {
        this.externalPerson = externalPerson;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public Boolean getCorrespondingAuthor() {
        return correspondingAuthor;
    }

    public void setCorrespondingAuthor(Boolean correspondingAuthor) {
        this.correspondingAuthor = correspondingAuthor;
    }

    public Boolean getAssociationHidden() {
        return associationHidden;
    }

    public void setAssociationHidden(Boolean associationHidden) {
        this.associationHidden = associationHidden;
    }

    public List<OrganizationRef> getExternalOrganizations() {
        return externalOrganizations;
    }

    public void setExternalOrganizations(List<OrganizationRef> externalOrganizations) {
        this.externalOrganizations = externalOrganizations;
    }
}
