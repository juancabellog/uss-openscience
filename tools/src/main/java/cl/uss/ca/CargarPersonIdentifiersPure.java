package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.PersonIdentifierRepository;
import cl.uss.ca.pure.PersonIdentifierLoadService;
import cl.uss.ca.pure.PersonIdentifierMapper;
import cl.uss.ca.pure.PureApiClient;

import java.util.Properties;

public class CargarPersonIdentifiersPure {
    public static void main(String[] args) {
        try {
            Properties config = AppConfig.load();
            boolean debug = Boolean.parseBoolean(config.getProperty("pure.update.debug", "false"));
            String envDebug = System.getenv("PURE_UPDATE_DEBUG");
            if (envDebug != null && !envDebug.isBlank()) {
                debug = Boolean.parseBoolean(envDebug.trim());
            }
            new PersonIdentifierLoadService(
                    new PureApiClient(AppConfig.apiUrl(config), AppConfig.apiKey(config)),
                    new PersonIdentifierMapper(),
                    new PersonIdentifierRepository(
                            AppConfig.dbUrl(config),
                            AppConfig.dbUser(config),
                            AppConfig.dbPassword(config)
                    ),
                    debug
            ).cargar();
        } catch (Exception ex) {
            System.err.println("Error al cargar identifiers de personas: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
