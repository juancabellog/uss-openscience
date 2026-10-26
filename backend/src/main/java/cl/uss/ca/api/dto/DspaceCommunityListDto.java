package cl.uss.ca.api.dto;

import java.time.OffsetDateTime;

public class DspaceCommunityListDto {
    public String uuid;
    public String handle;
    public String name;
    public String parentUuid;
    public String parentName;
    public Integer archivedItemsCount;
    public long collectionCount;
    public long itemCount;
    public OffsetDateTime lastSyncAt;
}
