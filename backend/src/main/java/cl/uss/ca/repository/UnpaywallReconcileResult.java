package cl.uss.ca.repository;

import cl.uss.ca.sync.diff.SyncChange;

import java.util.ArrayList;
import java.util.List;

public class UnpaywallReconcileResult {
    public String kind = "UNCHANGED";
    public boolean skippedFresh;
    public final List<SyncChange> changes = new ArrayList<>();
}
