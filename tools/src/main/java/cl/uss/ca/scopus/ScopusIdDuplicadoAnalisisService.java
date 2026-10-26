package cl.uss.ca.scopus;

import cl.uss.ca.db.ScopusIdDuplicadoRepository;
import cl.uss.ca.db.ScopusIdDuplicadoRepository.ScopusPersonIdentifierRow;
import cl.uss.ca.db.ScopusIdDuplicadoResumen;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
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

public class ScopusIdDuplicadoAnalisisService {
    public static final String ARCHIVO_DEFAULT = "scopus_ids_posiblemente_mal_asignados.csv";

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader(
                    "person_id",
                    "uuid",
                    "nombre",
                    "scopus_id_repetido",
                    "cantidad_scopus_ids",
                    "todos_scopus_ids"
            )
            .build();

    private final ScopusIdDuplicadoRepository repository;
    private final boolean debug;

    public ScopusIdDuplicadoAnalisisService(ScopusIdDuplicadoRepository repository, boolean debug) {
        this.repository = repository;
        this.debug = debug;
    }

    public ScopusIdDuplicadoResumen analizar(Path csvSalida) throws SQLException, IOException {
        ScopusIdDuplicadoResumen resumen = new ScopusIdDuplicadoResumen();
        List<String> scopusRepetidos;
        List<ScopusPersonIdentifierRow> filas;
        try (Connection connection = repository.abrirConexion()) {
            scopusRepetidos = repository.buscarScopusIdsRepetidos(connection);
            filas = repository.buscarIdentificadoresDePersonasInvolucradas(connection);
        }

        resumen.setScopusIdsRepetidos(scopusRepetidos.size());
        System.out.println("Analizando " + scopusRepetidos.size() + " Scopus ID compartidos...");

        Map<Long, PersonaScopus> personasPorId = construirPersonas(filas);
        List<ScopusDuplicado> grupos = construirGrupos(scopusRepetidos, personasPorId);
        List<ScopusIdAnomalia> anomalias = clasificar(grupos, resumen);

        Path escrito = escribirCsv(csvSalida, anomalias);
        resumen.setFilasCsv(anomalias.size());
        resumen.setArchivoGenerado(escrito.getFileName().toString());
        imprimirAnomalias(anomalias);
        resumen.imprimir();
        return resumen;
    }

    Map<Long, PersonaScopus> construirPersonas(List<ScopusPersonIdentifierRow> filas) {
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

    List<ScopusDuplicado> construirGrupos(List<String> scopusRepetidos,
                                          Map<Long, PersonaScopus> personasPorId) {
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
        return new ArrayList<>(grupos.values());
    }

    List<ScopusIdAnomalia> clasificar(List<ScopusDuplicado> grupos, ScopusIdDuplicadoResumen resumen) {
        List<ScopusIdAnomalia> anomalias = new ArrayList<>();
        Set<Long> personasReportadas = new LinkedHashSet<>();
        int procesados = 0;
        int total = grupos.size();
        for (ScopusDuplicado grupo : grupos) {
            procesados++;
            if (total > 0 && (procesados % 50 == 0 || procesados == total)) {
                System.out.println("Procesados " + procesados + "/" + total + "...");
            }
            if (grupo.tienePatronUnoVsMultiples()) {
                resumen.registrarGrupoPatron();
                for (PersonaScopus persona : grupo.personasConMultiplesIds()) {
                    personasReportadas.add(persona.getPersonId());
                    anomalias.add(new ScopusIdAnomalia(
                            persona.getPersonId(),
                            persona.getUuid(),
                            persona.getNombre(),
                            grupo.getScopusId(),
                            persona.cantidadScopusIds(),
                            persona.todosScopusIdsTexto()
                    ));
                    if (debug) {
                        System.out.println("DEBUG Scopus ID repetido=" + grupo.getScopusId()
                                + " person_id=" + persona.getPersonId()
                                + " cantidad=" + persona.cantidadScopusIds());
                    }
                }
            } else if (grupo.todosConUnSoloId()) {
                resumen.registrarTodosConUnId();
            } else if (grupo.todosConMultiplesIds()) {
                resumen.registrarTodosConMultiplesIds();
            }
        }
        anomalias.sort(Comparator
                .comparing(ScopusIdAnomalia::scopusIdRepetido)
                .thenComparing(ScopusIdAnomalia::personId));
        resumen.setPersonasPosiblementeMalAsignadas(personasReportadas.size());
        return anomalias;
    }

    Path escribirCsv(Path destino, List<ScopusIdAnomalia> anomalias) throws IOException {
        Path absoluto = destino.toAbsolutePath().normalize();
        Path parent = absoluto.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(absoluto, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT)) {
            for (ScopusIdAnomalia anomalia : anomalias) {
                printer.printRecord(
                        anomalia.personId(),
                        anomalia.uuid(),
                        anomalia.nombre(),
                        anomalia.scopusIdRepetido(),
                        anomalia.cantidadScopusIds(),
                        anomalia.todosScopusIds()
                );
            }
        }
        return absoluto;
    }

    private void imprimirAnomalias(List<ScopusIdAnomalia> anomalias) {
        if (anomalias.isEmpty()) {
            System.out.println("No se encontraron personas con el patrón 1 ID vs múltiples IDs.");
            System.out.println();
            return;
        }
        System.out.println();
        for (ScopusIdAnomalia anomalia : anomalias) {
            System.out.println("Person ID:             " + anomalia.personId());
            System.out.println("Nombre:                " + anomalia.nombre());
            System.out.println("Scopus ID repetido:    " + anomalia.scopusIdRepetido());
            System.out.println("Cantidad Scopus ID:    " + anomalia.cantidadScopusIds());
            System.out.println("Todos sus Scopus ID:   " + anomalia.todosScopusIds());
            System.out.println();
        }
    }
}
