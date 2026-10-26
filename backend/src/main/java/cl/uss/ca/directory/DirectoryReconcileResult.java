package cl.uss.ca.directory;

public final class DirectoryReconcileResult {
    public final DirectoryReconcileDecision decision;
    public final String issueCode;
    public final DirectoryPersonRef existing;

    private DirectoryReconcileResult(
            DirectoryReconcileDecision decision,
            String issueCode,
            DirectoryPersonRef existing
    ) {
        this.decision = decision;
        this.issueCode = issueCode;
        this.existing = existing;
    }

    public static DirectoryReconcileResult of(DirectoryReconcileDecision decision, DirectoryPersonRef existing) {
        return new DirectoryReconcileResult(decision, null, existing);
    }

    public static DirectoryReconcileResult issue(String code, DirectoryPersonRef existing) {
        return new DirectoryReconcileResult(DirectoryReconcileDecision.ISSUE, code, existing);
    }
}
