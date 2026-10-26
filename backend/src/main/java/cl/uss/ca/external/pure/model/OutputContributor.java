package cl.uss.ca.external.pure.model;

import java.util.ArrayList;
import java.util.List;

public class OutputContributor {
    private Long pureId;
    private String typeDiscriminator;
    private String firstName;
    private String lastName;
    private String roleUri;
    private String roleEs;
    private String roleEn;
    private String personUuid;
    private String externalPersonUuid;
    private Boolean hidden;
    private Boolean correspondingAuthor;
    private Boolean associationHidden;
    private List<String> organizationUuids = new ArrayList<>();

    public boolean esInterno() {
        return typeDiscriminator != null
                && typeDiscriminator.toLowerCase().contains("internal");
    }

    public boolean esExterno() {
        return typeDiscriminator != null
                && typeDiscriminator.toLowerCase().contains("external");
    }

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

    public String getRoleEs() {
        return roleEs;
    }

    public void setRoleEs(String roleEs) {
        this.roleEs = roleEs;
    }

    public String getRoleEn() {
        return roleEn;
    }

    public void setRoleEn(String roleEn) {
        this.roleEn = roleEn;
    }

    public String getPersonUuid() {
        return personUuid;
    }

    public void setPersonUuid(String personUuid) {
        this.personUuid = personUuid;
    }

    public String getExternalPersonUuid() {
        return externalPersonUuid;
    }

    public void setExternalPersonUuid(String externalPersonUuid) {
        this.externalPersonUuid = externalPersonUuid;
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

    public List<String> getOrganizationUuids() {
        return organizationUuids;
    }

    public void setOrganizationUuids(List<String> organizationUuids) {
        this.organizationUuids = organizationUuids == null ? new ArrayList<>() : organizationUuids;
    }
}
