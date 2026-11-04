package cl.uss.ca.ethics.auth;

/** Contexto de una invitación de Ética al activar la cuenta compartida. No es otra identidad. */
public final class EthicsActivationView {
    public final boolean ethics;
    public final boolean pureReadonly;
    public final String givenName;
    public final String familyName;
    public final String positionTitle;

    public EthicsActivationView(
            boolean ethics,
            boolean pureReadonly,
            String givenName,
            String familyName,
            String positionTitle
    ) {
        this.ethics = ethics;
        this.pureReadonly = pureReadonly;
        this.givenName = givenName;
        this.familyName = familyName;
        this.positionTitle = positionTitle;
    }

    public static EthicsActivationView none() {
        return new EthicsActivationView(false, false, null, null, null);
    }
}
