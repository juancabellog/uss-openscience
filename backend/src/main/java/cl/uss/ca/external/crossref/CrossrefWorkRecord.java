package cl.uss.ca.external.crossref;

import java.util.ArrayList;
import java.util.List;

public class CrossrefWorkRecord {
    public String doi;
    public String canonicalDoi;
    public String type;
    public String title;
    public String subtitle;
    public String shortTitle;
    public String containerTitle;
    public String publisher;
    public String volume;
    public String issue;
    public String page;
    public String articleNumber;
    public String language;
    public Integer referenceCount;
    public Integer isReferencedByCount;
    public String publishedDate;
    public String publishedPrintDate;
    public String publishedOnlineDate;
    public String issuedDate;
    public String createdCrossrefAt;
    public String depositedCrossrefAt;
    public String indexedCrossrefAt;
    public String registrationAgency;
    public String registrationAgencyId;
    public String rawJson;
    public final List<CrossrefAuthorRecord> authors = new ArrayList<>();
    public final List<CrossrefIssnRecord> issns = new ArrayList<>();
    public final List<CrossrefFunderRecord> funders = new ArrayList<>();
    public final List<CrossrefLicenseRecord> licenses = new ArrayList<>();
    public final List<CrossrefRelationRecord> relations = new ArrayList<>();
}
