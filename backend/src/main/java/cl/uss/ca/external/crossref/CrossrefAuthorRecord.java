package cl.uss.ca.external.crossref;

import java.util.ArrayList;
import java.util.List;

public class CrossrefAuthorRecord {
    public int sequenceNo;
    public String given;
    public String family;
    public String suffix;
    public String fullName;
    public String orcid;
    public Boolean authenticatedOrcid;
    public final List<CrossrefAffiliationRecord> affiliations = new ArrayList<>();
}
