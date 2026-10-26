package cl.uss.ca.sync.diff;

import java.util.ArrayList;
import java.util.List;

public class AggregateOutcome {
    public enum Kind {
        CREATED,
        UPDATED,
        UNCHANGED,
        REACTIVATED
    }

    public Kind kind;
    public Long localId;
    public Long pureId;
    public String uuid;
    public boolean parentChanged;
    public boolean childrenChanged;
    public final List<SyncChange> changes = new ArrayList<>();

    public boolean persistParent() {
        return kind != Kind.UNCHANGED;
    }

    public boolean persistChildren() {
        return kind == Kind.CREATED || childrenChanged;
    }

    public boolean touchChangeDetected() {
        return kind != Kind.UNCHANGED;
    }
}
