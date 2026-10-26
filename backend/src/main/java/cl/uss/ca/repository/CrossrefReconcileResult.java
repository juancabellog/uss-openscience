package cl.uss.ca.repository;

import cl.uss.ca.sync.diff.SyncChange;

import java.util.ArrayList;
import java.util.List;

public class CrossrefReconcileResult {
    public String kind = "UNCHANGED";
    public final List<SyncChange> changes = new ArrayList<>();
}
