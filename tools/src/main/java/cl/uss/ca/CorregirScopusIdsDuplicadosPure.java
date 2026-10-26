package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.ScopusIdDuplicadoRepository;
import cl.uss.ca.pure.PureApiClient;
import cl.uss.ca.scopus.CorreccionScopusOpciones;
import cl.uss.ca.scopus.ScopusIdCorreccionService;

import java.util.Properties;

public class CorregirScopusIdsDuplicadosPure {
    public static void main(String[] args) {
        try {
            CorreccionScopusOpciones opciones = CorreccionScopusOpciones.parse(args);
            Properties config = AppConfig.load();
            new ScopusIdCorreccionService(
                    new ScopusIdDuplicadoRepository(
                            AppConfig.dbUrl(config),
                            AppConfig.dbUser(config),
                            AppConfig.dbPassword(config)
                    ),
                    new PureApiClient(AppConfig.apiUrl(config), AppConfig.apiKey(config)),
                    opciones
            ).corregir();
        } catch (Exception ex) {
            System.err.println("Error al corregir Scopus IDs en Pure: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
