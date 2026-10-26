package cl.uss.ca.orcid;

import cl.uss.ca.db.OrcidPersonaDoiRepository;
import cl.uss.ca.db.PersonaDoisBd;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrcidValidationService {
    private final OrcidPublicClient orcidClient;
    private final OrcidNombreMatcher nombreMatcher;
    private final OrcidValidationExcelIo excelIo;
    private final OrcidPersonaDoiRepository personaRepository;
    private final boolean debug;
    private final Map<String, OrcidValidationData> cacheOrcid = new LinkedHashMap<>();
    private final Map<Long, String> uuidPorPersonId = new LinkedHashMap<>();
    private final Map<String, Set<String>> doisBdPorUuid = new LinkedHashMap<>();

    public OrcidValidationService(OrcidPersonaDoiRepository personaRepository) {
        this(new OrcidPublicClient(), new OrcidNombreMatcher(), new OrcidValidationExcelIo(),
                personaRepository, false);
    }

    public OrcidValidationService(OrcidPublicClient orcidClient,
                                  OrcidNombreMatcher nombreMatcher,
                                  OrcidValidationExcelIo excelIo,
                                  OrcidPersonaDoiRepository personaRepository,
                                  boolean debug) {
        this.orcidClient = orcidClient;
        this.nombreMatcher = nombreMatcher;
        this.excelIo = excelIo;
        this.personaRepository = personaRepository;
        this.debug = debug;
    }

    public OrcidValidationResumen validarArchivoExcel(Path entrada) throws Exception {
        Path absoluto = entrada.toAbsolutePath().normalize();
        if (!Files.exists(absoluto)) {
            throw new IllegalArgumentException("No existe el XLSX de entrada: " + absoluto);
        }
        List<OrcidExcelFila> filas = excelIo.leer(absoluto);
        List<OrcidFilaValidada> validadas = new ArrayList<>();
        OrcidValidationResumen resumen = new OrcidValidationResumen();
        int total = filas.size();
        int indice = 0;

        for (OrcidExcelFila fila : filas) {
            indice++;
            System.out.println("Procesando fila " + indice + "/" + total);
            System.out.println("ORCID: " + fila.getOrcid());
            System.out.println("Persona: " + fila.getNombre());

            OrcidFilaValidada validada = validarFila(fila, resumen);
            validadas.add(validada);
            resumen.registrarFila(validada);

            System.out.println("Procesando Id=" + fila.getId());
            System.out.println("UUID=" + (validada.getUuid().isEmpty() ? "" : validada.getUuid()));
            System.out.println("DOI BD=" + validada.getCantidadDoisBd());
            System.out.println("DOI ORCID=" + validada.getCantidadDoisOrcid());
            System.out.println("DOI comunes=" + validada.getCantidadDoisComunes());
            if (debug && !validada.getDoisEnComun().isEmpty()) {
                System.out.println("DEBUG DOI's en común: " + validada.getDoisEnComun());
            }
            System.out.println("Nombre ORCID: " + validada.getNombreOrcid());
            System.out.println("Nombre coherente: " + validada.getOk());
            System.out.println("Afiliación USS: " + validada.getAfiliacion());
            System.out.println();
        }

        Path destino = excelIo.destinoSeguro(absoluto);
        Path escrito = excelIo.escribirExcelResultado(validadas, destino);
        resumen.setArchivoGenerado(escrito);
        resumen.imprimir();
        return resumen;
    }

    public OrcidFilaValidada validarFila(OrcidExcelFila fila, OrcidValidationResumen resumen)
            throws InterruptedException {
        PersonaBd personaBd = obtenerPersonaBd(fila);
        OrcidValidationData datos = obtenerDatosOrcid(fila, resumen);
        return evaluarFila(fila, personaBd, datos);
    }

    private OrcidValidationData obtenerDatosOrcid(OrcidExcelFila fila, OrcidValidationResumen resumen)
            throws InterruptedException {
        String orcidOriginal = fila.getOrcid();
        if (orcidOriginal == null || orcidOriginal.isBlank()) {
            return OrcidValidationData.vacio();
        }
        String orcid = OrcidPublicClient.normalizarOrcid(orcidOriginal);
        if (!OrcidPublicClient.esFormatoValido(orcid)) {
            System.err.println("WARN ORCID con formato inválido en fila "
                    + fila.getNumeroFilaExcel() + ": " + orcidOriginal);
            return OrcidValidationData.invalido(orcidOriginal);
        }
        return consultarOrcidConCache(orcid, resumen);
    }

    private OrcidValidationData consultarOrcidConCache(String orcid, OrcidValidationResumen resumen)
            throws InterruptedException {
        OrcidValidationData cacheado = cacheOrcid.get(orcid);
        if (cacheado != null) {
            resumen.registrarCache();
            System.out.println("ORCID obtenido desde cache");
            return cacheado;
        }
        resumen.registrarConsultaHttp();
        OrcidValidationData datos = orcidClient.consultarOrcid(orcid);
        if (datos.getEstado() == OrcidValidationData.Estado.ERROR) {
            resumen.registrarErrorHttp();
            System.err.println("WARN " + datos.getMensaje());
        }
        cacheOrcid.put(orcid, datos);
        return datos;
    }

    private PersonaBd obtenerPersonaBd(OrcidExcelFila fila) {
        Long personId = parsearPersonId(fila.getId());
        if (personId == null) {
            return PersonaBd.vacia();
        }
        if (uuidPorPersonId.containsKey(personId)) {
            String uuid = uuidPorPersonId.get(personId);
            Set<String> dois = uuid.isEmpty() ? Set.of() : doisBdPorUuid.getOrDefault(uuid, Set.of());
            return new PersonaBd(uuid, dois);
        }
        try {
            PersonaDoisBd consulta = personaRepository.buscarPorPersonId(personId);
            if (!consulta.encontrada()) {
                System.err.println("WARN No se encontró pure_person para id=" + personId);
                uuidPorPersonId.put(personId, "");
                return PersonaBd.vacia();
            }
            String uuid = consulta.getUuid();
            Set<String> doisNormalizados = normalizarDoisBd(consulta.getDois());
            uuidPorPersonId.put(personId, uuid);
            doisBdPorUuid.put(uuid, doisNormalizados);
            return new PersonaBd(uuid, doisNormalizados);
        } catch (SQLException ex) {
            System.err.println("WARN Error al consultar MySQL para id=" + personId + ": " + ex.getMessage());
            uuidPorPersonId.put(personId, "");
            return PersonaBd.vacia();
        }
    }

    private OrcidFilaValidada evaluarFila(OrcidExcelFila fila, PersonaBd personaBd,
                                         OrcidValidationData datos) {
        String uuid = personaBd.uuid;
        Set<String> doisBd = personaBd.doisNormalizados;
        Set<String> doisOrcid = datos.getDoisOrcid() == null ? Set.of() : datos.getDoisOrcid();
        Set<String> comunes = interseccionDois(doisBd, doisOrcid);
        String doisEnComun = formatearDoisComunes(comunes);

        String nombreOrcid;
        String ok;
        String afiliacion;
        if (datos.getEstado() == OrcidValidationData.Estado.VACIO
                || datos.getEstado() == OrcidValidationData.Estado.INVALIDO) {
            nombreOrcid = "";
            ok = "NO";
            afiliacion = datos.getEstado() == OrcidValidationData.Estado.INVALIDO ? "ERROR" : "NO";
        } else if (datos.getEstado() == OrcidValidationData.Estado.ERROR) {
            nombreOrcid = "";
            ok = "ERROR";
            afiliacion = "ERROR";
        } else if (datos.getEstado() == OrcidValidationData.Estado.NO_ENCONTRADO) {
            nombreOrcid = "";
            ok = "NO";
            afiliacion = "NO";
        } else {
            nombreOrcid = datos.getNombreOrcid();
            ok = nombresCoherentesConAliases(fila.getNombre(), nombreOrcid, datos.getOtherNames())
                    ? "SI"
                    : "NO";
            afiliacion = datos.isAfiliacionUss() ? "SI" : "NO";
        }

        return new OrcidFilaValidada(
                fila,
                uuid,
                nombreOrcid,
                ok,
                afiliacion,
                doisEnComun,
                doisBd.size(),
                doisOrcid.size(),
                comunes.size()
        );
    }

    private boolean nombresCoherentesConAliases(String nombreExcel, String nombreOrcid,
                                                List<String> otherNames) {
        if (nombreMatcher.nombresCoherentes(nombreExcel, nombreOrcid)) {
            return true;
        }
        if (otherNames == null) {
            return false;
        }
        for (String alias : otherNames) {
            if (nombreMatcher.nombresCoherentes(nombreExcel, alias)) {
                return true;
            }
        }
        return false;
    }

    static Set<String> interseccionDois(Set<String> doisBd, Set<String> doisOrcid) {
        Set<String> comunes = new LinkedHashSet<>();
        if (doisBd == null || doisOrcid == null) {
            return comunes;
        }
        for (String doi : doisBd) {
            if (doisOrcid.contains(doi)) {
                comunes.add(doi);
            }
        }
        return comunes;
    }

    static String formatearDoisComunes(Set<String> comunes) {
        if (comunes == null || comunes.isEmpty()) {
            return "";
        }
        return String.join("; ", comunes);
    }

    private static Set<String> normalizarDoisBd(Set<String> originales) {
        Set<String> normalizados = new LinkedHashSet<>();
        if (originales == null) {
            return normalizados;
        }
        for (String doi : originales) {
            String normalizado = DoiNormalizer.normalizarDoi(doi);
            if (normalizado != null) {
                normalizados.add(normalizado);
            }
        }
        return normalizados;
    }

    private static Long parsearPersonId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException ex) {
            System.err.println("WARN Id no numérico: " + id);
            return null;
        }
    }

    private static final class PersonaBd {
        private final String uuid;
        private final Set<String> doisNormalizados;

        private PersonaBd(String uuid, Set<String> doisNormalizados) {
            this.uuid = uuid == null ? "" : uuid;
            this.doisNormalizados = doisNormalizados == null ? Set.of() : doisNormalizados;
        }

        private static PersonaBd vacia() {
            return new PersonaBd("", Set.of());
        }
    }
}
