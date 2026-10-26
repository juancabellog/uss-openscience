package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.ElectronicVersion;
import cl.uss.ca.external.pure.model.JournalData;
import cl.uss.ca.external.pure.model.KeywordData;
import cl.uss.ca.external.pure.model.KeywordGroupData;
import cl.uss.ca.external.pure.model.OutputContributor;
import cl.uss.ca.external.pure.model.OutputIdentifier;
import cl.uss.ca.external.pure.model.OutputLink;
import cl.uss.ca.external.pure.model.OutputStatus;
import cl.uss.ca.external.pure.model.ResearchOutput;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ResearchOutputAggregateComparer {
    private static final Logger LOG = Logger.getLogger(ResearchOutputAggregateComparer.class.getName());

    public AggregateOutcome compare(ResearchOutput incoming, ResearchOutputSnapshot existing) {
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
        outcome.parentChanged = diffParent(existing.parent, incoming, outcome);
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

    private static boolean diffParent(ResearchOutput existing, ResearchOutput incoming, AggregateOutcome outcome) {
        int before = outcome.changes.size();
        SyncValues.field(outcome.changes, "pureId", existing.getPureId(), incoming.getPureId());
        SyncValues.field(outcome.changes, "title", existing.getTitle(), incoming.getTitle());
        SyncValues.field(outcome.changes, "typeDiscriminator", existing.getTypeDiscriminator(), incoming.getTypeDiscriminator());
        SyncValues.field(outcome.changes, "typeUri", existing.getTypeUri(), incoming.getTypeUri());
        SyncValues.field(outcome.changes, "categoryUri", existing.getCategoryUri(), incoming.getCategoryUri());
        SyncValues.field(outcome.changes, "peerReview", existing.getPeerReview(), incoming.getPeerReview());
        SyncValues.field(outcome.changes, "languageUri", existing.getLanguageUri(), incoming.getLanguageUri());
        SyncValues.field(outcome.changes, "submissionYear", existing.getSubmissionYear(), incoming.getSubmissionYear());
        SyncValues.field(outcome.changes, "totalNumberOfContributors", existing.getTotalNumberOfContributors(), incoming.getTotalNumberOfContributors());
        SyncValues.field(outcome.changes, "managingOrganizationUuid", existing.getManagingOrganizationUuid(), incoming.getManagingOrganizationUuid());
        SyncValues.field(outcome.changes, "pages", existing.getPages(), incoming.getPages());
        SyncValues.field(outcome.changes, "volume", existing.getVolume(), incoming.getVolume());
        SyncValues.field(outcome.changes, "journalNumber", existing.getJournalNumber(), incoming.getJournalNumber());
        SyncValues.field(outcome.changes, "numberOfPages", existing.getNumberOfPages(), incoming.getNumberOfPages());
        SyncValues.field(outcome.changes, "abstractEs", existing.getAbstractEs(), incoming.getAbstractEs());
        SyncValues.field(outcome.changes, "abstractEn", existing.getAbstractEn(), incoming.getAbstractEn());
        SyncValues.field(outcome.changes, "portalUrl", existing.getPortalUrl(), incoming.getPortalUrl());
        SyncValues.field(outcome.changes, "visibilityKey", existing.getVisibilityKey(), incoming.getVisibilityKey());
        SyncValues.field(outcome.changes, "workflowStep", existing.getWorkflowStep(), incoming.getWorkflowStep());
        SyncValues.field(outcome.changes, "workflowEs", existing.getWorkflowEs(), incoming.getWorkflowEs());
        SyncValues.field(outcome.changes, "workflowEn", existing.getWorkflowEn(), incoming.getWorkflowEn());
        SyncValues.field(outcome.changes, "createdBy", existing.getCreatedBy(), incoming.getCreatedBy());
        SyncValues.field(outcome.changes, "modifiedBy", existing.getModifiedBy(), incoming.getModifiedBy());
        SyncValues.field(outcome.changes, "version", existing.getVersion(), incoming.getVersion());
        if (!SyncValues.eq(existing.getCreatedDate(), incoming.getCreatedDate())) {
            SyncValues.field(outcome.changes, "createdDate", existing.getCreatedDate(), incoming.getCreatedDate());
        }
        if (!SyncValues.eq(existing.getModifiedDate(), incoming.getModifiedDate())) {
            SyncValues.field(outcome.changes, "modifiedDate", existing.getModifiedDate(), incoming.getModifiedDate());
        }
        return outcome.changes.size() > before;
    }

    private static boolean diffChildren(ResearchOutputSnapshot existing, ResearchOutput incoming, AggregateOutcome outcome) {
        boolean changed = SyncValues.diffSignatures(
                existing.statuses,
                incoming.getStatuses(),
                ResearchOutputAggregateComparer::statusSignature,
                OutputStatus::getStatusUri,
                "STATUS",
                "Status added",
                "Status removed",
                outcome.changes
        );
        changed |= SyncValues.diffSignatures(
                existing.contributors,
                incoming.getContributors(),
                ResearchOutputAggregateComparer::contributorSignature,
                ResearchOutputAggregateComparer::contributorLabel,
                "CONTRIBUTOR",
                "Contributor added",
                "Contributor removed",
                outcome.changes
        );
        changed |= SyncValues.diffSignatures(
                existing.electronicVersions,
                incoming.getElectronicVersions(),
                ResearchOutputAggregateComparer::eversionSignature,
                ElectronicVersion::getDoi,
                "ELECTRONIC_VERSION",
                "Electronic version added",
                "Electronic version removed",
                outcome.changes
        );
        changed |= SyncValues.diffSignatures(
                existing.identifiers,
                incoming.getIdentifiers(),
                ResearchOutputAggregateComparer::identifierSignature,
                OutputIdentifier::getValue,
                "IDENTIFIER",
                "Identifier added",
                "Identifier removed",
                outcome.changes
        );
        changed |= SyncValues.diffSignatures(
                existing.links,
                incoming.getLinks(),
                ResearchOutputAggregateComparer::linkSignature,
                OutputLink::getUrl,
                "LINK",
                "Link added",
                "Link removed",
                outcome.changes
        );
        changed |= SyncValues.diffSignatures(
                existing.externalOrganizationUuids,
                incoming.getExternalOrganizationUuids(),
                SyncValues::norm,
                value -> value,
                "EXTERNAL_ORGANIZATION",
                "External organization added",
                "External organization removed",
                outcome.changes
        );
        changed |= SyncValues.diffSignatures(
                existing.keywordGroups,
                incoming.getKeywordGroups(),
                ResearchOutputAggregateComparer::keywordGroupSignature,
                KeywordGroupData::getLogicalName,
                "KEYWORD",
                "Keyword group added",
                "Keyword group removed",
                outcome.changes
        );
        changed |= diffJournal(existing, incoming, outcome);
        return changed;
    }

    static boolean diffJournal(
            ResearchOutputSnapshot existing,
            ResearchOutput incoming,
            AggregateOutcome outcome
    ) {
        JournalData previous = existing == null ? null : existing.journal;
        JournalData current = incoming == null ? null : incoming.getJournal();
        String oldKey = journalBusinessKey(previous);
        String newKey = journalBusinessKey(current);
        boolean equal = journalSemanticEquals(previous, current);
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE,
                    "researchOutputId={0} journalKey OLD={1} journalKey NEW={2} semanticEquals={3}",
                    new Object[]{
                            incoming == null ? null : incoming.getUuid(),
                            oldKey == null ? "none" : oldKey,
                            newKey == null ? "none" : newKey,
                            equal
                    });
        }
        if (equal) {
            return false;
        }
        if (previous == null) {
            outcome.changes.add(SyncChange.updated(
                    "JOURNAL",
                    "Journal added",
                    null,
                    journalAuditLabel(current)
            ));
            return true;
        }
        if (current == null) {
            outcome.changes.add(SyncChange.updated(
                    "JOURNAL",
                    "Journal removed",
                    journalAuditLabel(previous),
                    null
            ));
            return true;
        }
        outcome.changes.add(SyncChange.updated(
                "JOURNAL",
                "Journal changed",
                journalAuditLabel(previous),
                journalAuditLabel(current)
        ));
        return true;
    }

    static boolean journalSemanticEquals(JournalData left, JournalData right) {
        return sameJournalIdentity(left, right);
    }

    /**
     * Research Output references a catalog Journal by UUID only.
     * ISSN and title on the association are display fragments, not identity.
     * Missing UUID is unresolved, never invented from ISSN.
     */
    static boolean sameJournalIdentity(JournalData left, JournalData right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        String leftUuid = SyncValues.normUuid(left.getUuid());
        String rightUuid = SyncValues.normUuid(right.getUuid());
        if (leftUuid == null || rightUuid == null) {
            return true;
        }
        return leftUuid.equals(rightUuid);
    }

    static String journalBusinessKey(JournalData journal) {
        if (journal == null) {
            return null;
        }
        String uuid = SyncValues.normUuid(journal.getUuid());
        return uuid == null ? null : "uuid:" + uuid;
    }

    private static String statusSignature(OutputStatus status) {
        return SyncValues.key(
                status.getStatusUri(),
                status.getCurrentStatus(),
                status.getPublicationYear(),
                status.getPublicationMonth(),
                status.getPublicationDay()
        );
    }

    private static String contributorSignature(OutputContributor contributor) {
        List<String> orgs = new ArrayList<>(SyncValues.list(contributor.getOrganizationUuids()));
        orgs.sort(Comparator.nullsLast(String::compareTo));
        return SyncValues.key(
                contributor.getTypeDiscriminator(),
                contributor.getFirstName(),
                contributor.getLastName(),
                contributor.getRoleUri(),
                contributor.getPersonUuid(),
                contributor.getExternalPersonUuid(),
                contributor.getHidden(),
                contributor.getCorrespondingAuthor(),
                contributor.getAssociationHidden(),
                String.join(",", orgs)
        );
    }

    private static String contributorLabel(OutputContributor contributor) {
        String name = ((SyncValues.norm(contributor.getLastName()) == null ? "" : contributor.getLastName())
                + " " + (SyncValues.norm(contributor.getFirstName()) == null ? "" : contributor.getFirstName())).trim();
        return SyncValues.norm(name) != null ? name : contributor.getPersonUuid();
    }

    private static String eversionSignature(ElectronicVersion version) {
        return SyncValues.key(
                version.getTypeDiscriminator(),
                version.getDoi(),
                version.getAccessTypeUri(),
                version.getVersionTypeUri()
        );
    }

    private static String identifierSignature(OutputIdentifier identifier) {
        return SyncValues.key(identifier.getIdSource(), identifier.getValue());
    }

    private static String linkSignature(OutputLink link) {
        return SyncValues.key(link.getUrl(), link.getLinkTypeUri());
    }

    private static String keywordGroupSignature(KeywordGroupData group) {
        List<String> keywords = new ArrayList<>();
        for (KeywordData keyword : SyncValues.list(group.getKeywords())) {
            keywords.add(SyncValues.key(
                    keyword.getKeywordType(),
                    keyword.getKeywordUri(),
                    keyword.getKeywordEs(),
                    keyword.getKeywordEn(),
                    keyword.getFreeKeyword(),
                    keyword.getLocale()
            ));
        }
        keywords.sort(String::compareTo);
        return SyncValues.key(group.getLogicalName(), group.getNameEs(), group.getNameEn(), String.join(";", keywords));
    }

    static String journalAuditLabel(JournalData journal) {
        if (journal == null) {
            return null;
        }
        String uuid = SyncValues.normUuid(journal.getUuid());
        if (uuid == null) {
            return SyncValues.norm(journal.getTitle());
        }
        String title = SyncValues.norm(journal.getTitle());
        return title == null ? uuid : title + " | " + uuid;
    }
}
