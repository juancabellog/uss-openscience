package cl.uss.ca.ethics.auth;

/**
 * Configuración de ética ilegible o inválida. Falla el arranque: no hay permisos a medias.
 */
public class EthicsAuthException extends RuntimeException {
    private final String code;

    public EthicsAuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
