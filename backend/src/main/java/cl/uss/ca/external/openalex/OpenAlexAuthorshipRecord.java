package cl.uss.ca.external.openalex;

import java.util.ArrayList;
import java.util.List;

public class OpenAlexAuthorshipRecord {
    public Long id;
    public int sourceOrder;
    public String authorPosition;
    public boolean corresponding;
    public String rawAuthorName;
    public String rawAffiliationString;
    public String openalexAuthorId;
    public String authorDisplayName;
    public String authorOrcid;
    public String observedOrcid;
    public final List<OpenAlexInstitutionRecord> institutions = new ArrayList<>();
}
