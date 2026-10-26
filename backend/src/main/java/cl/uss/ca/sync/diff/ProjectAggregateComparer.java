package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.ProjectDescriptionData;
import cl.uss.ca.external.pure.model.ProjectIdentifierData;
import cl.uss.ca.external.pure.model.ProjectNatureTypeData;
import cl.uss.ca.external.pure.model.ProjectOrganizationData;
import cl.uss.ca.external.pure.model.ProjectParticipantData;
import cl.uss.ca.external.pure.model.PureProject;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ProjectAggregateComparer {

    public AggregateOutcome compare(PureProject incoming, ProjectSnapshot existing) {
        AggregateOutcome outcome = new AggregateOutcome();
        outcome.uuid = incoming.getUuid();
        outcome.pureId = incoming.getPureId();
        if (existing == null) {
            outcome.kind = AggregateOutcome.Kind.CREATED;
            outcome.parentChanged = true;
            outcome.childrenChanged = true;
            outcome.changes.add(SyncChange.created());
            return outcome;
        }
        outcome.localId = existing.id;
        boolean reactivated = existing.activeInPure != null && !existing.activeInPure;
        outcome.parentChanged = diffParent(existing, incoming, outcome);
        outcome.childrenChanged = diffChildren(existing, incoming, outcome);
        if (reactivated) {
            outcome.kind = AggregateOutcome.Kind.REACTIVATED;
            outcome.changes.add(0, SyncChange.reactivated());
        } else if (outcome.parentChanged || outcome.childrenChanged) {
            outcome.kind = AggregateOutcome.Kind.UPDATED;
        } else {
            outcome.kind = AggregateOutcome.Kind.UNCHANGED;
            outcome.changes.clear();
        }
        return outcome;
    }

    private static boolean diffParent(ProjectSnapshot existing, PureProject incoming, AggregateOutcome outcome) {
        int before = outcome.changes.size();
        SyncValues.field(outcome.changes, "pureId", existing.pureId, incoming.getPureId());
        SyncValues.field(outcome.changes, "typeDiscriminator", existing.typeDiscriminator, incoming.getTypeDiscriminator());
        SyncValues.field(outcome.changes, "titleEs", existing.titleEs, incoming.getTitleEs());
        SyncValues.field(outcome.changes, "titleEn", existing.titleEn, incoming.getTitleEn());
        SyncValues.field(outcome.changes, "acronym", existing.acronym, incoming.getAcronym());
        SyncValues.field(outcome.changes, "createdBy", existing.createdBy, incoming.getCreatedBy());
        if (!SyncValues.eq(existing.createdDatePure, incoming.getCreatedDatePure())) {
            SyncValues.field(outcome.changes, "createdDatePure", existing.createdDatePure, incoming.getCreatedDatePure());
        }
        SyncValues.field(outcome.changes, "modifiedBy", existing.modifiedBy, incoming.getModifiedBy());
        if (!SyncValues.eq(existing.modifiedDatePure, incoming.getModifiedDatePure())) {
            SyncValues.field(outcome.changes, "modifiedDatePure", existing.modifiedDatePure, incoming.getModifiedDatePure());
        }
        SyncValues.field(outcome.changes, "portalUrl", existing.portalUrl, incoming.getPortalUrl());
        SyncValues.field(outcome.changes, "version", existing.version, incoming.getVersion());
        SyncValues.field(outcome.changes, "periodStartDate", existing.periodStartDate, incoming.getPeriodStartDate());
        SyncValues.field(outcome.changes, "periodEndDate", existing.periodEndDate, incoming.getPeriodEndDate());
        SyncValues.field(outcome.changes, "typeUri", existing.typeUri, incoming.getTypeUri());
        SyncValues.field(outcome.changes, "typeEn", existing.typeEn, incoming.getTypeEn());
        SyncValues.field(outcome.changes, "typeEs", existing.typeEs, incoming.getTypeEs());
        SyncValues.field(outcome.changes, "workflowStep", existing.workflowStep, incoming.getWorkflowStep());
        SyncValues.field(outcome.changes, "workflowDescriptionEn", existing.workflowDescriptionEn, incoming.getWorkflowDescriptionEn());
        SyncValues.field(outcome.changes, "workflowDescriptionEs", existing.workflowDescriptionEs, incoming.getWorkflowDescriptionEs());
        SyncValues.field(outcome.changes, "visibilityKey", existing.visibilityKey, incoming.getVisibilityKey());
        SyncValues.field(outcome.changes, "visibilityEn", existing.visibilityEn, incoming.getVisibilityEn());
        SyncValues.field(outcome.changes, "visibilityEs", existing.visibilityEs, incoming.getVisibilityEs());
        SyncValues.field(outcome.changes, "managingOrganizationUuid", existing.managingOrganizationUuid, incoming.getManagingOrganizationUuid());
        SyncValues.field(outcome.changes, "systemName", existing.systemName, incoming.getSystemName());
        return outcome.changes.size() > before;
    }

    private static boolean diffChildren(ProjectSnapshot existing, PureProject incoming, AggregateOutcome outcome) {
        boolean changed = SyncValues.diffByBusinessKey(
                existing.prettyUrlIdentifiers,
                incoming.getPrettyUrlIdentifiers(),
                SyncValues::norm,
                (left, right) -> SyncValues.eq(left, right),
                (left, right) -> 0,
                (left, right) -> { },
                value -> value,
                "PRETTY_URL",
                "Pretty URL added",
                "Pretty URL removed",
                incoming.getUuid(),
                outcome.changes
        );
        changed |= SyncValues.diffByBusinessKey(
                existing.participants,
                incoming.getParticipants(),
                ProjectAggregateComparer::participantKey,
                ProjectAggregateComparer::participantEquals,
                (left, right) -> 0,
                (left, right) -> emitParticipantUpdates(left, right, incoming.getUuid(), outcome),
                ProjectAggregateComparer::participantLabel,
                "PARTICIPANT",
                "Participant added",
                "Participant removed",
                incoming.getUuid(),
                outcome.changes
        );
        changed |= SyncValues.diffByBusinessKey(
                existing.descriptions,
                incoming.getDescriptions(),
                ProjectAggregateComparer::descriptionKey,
                ProjectAggregateComparer::descriptionEquals,
                (left, right) -> 0,
                (left, right) -> SyncValues.field(outcome.changes, "descriptionType", left.getTypeUri(), right.getTypeUri()),
                ProjectDescriptionData::getTypeUri,
                "DESCRIPTION",
                "Description added",
                "Description removed",
                incoming.getUuid(),
                outcome.changes
        );
        changed |= SyncValues.diffByBusinessKey(
                existing.identifiers,
                incoming.getIdentifiers(),
                ProjectAggregateComparer::identifierKey,
                ProjectAggregateComparer::identifierEquals,
                (left, right) -> 0,
                (left, right) -> {
                    SyncValues.field(outcome.changes, "identifierValue", left.getValue(), right.getValue());
                    SyncValues.field(outcome.changes, "identifierType", left.getTypeUri(), right.getTypeUri());
                },
                ProjectIdentifierData::getValue,
                "IDENTIFIER",
                "Identifier added",
                "Identifier removed",
                incoming.getUuid(),
                outcome.changes
        );
        changed |= SyncValues.diffByBusinessKey(
                existing.natureTypes,
                incoming.getNatureTypes(),
                nature -> SyncValues.norm(nature.getUri()),
                (left, right) -> SyncValues.eq(left.getUri(), right.getUri())
                        && SyncValues.eq(left.getTermEn(), right.getTermEn())
                        && SyncValues.eq(left.getTermEs(), right.getTermEs()),
                (left, right) -> 0,
                (left, right) -> SyncValues.field(outcome.changes, "natureTerm", left.getTermEs(), right.getTermEs()),
                ProjectNatureTypeData::getUri,
                "NATURE_TYPE",
                "Nature type added",
                "Nature type removed",
                incoming.getUuid(),
                outcome.changes
        );
        changed |= SyncValues.diffByBusinessKey(
                existing.organizations,
                incoming.getOrganizations(),
                org -> SyncValues.norm(org.getOrganizationUuid()),
                (left, right) -> SyncValues.eq(left.getOrganizationUuid(), right.getOrganizationUuid())
                        && SyncValues.eq(left.getOrganizationSystemName(), right.getOrganizationSystemName()),
                (left, right) -> 0,
                (left, right) -> SyncValues.field(
                        outcome.changes,
                        "organizationSystemName",
                        left.getOrganizationSystemName(),
                        right.getOrganizationSystemName()
                ),
                ProjectOrganizationData::getOrganizationUuid,
                "ORGANIZATION",
                "Organization added",
                "Organization removed",
                incoming.getUuid(),
                outcome.changes
        );
        return changed;
    }

    private static void emitParticipantUpdates(
            ProjectParticipantData previous,
            ProjectParticipantData current,
            String parentUuid,
            AggregateOutcome outcome
    ) {
        SyncValues.field(outcome.changes, "participantName", participantLabel(previous), participantLabel(current));
        SyncValues.field(outcome.changes, "participantRole", previous.getRoleUri(), current.getRoleUri());
        SyncValues.field(outcome.changes, "participantPersonUuid", previous.getPersonUuid(), current.getPersonUuid());
        SyncValues.diffByBusinessKey(
                previous.getOrganizations(),
                current.getOrganizations(),
                org -> SyncValues.norm(org.getOrganizationUuid()),
                (left, right) -> SyncValues.eq(left.getOrganizationUuid(), right.getOrganizationUuid()),
                (left, right) -> 0,
                (left, right) -> { },
                ProjectOrganizationData::getOrganizationUuid,
                "PARTICIPANT_ORGANIZATION",
                "Participant organization added",
                "Participant organization removed",
                parentUuid,
                outcome.changes
        );
    }

    static String participantKey(ProjectParticipantData participant) {
        if (participant == null) {
            return null;
        }
        if (participant.getPureId() != null) {
            return "pure:" + participant.getPureId();
        }
        return SyncValues.key(participant.getPersonUuid(), participant.getFirstName(), participant.getLastName(), participant.getRoleUri());
    }

    static boolean participantEquals(ProjectParticipantData left, ProjectParticipantData right) {
        return SyncValues.eq(left.getTypeDiscriminator(), right.getTypeDiscriminator())
                && SyncValues.eq(left.getFirstName(), right.getFirstName())
                && SyncValues.eq(left.getLastName(), right.getLastName())
                && SyncValues.eq(left.getRoleUri(), right.getRoleUri())
                && SyncValues.eq(left.getRoleTermEn(), right.getRoleTermEn())
                && SyncValues.eq(left.getRoleTermEs(), right.getRoleTermEs())
                && SyncValues.eq(left.getPersonUuid(), right.getPersonUuid())
                && SyncValues.eq(left.getAssociationHidden(), right.getAssociationHidden())
                && organizationUuids(left.getOrganizations()).equals(organizationUuids(right.getOrganizations()));
    }

    private static Set<String> organizationUuids(List<ProjectOrganizationData> organizations) {
        Set<String> values = new LinkedHashSet<>();
        if (organizations == null) {
            return values;
        }
        for (ProjectOrganizationData organization : organizations) {
            String uuid = organization == null ? null : SyncValues.norm(organization.getOrganizationUuid());
            if (uuid != null) {
                values.add(uuid);
            }
        }
        return values;
    }

    static String participantLabel(ProjectParticipantData participant) {
        if (participant == null) {
            return null;
        }
        String name = SyncValues.norm((participant.getFirstName() == null ? "" : participant.getFirstName())
                + " " + (participant.getLastName() == null ? "" : participant.getLastName()));
        return name == null ? participant.getPersonUuid() : name;
    }

    static String descriptionKey(ProjectDescriptionData description) {
        if (description == null) {
            return null;
        }
        if (description.getPureId() != null) {
            return "pure:" + description.getPureId();
        }
        return SyncValues.norm(description.getTypeUri());
    }

    static boolean descriptionEquals(ProjectDescriptionData left, ProjectDescriptionData right) {
        return SyncValues.eq(left.getTypeUri(), right.getTypeUri())
                && SyncValues.eq(left.getTypeEn(), right.getTypeEn())
                && SyncValues.eq(left.getTypeEs(), right.getTypeEs());
    }

    static String identifierKey(ProjectIdentifierData identifier) {
        if (identifier == null) {
            return null;
        }
        if (identifier.getPureId() != null) {
            return "pure:" + identifier.getPureId();
        }
        return SyncValues.key(identifier.getTypeDiscriminator(), identifier.getValue(), identifier.getTypeUri());
    }

    static boolean identifierEquals(ProjectIdentifierData left, ProjectIdentifierData right) {
        return SyncValues.eq(left.getTypeDiscriminator(), right.getTypeDiscriminator())
                && SyncValues.eq(left.getValue(), right.getValue())
                && SyncValues.eq(left.getTypeUri(), right.getTypeUri());
    }
}
