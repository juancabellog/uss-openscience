package cl.uss.ca.openalex;

import cl.uss.ca.db.OrcidPendienteRepository;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OrcidSearchService {
    private final OrcidPendienteRepository repository;
    private final OpenAlexService openAlexService;
    private final ResearcherMatchingService matchingService;
    private final boolean debug;

    public OrcidSearchService(OrcidPendienteRepository repository,
                              OpenAlexService openAlexService,
                              ResearcherMatchingService matchingService,
                              boolean debug) {
        this.repository = repository;
        this.openAlexService = openAlexService;
        this.matchingService = matchingService;
        this.debug = debug;
    }

    public OrcidSearchResumen buscarInvestigadoresSinOrcid() throws SQLException {
        List<InvestigadorPendiente> investigadores = repository.buscarInvestigadoresSinOrcid();
        OrcidSearchResumen resumen = new OrcidSearchResumen();
        Set<Long> personasCompletadas = new HashSet<>();

        for (InvestigadorPendiente investigador : investigadores) {
            if (investigador == null || investigador.getPersonId() == null) {
                continue;
            }
            if (!personasCompletadas.add(investigador.getPersonId())) {
                continue;
            }
            buscarOrcidPersona(investigador, resumen);
        }
        resumen.actualizarExcel();
        resumen.imprimir();
        return resumen;
    }

    public OrcidEncontrado buscarOrcidPersona(InvestigadorPendiente investigador,
                                              OrcidSearchResumen resumen) {
        List<PublicacionPendiente> publicaciones = investigador.getPublicaciones();
        resumen.agregarPublicaciones(publicaciones.size());
        System.out.println("Buscando ORCID: " + investigador.getNombreCompleto());
        System.out.println("Publicaciones disponibles: " + publicaciones.size());

        Set<String> doisConsultados = new HashSet<>();
        int indice = 0;
        int revisadas = 0;

        for (PublicacionPendiente publicacion : publicaciones) {
            String doi = publicacion.getDoi();
            String clave = claveDoi(doi);
            if (clave == null) {
                continue;
            }
            if (!doisConsultados.add(clave)) {
                resumen.registrarDoiDuplicado();
                continue;
            }
            indice++;
            revisadas++;
            System.out.println("Consultando DOI " + indice + "/" + publicaciones.size() + ": " + doi.trim());

            OrcidEncontrado encontrado = buscarOrcidEnPublicacion(investigador, publicacion, resumen);
            if (encontrado != null) {
                System.out.println("ORCID encontrado para " + investigador.getNombreCompleto()
                        + ": " + encontrado.getOrcid());
                mostrarResultadoEncontrado(encontrado);
                resumen.registrarPersonaEncontrada(encontrado);
                return encontrado;
            }
        }

        System.out.println("ORCID no encontrado para " + investigador.getNombreCompleto()
                + " después de revisar " + revisadas + " publicaciones");
        mostrarResultadoNoEncontrado(investigador, revisadas);
        resumen.registrarPersonaNoEncontrada(
                investigador.getPersonId(),
                investigador.getNombreCompleto());
        return null;
    }

    public OrcidEncontrado buscarOrcidEnPublicacion(InvestigadorPendiente investigador,
                                                    PublicacionPendiente publicacion,
                                                    OrcidSearchResumen resumen) {
        try {
            resumen.registrarDoiConsultado();
            OpenAlexWork work = openAlexService.obtenerPublicacionPorDoi(publicacion.getDoi());
            OpenAlexAuthor autor = buscarAutorCorrespondiente(investigador, work.getAuthors());
            if (autor == null) {
                return null;
            }
            String orcid = OpenAlexService.normalizarOrcid(autor.getOrcid());
            if (orcid == null) {
                return null;
            }
            String titulo = publicacion.getTitle() != null ? publicacion.getTitle() : work.getTitle();
            return new OrcidEncontrado(
                    investigador.getPersonId(),
                    investigador.getNombreCompleto(),
                    investigador.getCargo(),
                    orcid,
                    publicacion.getDoi().trim(),
                    titulo,
                    autor.nombreMostrado(),
                    autor.getPosicion()
            );
        } catch (OpenAlexNotFoundException ex) {
            resumen.registrarDoiNoEncontrado();
            System.err.println("WARN DOI no encontrado en OpenAlex: " + publicacion.getDoi());
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Búsqueda OpenAlex interrumpida", ex);
        } catch (Exception ex) {
            resumen.registrarErrorOpenAlex();
            System.err.println("WARN OpenAlex falló para DOI " + publicacion.getDoi()
                    + ": " + ex.getMessage());
            return null;
        }
    }

    public OpenAlexAuthor buscarAutorCorrespondiente(InvestigadorPendiente investigador,
                                                     List<OpenAlexAuthor> authors) {
        OpenAlexAuthor autor = matchingService.buscarAutorCorrespondiente(
                investigador.getFirstName(), investigador.getLastName(), authors);
        if (debug && authors != null) {
            String nombreBd = investigador.getNombreCompleto();
            for (OpenAlexAuthor candidato : authors) {
                boolean match = matchingService.coincideConPersona(nombreBd, candidato);
                System.out.println("DEBUG:");
                System.out.println("Comparando:");
                System.out.println("BD: " + nombreBd);
                System.out.println("OpenAlex: " + candidato.nombreMostrado());
                System.out.println("Resultado matching: " + (match ? "COINCIDE" : "NO COINCIDE"));
            }
        }
        return autor;
    }

    public static String normalizarOrcid(String orcid) {
        return OpenAlexService.normalizarOrcid(orcid);
    }

    public static String normalizarDoi(String doi) {
        return claveDoi(doi);
    }

    private static String claveDoi(String doi) {
        if (doi == null) {
            return null;
        }
        String trimmed = doi.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    public void mostrarResultadoEncontrado(OrcidEncontrado resultado) {
        System.out.println();
        System.out.println("ORCID ENCONTRADO");
        System.out.println();
        System.out.println("Person ID : " + resultado.getPersonId());
        System.out.println("Persona   : " + resultado.getNombrePersona());
        System.out.println("Cargo     : " + (resultado.getCargo() == null ? "" : resultado.getCargo()));
        System.out.println("ORCID     : " + resultado.getOrcid());
        System.out.println();
        System.out.println("DOI:");
        System.out.println(resultado.getDoi());
        System.out.println();
        System.out.println("Publicación:");
        System.out.println(resultado.getTitulo() == null ? "" : resultado.getTitulo());
        System.out.println();
        System.out.println("Autor OpenAlex:");
        System.out.println(resultado.getAutorOpenAlex());
        System.out.println("Posición: " + (resultado.getPosicion() == null ? "" : resultado.getPosicion()));
        System.out.println();
    }

    public void mostrarResultadoNoEncontrado(InvestigadorPendiente investigador, int revisadas) {
        System.out.println();
        System.out.println("ORCID NO ENCONTRADO");
        System.out.println();
        System.out.println("Person ID : " + investigador.getPersonId());
        System.out.println("Persona   : " + investigador.getNombreCompleto());
        System.out.println("Publicaciones revisadas: " + revisadas);
        System.out.println();
    }
}
