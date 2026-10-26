package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.JournalIdentifierData;
import cl.uss.ca.external.pure.model.JournalIssnData;
import cl.uss.ca.external.pure.model.JournalKeywordGroupData;
import cl.uss.ca.external.pure.model.JournalTitleData;
import cl.uss.ca.external.pure.model.PureJournal;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class JournalSnapshot {
    public Long id;
    public Long pureId;
    public String uuid;
    public Boolean activeInPure;
    public String createdBy;
    public OffsetDateTime createdDatePure;
    public String modifiedBy;
    public OffsetDateTime modifiedDatePure;
    public String version;
    public String publisherUuid;
    public String publisherSystemName;
    public String typeUri;
    public String typeEn;
    public String typeEs;
    public String workflowStep;
    public String workflowDescriptionEn;
    public String workflowDescriptionEs;
    public String systemName;
    public final List<JournalTitleData> titles = new ArrayList<>();
    public final List<JournalIssnData> issns = new ArrayList<>();
    public final List<JournalIdentifierData> identifiers = new ArrayList<>();
    public final List<JournalKeywordGroupData> keywordGroups = new ArrayList<>();

    public PureJournal asJournal() {
        PureJournal journal = new PureJournal();
        journal.setPureId(pureId);
        journal.setUuid(uuid);
        journal.setCreatedBy(createdBy);
        journal.setCreatedDatePure(createdDatePure);
        journal.setModifiedBy(modifiedBy);
        journal.setModifiedDatePure(modifiedDatePure);
        journal.setVersion(version);
        journal.setPublisherUuid(publisherUuid);
        journal.setPublisherSystemName(publisherSystemName);
        journal.setTypeUri(typeUri);
        journal.setTypeEn(typeEn);
        journal.setTypeEs(typeEs);
        journal.setWorkflowStep(workflowStep);
        journal.setWorkflowDescriptionEn(workflowDescriptionEn);
        journal.setWorkflowDescriptionEs(workflowDescriptionEs);
        journal.setSystemName(systemName);
        journal.getTitles().addAll(titles);
        journal.getIssns().addAll(issns);
        journal.getIdentifiers().addAll(identifiers);
        journal.getKeywordGroups().addAll(keywordGroups);
        return journal;
    }
}
