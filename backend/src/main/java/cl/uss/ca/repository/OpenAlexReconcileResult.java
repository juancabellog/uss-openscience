package cl.uss.ca.repository;

import cl.uss.ca.sync.diff.SyncChange;

import java.util.ArrayList;
import java.util.List;

public class OpenAlexReconcileResult {
    public String kind = "UNCHANGED";
    public long workId;
    public final List<SyncChange> changes = new ArrayList<>();
}
