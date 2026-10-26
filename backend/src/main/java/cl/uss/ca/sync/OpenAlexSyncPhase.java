package cl.uss.ca.sync;

public enum OpenAlexSyncPhase {
    WORKS,
    AUTHORS,
    CONFLICTS,
    FINALIZING;

    public boolean isAuthors() {
        return this == AUTHORS;
    }
}
