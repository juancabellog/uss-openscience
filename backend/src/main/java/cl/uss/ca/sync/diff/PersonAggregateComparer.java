package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.AcademicTitle;
import cl.uss.ca.external.pure.PersonIdentifier;
import cl.uss.ca.external.pure.PurePerson;
import cl.uss.ca.external.pure.StaffAssociation;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PersonAggregateComparer {

    public AggregateOutcome compare(PurePerson incoming, PersonSnapshot existing, boolean compareIdentifiers) {
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
        outcome.childrenChanged = diffChildren(existing, incoming, compareIdentifiers, outcome);
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

    private static boolean diffParent(PersonSnapshot existing, PurePerson incoming, AggregateOutcome outcome) {
        int before = outcome.changes.size();
        SyncValues.field(outcome.changes, "pureId", existing.pureId, incoming.getPureId());
        SyncValues.field(outcome.changes, "firstName", existing.firstName, incoming.getFirstName());
        SyncValues.field(outcome.changes, "lastName", existing.lastName, incoming.getLastName());
        SyncValues.field(outcome.changes, "orcid", existing.orcid, incoming.getOrcid());
        if (existing.orcidAuthenticated == null
                || existing.orcidAuthenticated != incoming.isOrcidAuthenticated()) {
            outcome.changes.add(SyncChange.updated(
                    "PARENT",
                    "orcidAuthenticated",
                    existing.orcidAuthenticated == null ? null : String.valueOf(existing.orcidAuthenticated),
                    String.valueOf(incoming.isOrcidAuthenticated())
            ));
        }
        SyncValues.field(outcome.changes, "email", existing.email, incoming.getEmail());
        SyncValues.field(outcome.changes, "fechaIngreso", existing.fechaIngreso, incoming.getFechaIngreso());
        SyncValues.field(outcome.changes, "cargo", existing.cargo, incoming.getCargo());
        SyncValues.field(outcome.changes, "titulo", existing.titulo, incoming.getTitulo());
        SyncValues.field(outcome.changes, "createdBy", existing.createdBy, incoming.getCreatedBy());
        SyncValues.field(outcome.changes, "modifiedBy", existing.modifiedBy, incoming.getModifiedBy());
        if (!SyncValues.eq(existing.fechaCreacionPure, incoming.getFechaCreacionPure())) {
            SyncValues.field(outcome.changes, "fechaCreacionPure", existing.fechaCreacionPure, incoming.getFechaCreacionPure());
        }
        if (!SyncValues.eq(existing.fechaModificacionPure, incoming.getFechaModificacionPure())) {
            SyncValues.field(outcome.changes, "fechaModificacionPure", existing.fechaModificacionPure, incoming.getFechaModificacionPure());
        }
        return outcome.changes.size() > before;
    }

    private static boolean diffChildren(
            PersonSnapshot existing,
            PurePerson incoming,
            boolean compareIdentifiers,
            AggregateOutcome outcome
    ) {
        boolean changed = SyncValues.diffByBusinessKey(
                existing.asociaciones,
                incoming.getAsociaciones(),
                PersonAggregateComparer::organizationBusinessKey,
                PersonAggregateComparer::organizationSemanticEquals,
                PersonAggregateComparer::organizationAffinity,
                (oldItem, newItem) -> emitOrganizationUpdates(oldItem, newItem, outcome.changes),
                PersonAggregateComparer::organizationLabel,
                "ORGANIZATION",
                "Organization added",
                "Organization removed",
                incoming.getUuid(),
                outcome.changes
        );
        changed |= SyncValues.diffSignatures(
                existing.titulos,
                incoming.getTitulos(),
                PersonAggregateComparer::titleSignature,
                AcademicTitle::getValue,
                "TITLE",
                "Title added",
                "Title removed",
                outcome.changes
        );
        if (compareIdentifiers) {
            changed |= SyncValues.diffSignatures(
                    existing.identifiers,
                    incoming.getIdentifiers(),
                    PersonAggregateComparer::identifierSignature,
                    PersonIdentifier::getValue,
                    "IDENTIFIER",
                    "Identifier added",
                    "Identifier removed",
                    outcome.changes
            );
            relabelIdentifierChanges(existing, incoming, outcome);
        }
        return changed;
    }

    private static void relabelIdentifierChanges(
            PersonSnapshot existing,
            PurePerson incoming,
            AggregateOutcome outcome
    ) {
        for (SyncChange change : outcome.changes) {
            if (!"IDENTIFIER".equals(change.changeScope)) {
                continue;
            }
            PersonIdentifier sample = change.oldValue != null
                    ? findIdentifier(existing.identifiers, change.oldValue)
                    : findIdentifier(incoming.getIdentifiers(), change.newValue);
            String type = sample == null ? null : sample.getIdentifierType();
            if (type != null && !type.isBlank()) {
                change.description = change.oldValue != null ? type + " removed" : type + " added";
            }
        }
    }

    private static PersonIdentifier findIdentifier(java.util.List<PersonIdentifier> items, String value) {
        if (items == null || value == null) {
            return null;
        }
        for (PersonIdentifier item : items) {
            if (item != null && value.equals(SyncValues.norm(item.getValue()))) {
                return item;
            }
        }
        return null;
    }

    /**
     * Identity of the person-organization relation.
     * PURE allows more than one staff association with the same organization UUID
     * (different period/role). Those duplicates stay as separate list entries under
     * the same key and are paired by semantic equality, not collapsed.
     * Local autoincrement id is not part of StaffAssociation and is never used.
     */
    static String organizationBusinessKey(StaffAssociation association) {
        if (association == null) {
            return null;
        }
        String uuid = SyncValues.normUuid(association.getOrganizationUuid());
        if (uuid != null) {
            return uuid;
        }
        if (association.getPureId() != null) {
            return "pure-association:" + association.getPureId();
        }
        return null;
    }

    static boolean organizationSemanticEquals(StaffAssociation left, StaffAssociation right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return SyncValues.eq(left.getEmploymentType(), right.getEmploymentType())
                && SyncValues.eq(left.getEmail(), right.getEmail())
                && SyncValues.eq(left.getStartDate(), right.getStartDate())
                && SyncValues.eq(left.getEndDate(), right.getEndDate())
                && left.isPrimaryAssociation() == right.isPrimaryAssociation()
                && SyncValues.eq(left.getContractType(), right.getContractType())
                && SyncValues.eqFte(left.getFte(), right.getFte())
                && SyncValues.eq(left.getJobDescription(), right.getJobDescription())
                && SyncValues.eq(left.getJobTitle(), right.getJobTitle())
                && SyncValues.eq(left.getStaffType(), right.getStaffType());
    }

    static int organizationAffinity(StaffAssociation left, StaffAssociation right) {
        if (left == null || right == null) {
            return 0;
        }
        int score = 0;
        if (SyncValues.eq(left.getStartDate(), right.getStartDate())) {
            score += 4;
        }
        if (SyncValues.eq(left.getEmploymentType(), right.getEmploymentType())) {
            score += 2;
        }
        if (SyncValues.eq(left.getJobTitle(), right.getJobTitle())) {
            score += 1;
        }
        return score;
    }

    private static void emitOrganizationUpdates(
            StaffAssociation previous,
            StaffAssociation current,
            java.util.List<SyncChange> changes
    ) {
        String context = organizationFieldContext(previous, current);
        emitOrganizationField(changes, context, "employmentType", previous.getEmploymentType(), current.getEmploymentType());
        emitOrganizationField(changes, context, "email", previous.getEmail(), current.getEmail());
        emitOrganizationField(changes, context, "startDate", date(previous.getStartDate()), date(current.getStartDate()));
        emitOrganizationField(changes, context, "endDate", date(previous.getEndDate()), date(current.getEndDate()));
        if (previous.isPrimaryAssociation() != current.isPrimaryAssociation()) {
            changes.add(SyncChange.updated(
                    "ORGANIZATION",
                    context + "primaryAssociation",
                    String.valueOf(previous.isPrimaryAssociation()),
                    String.valueOf(current.isPrimaryAssociation())
            ));
        }
        emitOrganizationField(changes, context, "contractType", previous.getContractType(), current.getContractType());
        if (!SyncValues.eqFte(previous.getFte(), current.getFte())) {
            changes.add(SyncChange.updated(
                    "ORGANIZATION",
                    context + "fte",
                    SyncValues.fte(previous.getFte()),
                    SyncValues.fte(current.getFte())
            ));
        }
        emitOrganizationField(changes, context, "jobDescription", previous.getJobDescription(), current.getJobDescription());
        emitOrganizationField(changes, context, "jobTitle", previous.getJobTitle(), current.getJobTitle());
        emitOrganizationField(changes, context, "staffType", previous.getStaffType(), current.getStaffType());
    }

    private static void emitOrganizationField(
            java.util.List<SyncChange> changes,
            String context,
            String field,
            String oldValue,
            String newValue
    ) {
        if (!SyncValues.eq(oldValue, newValue)) {
            changes.add(SyncChange.updated("ORGANIZATION", context + field, oldValue, newValue));
        }
    }

    static String organizationFieldContext(StaffAssociation previous, StaffAssociation current) {
        String uuid = firstNonBlank(
                current == null ? null : current.getOrganizationUuid(),
                previous == null ? null : previous.getOrganizationUuid()
        );
        String name = firstNonBlank(
                current == null ? null : current.getOrganizationName(),
                previous == null ? null : previous.getOrganizationName()
        );
        if (uuid == null && name == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(uuid == null ? "" : uuid.trim());
        builder.append("||");
        if (name != null) {
            builder.append(name.trim());
        }
        builder.append("||");
        return builder.toString();
    }

    private static String firstNonBlank(String left, String right) {
        String normalizedLeft = SyncValues.norm(left);
        if (normalizedLeft != null) {
            return left.trim();
        }
        return SyncValues.norm(right) == null ? null : right.trim();
    }

    private static String date(java.time.LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static String organizationLabel(StaffAssociation association) {
        String uuid = association.getOrganizationUuid();
        return SyncValues.normUuid(uuid) != null
                ? uuid.trim()
                : association.getJobTitle();
    }

    private static String titleSignature(AcademicTitle title) {
        return SyncValues.key(title.getType(), title.getValue());
    }

    private static String identifierSignature(PersonIdentifier identifier) {
        return SyncValues.key(
                identifier.getTypeDiscriminator(),
                identifier.getIdentifierType(),
                identifier.getValue()
        );
    }
}
