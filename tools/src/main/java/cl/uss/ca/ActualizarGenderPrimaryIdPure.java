package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.GenderPrimaryIdRepository;
import cl.uss.ca.pure.GenderPrimaryIdUpdateService;
import cl.uss.ca.pure.PersonMapper;
import cl.uss.ca.pure.PureApiClient;
import cl.uss.ca.pure.PurePersonService;

import java.util.Properties;

public class ActualizarGenderPrimaryIdPure {
    public static void main(String[] args) {
        try {
            Properties config = AppConfig.load();
            PersonMapper mapper = new PersonMapper();
            PurePersonService personService = new PurePersonService(
                    new PureApiClient(AppConfig.apiUrl(config), AppConfig.apiKey(config)),
                    mapper
            );
            GenderPrimaryIdRepository repository = new GenderPrimaryIdRepository(
                    AppConfig.dbUrl(config),
                    AppConfig.dbUser(config),
                    AppConfig.dbPassword(config)
            );
            boolean debug = Boolean.parseBoolean(config.getProperty("pure.update.debug", "false"));
            String envDebug = System.getenv("PURE_UPDATE_DEBUG");
            if (envDebug != null && !envDebug.isBlank()) {
                debug = Boolean.parseBoolean(envDebug.trim());
            }
            new GenderPrimaryIdUpdateService(personService, mapper, repository, debug).actualizar();
        } catch (Exception ex) {
            System.err.println("Error al actualizar gender y primaryId: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
