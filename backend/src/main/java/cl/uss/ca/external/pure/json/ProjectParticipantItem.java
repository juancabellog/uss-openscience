package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectParticipantItem {
    private String typeDiscriminator;
    private Long pureId;
    private PersonName name;
    private ClassificationRef role;
    private Boolean associationHidden;
    private OrganizationRef person;
    private List<OrganizationRef> organizations;

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

    public Boolean getAssociationHidden() {
        return associationHidden;
    }

    public void setAssociationHidden(Boolean associationHidden) {
        this.associationHidden = associationHidden;
    }

    public OrganizationRef getPerson() {
        return person;
    }

    public void setPerson(OrganizationRef person) {
        this.person = person;
    }

    public List<OrganizationRef> getOrganizations() {
        return organizations;
    }

    public void setOrganizations(List<OrganizationRef> organizations) {
        this.organizations = organizations;
    }
}
