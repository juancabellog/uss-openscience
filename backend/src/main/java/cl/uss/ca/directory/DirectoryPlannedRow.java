package cl.uss.ca.directory;

public final class DirectoryPlannedRow {
    public final DirectoryIncomingRow incoming;
    public final DirectoryReconcileDecision decision;
    public final String issueCode;
    public final DirectoryPersonRef existing;

    public DirectoryPlannedRow(
            DirectoryIncomingRow incoming,
            DirectoryReconcileDecision decision,
            String issueCode,
            DirectoryPersonRef existing
    ) {
        this.incoming = incoming;
        this.decision = decision;
        this.issueCode = issueCode;
        this.existing = existing;
    }
}
