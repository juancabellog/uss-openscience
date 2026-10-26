package cl.uss.ca.scopus;

import cl.uss.ca.db.CorreccionScopusResumen;
import cl.uss.ca.db.ScopusIdDuplicadoRepository;
import cl.uss.ca.db.ScopusIdDuplicadoRepository.ScopusPersonIdentifierRow;
import cl.uss.ca.pure.PureApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScopusIdCorreccionService {
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader(
                    "person_id",
                    "uuid",
                    "nombre",
                    "scopus_id_repetido",
                    "identifier_pure_id",
                    "otros_scopus_ids",
                    "accion",
                    "http_get",
                    "http_put",
                    "mensaje"
            )
            .build();

    private final ScopusIdDuplicadoRepository repository;
    private final PureApiClient pureClient;
    private final ObjectMapper objectMapper;
    private final CorreccionScopusOpciones opciones;

    public ScopusIdCorreccionService(ScopusIdDuplicadoRepository repository,
                                     PureApiClient pureClient,
                                     CorreccionScopusOpciones opciones) {
        this.repository = repository;
        this.pureClient = pureClient;
        this.objectMapper = pureClient.objectMapper();
        this.opciones = opciones;
    }

    public CorreccionScopusResumen corregir() throws SQLException, IOException, InterruptedException {
        imprimirBanner();
        CorreccionScopusResumen resumen = new CorreccionScopusResumen();
        resumen.setModo(opciones.modoResumen());

        List<CorreccionScopus> candidatos = construirCandidatosCorreccion(resumen);
        candidatos = filtrarCandidatos(candidatos);
        if (opciones.getLimit() != null && candidatos.size() > opciones.getLimit()) {
            candidatos = new ArrayList<>(candidatos.subList(0, opciones.getLimit()));
        }
        resumen.setPersonasCandidatas(candidatos.size());
        System.out.println("Personas candidatas a procesar: " + candidatos.size());
        System.out.println();

        List<ResultadoCorreccion> resultados = new ArrayList<>();
        int erroresPutConsecutivos = 0;
        for (CorreccionScopus candidato : candidatos) {
            ResultadoCorreccion resultado = procesarCandidato(candidato, resumen);
            resultados.add(resultado);
            if (opciones.isExecute() && ResultadoCorreccion.ERROR_PUT.equals(resultado.accion())) {
                erroresPutConsecutivos++;
                if (erroresPutConsecutivos >= opciones.getErroresPutConsecutivosMax()) {
                    System.err.println("Proceso detenido por múltiples errores consecutivos de actualización.");
                    break;
                }
            } else if (opciones.isExecute() && ResultadoCorreccion.ELIMINADO.equals(resultado.accion())) {
                erroresPutConsecutivos = 0;
            }
        }

        Path csv = escribirAuditoria(opciones.getCsvSalida(), resultados);
        resumen.setArchivoAuditoria(csv.getFileName().toString());
        resumen.imprimir();
        return resumen;
    }

    List<CorreccionScopus> construirCandidatosCorreccion(CorreccionScopusResumen resumen) throws SQLException {
        List<String> scopusRepetidos;
        List<ScopusPersonIdentifierRow> filas;
        try (Connection connection = repository.abrirConexion()) {
            scopusRepetidos = repository.buscarScopusIdsRepetidos(connection);
            filas = repository.buscarIdentificadoresDePersonasInvolucradas(connection);
        }
        resumen.setScopusIdsCompartidos(scopusRepetidos.size());
        System.out.println("Scopus ID compartidos detectados: " + scopusRepetidos.size());

        Map<Long, PersonaScopus> personasPorId = construirPersonas(filas);
        Map<String, ScopusDuplicado> grupos = new LinkedHashMap<>();
        for (String scopusId : scopusRepetidos) {
            grupos.put(scopusId, new ScopusDuplicado(scopusId));
        }
        for (PersonaScopus persona : personasPorId.values()) {
            for (String scopusId : persona.getScopusIds()) {
                ScopusDuplicado grupo = grupos.get(scopusId);
                if (grupo != null) {
                    grupo.agregarPersona(persona);
                }
            }
        }

        List<CorreccionScopus> candidatos = new ArrayList<>();
        int gruposCorregibles = 0;
        for (ScopusDuplicado grupo : grupos.values()) {
            if (!grupo.tienePatronUnoVsMultiples()) {
                continue;
            }
            gruposCorregibles++;
            for (PersonaScopus persona : grupo.personasConMultiplesIds()) {
                if (persona.getUuid() == null || persona.getUuid().isBlank()) {
                    continue;
                }
                Set<String> otros = new LinkedHashSet<>(persona.getScopusIds());
                otros.remove(grupo.getScopusId());
                if (otros.isEmpty()) {
                    continue;
                }
                candidatos.add(new CorreccionScopus(
                        persona.getPersonId(),
                        persona.getUuid(),
                        persona.getNombre(),
                        grupo.getScopusId(),
                        otros
                ));
            }
        }
        resumen.setGruposCorregibles(gruposCorregibles);
        candidatos.sort(Comparator
                .comparing(CorreccionScopus::scopusIdEliminar)
                .thenComparing(CorreccionScopus::personId));
        return candidatos;
    }

    private List<CorreccionScopus> filtrarCandidatos(List<CorreccionScopus> candidatos) {
        Long personId = opciones.getPersonId();
        String uuid = opciones.getUuid();
        if (personId == null && (uuid == null || uuid.isBlank())) {
            return candidatos;
        }
        List<CorreccionScopus> filtrados = new ArrayList<>();
        for (CorreccionScopus candidato : candidatos) {
            boolean coincidePersona = personId == null || candidato.personId() == personId;
            boolean coincideUuid = uuid == null || uuid.isBlank()
                    || uuid.equalsIgnoreCase(candidato.uuid());
            if (coincidePersona && coincideUuid) {
                filtrados.add(candidato);
            }
        }
        return filtrados;
    }

    ResultadoCorreccion procesarCandidato(CorreccionScopus candidato, CorreccionScopusResumen resumen)
            throws InterruptedException {
        if (candidato.uuid() == null || candidato.uuid().isBlank()) {
            resumen.registrarValidacionRechazada();
            return new ResultadoCorreccion(candidato, "", ResultadoCorreccion.VALIDACION_FALLIDA,
                    null, null, "uuid vacío");
        }
        HttpResponse<String> getResponse;
        try {
            getResponse = obtenerPersonaPure(candidato.uuid());
        } catch (IOException ex) {
            resumen.registrarGet();
            resumen.registrarValidacionRechazada();
            return new ResultadoCorreccion(candidato, "", ResultadoCorreccion.ERROR_GET,
                    null, null, "Error GET: " + ex.getMessage());
        }
        resumen.registrarGet();
        int httpGet = getResponse.statusCode();
        if (httpGet == 404) {
            resumen.registrarValidacionRechazada();
            return new ResultadoCorreccion(candidato, "", ResultadoCorreccion.NO_ENCONTRADO_EN_PURE,
                    httpGet, null, "Persona no encontrada en Pure");
        }
        if (!PureApiClient.esRespuestaExitosa(httpGet) || getResponse.body() == null) {
            resumen.registrarValidacionRechazada();
            return new ResultadoCorreccion(candidato, "", ResultadoCorreccion.ERROR_GET,
                    httpGet, null, "GET no exitoso HTTP " + httpGet);
        }

        JsonNode personJson;
        try {
            personJson = objectMapper.readTree(getResponse.body());
        } catch (IOException ex) {
            resumen.registrarValidacionRechazada();
            return new ResultadoCorreccion(candidato, "", ResultadoCorreccion.ERROR_GET,
                    httpGet, null, "JSON GET inválido: " + ex.getMessage());
        }

        JsonNode objetivo = ScopusIdentifierHelper.buscarIdentifierScopus(personJson, candidato.scopusIdEliminar());
        String mensajeValidacion = validarCorreccion(personJson, candidato);
        if (mensajeValidacion != null) {
            resumen.registrarValidacionRechazada();
            imprimirCandidato(candidato, personJson, false);
            System.err.println("WARN Validación fallida: " + mensajeValidacion);
            System.out.println();
            return new ResultadoCorreccion(
                    candidato,
                    objetivo == null ? "" : ScopusIdentifierHelper.identifierPureIdDe(objetivo),
                    ResultadoCorreccion.VALIDACION_FALLIDA,
                    httpGet,
                    null,
                    mensajeValidacion
            );
        }

        resumen.registrarValidacionCorrecta();
        String identifierPureId = ScopusIdentifierHelper.identifierPureIdDe(objetivo);
        imprimirCandidato(candidato, personJson, true);

        if (!opciones.isExecute()) {
            System.out.println("DRY RUN - NO SE REALIZA PUT");
            System.out.println();
            return new ResultadoCorreccion(candidato, identifierPureId,
                    ResultadoCorreccion.DRY_RUN_ELIMINARIA, httpGet, null,
                    "Validado contra Pure; PUT no enviado");
        }

        JsonNode copia = personJson.deepCopy();
        if (!(copia instanceof ObjectNode editable)) {
            resumen.registrarValidacionRechazada();
            return new ResultadoCorreccion(candidato, identifierPureId,
                    ResultadoCorreccion.VALIDACION_FALLIDA, httpGet, null,
                    "El JSON de Person no es un objeto modificable");
        }
        int eliminados = eliminarIdentifierScopus(editable, candidato.scopusIdEliminar());
        if (eliminados != 1) {
            resumen.registrarValidacionRechazada();
            return new ResultadoCorreccion(candidato, identifierPureId,
                    ResultadoCorreccion.VALIDACION_FALLIDA, httpGet, null,
                    "La eliminación en el JSON no removió exactamente 1 identifier");
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(editable);
        } catch (IOException ex) {
            return new ResultadoCorreccion(candidato, identifierPureId, ResultadoCorreccion.ERROR_PUT,
                    httpGet, null, "No se pudo serializar el PUT: " + ex.getMessage());
        }

        HttpResponse<String> putResponse;
        try {
            putResponse = actualizarPersonaPure(candidato.uuid(), payload, PureApiClient.etagDe(getResponse));
        } catch (IOException ex) {
            resumen.registrarPut();
            resumen.registrarPutError();
            return new ResultadoCorreccion(candidato, identifierPureId, ResultadoCorreccion.ERROR_PUT,
                    httpGet, null, "Error PUT: " + ex.getMessage());
        }
        resumen.registrarPut();
        int httpPut = putResponse.statusCode();
        if (!PureApiClient.esRespuestaExitosa(httpPut)) {
            resumen.registrarPutError();
            System.err.println("ERROR PUT HTTP " + httpPut + " uuid=" + candidato.uuid());
            System.out.println();
            return new ResultadoCorreccion(candidato, identifierPureId, ResultadoCorreccion.ERROR_PUT,
                    httpGet, httpPut, "PUT no exitoso HTTP " + httpPut);
        }
        resumen.registrarPutExitoso();

        String mensajeVerificacion = verificarActualizacionPure(candidato);
        if (mensajeVerificacion == null) {
            resumen.registrarVerificacionOk();
            System.out.println("PUT exitoso. Verificación GET posterior OK.");
            System.out.println();
            return new ResultadoCorreccion(candidato, identifierPureId, ResultadoCorreccion.ELIMINADO,
                    httpGet, httpPut, "Scopus ID eliminado en Pure");
        }
        resumen.registrarVerificacionError();
        System.err.println("WARN Verificación posterior: " + mensajeVerificacion);
        System.out.println();
        return new ResultadoCorreccion(candidato, identifierPureId, ResultadoCorreccion.ELIMINADO,
                httpGet, httpPut, "PUT exitoso pero verificación: " + mensajeVerificacion);
    }

    HttpResponse<String> obtenerPersonaPure(String uuid) throws IOException, InterruptedException {
        return pureClient.obtenerPersonaPorUuid(uuid);
    }

    HttpResponse<String> actualizarPersonaPure(String uuid, String jsonBody, String etag)
            throws IOException, InterruptedException {
        return pureClient.actualizarPersonaPorUuid(uuid, jsonBody, etag);
    }

    String validarCorreccion(JsonNode personJson, CorreccionScopus candidato) {
        if (personJson == null || personJson.isNull() || !personJson.isObject()) {
            return "El GET no devolvió un objeto Person";
        }
        JsonNode identifiers = personJson.get("identifiers");
        if (identifiers == null || !identifiers.isArray()) {
            return "El JSON no contiene identifiers[]";
        }
        List<JsonNode> coincidencias = ScopusIdentifierHelper.buscarIdentifiersScopus(
                personJson, candidato.scopusIdEliminar());
        if (coincidencias.isEmpty()) {
            return "El Scopus ID repetido no aparece en Pure";
        }
        if (coincidencias.size() != 1) {
            return "El Scopus ID repetido aparece " + coincidencias.size() + " veces en Pure";
        }
        Set<String> scopusEnPure = ScopusIdentifierHelper.obtenerScopusIdsPure(personJson);
        if (!scopusEnPure.contains(candidato.scopusIdEliminar())) {
            return "El Scopus ID repetido no es un identifier Scopus en Pure";
        }
        if (scopusEnPure.size() < 2) {
            return "Pure no tiene otro Scopus ID distinto; no se elimina para no dejar a la persona sin Scopus ID";
        }
        Set<String> restantes = new LinkedHashSet<>(scopusEnPure);
        restantes.remove(candidato.scopusIdEliminar());
        if (restantes.isEmpty()) {
            return "Después de eliminar no quedaría ningún Scopus ID";
        }
        int totalOriginal = ScopusIdentifierHelper.identifiersDe(personJson).size();
        if (totalOriginal - 1 < 1) {
            return "Después de eliminar no quedaría ningún identifier";
        }
        return null;
    }

    int eliminarIdentifierScopus(ObjectNode personJson, String scopusId) {
        return ScopusIdentifierHelper.eliminarIdentifierScopus(personJson, scopusId);
    }

    String verificarActualizacionPure(CorreccionScopus candidato) throws InterruptedException {
        try {
            HttpResponse<String> response = obtenerPersonaPure(candidato.uuid());
            if (!PureApiClient.esRespuestaExitosa(response.statusCode()) || response.body() == null) {
                return "GET posterior HTTP " + response.statusCode();
            }
            JsonNode personJson = objectMapper.readTree(response.body());
            Set<String> scopus = ScopusIdentifierHelper.obtenerScopusIdsPure(personJson);
            if (scopus.contains(candidato.scopusIdEliminar())) {
                return "el Scopus ID eliminado sigue presente";
            }
            for (String otro : candidato.otrosScopusIds()) {
                if (!scopus.contains(otro)) {
                    return "falta Scopus ID que debía permanecer: " + otro;
                }
            }
            return null;
        } catch (IOException ex) {
            return "error al verificar GET posterior: " + ex.getMessage();
        }
    }

    Path escribirAuditoria(Path destino, List<ResultadoCorreccion> resultados) throws IOException {
        Path absoluto = destino.toAbsolutePath().normalize();
        Path parent = absoluto.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(absoluto, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT)) {
            for (ResultadoCorreccion resultado : resultados) {
                CorreccionScopus correccion = resultado.correccion();
                printer.printRecord(
                        correccion.personId(),
                        correccion.uuid(),
                        correccion.nombre(),
                        correccion.scopusIdEliminar(),
                        resultado.identifierPureId(),
                        correccion.otrosScopusIdsTexto(),
                        resultado.accion(),
                        resultado.httpGet() == null ? "" : resultado.httpGet(),
                        resultado.httpPut() == null ? "" : resultado.httpPut(),
                        resultado.mensaje()
                );
            }
        }
        return absoluto;
    }

    private Map<Long, PersonaScopus> construirPersonas(List<ScopusPersonIdentifierRow> filas) {
        Map<Long, PersonaScopus> personas = new LinkedHashMap<>();
        for (ScopusPersonIdentifierRow fila : filas) {
            PersonaScopus persona = personas.computeIfAbsent(
                    fila.personId(),
                    id -> new PersonaScopus(id, fila.uuid(), fila.firstName(), fila.lastName())
            );
            persona.agregarScopusId(fila.identifierValue());
        }
        return personas;
    }

    private void imprimirBanner() {
        System.out.println("===============================================");
        System.out.println("CORRECCIÓN SCOPUS IDs PURE");
        System.out.println("===============================================");
        System.out.println();
        System.out.println("MODO: " + opciones.modoTexto());
        System.out.println();
        if (opciones.isExecute()) {
            System.out.println("ATENCIÓN: SE REALIZARÁN MODIFICACIONES EN PURE");
        } else {
            System.out.println("NO SE REALIZARÁN MODIFICACIONES EN PURE");
        }
        System.out.println();
    }

    private void imprimirCandidato(CorreccionScopus candidato, JsonNode personJson, boolean valido) {
        Set<String> scopusEnPure = ScopusIdentifierHelper.obtenerScopusIdsPure(personJson);
        Set<String> permaneceran = new LinkedHashSet<>(scopusEnPure);
        permaneceran.remove(candidato.scopusIdEliminar());
        System.out.println("-----------------------------------------------");
        System.out.println("CANDIDATO A CORRECCIÓN");
        System.out.println("-----------------------------------------------");
        System.out.println();
        System.out.println("Person ID:       " + candidato.personId());
        System.out.println("UUID:            " + candidato.uuid());
        System.out.println("Nombre:          " + candidato.nombre());
        System.out.println();
        System.out.println("Scopus repetido:");
        System.out.println(candidato.scopusIdEliminar());
        System.out.println();
        System.out.println("Scopus actuales en Pure:");
        for (String scopusId : scopusEnPure) {
            System.out.println(scopusId);
        }
        System.out.println();
        if (valido) {
            System.out.println("Se eliminará:");
            System.out.println(candidato.scopusIdEliminar());
            System.out.println();
            System.out.println("Permanecerán:");
            for (String scopusId : permaneceran) {
                System.out.println(scopusId);
            }
            System.out.println();
        }
    }
}
