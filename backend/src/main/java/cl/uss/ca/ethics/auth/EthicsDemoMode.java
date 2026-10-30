package cl.uss.ca.ethics.auth;

/**
 * El selector demo no autoriza. En producción no se muestra, aunque alguien active la bandera.
 */
public final class EthicsDemoMode {
    private EthicsDemoMode() {
    }

    public static boolean selectorVisible(boolean enabled, boolean production) {
        return enabled && !production;
    }
}
