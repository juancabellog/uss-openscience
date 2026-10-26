package cl.uss.ca.api.dto;

public class PersonOrcidSyncResultDto {
    public long personId;
    public String orcid;
    public String status;
    public long groupsReceived;
    public long groupsCreated;
    public long groupsUpdated;
    public long groupsUnchanged;
    public long groupsInactive;
    public long assertionsReceived;
    public long assertionsCreated;
    public long assertionsUpdated;
    public long assertionsUnchanged;
    public long assertionsInactive;
    public long detailsFetched;
    public long detailErrors;
    public long received;
    public long created;
    public long updated;
    public long unchanged;
    public long reactivated;
    public long markedInactive;
}
