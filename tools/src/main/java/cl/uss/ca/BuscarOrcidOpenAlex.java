package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.OrcidPendienteRepository;
import cl.uss.ca.openalex.OpenAlexService;
import cl.uss.ca.openalex.OrcidSearchService;
import cl.uss.ca.openalex.ResearcherMatchingService;

import java.util.Properties;

public class BuscarOrcidOpenAlex {
    public static void main(String[] args) {
        try {
            Properties config = AppConfig.load();
            OrcidSearchService servicio = new OrcidSearchService(
                    new OrcidPendienteRepository(
                            AppConfig.dbUrl(config),
                            AppConfig.dbUser(config),
                            AppConfig.dbPassword(config)
                    ),
                    new OpenAlexService(AppConfig.openAlexMailto(config)),
                    new ResearcherMatchingService(),
                    AppConfig.orcidSearchDebug(config)
            );
            servicio.buscarInvestigadoresSinOrcid();
        } catch (Exception ex) {
            System.err.println("Error al buscar ORCID en OpenAlex: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
