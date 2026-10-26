package cl.uss.ca.external.pure;

import cl.uss.ca.external.pure.json.ClassificationRef;
import cl.uss.ca.external.pure.json.DateRange;
import cl.uss.ca.external.pure.json.IdentifierItem;
import cl.uss.ca.external.pure.json.OrganizationRef;
import cl.uss.ca.external.pure.json.PersonName;
import cl.uss.ca.external.pure.json.ProjectDescriptionItem;
import cl.uss.ca.external.pure.json.ProjectItem;
import cl.uss.ca.external.pure.json.ProjectParticipantItem;
import cl.uss.ca.external.pure.json.VisibilityItem;
import cl.uss.ca.external.pure.json.WorkflowItem;
import cl.uss.ca.external.pure.model.ProjectDescriptionData;
import cl.uss.ca.external.pure.model.ProjectIdentifierData;
import cl.uss.ca.external.pure.model.ProjectNatureTypeData;
import cl.uss.ca.external.pure.model.ProjectOrganizationData;
import cl.uss.ca.external.pure.model.ProjectParticipantData;
import cl.uss.ca.external.pure.model.PureProject;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ProjectMapper {

    public PureProject mapear(ProjectItem item) {
        if (item == null) {
            return null;
        }
        String uuid = LocalizedTexts.trimToNull(item.getUuid());
        if (uuid == null) {
            return null;
        }
        PureProject project = new PureProject();
        project.setPureId(item.getPureId());
        project.setUuid(uuid);
        project.setTypeDiscriminator(LocalizedTexts.trimToNull(item.getTypeDiscriminator()));
        project.setTitleEs(LocalizedTexts.es(item.getTitle()));
        project.setTitleEn(LocalizedTexts.en(item.getTitle()));
        project.setAcronym(LocalizedTexts.trimToNull(item.getAcronym()));
        project.setCreatedBy(LocalizedTexts.trimToNull(item.getCreatedBy()));
        project.setCreatedDatePure(parseDateTime(item.getCreatedDate()));
        project.setModifiedBy(LocalizedTexts.trimToNull(item.getModifiedBy()));
        project.setModifiedDatePure(parseDateTime(item.getModifiedDate()));
        project.setPortalUrl(LocalizedTexts.trimToNull(item.getPortalUrl()));
        project.setVersion(LocalizedTexts.trimToNull(item.getVersion()));
        DateRange period = item.getPeriod();
        if (period != null) {
            project.setPeriodStartDate(parseDate(period.getStartDate()));
            project.setPeriodEndDate(parseDate(period.getEndDate()));
        }
        ClassificationRef type = item.getType();
        if (type != null) {
            project.setTypeUri(LocalizedTexts.uri(type));
            project.setTypeEn(LocalizedTexts.en(type.getTerm()));
            project.setTypeEs(LocalizedTexts.es(type.getTerm()));
        }
        WorkflowItem workflow = item.getWorkflow();
        if (workflow != null) {
            project.setWorkflowStep(LocalizedTexts.trimToNull(workflow.getStep()));
            project.setWorkflowDescriptionEn(LocalizedTexts.en(workflow.getDescription()));
            project.setWorkflowDescriptionEs(LocalizedTexts.es(workflow.getDescription()));
        }
        VisibilityItem visibility = item.getVisibility();
        if (visibility != null) {
            project.setVisibilityKey(LocalizedTexts.trimToNull(visibility.getKey()));
            project.setVisibilityEn(LocalizedTexts.en(visibility.getDescription()));
            project.setVisibilityEs(LocalizedTexts.es(visibility.getDescription()));
        }
        OrganizationRef managing = item.getManagingOrganization();
        if (managing != null) {
            project.setManagingOrganizationUuid(LocalizedTexts.trimToNull(managing.getUuid()));
        }
        project.setSystemName(LocalizedTexts.trimToNull(item.getSystemName()));
        mapPrettyUrls(item.getPrettyUrlIdentifiers(), project);
        mapParticipants(item.getParticipants(), project);
        mapDescriptions(item.getDescriptions(), project);
        mapIdentifiers(item.getIdentifiers(), project);
        mapNatureTypes(item.getNatureTypes(), project);
        mapOrganizations(item.getOrganizations(), project);
        return project;
    }

    private static void mapPrettyUrls(List<String> items, PureProject project) {
        Set<String> seen = new LinkedHashSet<>();
        for (String item : empty(items)) {
            String value = LocalizedTexts.trimToNull(item);
            if (value == null || !seen.add(value)) {
                continue;
            }
            project.getPrettyUrlIdentifiers().add(value);
        }
    }

    private static void mapParticipants(List<ProjectParticipantItem> items, PureProject project) {
        for (ProjectParticipantItem item : empty(items)) {
            if (item == null) {
                continue;
            }
            ProjectParticipantData data = new ProjectParticipantData();
            data.setPureId(item.getPureId());
            data.setTypeDiscriminator(LocalizedTexts.trimToNull(item.getTypeDiscriminator()));
            PersonName name = item.getName();
            if (name != null) {
                data.setFirstName(LocalizedTexts.trimToNull(name.getFirstName()));
                data.setLastName(LocalizedTexts.trimToNull(name.getLastName()));
            }
            ClassificationRef role = item.getRole();
            if (role != null) {
                data.setRoleUri(LocalizedTexts.uri(role));
                data.setRoleTermEn(LocalizedTexts.en(role.getTerm()));
                data.setRoleTermEs(LocalizedTexts.es(role.getTerm()));
            }
            data.setAssociationHidden(item.getAssociationHidden());
            OrganizationRef person = item.getPerson();
            if (person != null) {
                data.setPersonUuid(LocalizedTexts.trimToNull(person.getUuid()));
            }
            Set<String> seenOrgs = new LinkedHashSet<>();
            for (OrganizationRef organization : empty(item.getOrganizations())) {
                ProjectOrganizationData org = organizationData(organization);
                if (org == null || !seenOrgs.add(org.getOrganizationUuid())) {
                    continue;
                }
                data.getOrganizations().add(org);
            }
            project.getParticipants().add(data);
        }
    }

    private static void mapDescriptions(List<ProjectDescriptionItem> items, PureProject project) {
        for (ProjectDescriptionItem item : empty(items)) {
            if (item == null) {
                continue;
            }
            ProjectDescriptionData data = new ProjectDescriptionData();
            data.setPureId(item.getPureId());
            ClassificationRef type = item.getType();
            if (type != null) {
                data.setTypeUri(LocalizedTexts.uri(type));
                data.setTypeEn(LocalizedTexts.en(type.getTerm()));
                data.setTypeEs(LocalizedTexts.es(type.getTerm()));
            }
            project.getDescriptions().add(data);
        }
    }

    private static void mapIdentifiers(List<IdentifierItem> items, PureProject project) {
        for (IdentifierItem item : empty(items)) {
            if (item == null) {
                continue;
            }
            ProjectIdentifierData data = new ProjectIdentifierData();
            data.setPureId(item.getPureId());
            data.setTypeDiscriminator(LocalizedTexts.trimToNull(item.getTypeDiscriminator()));
            data.setValue(firstNonBlank(item.getValue(), item.getId()));
            ClassificationRef type = item.getType();
            if (type != null) {
                data.setTypeUri(LocalizedTexts.uri(type));
                data.setTypeEn(LocalizedTexts.en(type.getTerm()));
                data.setTypeEs(LocalizedTexts.es(type.getTerm()));
            }
            project.getIdentifiers().add(data);
        }
    }

    private static void mapNatureTypes(List<ClassificationRef> items, PureProject project) {
        Set<String> seen = new LinkedHashSet<>();
        for (ClassificationRef item : empty(items)) {
            String uri = LocalizedTexts.uri(item);
            if (uri == null || !seen.add(uri)) {
                continue;
            }
            ProjectNatureTypeData data = new ProjectNatureTypeData();
            data.setUri(uri);
            data.setTermEn(LocalizedTexts.en(item.getTerm()));
            data.setTermEs(LocalizedTexts.es(item.getTerm()));
            project.getNatureTypes().add(data);
        }
    }

    private static void mapOrganizations(List<OrganizationRef> items, PureProject project) {
        Set<String> seen = new LinkedHashSet<>();
        for (OrganizationRef item : empty(items)) {
            ProjectOrganizationData data = organizationData(item);
            if (data == null || !seen.add(data.getOrganizationUuid())) {
                continue;
            }
            project.getOrganizations().add(data);
        }
    }

    private static ProjectOrganizationData organizationData(OrganizationRef item) {
        if (item == null) {
            return null;
        }
        String uuid = LocalizedTexts.trimToNull(item.getUuid());
        if (uuid == null) {
            return null;
        }
        ProjectOrganizationData data = new ProjectOrganizationData();
        data.setOrganizationUuid(uuid);
        data.setOrganizationSystemName(LocalizedTexts.trimToNull(item.getSystemName()));
        return data;
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

    private static LocalDate parseDate(String raw) {
        String value = LocalizedTexts.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String left, String right) {
        String first = LocalizedTexts.trimToNull(left);
        return first != null ? first : LocalizedTexts.trimToNull(right);
    }

    private static <T> List<T> empty(List<T> items) {
        return items == null ? List.of() : items;
    }
}
