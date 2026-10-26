package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class DspaceItemListDto {
    public String uuid;
    public String handle;
    public String handleUrl;
    public String name;
    public String dcType;
    public String doi;
    public String normalizedDoi;
    public String accessStatus;
    public String fulltextAvailability;
    public String collectionUuid;
    public String collectionName;
    public String communityUuid;
    public String communityName;
    public String communityPath;
    public String lastOutcome;
    public OffsetDateTime lastSyncAt;
    public OffsetDateTime lastModifiedRemote;
    public String matchMethod;
    public String matchStatus;
    public boolean canonical;
    public Double confidence;
    public Long researchOutputId;
    public String researchOutputUuid;
    public String researchOutputTitle;
    public Integer sharedDoiCount;
    public boolean hasOriginalFile;
    public String dspaceAccessCanonical;
    public String pureAccessCanonical;
    public String pureAccessRaw;
    public String accessComparison;
}
