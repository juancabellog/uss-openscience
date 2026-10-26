package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.PureOrganization;

import java.time.OffsetDateTime;

public class OrganizationSnapshot {
    public Long id;
    public Long pureId;
    public String uuid;
    public Boolean activeInPure;
    public String typeDiscriminator;
    public String nameEs;
    public String nameEn;
    public String createdBy;
    public OffsetDateTime createdDatePure;
    public String modifiedBy;
    public OffsetDateTime modifiedDatePure;
    public String version;
    public String systemName;
    public String typeUri;
    public String typeEn;
    public String typeEs;
    public String parentUuid;

    public PureOrganization asOrganization() {
        PureOrganization organization = new PureOrganization();
        organization.setPureId(pureId);
        organization.setUuid(uuid);
        organization.setTypeDiscriminator(typeDiscriminator);
        organization.setNameEs(nameEs);
        organization.setNameEn(nameEn);
        organization.setCreatedBy(createdBy);
        organization.setCreatedDatePure(createdDatePure);
        organization.setModifiedBy(modifiedBy);
        organization.setModifiedDatePure(modifiedDatePure);
        organization.setVersion(version);
        organization.setSystemName(systemName);
        organization.setTypeUri(typeUri);
        organization.setTypeEn(typeEn);
        organization.setTypeEs(typeEs);
        organization.setParentUuid(parentUuid);
        return organization;
    }
}
