package cl.uss.ca;

import cl.uss.ca.config.AppConfig;
import cl.uss.ca.db.PersistenciaResumen;
import cl.uss.ca.db.PurePersonRepository;
import cl.uss.ca.pure.PersonMapper;
import cl.uss.ca.pure.PureApiClient;
import cl.uss.ca.pure.PurePerson;
import cl.uss.ca.pure.PurePersonService;

import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class CargarPersonasPure {
    public static void main(String[] args) {
        try {
            Properties config = AppConfig.load();
            PurePersonService servicio = new PurePersonService(
                    new PureApiClient(AppConfig.apiUrl(config), AppConfig.apiKey(config)),
                    new PersonMapper()
            );
            List<PurePerson> personas = servicio.obtenerTodasLasPersonas();

            PurePersonRepository repository = new PurePersonRepository(
                    AppConfig.dbUrl(config),
                    AppConfig.dbUser(config),
                    AppConfig.dbPassword(config)
            );
            System.out.println("Guardando " + personas.size() + " personas y sus relaciones en MySQL...");
            PersistenciaResumen persistido = repository.guardarTodas(personas);
            registrarResumenFinal(personas, persistido);
        } catch (Exception ex) {
            System.err.println("Error al cargar personas: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void registrarResumenFinal(List<PurePerson> personas, PersistenciaResumen persistido) {
        long conOrcid = personas.stream()
                .map(PurePerson::getOrcid)
                .filter(Objects::nonNull)
                .count();
        long autenticados = personas.stream()
                .filter(PurePerson::isOrcidAuthenticated)
                .count();

        System.out.println("Carga Pure finalizada");
        System.out.println();
        System.out.println("Personas: " + persistido.getPersonas());
        System.out.println("Asociaciones organizacionales: " + persistido.getAsociaciones());
        System.out.println("Títulos: " + persistido.getTitulos());
        System.out.println();
        System.out.println("Con ORCID: " + conOrcid);
        System.out.println("Sin ORCID: " + (personas.size() - conOrcid));
        System.out.println("ORCID autenticado: " + autenticados);
    }
}
