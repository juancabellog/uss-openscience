package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.ScopusAuthorRepository;
import cl.uss.ca.scopus.ScopusAuthorCsvParser;
import cl.uss.ca.scopus.ScopusAuthorCsvService;

import java.nio.file.Path;
import java.util.Properties;

public class CargarAutoresScopusCsv {
    public static void main(String[] args) {
        try {
            Path csv = resolverArchivo(args);
            Properties config = AppConfig.load();
            boolean debug = Boolean.parseBoolean(config.getProperty("scopus.csv.debug", "false"));
            String envDebug = System.getenv("SCOPUS_CSV_DEBUG");
            if (envDebug != null && !envDebug.isBlank()) {
                debug = Boolean.parseBoolean(envDebug.trim());
            }
            new ScopusAuthorCsvService(
                    new ScopusAuthorCsvParser(),
                    new ScopusAuthorRepository(
                            AppConfig.dbUrl(config),
                            AppConfig.dbUser(config),
                            AppConfig.dbPassword(config)
                    ),
                    debug
            ).cargar(csv);
        } catch (Exception ex) {
            System.err.println("Error al cargar autores Scopus: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static Path resolverArchivo(String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException("""
                    Debe indicar el archivo CSV a importar.

                    Formato esperado:
                    Scopus-Author-[NOMBRE AFILIACION]-[AFFILIATION_ID].csv

                    Ejemplo:
                    Scopus-Author-Universidad Mayor-60005881.csv
                    """);
        }
        return Path.of(args[0].trim());
    }
}
