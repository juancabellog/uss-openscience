package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.ScopusPublicationAffiliationRepository;
import cl.uss.ca.db.ScopusPublicationAuthorRepository;
import cl.uss.ca.db.ScopusPublicationKeywordRepository;
import cl.uss.ca.db.ScopusPublicationRepository;
import cl.uss.ca.scopus.importer.ScopusPublicationCsvParser;
import cl.uss.ca.scopus.importer.ScopusPublicationImportOptions;
import cl.uss.ca.scopus.importer.ScopusPublicationImportService;

import java.nio.file.Path;
import java.util.Properties;

public class CargarPublicacionesScopusCsv {
    public static void main(String[] args) {
        System.setProperty("com.mysql.cj.disableAbandonedConnectionCleanup", "true");
        try {
            ScopusPublicationImportOptions opciones = ScopusPublicationImportOptions.parse(args);
            Properties config = AppConfig.load();
            aplicarArchivoPorDefecto(opciones, config);
            new ScopusPublicationImportService(
                    new ScopusPublicationCsvParser(),
                    new ScopusPublicationRepository(),
                    new ScopusPublicationAuthorRepository(),
                    new ScopusPublicationAffiliationRepository(),
                    new ScopusPublicationKeywordRepository(),
                    AppConfig.dbUrl(config),
                    AppConfig.dbUser(config),
                    AppConfig.dbPassword(config)
            ).importar(opciones);
        } catch (Exception ex) {
            System.err.println("Error al cargar publicaciones Scopus: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        } finally {
            detenerLimpiezaMysql();
        }
    }

    private static void detenerLimpiezaMysql() {
        try {
            com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
        } catch (Exception ignored) {
            // exec:java no debe quedar esperando el hilo daemon del conector MySQL
        }
    }

    static void aplicarArchivoPorDefecto(ScopusPublicationImportOptions opciones, Properties config) {
        if (opciones.getArchivo() != null) {
            return;
        }
        String env = System.getenv("SCOPUS_PUBLICATION_CSV");
        String csv = (env != null && !env.isBlank()) ? env.trim() : AppConfig.optional(config, "scopus.publication.csv");
        if (csv != null) {
            opciones.setArchivo(Path.of(csv));
        }
    }
}
