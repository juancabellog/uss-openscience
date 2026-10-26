package cl.uss.ca.external.orcid;

import java.util.ArrayList;
import java.util.List;

public class OrcidWorkGroupRecord {
    public String orcid;
    public Long lastModifiedOrcid;
    public String rawJson;
    public List<OrcidExternalIdRecord> externalIds = new ArrayList<>();
    public List<OrcidWorkRecord> summaries = new ArrayList<>();
}
