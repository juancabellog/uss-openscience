package cl.uss.ca.scopus;

import cl.uss.ca.db.ScopusIdsRepetidosExcelRepository;
import cl.uss.ca.db.ScopusIdsRepetidosResumen;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScopusIdsRepetidosExcelService {
    public static final String ARCHIVO_DEFAULT = "Scopus Id Repetidos.xlsx";

    private final ScopusIdsRepetidosExcelRepository repository;
    private final ScopusIdsRepetidosExcelWriter writer;

    public ScopusIdsRepetidosExcelService(ScopusIdsRepetidosExcelRepository repository,
                                          ScopusIdsRepetidosExcelWriter writer) {
        this.repository = repository;
        this.writer = writer;
    }

    public ScopusIdsRepetidosResumen generar(Path destino) throws SQLException, IOException {
        System.out.println("Consultando Scopus ID repetidos en MySQL...");
        List<ScopusDuplicadoRow> filas;
        try (Connection connection = repository.abrirConexion()) {
            filas = repository.buscarParejas(connection);
        }
        Path escrito = writer.escribir(filas, destino);
        ScopusIdsRepetidosResumen resumen = construirResumen(filas, escrito);
        resumen.imprimir();
        return resumen;
    }

    ScopusIdsRepetidosResumen construirResumen(List<ScopusDuplicadoRow> filas, Path excel) {
        Set<String> scopusRepetidos = new HashSet<>();
        Set<Long> personas = new HashSet<>();
        Map<Long, Integer> cantidadPorPersona = new HashMap<>();
        for (ScopusDuplicadoRow fila : filas) {
            scopusRepetidos.add(fila.scopusIdRepetido());
            personas.add(fila.idPerson());
            personas.add(fila.idPerson2());
            cantidadPorPersona.putIfAbsent(fila.idPerson(), fila.cantidadScopus());
            cantidadPorPersona.putIfAbsent(fila.idPerson2(), fila.cantidadScopus2());
        }
        int conUno = 0;
        int conVarios = 0;
        for (int cantidad : cantidadPorPersona.values()) {
            if (cantidad == 1) {
                conUno++;
            } else if (cantidad > 1) {
                conVarios++;
            }
        }
        ScopusIdsRepetidosResumen resumen = new ScopusIdsRepetidosResumen();
        resumen.setScopusIdsRepetidos(scopusRepetidos.size());
        resumen.setPersonasInvolucradas(personas.size());
        resumen.setParejasGeneradas(filas.size());
        resumen.setPersonasConUnScopusId(conUno);
        resumen.setPersonasConVariosScopusIds(conVarios);
        resumen.setExcelGenerado(excel.toAbsolutePath().toString());
        return resumen;
    }
}
