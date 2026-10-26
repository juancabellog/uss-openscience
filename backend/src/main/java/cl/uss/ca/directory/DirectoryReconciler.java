package cl.uss.ca.directory;

/**
 * Directory identity is uuid. RUT HMAC reconciles imports. Email never creates
 * a second person when the RUT already matches. Weak name matches are ignored.
 */
public final class DirectoryReconciler {
    private DirectoryReconciler() {
    }

    public static DirectoryReconcileResult decide(DirectoryIncomingRow incoming, DirectoryPersonRef byRut, DirectoryPersonRef byEmail) {
        if (incoming == null) {
            return DirectoryReconcileResult.issue("INVALID_ROW", null);
        }
        boolean emailOk = DirectoryEmail.isUsable(incoming.emailNormalized);
        boolean rutOk = incoming.rutHmac != null && DirectoryRut.isValid(incoming.rutNormalized);
        if (!emailOk && incoming.emailNormalized != null) {
            return DirectoryReconcileResult.issue("INVALID_EMAIL", byEmail);
        }
        if (incoming.rutNormalized != null && !rutOk) {
            return DirectoryReconcileResult.issue("INVALID_RUT", byRut);
        }
        if (!emailOk && !rutOk) {
            return DirectoryReconcileResult.issue("MISSING_EMAIL", null);
        }
        if (byRut != null && byEmail != null && byRut.id != byEmail.id) {
            return DirectoryReconcileResult.issue("RUT_EMAIL_CONFLICT", byRut);
        }
        DirectoryPersonRef matched = byRut != null ? byRut : byEmail;
        if (matched == null) {
            if (!emailOk) {
                return DirectoryReconcileResult.issue("MISSING_EMAIL", null);
            }
            if (!rutOk) {
                return DirectoryReconcileResult.issue("MISSING_RUT", null);
            }
            return DirectoryReconcileResult.of(DirectoryReconcileDecision.CREATE, null);
        }
        if (byEmail != null && byRut == null && rutOk && byEmail.rutHmac != null
                && !byEmail.rutHmac.equals(incoming.rutHmac)) {
            return DirectoryReconcileResult.issue("EMAIL_RUT_CONFLICT", byEmail);
        }
        if (!matched.activeInDirectory) {
            return DirectoryReconcileResult.of(DirectoryReconcileDecision.REACTIVATE, matched);
        }
        if (incoming.sameSnapshot(matched) && (incoming.emailNormalized == null || matched.ownsEmail(incoming.emailNormalized))) {
            return DirectoryReconcileResult.of(DirectoryReconcileDecision.UNCHANGED, matched);
        }
        return DirectoryReconcileResult.of(DirectoryReconcileDecision.UPDATE, matched);
    }

    /**
     * Empresa is persisted as raw source. It becomes an alternate email only when
     * it is a usable address distinct from Email.
     */
    public static String alternateEmpresaEmail(String empresaRaw, String emailNormalized) {
        String empresa = DirectoryEmail.normalize(empresaRaw);
        if (!DirectoryEmail.isUsable(empresa)) {
            return null;
        }
        if (empresa.equals(emailNormalized)) {
            return null;
        }
        return empresa;
    }
}
