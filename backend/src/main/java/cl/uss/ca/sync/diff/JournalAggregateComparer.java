package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.JournalClassificationData;
import cl.uss.ca.external.pure.model.JournalIdentifierData;
import cl.uss.ca.external.pure.model.JournalIssnData;
import cl.uss.ca.external.pure.model.JournalKeywordData;
import cl.uss.ca.external.pure.model.JournalKeywordGroupData;
import cl.uss.ca.external.pure.model.JournalTitleData;
import cl.uss.ca.external.pure.model.PureJournal;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JournalAggregateComparer {

    public AggregateOutcome compare(PureJournal incoming, JournalSnapshot existing) {
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

    private static boolean diffParent(JournalSnapshot existing, PureJournal incoming, AggregateOutcome outcome) {
        int before = outcome.changes.size();
        SyncValues.field(outcome.changes, "pureId", existing.pureId, incoming.getPureId());
        SyncValues.field(outcome.changes, "createdBy", existing.createdBy, incoming.getCreatedBy());
        if (!SyncValues.eq(existing.createdDatePure, incoming.getCreatedDatePure())) {
            SyncValues.field(outcome.changes, "createdDatePure", existing.createdDatePure, incoming.getCreatedDatePure());
        }
        SyncValues.field(outcome.changes, "modifiedBy", existing.modifiedBy, incoming.getModifiedBy());
        if (!SyncValues.eq(existing.modifiedDatePure, incoming.getModifiedDatePure())) {
            SyncValues.field(outcome.changes, "modifiedDatePure", existing.modifiedDatePure, incoming.getModifiedDatePure());
        }
        SyncValues.field(outcome.changes, "version", existing.version, incoming.getVersion());
        SyncValues.field(outcome.changes, "publisherUuid", existing.publisherUuid, incoming.getPublisherUuid());
        SyncValues.field(outcome.changes, "publisherSystemName", existing.publisherSystemName, incoming.getPublisherSystemName());
        SyncValues.field(outcome.changes, "typeUri", existing.typeUri, incoming.getTypeUri());
        SyncValues.field(outcome.changes, "typeEn", existing.typeEn, incoming.getTypeEn());
        SyncValues.field(outcome.changes, "typeEs", existing.typeEs, incoming.getTypeEs());
        SyncValues.field(outcome.changes, "workflowStep", existing.workflowStep, incoming.getWorkflowStep());
        SyncValues.field(outcome.changes, "workflowDescriptionEn", existing.workflowDescriptionEn, incoming.getWorkflowDescriptionEn());
        SyncValues.field(outcome.changes, "workflowDescriptionEs", existing.workflowDescriptionEs, incoming.getWorkflowDescriptionEs());
        SyncValues.field(outcome.changes, "systemName", existing.systemName, incoming.getSystemName());
        return outcome.changes.size() > before;
    }

    private static boolean diffChildren(JournalSnapshot existing, PureJournal incoming, AggregateOutcome outcome) {
        boolean changed = SyncValues.diffByBusinessKey(
                existing.titles,
                incoming.getTitles(),
                JournalAggregateComparer::titleKey,
                JournalAggregateComparer::titleEquals,
                (left, right) -> 0,
                (left, right) -> SyncValues.field(outcome.changes, "title", left.getTitle(), right.getTitle()),
                JournalTitleData::getTitle,
                "TITLE",
                "Title added",
                "Title removed",
                incoming.getUuid(),
                outcome.changes
        );
        changed |= SyncValues.diffByBusinessKey(
                existing.issns,
                incoming.getIssns(),
                JournalAggregateComparer::issnKey,
                JournalAggregateComparer::issnEquals,
                (left, right) -> 0,
                (left, right) -> SyncValues.field(outcome.changes, "issn", left.getIssn(), right.getIssn()),
                JournalIssnData::getIssn,
                "ISSN",
                "ISSN added",
                "ISSN removed",
                incoming.getUuid(),
                outcome.changes
        );
        changed |= SyncValues.diffByBusinessKey(
                existing.identifiers,
                incoming.getIdentifiers(),
                JournalAggregateComparer::identifierKey,
                JournalAggregateComparer::identifierEquals,
                (left, right) -> 0,
                (left, right) -> emitIdentifierUpdates(left, right, outcome),
                JournalAggregateComparer::identifierLabel,
                "IDENTIFIER",
                "Identifier added",
                "Identifier removed",
                incoming.getUuid(),
                outcome.changes
        );
        changed |= SyncValues.diffByBusinessKey(
                existing.keywordGroups,
                incoming.getKeywordGroups(),
                JournalAggregateComparer::groupKey,
                JournalAggregateComparer::groupEquals,
                (left, right) -> 0,
                (left, right) -> emitGroupUpdates(left, right, incoming.getUuid(), outcome),
                JournalKeywordGroupData::getLogicalName,
                "KEYWORD_GROUP",
                "Keyword group added",
                "Keyword group removed",
                incoming.getUuid(),
                outcome.changes
        );
        return changed;
    }

    private static void emitIdentifierUpdates(
            JournalIdentifierData previous,
            JournalIdentifierData current,
            AggregateOutcome outcome
    ) {
        SyncValues.field(outcome.changes, "identifierValue", previous.getValue(), current.getValue());
        SyncValues.field(outcome.changes, "identifierType", previous.getTypeEn(), current.getTypeEn());
    }

    private static void emitGroupUpdates(
            JournalKeywordGroupData previous,
            JournalKeywordGroupData current,
            String parentUuid,
            AggregateOutcome outcome
    ) {
        SyncValues.field(outcome.changes, "keywordGroupName", previous.getNameEn(), current.getNameEn());
        SyncValues.diffByBusinessKey(
                previous.getClassifications(),
                current.getClassifications(),
                JournalAggregateComparer::classificationKey,
                JournalAggregateComparer::classificationEquals,
                (left, right) -> 0,
                (left, right) -> SyncValues.field(outcome.changes, "classificationTerm", left.getTermEn(), right.getTermEn()),
                JournalClassificationData::getTermEn,
                "CLASSIFICATION",
                "Classification added",
                "Classification removed",
                parentUuid,
                outcome.changes
        );
        SyncValues.diffByBusinessKey(
                previous.getKeywords(),
                current.getKeywords(),
                JournalAggregateComparer::keywordKey,
                JournalAggregateComparer::keywordEquals,
                (left, right) -> 0,
                (left, right) -> SyncValues.field(outcome.changes, "keywordUri", left.getKeywordUri(), right.getKeywordUri()),
                JournalKeywordData::getKeywordUri,
                "KEYWORD",
                "Keyword added",
                "Keyword removed",
                parentUuid,
                outcome.changes
        );
    }

    static String titleKey(JournalTitleData title) {
        if (title == null) {
            return null;
        }
        if (title.getPureId() != null) {
            return "pure:" + title.getPureId();
        }
        return SyncValues.norm(title.getTitle()) == null ? null : "title:" + SyncValues.norm(title.getTitle());
    }

    static boolean titleEquals(JournalTitleData left, JournalTitleData right) {
        return SyncValues.eq(left == null ? null : left.getTitle(), right == null ? null : right.getTitle());
    }

    static String issnKey(JournalIssnData issn) {
        if (issn == null) {
            return null;
        }
        if (issn.getPureId() != null) {
            return "pure:" + issn.getPureId();
        }
        String value = SyncValues.norm(issn.getIssn());
        return value == null ? null : "issn:" + value;
    }

    static boolean issnEquals(JournalIssnData left, JournalIssnData right) {
        return SyncValues.eq(left == null ? null : left.getIssn(), right == null ? null : right.getIssn());
    }

    static String identifierKey(JournalIdentifierData identifier) {
        if (identifier == null) {
            return null;
        }
        if (identifier.getPureId() != null) {
            return "pure:" + identifier.getPureId();
        }
        return SyncValues.key(
                identifier.getTypeDiscriminator(),
                identifier.getIdSource(),
                identifier.getValue(),
                identifier.getTypeUri()
        );
    }

    static boolean identifierEquals(JournalIdentifierData left, JournalIdentifierData right) {
        return SyncValues.eq(left.getTypeDiscriminator(), right.getTypeDiscriminator())
                && SyncValues.eq(left.getIdSource(), right.getIdSource())
                && SyncValues.eq(left.getValue(), right.getValue())
                && SyncValues.eq(left.getTypeUri(), right.getTypeUri());
    }

    static String identifierLabel(JournalIdentifierData identifier) {
        return identifier == null ? null : identifier.getValue();
    }

    static String groupKey(JournalKeywordGroupData group) {
        if (group == null) {
            return null;
        }
        if (group.getPureId() != null) {
            return "pure:" + group.getPureId();
        }
        return SyncValues.norm(group.getLogicalName()) == null ? null : "logical:" + group.getLogicalName();
    }

    static boolean groupEquals(JournalKeywordGroupData left, JournalKeywordGroupData right) {
        return SyncValues.eq(left.getLogicalName(), right.getLogicalName())
                && SyncValues.eq(left.getTypeDiscriminator(), right.getTypeDiscriminator())
                && left.getClassifications().size() == right.getClassifications().size()
                && left.getKeywords().size() == right.getKeywords().size();
    }

    static String classificationKey(JournalClassificationData classification) {
        return classification == null ? null : SyncValues.norm(classification.getUri());
    }

    static boolean classificationEquals(JournalClassificationData left, JournalClassificationData right) {
        return SyncValues.eq(left.getUri(), right.getUri())
                && SyncValues.eq(left.getTermEn(), right.getTermEn())
                && SyncValues.eq(left.getTermEs(), right.getTermEs());
    }

    static String keywordKey(JournalKeywordData keyword) {
        if (keyword == null) {
            return null;
        }
        if (keyword.getPureId() != null) {
            return "pure:" + keyword.getPureId();
        }
        return SyncValues.norm(keyword.getKeywordUri());
    }

    static boolean keywordEquals(JournalKeywordData left, JournalKeywordData right) {
        return SyncValues.eq(left.getKeywordUri(), right.getKeywordUri())
                && SyncValues.eq(left.getState(), right.getState())
                && SyncValues.eq(left.getOrigin(), right.getOrigin());
    }
}
