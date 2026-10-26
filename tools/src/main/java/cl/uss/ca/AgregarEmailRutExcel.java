package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.PurePersonContactoRepository;
import cl.uss.ca.orcid.OrcidEmailRutExcelService;

import java.nio.file.Path;
import java.util.Properties;

public class AgregarEmailRutExcel {
    public static void main(String[] args) {
        try {
            Path entrada = args.length > 0
                    ? Path.of(args[0])
                    : Path.of("Orcid validados.xlsx");
            Properties config = AppConfig.load();
            OrcidEmailRutExcelService servicio = new OrcidEmailRutExcelService(
                    new PurePersonContactoRepository(),
                    AppConfig.dbUrl(config),
                    AppConfig.dbUser(config),
                    AppConfig.dbPassword(config)
            );
            servicio.enriquecer(entrada);
        } catch (Exception ex) {
            System.err.println("Error al agregar email y RUT: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
