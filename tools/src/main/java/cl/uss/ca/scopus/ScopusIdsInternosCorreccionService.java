package cl.uss.ca.scopus;

import cl.uss.ca.db.CorreccionScopusInternoResumen;
import cl.uss.ca.db.ScopusIdsInternosDuplicadosRepository;
import cl.uss.ca.db.ScopusIdsInternosDuplicadosRepository.CandidatoScopusInterno;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Esta clase elimina ocurrencias duplicadas de un mismo Scopus ID.
 * NO elimina Scopus ID distintos pertenecientes a la misma persona.
 * Para un Scopus ID duplicado conserva siempre la segunda ocurrencia
 * recibida desde Pure y elimina las demás.
 *
 * Ejemplo: [A, B, B] -> [A, B2]
 * NO: -> [B2]
 */
public class ScopusIdsInternosCorreccionService {
    public static final String ARCHIVO_AUDITORIA = "correccion_scopus_duplicados_internos.csv";

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader(
                    "person_id",
                    "uuid",
                    "nombre",
                    "scopus_id_duplicado",
                    "scopus_ids_distintos_antes",
                    "ocurrencias_antes",
                    "pure_id_primera_ocurrencia",
                    "pure_id_segunda_ocurrencia_conservada",
                    "pure_ids_eliminados",
                    "scopus_ids_distintos_despues",
                    "ocurrencias_despues",
                    "accion",
                    "mensaje"
            )
            .build();

    private final ScopusIdsInternosDuplicadosRepository repository;
    private final PureApiClient pureClient;
    private final ObjectMapper objectMapper;
    private final CorreccionScopusOpciones opciones;

    public ScopusIdsInternosCorreccionService(ScopusIdsInternosDuplicadosRepository repository,
                                              PureApiClient pureClient,
                                              CorreccionScopusOpciones opciones) {
        this.repository = repository;
        this.pureClient = pureClient;
        this.objectMapper = pureClient.objectMapper();
        this.opciones = opciones;
    }

    public CorreccionScopusInternoResumen corregir() throws SQLException, IOException, InterruptedException {
        imprimirBanner();
        CorreccionScopusInternoResumen resumen = new CorreccionScopusInternoResumen();
        resumen.setModo(opciones.modoResumen());

        List<CandidatoScopusInterno> filasBd = buscarCandidatosEnBd();
        List<PersonaScopusInterna> personas = agruparPorPersona(filasBd);
        personas = filtrarPersonas(personas);
        if (opciones.getLimit() != null && personas.size() > opciones.getLimit()) {
            personas = new ArrayList<>(personas.subList(0, opciones.getLimit()));
        }
        resumen.setPersonasCandidatasBd(personas.size());
        resumen.setScopusDuplicadosDetectados(contarScopus(personas));
        System.out.println("Personas candidatas BD: " + personas.size());
        System.out.println("Scopus duplicados detectados: " + contarScopus(personas));
        System.out.println();

        List<ResultadoCorreccionInterna> resultados = new ArrayList<>();
        int erroresPutConsecutivos = 0;
        for (PersonaScopusInterna persona : personas) {
            List<ResultadoCorreccionInterna> dePersona = procesarPersona(persona, resumen);
            resultados.addAll(dePersona);
            boolean putError = dePersona.stream()
                    .anyMatch(r -> ResultadoCorreccionInterna.ERROR_PUT.equals(r.accion()));
            boolean putOk = dePersona.stream()
                    .anyMatch(r -> ResultadoCorreccionInterna.CORREGIDO_OK.equals(r.accion())
                            || ResultadoCorreccionInterna.VERIFICACION_FALLIDA.equals(r.accion()));
            if (opciones.isExecute() && putError) {
                erroresPutConsecutivos++;
                if (erroresPutConsecutivos >= opciones.getErroresPutConsecutivosMax()) {
                    System.err.println("Proceso detenido por múltiples errores consecutivos de actualización.");
                    break;
                }
            } else if (opciones.isExecute() && putOk) {
                erroresPutConsecutivos = 0;
            }
        }

        Path csv = generarAuditoria(opciones.getCsvSalida(), resultados);
        resumen.setArchivoAuditoria(csv.getFileName().toString());
        resumen.imprimir();
        return resumen;
    }

    List<CandidatoScopusInterno> buscarCandidatosEnBd() throws SQLException {
        try (Connection connection = repository.abrirConexion()) {
            return repository.buscarCandidatos(connection);
        }
    }

    List<PersonaScopusInterna> agruparPorPersona(List<CandidatoScopusInterno> filas) {
        Map<String, PersonaScopusInterna> porUuid = new LinkedHashMap<>();
        for (CandidatoScopusInterno fila : filas) {
            PersonaScopusInterna persona = porUuid.computeIfAbsent(
                    fila.uuid(),
                    uuid -> new PersonaScopusInterna(fila.personId(), uuid, fila.nombre())
            );
            persona.agregar(new ScopusInternoCandidato(fila.scopusId(), fila.ocurrenciasBd()));
        }
        return new ArrayList<>(porUuid.values());
    }

    HttpResponse<String> obtenerPersonaPure(String uuid) throws IOException, InterruptedException {
        return pureClient.obtenerPersonaPorUuid(uuid);
    }

    boolean esScopusIdentifier(JsonNode identifier) {
        return ScopusIdentifierHelper.esScopusIdentifier(identifier);
    }

    Map<String, List<JsonNode>> agruparScopusPorId(JsonNode personJson) {
        return ScopusIdentifierHelper.agruparScopusPorId(personJson);
    }

    List<JsonNode> obtenerCoincidenciasScopus(JsonNode personJson, String scopusId) {
        return ScopusIdentifierHelper.buscarIdentifiersScopus(personJson, scopusId);
    }

    JsonNode seleccionarSegundaOcurrencia(List<JsonNode> coincidencias) {
        return ScopusIdentifierHelper.seleccionarSegundaOcurrencia(coincidencias);
    }

    int eliminarTodasExceptoSegunda(ObjectNode personJson, String scopusId) {
        return ScopusIdentifierHelper.eliminarTodasExceptoSegunda(personJson, scopusId);
    }

    HttpResponse<String> actualizarPersonaPure(String uuid, String jsonBody, String etag)
            throws IOException, InterruptedException {
        return pureClient.actualizarPersonaPorUuid(uuid, jsonBody, etag);
    }

    private List<ResultadoCorreccionInterna> procesarPersona(PersonaScopusInterna persona,
                                                            CorreccionScopusInternoResumen resumen)
            throws InterruptedException {
        if (persona.getUuid() == null || persona.getUuid().isBlank()) {
            resumen.registrarValidacionFallida();
            return List.of(resultadoBasico(persona, null, "", "",
                    ResultadoCorreccionInterna.VALIDACION_FALLIDA, "uuid vacío"));
        }
        HttpResponse<String> getResponse;
        try {
            getResponse = obtenerPersonaPure(persona.getUuid());
        } catch (IOException ex) {
            resumen.registrarGet();
            resumen.registrarValidacionFallida();
            return resultadosErrorGet(persona, "Error GET: " + ex.getMessage());
        }
        resumen.registrarGet();
        int httpGet = getResponse.statusCode();
        if (httpGet == 404) {
            resumen.registrarValidacionFallida();
            return resultadosError(persona, ResultadoCorreccionInterna.NO_ENCONTRADO_EN_PURE,
                    "Persona no encontrada en Pure");
        }
        if (!PureApiClient.esRespuestaExitosa(httpGet) || getResponse.body() == null) {
            resumen.registrarValidacionFallida();
            return resultadosError(persona, ResultadoCorreccionInterna.ERROR_GET,
                    "GET no exitoso HTTP " + httpGet);
        }

        JsonNode personJson;
        try {
            personJson = objectMapper.readTree(getResponse.body());
        } catch (IOException ex) {
            resumen.registrarValidacionFallida();
            return resultadosError(persona, ResultadoCorreccionInterna.ERROR_GET,
                    "JSON GET inválido: " + ex.getMessage());
        }
        if (personJson.get("identifiers") == null || !personJson.get("identifiers").isArray()) {
            resumen.registrarValidacionFallida();
            return resultadosError(persona, ResultadoCorreccionInterna.VALIDACION_FALLIDA,
                    "El JSON no contiene identifiers[]");
        }

        EstadoScopusPersona estadoAntes = EstadoScopusPersona.de(personJson);
        List<DecisionScopus> decisiones = new ArrayList<>();
        for (ScopusInternoCandidato candidato : persona.getScopus()) {
            decisiones.add(analizarScopus(persona, candidato, personJson, estadoAntes, resumen));
        }

        List<DecisionScopus> aAplicar = decisiones.stream().filter(DecisionScopus::aplicar).toList();
        ProyeccionPersona proyeccion = proyectarCorreccion(personJson, aAplicar);
        if (proyeccion == null) {
            resumen.registrarValidacionFallida();
            imprimirPersona(persona, personJson, personJson, estadoAntes.scopusIdsDistintos,
                    estadoAntes.scopusIdsDistintos, persona.getScopusIdsDuplicados());
            return resultadosError(persona, ResultadoCorreccionInterna.VALIDACION_FALLIDA,
                    "El JSON de Person no es un objeto modificable");
        }
        final JsonNode personProyectado = proyeccion.personJson();
        final int eliminadosEsperados = proyeccion.eliminadosEsperados();

        EstadoScopusPersona estadoDespues = EstadoScopusPersona.de(personProyectado);
        String idsAntesTexto = unir(estadoAntes.scopusIdsDistintos);
        String idsDespuesTexto = unir(estadoDespues.scopusIdsDistintos);
        imprimirPersona(persona, personJson, personProyectado,
                estadoAntes.scopusIdsDistintos, estadoDespues.scopusIdsDistintos,
                persona.getScopusIdsDuplicados());

        ValidacionLocal validacion = validarResultadoLocal(estadoAntes, personProyectado, aAplicar,
                eliminadosEsperados);
        if (!validacion.valida()) {
            resumen.registrarValidacionFallida();
            String accion = validacion.mismosDistintos()
                    ? ResultadoCorreccionInterna.VALIDACION_FALLIDA
                    : ResultadoCorreccionInterna.VALIDACION_FALLIDA_SCOPUS_DISTINTOS;
            if (!opciones.isExecute()) {
                System.out.println("DRY RUN - NO SE REALIZA PUT");
                System.out.println();
            } else {
                System.out.println("NO SE REALIZA PUT");
                System.out.println();
            }
            return decisiones.stream()
                    .map(d -> d.conAccion(accion, validacion.mensaje(), idsAntesTexto, idsDespuesTexto,
                            ocurrenciasDespuesDe(personProyectado, d)))
                    .toList();
        }

        if (aAplicar.isEmpty() || !opciones.isExecute()) {
            if (!opciones.isExecute()) {
                System.out.println("DRY RUN - NO SE REALIZA PUT");
                System.out.println();
            }
            return decisiones.stream()
                    .map(d -> d.conSets(idsAntesTexto, idsDespuesTexto, ocurrenciasDespuesDe(personProyectado, d)))
                    .toList();
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(personProyectado);
        } catch (IOException ex) {
            return decisiones.stream()
                    .map(d -> d.aplicar()
                            ? d.conAccion(ResultadoCorreccionInterna.ERROR_PUT, "No se pudo serializar el PUT",
                            idsAntesTexto, idsDespuesTexto, null)
                            : d.conSets(idsAntesTexto, idsDespuesTexto, ocurrenciasDespuesDe(personJson, d)))
                    .toList();
        }

        HttpResponse<String> putResponse;
        try {
            putResponse = actualizarPersonaPure(persona.getUuid(), payload, PureApiClient.etagDe(getResponse));
        } catch (IOException ex) {
            resumen.registrarPutFallido();
            return decisiones.stream()
                    .map(d -> d.aplicar()
                            ? d.conAccion(ResultadoCorreccionInterna.ERROR_PUT, "Error PUT: " + ex.getMessage(),
                            idsAntesTexto, idsDespuesTexto, null)
                            : d.conSets(idsAntesTexto, idsDespuesTexto, ocurrenciasDespuesDe(personJson, d)))
                    .toList();
        }
        int httpPut = putResponse.statusCode();
        if (!PureApiClient.esRespuestaExitosa(httpPut)) {
            resumen.registrarPutFallido();
            System.err.println("ERROR PUT HTTP " + httpPut + " uuid=" + persona.getUuid());
            return decisiones.stream()
                    .map(d -> d.aplicar()
                            ? d.conAccion(ResultadoCorreccionInterna.ERROR_PUT,
                            "PUT no exitoso HTTP " + httpPut, idsAntesTexto, idsDespuesTexto, null)
                            : d.conSets(idsAntesTexto, idsDespuesTexto, ocurrenciasDespuesDe(personJson, d)))
                    .toList();
        }
        resumen.registrarPutExitoso();
        resumen.registrarPersonaModificada();
        resumen.registrarIdentifiersEliminados(eliminadosEsperados);
        aAplicar.forEach(d -> resumen.registrarSegundaConservada());

        JsonNode personVerificado = verificarActualizacion(persona.getUuid());
        String mensajeVerif = personVerificado == null
                ? "No se pudo verificar GET posterior"
                : verificarPersonaDespues(personVerificado, estadoAntes, aAplicar);
        Set<String> idsVerificados = personVerificado == null
                ? Set.of()
                : ScopusIdentifierHelper.obtenerScopusIdsPure(personVerificado);
        String idsVerificadosTexto = personVerificado == null ? "" : unir(idsVerificados);
        if (mensajeVerif != null) {
            resumen.registrarVerificacionFallida();
        } else {
            resumen.registrarVerificacionOk();
        }

        List<ResultadoCorreccionInterna> finales = new ArrayList<>();
        for (DecisionScopus decision : decisiones) {
            if (!decision.aplicar()) {
                finales.add(decision.conSets(idsAntesTexto, idsVerificadosTexto,
                        ocurrenciasDespuesDe(personVerificado == null ? personJson : personVerificado, decision)));
                continue;
            }
            if (mensajeVerif == null) {
                finales.add(decision.conExito(idsAntesTexto, idsVerificadosTexto, personVerificado));
            } else {
                finales.add(decision.conVerificacionFallida(idsAntesTexto, idsVerificadosTexto,
                        personVerificado, mensajeVerif));
            }
        }
        return finales;
    }

    private DecisionScopus analizarScopus(PersonaScopusInterna persona,
                                          ScopusInternoCandidato candidato,
                                          JsonNode personJson,
                                          EstadoScopusPersona estadoAntes,
                                          CorreccionScopusInternoResumen resumen) {
        List<JsonNode> coincidencias = estadoAntes.scopusPorId.getOrDefault(
                candidato.scopusId(), List.of());
        if (coincidencias.isEmpty()) {
            coincidencias = obtenerCoincidenciasScopus(personJson, candidato.scopusId());
        }
        int n = coincidencias.size();
        String idsAntes = unir(estadoAntes.scopusIdsDistintos);
        if (n == 0) {
            resumen.registrarValidacionFallida();
            return DecisionScopus.sinAplicar(persona, candidato, n,
                    ResultadoCorreccionInterna.VALIDACION_FALLIDA,
                    "El Scopus ID de la BD no existe actualmente en Pure",
                    idsAntes, idsAntes, "", "", "", null);
        }
        if (n == 1) {
            resumen.registrarYaCorregido();
            String unico = ScopusIdentifierHelper.identifierPureIdDe(coincidencias.get(0));
            return DecisionScopus.sinAplicar(persona, candidato, n,
                    ResultadoCorreccionInterna.YA_CORREGIDO,
                    "Pure ya tiene una sola ocurrencia",
                    idsAntes, idsAntes, unico, unico, "", 1);
        }
        JsonNode segunda = seleccionarSegundaOcurrencia(coincidencias);
        if (segunda == null) {
            resumen.registrarValidacionFallida();
            return DecisionScopus.sinAplicar(persona, candidato, n,
                    ResultadoCorreccionInterna.VALIDACION_FALLIDA,
                    "No se pudo determinar la segunda ocurrencia",
                    idsAntes, idsAntes, "", "", "", null);
        }
        resumen.registrarDuplicadoConfirmado();
        String primera = ScopusIdentifierHelper.identifierPureIdDe(coincidencias.get(0));
        String conservado = ScopusIdentifierHelper.identifierPureIdDe(segunda);
        List<String> eliminados = new ArrayList<>();
        for (int i = 0; i < coincidencias.size(); i++) {
            if (i != 1) {
                eliminados.add(ScopusIdentifierHelper.identifierPureIdDe(coincidencias.get(i)));
            }
        }
        String mensaje = "";
        if (candidato.ocurrenciasBd() != n) {
            mensaje = "WARN Cantidad BD diferente de cantidad Pure";
            System.err.println(mensaje + " uuid=" + persona.getUuid()
                    + " scopus=" + candidato.scopusId()
                    + " bd=" + candidato.ocurrenciasBd() + " pure=" + n);
        }
        return DecisionScopus.aplicar(persona, candidato, n, primera, conservado,
                String.join(";", eliminados), mensaje, idsAntes);
    }

    private ProyeccionPersona proyectarCorreccion(JsonNode personJson, List<DecisionScopus> aAplicar) {
        if (aAplicar.isEmpty()) {
            return new ProyeccionPersona(personJson, 0);
        }
        JsonNode copia = personJson.deepCopy();
        if (!(copia instanceof ObjectNode editable)) {
            return null;
        }
        int eliminadosEsperados = 0;
        for (DecisionScopus decision : aAplicar) {
            eliminadosEsperados += Math.max(0, decision.ocurrenciasPure() - 1);
            eliminarTodasExceptoSegunda(editable, decision.candidato().scopusId());
        }
        return new ProyeccionPersona(editable, eliminadosEsperados);
    }

    ValidacionLocal validarResultadoLocal(EstadoScopusPersona antes,
                                          JsonNode personDespues,
                                          List<DecisionScopus> aplicadas,
                                          int eliminadosEsperados) {
        EstadoScopusPersona despues = EstadoScopusPersona.de(personDespues);

        if (!antes.scopusIdsDistintos.equals(despues.scopusIdsDistintos)) {
            return ValidacionLocal.distintosFallidos(
                    "VALIDACION_FALLIDA_SCOPUS_DISTINTOS: el set de Scopus ID distintos cambió");
        }

        for (Map.Entry<String, List<JsonNode>> entrada : antes.scopusPorId.entrySet()) {
            String scopusId = entrada.getKey();
            List<JsonNode> ocurrenciasAntes = entrada.getValue();
            List<JsonNode> ocurrenciasDespues = despues.scopusPorId.getOrDefault(scopusId, List.of());
            if (ocurrenciasAntes.size() == 1) {
                if (ocurrenciasDespues.size() != 1) {
                    return ValidacionLocal.fallida(
                            "El Scopus ID no duplicado no quedó con exactamente 1 ocurrencia: " + scopusId);
                }
                String pureIdAntes = ScopusIdentifierHelper.identifierPureIdDe(ocurrenciasAntes.get(0));
                String pureIdDespues = ScopusIdentifierHelper.identifierPureIdDe(ocurrenciasDespues.get(0));
                if (!pureIdAntes.equals(pureIdDespues)) {
                    return ValidacionLocal.fallida(
                            "El Scopus ID no duplicado cambió de pureId: " + scopusId);
                }
            }
        }

        for (DecisionScopus decision : aplicadas) {
            String scopusId = decision.candidato().scopusId();
            List<JsonNode> restantes = despues.scopusPorId.getOrDefault(scopusId, List.of());
            if (restantes.size() != 1) {
                return ValidacionLocal.fallida(
                        "Tras modificar no quedó exactamente 1 ocurrencia de " + scopusId);
            }
            String pureIdRestante = ScopusIdentifierHelper.identifierPureIdDe(restantes.get(0));
            if (!decision.pureIdConservado().isEmpty()
                    && !decision.pureIdConservado().equals(pureIdRestante)) {
                return ValidacionLocal.fallida(
                        "La ocurrencia conservada no coincide con el pureId de la segunda ocurrencia original");
            }
        }

        if (antes.tienePrimaryId && !despues.tienePrimaryId) {
            return ValidacionLocal.fallida("PrimaryId desapareció");
        }
        if (!antes.firmasNoScopus.equals(despues.firmasNoScopus)) {
            return ValidacionLocal.fallida("Se modificaron identifiers que no son Scopus");
        }
        if (despues.totalIdentifiers != antes.totalIdentifiers - eliminadosEsperados) {
            return ValidacionLocal.fallida(
                    "totalIdentifiersDespues != totalIdentifiersAntes - cantidadEliminados");
        }
        return ValidacionLocal.correcta();
    }

    JsonNode verificarActualizacion(String uuid) throws InterruptedException {
        try {
            HttpResponse<String> response = obtenerPersonaPure(uuid);
            if (!PureApiClient.esRespuestaExitosa(response.statusCode()) || response.body() == null) {
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            return null;
        }
    }

    String verificarPersonaDespues(JsonNode personJson,
                                   EstadoScopusPersona antes,
                                   List<DecisionScopus> aplicadas) {
        Set<String> scopusIdsDespues = ScopusIdentifierHelper.obtenerScopusIdsPure(personJson);
        if (!antes.scopusIdsDistintos.equals(scopusIdsDespues)) {
            return "NO permanecieron todos los Scopus ID distintos";
        }
        for (Map.Entry<String, List<JsonNode>> entrada : antes.scopusPorId.entrySet()) {
            if (entrada.getValue().size() != 1) {
                continue;
            }
            List<JsonNode> restantes = ScopusIdentifierHelper.buscarIdentifiersScopus(
                    personJson, entrada.getKey());
            if (restantes.size() != 1) {
                return "El Scopus ID no duplicado no quedó intacto: " + entrada.getKey();
            }
            String pureIdAntes = ScopusIdentifierHelper.identifierPureIdDe(entrada.getValue().get(0));
            String pureIdDespues = ScopusIdentifierHelper.identifierPureIdDe(restantes.get(0));
            if (!pureIdAntes.equals(pureIdDespues)) {
                return "El Scopus ID no duplicado cambió de pureId: " + entrada.getKey();
            }
        }
        for (DecisionScopus decision : aplicadas) {
            String error = verificarScopusConservado(personJson, decision);
            if (error != null) {
                return error;
            }
        }
        if (antes.tienePrimaryId && !ScopusIdentifierHelper.tienePrimaryId(personJson)) {
            return "PrimaryId desapareció";
        }
        if (!antes.firmasNoScopus.equals(ScopusIdentifierHelper.firmasIdentifiersNoScopus(personJson))) {
            return "Se modificaron identifiers que no son Scopus";
        }
        return null;
    }

    String verificarScopusConservado(JsonNode personJson, DecisionScopus decision) {
        List<JsonNode> restantes = obtenerCoincidenciasScopus(personJson, decision.candidato().scopusId());
        if (restantes.size() != 1) {
            return "quedaron " + restantes.size() + " ocurrencias de " + decision.candidato().scopusId();
        }
        String pureIdDespues = ScopusIdentifierHelper.identifierPureIdDe(restantes.get(0));
        if (!decision.pureIdConservado().isEmpty() && !decision.pureIdConservado().equals(pureIdDespues)) {
            return "quedó pureId=" + pureIdDespues + " en vez de " + decision.pureIdConservado();
        }
        return null;
    }

    Path generarAuditoria(Path destino, List<ResultadoCorreccionInterna> resultados) throws IOException {
        Path absoluto = destino.toAbsolutePath().normalize();
        Path parent = absoluto.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(absoluto, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT)) {
            for (ResultadoCorreccionInterna r : resultados) {
                printer.printRecord(
                        r.personId(),
                        r.uuid(),
                        r.nombre(),
                        r.scopusIdDuplicado(),
                        r.scopusIdsDistintosAntes(),
                        vacioSiNull(r.ocurrenciasAntes()),
                        r.pureIdPrimeraOcurrencia(),
                        r.pureIdSegundaOcurrenciaConservada(),
                        r.pureIdsEliminados(),
                        r.scopusIdsDistintosDespues(),
                        vacioSiNull(r.ocurrenciasDespues()),
                        r.accion(),
                        r.mensaje()
                );
            }
        }
        return absoluto;
    }

    private List<PersonaScopusInterna> filtrarPersonas(List<PersonaScopusInterna> personas) {
        Long personId = opciones.getPersonId();
        String uuid = opciones.getUuid();
        if (personId == null && (uuid == null || uuid.isBlank())) {
            return personas;
        }
        List<PersonaScopusInterna> filtradas = new ArrayList<>();
        for (PersonaScopusInterna persona : personas) {
            boolean coincidePersona = personId == null || persona.getPersonId() == personId;
            boolean coincideUuid = uuid == null || uuid.isBlank()
                    || uuid.equalsIgnoreCase(persona.getUuid());
            if (coincidePersona && coincideUuid) {
                filtradas.add(persona);
            }
        }
        return filtradas;
    }

    private void imprimirBanner() {
        System.out.println("===============================================");
        System.out.println("CORRECCIÓN SCOPUS DUPLICADOS INTERNOS PURE");
        System.out.println("===============================================");
        System.out.println();
        System.out.println("Esta clase elimina ocurrencias duplicadas de un mismo Scopus ID.");
        System.out.println("NO elimina Scopus ID distintos pertenecientes a la misma persona.");
        System.out.println("Para un Scopus ID duplicado conserva siempre la segunda ocurrencia");
        System.out.println("recibida desde Pure y elimina las demás.");
        System.out.println("Ejemplo: [A, B, B] -> [A, B2]   NO: -> [B2]");
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

    private void imprimirPersona(PersonaScopusInterna persona,
                                 JsonNode personAntes,
                                 JsonNode personDespues,
                                 Set<String> scopusIdsAntes,
                                 Set<String> scopusIdsDespues,
                                 Set<String> scopusIdsCandidatosBd) {
        Map<String, Integer> totales = new LinkedHashMap<>();
        for (JsonNode identifier : ScopusIdentifierHelper.identifiersDe(personAntes)) {
            String scopusId = ScopusIdentifierHelper.scopusIdDe(identifier);
            if (scopusId != null) {
                totales.merge(scopusId, 1, Integer::sum);
            }
        }
        Map<String, Integer> indicePorId = new LinkedHashMap<>();

        System.out.println("------------------------------------------------");
        System.out.println("Persona: " + persona.getPersonId());
        System.out.println("UUID: " + persona.getUuid());
        System.out.println("Nombre: " + persona.getNombre());
        System.out.println();
        System.out.println("SCOPUS ACTUALES PURE:");
        System.out.println();
        for (JsonNode identifier : ScopusIdentifierHelper.identifiersDe(personAntes)) {
            String scopusId = ScopusIdentifierHelper.scopusIdDe(identifier);
            if (scopusId == null) {
                continue;
            }
            int ocurrencia = indicePorId.merge(scopusId, 1, Integer::sum);
            int total = totales.getOrDefault(scopusId, 0);
            System.out.println(scopusId);
            System.out.println("    pureId=" + ScopusIdentifierHelper.identifierPureIdDe(identifier));
            if (total == 1) {
                System.out.println("    UNICO -> CONSERVAR");
            } else {
                System.out.println("    " + textoOcurrencia(ocurrencia));
                boolean candidato = scopusIdsCandidatosBd.contains(scopusId);
                if (candidato && ocurrencia == 2) {
                    System.out.println("    -> CONSERVAR");
                } else if (candidato) {
                    System.out.println("    -> ELIMINAR");
                } else {
                    System.out.println("    -> CONSERVAR (no candidato BD)");
                }
            }
            System.out.println();
        }

        System.out.println("SCOPUS DESPUÉS:");
        System.out.println();
        for (String scopusId : ScopusIdentifierHelper.obtenerScopusIdsPure(personDespues)) {
            System.out.println(scopusId);
        }
        System.out.println();
        System.out.println("SCOPUS ID DISTINTOS ANTES:");
        System.out.println(scopusIdsAntes.toString());
        System.out.println();
        System.out.println("SCOPUS ID DISTINTOS DESPUÉS:");
        System.out.println(scopusIdsDespues.toString());
        System.out.println();
        System.out.println("VALIDACIÓN:");
        System.out.println("MISMOS SCOPUS ID DISTINTOS = "
                + (scopusIdsAntes.equals(scopusIdsDespues) ? "SI" : "NO"));
        System.out.println();
    }

    private static String textoOcurrencia(int ocurrencia) {
        if (ocurrencia == 1) {
            return "primera ocurrencia";
        }
        if (ocurrencia == 2) {
            return "segunda ocurrencia";
        }
        return "ocurrencia " + ocurrencia;
    }

    private int contarScopus(List<PersonaScopusInterna> personas) {
        int total = 0;
        for (PersonaScopusInterna persona : personas) {
            total += persona.getScopus().size();
        }
        return total;
    }

    private List<ResultadoCorreccionInterna> resultadosErrorGet(PersonaScopusInterna persona, String mensaje) {
        return resultadosError(persona, ResultadoCorreccionInterna.ERROR_GET, mensaje);
    }

    private List<ResultadoCorreccionInterna> resultadosError(PersonaScopusInterna persona,
                                                             String accion,
                                                             String mensaje) {
        List<ResultadoCorreccionInterna> resultados = new ArrayList<>();
        if (persona.getScopus().isEmpty()) {
            resultados.add(resultadoBasico(persona, null, "", "", accion, mensaje));
            return resultados;
        }
        for (ScopusInternoCandidato candidato : persona.getScopus()) {
            resultados.add(resultadoBasico(persona, candidato, "", "", accion, mensaje));
        }
        return resultados;
    }

    private ResultadoCorreccionInterna resultadoBasico(PersonaScopusInterna persona,
                                                       ScopusInternoCandidato candidato,
                                                       String idsAntes,
                                                       String idsDespues,
                                                       String accion,
                                                       String mensaje) {
        return new ResultadoCorreccionInterna(
                persona.getPersonId(),
                persona.getUuid(),
                persona.getNombre(),
                candidato == null ? "" : candidato.scopusId(),
                idsAntes,
                null,
                "",
                "",
                "",
                idsDespues,
                null,
                accion,
                mensaje
        );
    }

    private static Integer ocurrenciasDespuesDe(JsonNode personJson, DecisionScopus decision) {
        if (personJson == null) {
            return null;
        }
        return ScopusIdentifierHelper.buscarIdentifiersScopus(
                personJson, decision.candidato().scopusId()).size();
    }

    static String unir(Collection<String> valores) {
        if (valores == null || valores.isEmpty()) {
            return "";
        }
        return valores.stream()
                .filter(valor -> valor != null && !valor.isBlank())
                .collect(Collectors.joining(";"));
    }

    private static Object vacioSiNull(Integer value) {
        return value == null ? "" : value;
    }

    static final class EstadoScopusPersona {
        final Map<String, List<JsonNode>> scopusPorId;
        final Set<String> scopusIdsDistintos;
        final List<String> firmasNoScopus;
        final boolean tienePrimaryId;
        final int totalIdentifiers;

        private EstadoScopusPersona(Map<String, List<JsonNode>> scopusPorId,
                                    Set<String> scopusIdsDistintos,
                                    List<String> firmasNoScopus,
                                    boolean tienePrimaryId,
                                    int totalIdentifiers) {
            this.scopusPorId = scopusPorId;
            this.scopusIdsDistintos = scopusIdsDistintos;
            this.firmasNoScopus = firmasNoScopus;
            this.tienePrimaryId = tienePrimaryId;
            this.totalIdentifiers = totalIdentifiers;
        }

        static EstadoScopusPersona de(JsonNode personJson) {
            Map<String, List<JsonNode>> scopusPorId = ScopusIdentifierHelper.agruparScopusPorId(personJson);
            return new EstadoScopusPersona(
                    scopusPorId,
                    new LinkedHashSet<>(scopusPorId.keySet()),
                    ScopusIdentifierHelper.firmasIdentifiersNoScopus(personJson),
                    ScopusIdentifierHelper.tienePrimaryId(personJson),
                    ScopusIdentifierHelper.identifiersDe(personJson).size()
            );
        }
    }

    record ProyeccionPersona(JsonNode personJson, int eliminadosEsperados) {
    }

    record ValidacionLocal(boolean valida, boolean mismosDistintos, String mensaje) {
        static ValidacionLocal correcta() {
            return new ValidacionLocal(true, true, null);
        }

        static ValidacionLocal fallida(String mensaje) {
            return new ValidacionLocal(false, true, mensaje);
        }

        static ValidacionLocal distintosFallidos(String mensaje) {
            return new ValidacionLocal(false, false, mensaje);
        }
    }

    static final class DecisionScopus {
        private final PersonaScopusInterna persona;
        private final ScopusInternoCandidato candidato;
        private final int ocurrenciasPure;
        private final String pureIdPrimera;
        private final String pureIdConservado;
        private final String pureIdsEliminados;
        private final String mensaje;
        private final boolean aplicar;
        private final ResultadoCorreccionInterna resultado;

        private DecisionScopus(PersonaScopusInterna persona,
                               ScopusInternoCandidato candidato,
                               int ocurrenciasPure,
                               String pureIdPrimera,
                               String pureIdConservado,
                               String pureIdsEliminados,
                               String mensaje,
                               boolean aplicar,
                               ResultadoCorreccionInterna resultado) {
            this.persona = persona;
            this.candidato = candidato;
            this.ocurrenciasPure = ocurrenciasPure;
            this.pureIdPrimera = pureIdPrimera;
            this.pureIdConservado = pureIdConservado;
            this.pureIdsEliminados = pureIdsEliminados;
            this.mensaje = mensaje;
            this.aplicar = aplicar;
            this.resultado = resultado;
        }

        static DecisionScopus aplicar(PersonaScopusInterna persona,
                                      ScopusInternoCandidato candidato,
                                      int ocurrenciasPure,
                                      String primera,
                                      String conservado,
                                      String eliminados,
                                      String mensaje,
                                      String idsAntes) {
            return new DecisionScopus(persona, candidato, ocurrenciasPure, primera, conservado,
                    eliminados, mensaje, true,
                    new ResultadoCorreccionInterna(
                            persona.getPersonId(), persona.getUuid(), persona.getNombre(),
                            candidato.scopusId(), idsAntes, ocurrenciasPure,
                            primera, conservado, eliminados, idsAntes, null,
                            ResultadoCorreccionInterna.DRY_RUN_CONSERVARIA_SEGUNDO, mensaje
                    ));
        }

        static DecisionScopus sinAplicar(PersonaScopusInterna persona,
                                         ScopusInternoCandidato candidato,
                                         int ocurrenciasPure,
                                         String accion,
                                         String mensaje,
                                         String idsAntes,
                                         String idsDespues,
                                         String primera,
                                         String conservado,
                                         String eliminados,
                                         Integer ocurrenciasDespues) {
            return new DecisionScopus(persona, candidato, ocurrenciasPure, primera, conservado,
                    eliminados, mensaje, false,
                    new ResultadoCorreccionInterna(
                            persona.getPersonId(), persona.getUuid(), persona.getNombre(),
                            candidato.scopusId(), idsAntes, ocurrenciasPure,
                            primera, conservado, eliminados, idsDespues, ocurrenciasDespues,
                            accion, mensaje
                    ));
        }

        boolean aplicar() {
            return aplicar;
        }

        ScopusInternoCandidato candidato() {
            return candidato;
        }

        int ocurrenciasPure() {
            return ocurrenciasPure;
        }

        String pureIdConservado() {
            return pureIdConservado == null ? "" : pureIdConservado;
        }

        ResultadoCorreccionInterna resultado() {
            return resultado;
        }

        ResultadoCorreccionInterna conSets(String idsAntes, String idsDespues, Integer ocurrenciasDespues) {
            return new ResultadoCorreccionInterna(
                    persona.getPersonId(), persona.getUuid(), persona.getNombre(),
                    candidato.scopusId(), idsAntes, ocurrenciasPure,
                    pureIdPrimera, pureIdConservado, pureIdsEliminados,
                    idsDespues, ocurrenciasDespues,
                    resultado.accion(), resultado.mensaje()
            );
        }

        ResultadoCorreccionInterna conAccion(String accion, String mensaje,
                                             String idsAntes, String idsDespues,
                                             Integer ocurrenciasDespues) {
            return new ResultadoCorreccionInterna(
                    persona.getPersonId(), persona.getUuid(), persona.getNombre(),
                    candidato.scopusId(), idsAntes, ocurrenciasPure,
                    pureIdPrimera, pureIdConservado, pureIdsEliminados,
                    idsDespues, ocurrenciasDespues,
                    accion, mensaje
            );
        }

        ResultadoCorreccionInterna conExito(String idsAntes, String idsDespues, JsonNode personDespues) {
            List<JsonNode> restantes = ScopusIdentifierHelper.buscarIdentifiersScopus(
                    personDespues, candidato.scopusId());
            String pureIdDespues = restantes.size() == 1
                    ? ScopusIdentifierHelper.identifierPureIdDe(restantes.get(0))
                    : "";
            return new ResultadoCorreccionInterna(
                    persona.getPersonId(), persona.getUuid(), persona.getNombre(),
                    candidato.scopusId(), idsAntes, ocurrenciasPure,
                    pureIdPrimera, pureIdConservado, pureIdsEliminados,
                    idsDespues, restantes.size(),
                    ResultadoCorreccionInterna.CORREGIDO_OK,
                    mensaje.isBlank() ? "quedó pureId=" + pureIdDespues : mensaje
            );
        }

        ResultadoCorreccionInterna conVerificacionFallida(String idsAntes, String idsDespues,
                                                          JsonNode personDespues, String mensajeVerif) {
            Integer ocurrencias = personDespues == null
                    ? null
                    : ScopusIdentifierHelper.buscarIdentifiersScopus(personDespues, candidato.scopusId()).size();
            return new ResultadoCorreccionInterna(
                    persona.getPersonId(), persona.getUuid(), persona.getNombre(),
                    candidato.scopusId(), idsAntes, ocurrenciasPure,
                    pureIdPrimera, pureIdConservado, pureIdsEliminados,
                    idsDespues, ocurrencias,
                    ResultadoCorreccionInterna.VERIFICACION_FALLIDA, mensajeVerif
            );
        }
    }
}
