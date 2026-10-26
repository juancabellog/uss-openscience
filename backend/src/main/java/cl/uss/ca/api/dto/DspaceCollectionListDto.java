package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class DspaceCollectionListDto {
    public String uuid;
    public String handle;
    public String name;
    public String communityUuid;
    public String communityName;
    public Integer archivedItemsCount;
    public long itemCount;
    public OffsetDateTime lastSyncAt;
}
