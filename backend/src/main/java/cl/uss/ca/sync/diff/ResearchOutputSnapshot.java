package cl.uss.ca.sync.diff;

import cl.uss.ca.external.pure.model.ElectronicVersion;
import cl.uss.ca.external.pure.model.JournalData;
import cl.uss.ca.external.pure.model.KeywordGroupData;
import cl.uss.ca.external.pure.model.OutputContributor;
import cl.uss.ca.external.pure.model.OutputIdentifier;
import cl.uss.ca.external.pure.model.OutputLink;
import cl.uss.ca.external.pure.model.OutputStatus;
import cl.uss.ca.external.pure.model.ResearchOutput;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ResearchOutputSnapshot {
    public Long id;
    public Boolean activeInPure;
    public final ResearchOutput parent = new ResearchOutput();
    public final List<OutputStatus> statuses = new ArrayList<>();
    public final List<OutputContributor> contributors = new ArrayList<>();
    public final List<ElectronicVersion> electronicVersions = new ArrayList<>();
    public final List<OutputIdentifier> identifiers = new ArrayList<>();
    public final List<OutputLink> links = new ArrayList<>();
    public JournalData journal;
    public Long journalAssociationPureId;
    public final List<String> externalOrganizationUuids = new ArrayList<>();
    public final List<KeywordGroupData> keywordGroups = new ArrayList<>();
}
