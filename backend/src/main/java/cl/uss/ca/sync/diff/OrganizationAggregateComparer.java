package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.PureOrganization;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrganizationAggregateComparer {

    public AggregateOutcome compare(PureOrganization incoming, OrganizationSnapshot existing) {
        AggregateOutcome outcome = new AggregateOutcome();
        outcome.uuid = incoming.getUuid();
        outcome.pureId = incoming.getPureId();
        if (existing == null) {
            outcome.kind = AggregateOutcome.Kind.CREATED;
            outcome.parentChanged = true;
            outcome.childrenChanged = false;
            outcome.changes.add(SyncChange.created());
            return outcome;
        }
        outcome.localId = existing.id;
        boolean reactivated = existing.activeInPure != null && !existing.activeInPure;
        outcome.parentChanged = diffParent(existing, incoming, outcome);
        outcome.childrenChanged = false;
        if (reactivated) {
            outcome.kind = AggregateOutcome.Kind.REACTIVATED;
            outcome.changes.add(0, SyncChange.reactivated());
        } else if (outcome.parentChanged) {
            outcome.kind = AggregateOutcome.Kind.UPDATED;
        } else {
            outcome.kind = AggregateOutcome.Kind.UNCHANGED;
            outcome.changes.clear();
        }
        return outcome;
    }

    private static boolean diffParent(OrganizationSnapshot existing, PureOrganization incoming, AggregateOutcome outcome) {
        int before = outcome.changes.size();
        SyncValues.field(outcome.changes, "pureId", existing.pureId, incoming.getPureId());
        SyncValues.field(outcome.changes, "typeDiscriminator", existing.typeDiscriminator, incoming.getTypeDiscriminator());
        SyncValues.field(outcome.changes, "nameEs", existing.nameEs, incoming.getNameEs());
        SyncValues.field(outcome.changes, "nameEn", existing.nameEn, incoming.getNameEn());
        SyncValues.field(outcome.changes, "createdBy", existing.createdBy, incoming.getCreatedBy());
        if (!SyncValues.eq(existing.createdDatePure, incoming.getCreatedDatePure())) {
            SyncValues.field(outcome.changes, "createdDatePure", existing.createdDatePure, incoming.getCreatedDatePure());
        }
        SyncValues.field(outcome.changes, "modifiedBy", existing.modifiedBy, incoming.getModifiedBy());
        if (!SyncValues.eq(existing.modifiedDatePure, incoming.getModifiedDatePure())) {
            SyncValues.field(outcome.changes, "modifiedDatePure", existing.modifiedDatePure, incoming.getModifiedDatePure());
        }
        SyncValues.field(outcome.changes, "version", existing.version, incoming.getVersion());
        SyncValues.field(outcome.changes, "systemName", existing.systemName, incoming.getSystemName());
        SyncValues.field(outcome.changes, "typeUri", existing.typeUri, incoming.getTypeUri());
        SyncValues.field(outcome.changes, "typeEn", existing.typeEn, incoming.getTypeEn());
        SyncValues.field(outcome.changes, "typeEs", existing.typeEs, incoming.getTypeEs());
        SyncValues.field(outcome.changes, "parentUuid", existing.parentUuid, incoming.getParentUuid());
        return outcome.changes.size() > before;
    }
}
