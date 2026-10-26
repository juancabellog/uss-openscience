package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.ResearchOutputRepository;
import cl.uss.ca.pure.PureApiClient;
import cl.uss.ca.pure.ResearchOutputMapper;
import cl.uss.ca.pure.ResearchOutputService;

import java.util.Properties;

public class CargarResearchOutputsPure {
    public static void main(String[] args) {
        try {
            Properties config = AppConfig.load();
            ResearchOutputService servicio = new ResearchOutputService(
                    new PureApiClient(AppConfig.apiUrl(config), AppConfig.apiKey(config)),
                    new ResearchOutputMapper(),
                    new ResearchOutputRepository(
                            AppConfig.dbUrl(config),
                            AppConfig.dbUser(config),
                            AppConfig.dbPassword(config)
                    )
            );
            servicio.cargarTodosLosResearchOutputs();
        } catch (Exception ex) {
            System.err.println("Error al cargar Research Outputs: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
