package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.ScopusIdDuplicadoRepository;
import cl.uss.ca.scopus.ScopusIdDuplicadoAnalisisService;

import java.nio.file.Path;
import java.util.Properties;

public class DetectarScopusIdsDuplicados {
    public static void main(String[] args) {
        try {
            Path csvSalida = args.length > 0
                    ? Path.of(args[0])
                    : Path.of(ScopusIdDuplicadoAnalisisService.ARCHIVO_DEFAULT);
            Properties config = AppConfig.load();
            boolean debug = Boolean.parseBoolean(config.getProperty("scopus.duplicados.debug", "false"));
            String envDebug = System.getenv("SCOPUS_DUPLICADOS_DEBUG");
            if (envDebug != null && !envDebug.isBlank()) {
                debug = Boolean.parseBoolean(envDebug.trim());
            }
            new ScopusIdDuplicadoAnalisisService(
                    new ScopusIdDuplicadoRepository(
                            AppConfig.dbUrl(config),
                            AppConfig.dbUser(config),
                            AppConfig.dbPassword(config)
                    ),
                    debug
            ).analizar(csvSalida);
        } catch (Exception ex) {
            System.err.println("Error al detectar Scopus ID duplicados: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
