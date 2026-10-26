package cl.uss.ca.external.openalex;

import java.util.ArrayList;
import java.util.List;

public class OpenAlexWorkRecord {
    public String openalexWorkId;
    public String doi;
    public String displayName;
    public Integer publicationYear;
    public String publicationDate;
    public String workType;
    public Integer citedByCount;
    public Integer referencedWorksCount;
    public Boolean isOa;
    public String oaStatus;
    public String language;
    public String primarySourceId;
    public String primarySourceDisplayName;
    public String primarySourceIssnL;
    public String updatedDate;
    public String rawJson;
    public boolean rawJsonTruncated;
    public Integer rawJsonOriginalSize;
    public boolean authorshipsIncomplete;
    public final List<OpenAlexAuthorshipRecord> authorships = new ArrayList<>();
    public final List<OpenAlexTopicRecord> topics = new ArrayList<>();

    /**
     * True only when the persisted authorship list is incomplete versus the
     * source payload. A large complete author list is not truncated.
     */
    public boolean authorshipsTruncated() {
        return authorshipsIncomplete;
    }
}
