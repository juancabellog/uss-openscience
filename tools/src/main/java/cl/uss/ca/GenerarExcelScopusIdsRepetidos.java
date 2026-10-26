package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.ScopusIdsRepetidosExcelRepository;
import cl.uss.ca.scopus.ScopusIdsRepetidosExcelService;
import cl.uss.ca.scopus.ScopusIdsRepetidosExcelWriter;

import java.nio.file.Path;
import java.util.Properties;

public class GenerarExcelScopusIdsRepetidos {
    public static void main(String[] args) {
        try {
            Path destino = args.length > 0
                    ? Path.of(args[0])
                    : Path.of(ScopusIdsRepetidosExcelService.ARCHIVO_DEFAULT);
            Properties config = AppConfig.load();
            new ScopusIdsRepetidosExcelService(
                    new ScopusIdsRepetidosExcelRepository(
                            AppConfig.dbUrl(config),
                            AppConfig.dbUser(config),
                            AppConfig.dbPassword(config)
                    ),
                    new ScopusIdsRepetidosExcelWriter()
            ).generar(destino);
        } catch (Exception ex) {
            System.err.println("Error al generar Excel de Scopus ID repetidos: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
