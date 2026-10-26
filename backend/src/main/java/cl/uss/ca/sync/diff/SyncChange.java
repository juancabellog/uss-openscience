package cl.uss.ca.sync.diff;

public class SyncChange {
    public String entity;
    public Long localRecordId;
    public Long pureId;
    public String recordUuid;
    public String recordKey;
    public String changeType;
    public String changeScope;
    public String description;
    public String oldValue;
    public String newValue;

    public static SyncChange of(
            String changeType,
            String changeScope,
            String description,
            String oldValue,
            String newValue
    ) {
        SyncChange change = new SyncChange();
        change.changeType = changeType;
        change.changeScope = changeScope;
        change.description = description;
        change.oldValue = SyncValues.clip(oldValue);
        change.newValue = SyncValues.clip(newValue);
        return change;
    }

    public static SyncChange created() {
        return of("CREATED", "PARENT", "Record created", null, null);
    }

    public static SyncChange reactivated() {
        return of("REACTIVATED", "PARENT", "Returned to PURE", "0", "1");
    }

    public static SyncChange inactivated() {
        return of("INACTIVATED", "PARENT", "No longer present in PURE", "1", "0");
    }

    public static SyncChange updated(String scope, String description, String oldValue, String newValue) {
        return of("UPDATED", scope, description, oldValue, newValue);
    }

    public SyncChange on(String entity, Long localRecordId, Long pureId, String recordUuid) {
        this.entity = entity;
        this.localRecordId = localRecordId;
        this.pureId = pureId;
        this.recordUuid = recordUuid;
        return this;
    }

    public SyncChange key(String recordKey) {
        this.recordKey = recordKey;
        return this;
    }
}
