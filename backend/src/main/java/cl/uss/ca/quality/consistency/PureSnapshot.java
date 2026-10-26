package cl.uss.ca.quality.consistency;

import java.util.ArrayList;
import java.util.List;

public class PureSnapshot {
    public long id;
    public Long pureId;
    public String uuid;
    public String title;
    public Integer publicationYear;
    public String journalTitle;
    public String publisher;
    public final List<String> issns = new ArrayList<>();
    public String volume;
    public String issue;
    public String pages;
    public String accessRaw;
    public AccessSemantic access;
    public String licenseRaw;
    public String licenseNormalized;
    public final List<String> awardNumbers = new ArrayList<>();
    public final List<AuthorEvidence> authors = new ArrayList<>();
    public final List<String> normalizedDois = new ArrayList<>();
}
