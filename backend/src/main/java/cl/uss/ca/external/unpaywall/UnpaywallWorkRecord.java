package cl.uss.ca.external.unpaywall;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class UnpaywallWorkRecord {
    public String doi;
    public String doiUrl;
    public Boolean isOa;
    public String oaStatus;
    public String genre;
    public String title;
    public Integer year;
    public String publishedDate;
    public String publisher;
    public String journalName;
    public String journalIssns;
    public String journalIssnL;
    public Boolean journalIsOa;
    public Boolean journalIsInDoaj;
    public Boolean hasRepositoryCopy;
    public OffsetDateTime apiUpdatedAt;
    public UnpaywallLocationRecord bestOaLocation;
    public final List<UnpaywallLocationRecord> oaLocations = new ArrayList<>();
}
