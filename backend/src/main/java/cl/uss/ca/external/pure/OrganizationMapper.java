package cl.uss.ca.external.pure;

import cl.uss.ca.external.pure.json.ClassificationRef;
import cl.uss.ca.external.pure.json.OrganizationItem;
import cl.uss.ca.external.pure.json.OrganizationRef;
import cl.uss.ca.external.pure.model.PureOrganization;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@ApplicationScoped
public class OrganizationMapper {

    public PureOrganization mapear(OrganizationItem item) {
        if (item == null) {
            return null;
        }
        String uuid = LocalizedTexts.trimToNull(item.getUuid());
        if (uuid == null) {
            return null;
        }
        PureOrganization organization = new PureOrganization();
        organization.setPureId(item.getPureId());
        organization.setUuid(uuid);
        organization.setTypeDiscriminator(LocalizedTexts.trimToNull(item.getTypeDiscriminator()));
        organization.setNameEs(LocalizedTexts.es(item.getName()));
        organization.setNameEn(LocalizedTexts.en(item.getName()));
        organization.setCreatedBy(LocalizedTexts.trimToNull(item.getCreatedBy()));
        organization.setCreatedDatePure(parseDateTime(item.getCreatedDate()));
        organization.setModifiedBy(LocalizedTexts.trimToNull(item.getModifiedBy()));
        organization.setModifiedDatePure(parseDateTime(item.getModifiedDate()));
        organization.setVersion(LocalizedTexts.trimToNull(item.getVersion()));
        organization.setSystemName(LocalizedTexts.trimToNull(item.getSystemName()));
        ClassificationRef type = item.getType();
        if (type != null) {
            organization.setTypeUri(LocalizedTexts.uri(type));
            organization.setTypeEn(LocalizedTexts.en(type.getTerm()));
            organization.setTypeEs(LocalizedTexts.es(type.getTerm()));
        }
        organization.setParentUuid(parentUuid(item));
        return organization;
    }

    private static String parentUuid(OrganizationItem item) {
        OrganizationRef parent = item.getParentOrganization();
        if (parent != null) {
            String uuid = LocalizedTexts.trimToNull(parent.getUuid());
            if (uuid != null) {
                return uuid;
            }
        }
        for (OrganizationRef candidate : empty(item.getParents())) {
            if (candidate == null) {
                continue;
            }
            String uuid = LocalizedTexts.trimToNull(candidate.getUuid());
            if (uuid != null) {
                return uuid;
            }
        }
        return null;
    }

    private static OffsetDateTime parseDateTime(String raw) {
        String value = LocalizedTexts.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static <T> List<T> empty(List<T> items) {
        return items == null ? List.of() : items;
    }
}
