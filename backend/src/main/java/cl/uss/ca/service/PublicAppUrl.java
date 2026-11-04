package cl.uss.ca.service;

/**
 * Public frontend origin used in activation / reset emails.
 * APP_PUBLIC_URL (environment) wins over bootstrap.properties so a JAR
 * packaged on a developer machine does not send localhost links from hidra.
 */
public final class PublicAppUrl {
    private PublicAppUrl() {
    }

    public static String resolve(String environmentValue, String configValue) {
        return normalize(firstNonBlank(environmentValue, configValue));
    }

    /**
     * Una URL local de Ethics no se usa si OpenScience ya apunta a un host público.
     * Así un JAR empaquetado en el equipo de desarrollo no manda localhost desde hidra.
     */
    public static String ethicsBase(String configuredEthics, String openscienceBase) {
        String ethics = normalize(configuredEthics);
        String app = normalize(openscienceBase);
        if (ethics != null && !looksLocal(ethics)) {
            return ethics;
        }
        if (app != null && !looksLocal(app)) {
            return origin(app) + "/ethics";
        }
        return ethics;
    }

    static String origin(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int slash = url.indexOf('/', scheme + 3);
        return slash < 0 ? url : url.substring(0, slash);
    }

    public static boolean looksLocal(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        String value = url.toLowerCase();
        return value.contains("://localhost") || value.contains("://127.0.0.1");
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
