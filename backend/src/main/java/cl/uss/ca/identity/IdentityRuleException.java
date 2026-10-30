package cl.uss.ca.identity;

/** Regla de negocio del modelo de personas. El código viaja al cliente; el mensaje es para la persona usuaria. */
public final class IdentityRuleException extends RuntimeException {
    private final String code;

    public IdentityRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
