package cl.uss.ca.external.pure.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectParticipantData {
    private Long pureId;
    private String typeDiscriminator;
    private String firstName;
    private String lastName;
    private String roleUri;
    private String roleTermEn;
    private String roleTermEs;
    private Boolean associationHidden;
    private String personUuid;
    private final List<ProjectOrganizationData> organizations = new ArrayList<>();

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

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getRoleUri() {
        return roleUri;
    }

    public void setRoleUri(String roleUri) {
        this.roleUri = roleUri;
    }

    public String getRoleTermEn() {
        return roleTermEn;
    }

    public void setRoleTermEn(String roleTermEn) {
        this.roleTermEn = roleTermEn;
    }

    public String getRoleTermEs() {
        return roleTermEs;
    }

    public void setRoleTermEs(String roleTermEs) {
        this.roleTermEs = roleTermEs;
    }

    public Boolean getAssociationHidden() {
        return associationHidden;
    }

    public void setAssociationHidden(Boolean associationHidden) {
        this.associationHidden = associationHidden;
    }

    public String getPersonUuid() {
        return personUuid;
    }

    public void setPersonUuid(String personUuid) {
        this.personUuid = personUuid;
    }

    public List<ProjectOrganizationData> getOrganizations() {
        return organizations;
    }
}
