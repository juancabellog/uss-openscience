package cl.uss.ca.external.pure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Content-item envelope for GET /organizations. Unknown PURE fields are ignored.
 * Confirmed in-repo: OrganizationRef {uuid, systemName, name}. Envelope fields
 * match JournalItem/ProjectItem. type and parentOrganization/parents are optional.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrganizationItem {
    private Long pureId;
    private String uuid;
    private String createdBy;
    private String createdDate;
    private String modifiedBy;
    private String modifiedDate;
    private String version;
    private String systemName;
    private String typeDiscriminator;
    private LocalizedText name;
    private ClassificationRef type;
    private OrganizationRef parentOrganization;
    private List<OrganizationRef> parents;

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

    public String getTypeDiscriminator() {
        return typeDiscriminator;
    }

    public void setTypeDiscriminator(String typeDiscriminator) {
        this.typeDiscriminator = typeDiscriminator;
    }

    public LocalizedText getName() {
        return name;
    }

    public void setName(LocalizedText name) {
        this.name = name;
    }

    public ClassificationRef getType() {
        return type;
    }

    public void setType(ClassificationRef type) {
        this.type = type;
    }

    public OrganizationRef getParentOrganization() {
        return parentOrganization;
    }

    public void setParentOrganization(OrganizationRef parentOrganization) {
        this.parentOrganization = parentOrganization;
    }

    public List<OrganizationRef> getParents() {
        return parents;
    }

    public void setParents(List<OrganizationRef> parents) {
        this.parents = parents;
    }
}
