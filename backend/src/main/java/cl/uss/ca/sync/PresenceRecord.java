package cl.uss.ca.sync;

public class PresenceRecord {
    public final long id;
    public final String uuid;
    public final Long pureId;

    public PresenceRecord(long id, String uuid, Long pureId) {
        this.id = id;
        this.uuid = uuid;
        this.pureId = pureId;
    }
}
