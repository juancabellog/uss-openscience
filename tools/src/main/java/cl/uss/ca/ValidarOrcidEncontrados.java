package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.OrcidPersonaDoiRepository;
import cl.uss.ca.orcid.OrcidValidationExcelIo;
import cl.uss.ca.orcid.OrcidNombreMatcher;
import cl.uss.ca.orcid.OrcidPublicClient;
import cl.uss.ca.orcid.OrcidValidationService;

import java.nio.file.Path;
import java.util.Properties;

public class ValidarOrcidEncontrados {
    public static void main(String[] args) {
        try {
            Path entrada = args.length > 0
                    ? Path.of(args[0])
                    : Path.of("orcid-encontrados.xlsx");
            Properties config = AppConfig.load();
            OrcidPersonaDoiRepository repository = new OrcidPersonaDoiRepository(
                    AppConfig.dbUrl(config),
                    AppConfig.dbUser(config),
                    AppConfig.dbPassword(config)
            );
            OrcidValidationService servicio = new OrcidValidationService(
                    new OrcidPublicClient(),
                    new OrcidNombreMatcher(),
                    new OrcidValidationExcelIo(),
                    repository,
                    AppConfig.orcidSearchDebug(config)
            );
            servicio.validarArchivoExcel(entrada);
        } catch (Exception ex) {
            System.err.println("Error al validar ORCID: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
