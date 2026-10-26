package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.OrcidUpdateRepository;
import cl.uss.ca.pure.OrcidUpdateService;
import cl.uss.ca.pure.PersonMapper;
import cl.uss.ca.pure.PureApiClient;
import cl.uss.ca.pure.PurePersonService;

import java.util.Properties;

public class ActualizarOrcidPure {
    public static void main(String[] args) {
        try {
            Properties config = AppConfig.load();
            PurePersonService personService = new PurePersonService(
                    new PureApiClient(AppConfig.apiUrl(config), AppConfig.apiKey(config)),
                    new PersonMapper()
            );
            OrcidUpdateRepository repository = new OrcidUpdateRepository(
                    AppConfig.dbUrl(config),
                    AppConfig.dbUser(config),
                    AppConfig.dbPassword(config)
            );
            new OrcidUpdateService(personService, repository).actualizar();
        } catch (Exception ex) {
            System.err.println("Error al actualizar ORCID desde Pure: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
