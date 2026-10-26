package cl.uss.ca.quality.consistency;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class SourceSnapshot {
    public String source;
    public String sourceRecordId;
    public String normalizedDoi;
    public OffsetDateTime lastSyncAt;
    public String title;
    public Integer publicationYear;
    public String journalTitle;
    public final List<String> issns = new ArrayList<>();
    public String volume;
    public String issue;
    public String pages;
    public String articleNumber;
    public LocatorKind locatorKind;
    public String publisher;
    public String accessRaw;
    public AccessSemantic access;
    public String licenseRaw;
    public String licenseNormalized;
    public final List<String> awardNumbers = new ArrayList<>();
    public final List<AuthorEvidence> authors = new ArrayList<>();
    public Boolean completeAuthorList;
}
