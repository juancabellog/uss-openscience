package cl.uss.ca.quality.consistency;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ExternalEvidence {
    public String source;
    public String sourceRecordId;
    public ConsistencyField field;
    public String originalValue;
    public String normalizedValue;
    public ComparisonResult result;
    public OffsetDateTime lastExternalSyncAt;
    public Boolean completeAuthorList;
    public LocatorKind locatorKind;
    public final List<AuthorEvidence> authors = new ArrayList<>();
    public List<AuthorEvidence> pureAuthors;
}
