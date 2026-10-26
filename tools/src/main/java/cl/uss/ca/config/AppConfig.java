package cl.uss.ca.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    private AppConfig() {
    }

    public static Properties load() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = AppConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IOException("No se encontró application.properties");
            }
            properties.load(input);
        }
        return properties;
    }

    public static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Falta configurar la propiedad " + key);
        }
        return value.trim();
    }

    public static String apiUrl(Properties properties) {
        return required(properties, "pure.api.url");
    }

    public static String apiKey(Properties properties) {
        String env = blankToNull(System.getenv("PURE_API_KEY"));
        if (env != null) {
            return env;
        }
        String property = blankToNull(properties.getProperty("pure.api.key"));
        if (property != null) {
            return property;
        }
        throw new IllegalStateException(
                "Falta la API Key de Pure. Defina la variable de entorno PURE_API_KEY "
                        + "o la propiedad pure.api.key");
    }

    public static String dbUrl(Properties properties) {
        return required(properties, "db.url");
    }

    public static String dbUser(Properties properties) {
        return required(properties, "db.user");
    }

    public static String dbPassword(Properties properties) {
        return required(properties, "db.password");
    }

    public static String optional(Properties properties, String key) {
        return blankToNull(properties.getProperty(key));
    }

    public static String openAlexMailto(Properties properties) {
        String env = blankToNull(System.getenv("OPENALEX_MAILTO"));
        if (env != null) {
            return env;
        }
        return blankToNull(properties.getProperty("openalex.mailto"));
    }

    public static boolean orcidSearchDebug(Properties properties) {
        String env = blankToNull(System.getenv("ORCID_SEARCH_DEBUG"));
        if (env != null) {
            return Boolean.parseBoolean(env);
        }
        return Boolean.parseBoolean(properties.getProperty("orcid.search.debug", "false"));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
