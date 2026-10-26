package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class OpenAlexMatchSummaryDto {
    public Long id;
    public Long personId;
    public Long personPureId;
    public String personUuid;
    public String personName;
    public String personPosition;
    public String personOrcid;
    public String openalexAuthorId;
    public String openalexDisplayName;
    public String openalexOrcid;
    public String openalexUrl;
    public String status;
    public String matchMethod;
    public boolean selected;
    public boolean selectedManually;
    public int matchingWorkCount;
    public boolean orcidMatch;
    public boolean ussAffiliation;
    public boolean reviewRequested;
    public OffsetDateTime lastEvaluatedAt;
    public OffsetDateTime confirmedAt;
    public String confirmedBy;
    public boolean identityConfirmed;
    public String orcidComparison;
    public boolean orcidSyncAvailable;
    public String orcidSyncBlockedReason;
    public final List<String> reasonCodes = new ArrayList<>();
}
