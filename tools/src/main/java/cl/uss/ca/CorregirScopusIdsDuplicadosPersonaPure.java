package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.ScopusIdsInternosDuplicadosRepository;
import cl.uss.ca.pure.PureApiClient;
import cl.uss.ca.scopus.CorreccionScopusOpciones;
import cl.uss.ca.scopus.ScopusIdsInternosCorreccionService;

import java.nio.file.Path;
import java.util.Properties;

/**
 * Esta clase elimina ocurrencias duplicadas de un mismo Scopus ID.
 * NO elimina Scopus ID distintos pertenecientes a la misma persona.
 * Para un Scopus ID duplicado conserva siempre la segunda ocurrencia
 * recibida desde Pure y elimina las demás.
 *
 * Ejemplo: [A, B, B] -> [A, B2]
 * NO: -> [B2]
 */
public class CorregirScopusIdsDuplicadosPersonaPure {
    public static void main(String[] args) {
        try {
            CorreccionScopusOpciones opciones = CorreccionScopusOpciones.parse(
                    args, Path.of(ScopusIdsInternosCorreccionService.ARCHIVO_AUDITORIA));
            Properties config = AppConfig.load();
            new ScopusIdsInternosCorreccionService(
                    new ScopusIdsInternosDuplicadosRepository(
                            AppConfig.dbUrl(config),
                            AppConfig.dbUser(config),
                            AppConfig.dbPassword(config)
                    ),
                    new PureApiClient(AppConfig.apiUrl(config), AppConfig.apiKey(config)),
                    opciones
            ).corregir();
        } catch (Exception ex) {
            System.err.println("Error al corregir Scopus IDs duplicados internos: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
