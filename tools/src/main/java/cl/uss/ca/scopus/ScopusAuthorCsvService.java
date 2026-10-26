package cl.uss.ca.scopus;

import cl.uss.ca.db.ScopusAuthorCargaResumen;
import cl.uss.ca.db.ScopusAuthorRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ScopusAuthorCsvService {
    public static final String ARCHIVO_AMBIGUOS = "scopus_author_ambiguos.csv";

    private static final CSVFormat CSV_AMBIGUOS = CSVFormat.DEFAULT.builder()
            .setHeader(
                    "archivo",
                    "affiliation_name",
                    "affiliation_id",
                    "author_name",
                    "auth_id",
                    "cantidad_coincidencias",
                    "scopus_author_ids_encontrados",
                    "motivo"
            )
            .build();

    private final ScopusAuthorCsvParser parser;
    private final ScopusAuthorRepository repository;
    private final boolean debug;

    public ScopusAuthorCsvService(ScopusAuthorCsvParser parser,
                                  ScopusAuthorRepository repository,
                                  boolean debug) {
        this.parser = parser;
        this.repository = repository;
        this.debug = debug;
    }

    public ScopusAuthorCargaResumen cargar(Path archivo) throws IOException, SQLException {
        Path absoluto = archivo.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absoluto)) {
            throw new IOException("No se encontró el archivo CSV: " + absoluto);
        }
        ScopusAffiliation affiliation = parseAffiliationFromFilename(absoluto);
        imprimirInicio(absoluto, affiliation);

        ScopusAuthorCargaResumen resumen = new ScopusAuthorCargaResumen();
        String nombreArchivo = absoluto.getFileName().toString();
        resumen.setArchivo(nombreArchivo);
        resumen.setAfiliacion(affiliation.affiliationName(), affiliation.affiliationId());
        List<ScopusAuthorAmbiguoRecord> ambiguos = new ArrayList<>();
        try (Connection connection = repository.abrirConexion();
             PreparedStatement insertAutor = repository.prepararInsertAutor(connection);
             PreparedStatement selectAuthId = repository.prepararSelectIdPorAuthId(connection);
             PreparedStatement selectNombre = repository.prepararSelectIdPorNombre(connection);
             PreparedStatement insertArea = repository.prepararInsertArea(connection);
             PreparedStatement upsertAfiliacion = repository.prepararUpsertAfiliacion(connection)) {
            parser.leer(absoluto, new ScopusAuthorCsvParser.FilaListener() {
                @Override
                public void onFilaLeida() {
                    resumen.registrarFilaLeida();
                }

                @Override
                public void onFilaValida(ScopusAuthorCsvRecord registro) {
                    guardarAutor(connection, insertAutor, selectAuthId, selectNombre, insertArea,
                            upsertAfiliacion, registro, affiliation, nombreArchivo, resumen, ambiguos);
                }

                @Override
                public void onFilaIgnorada(String authorName, String motivo) {
                    resumen.registrarFilaIgnorada();
                    if (debug) {
                        System.out.println("DEBUG se omite fila CSV: " + motivo
                                + " authorName=" + (authorName == null ? "" : authorName));
                    }
                }
            });
        }
        Path csvAmbiguos = generarCsvAmbiguos(Path.of(ARCHIVO_AMBIGUOS), ambiguos);
        resumen.setArchivoAmbiguos(csvAmbiguos.getFileName().toString());
        resumen.imprimir();
        return resumen;
    }

    ScopusAffiliation parseAffiliationFromFilename(Path csvFile) {
        return ScopusAffiliationParser.parseAffiliationFromFilename(csvFile);
    }

    AuthorLookupResult buscarAutor(PreparedStatement selectAuthId,
                                   PreparedStatement selectNombre,
                                   String authId,
                                   String authorName) throws SQLException {
        if (authId != null && !authId.isBlank()) {
            List<Long> ids = repository.buscarIdsPorAuthId(selectAuthId, authId);
            if (ids.size() == 1) {
                return AuthorLookupResult.foundByAuthId(ids.get(0));
            }
            if (ids.isEmpty()) {
                return AuthorLookupResult.notFound();
            }
            throw new SQLException("Auth-ID duplicado en scopus_author: " + authId + " ids=" + ids);
        }
        List<Long> ids = repository.buscarIdsPorNombre(selectNombre, authorName);
        if (ids.size() == 1) {
            return AuthorLookupResult.foundByName(ids.get(0));
        }
        if (ids.isEmpty()) {
            return AuthorLookupResult.notFound();
        }
        return AuthorLookupResult.ambiguousName(ids);
    }

    void guardarAutor(Connection connection,
                      PreparedStatement insertAutor,
                      PreparedStatement selectAuthId,
                      PreparedStatement selectNombre,
                      PreparedStatement insertArea,
                      PreparedStatement upsertAfiliacion,
                      ScopusAuthorCsvRecord registro,
                      ScopusAffiliation affiliation,
                      String nombreArchivo,
                      ScopusAuthorCargaResumen resumen,
                      List<ScopusAuthorAmbiguoRecord> ambiguos) {
        try {
            AuthorLookupResult lookup = buscarAutor(
                    selectAuthId, selectNombre, registro.authId(), registro.authorName());
            if (lookup.status() == AuthorLookupStatus.AMBIGUOUS_NAME) {
                registrarAmbiguo(registro, affiliation, nombreArchivo, lookup, resumen, ambiguos);
                connection.rollback();
                return;
            }

            long scopusAuthorId;
            boolean autorNuevo = !lookup.encontrado();
            if (lookup.encontrado()) {
                scopusAuthorId = lookup.scopusAuthorId();
            } else {
                advertirNombreExistenteSiCorresponde(selectNombre, registro);
                scopusAuthorId = repository.insertarAutor(insertAutor, registro);
                int areas = 0;
                for (String area : registro.subjectAreas()) {
                    repository.insertarArea(insertArea, scopusAuthorId, area);
                    areas++;
                }
                resumen.registrarAreasInsertadas(areas);
            }

            boolean afiliacionNueva = repository.upsertAfiliacion(
                    upsertAfiliacion, scopusAuthorId, affiliation);
            connection.commit();

            if (lookup.status() == AuthorLookupStatus.FOUND_BY_AUTH_ID) {
                resumen.registrarEncontradoPorAuthId();
            } else if (lookup.status() == AuthorLookupStatus.FOUND_BY_NAME) {
                resumen.registrarEncontradoPorNombre();
            } else if (registro.authId() != null && !registro.authId().isBlank()) {
                resumen.registrarNuevoConAuthId();
            } else {
                resumen.registrarNuevoSinAuthId();
            }
            if (afiliacionNueva) {
                resumen.registrarAfiliacionInsertada();
            } else {
                resumen.registrarAfiliacionExistente();
            }
            imprimirDebug(lookup, registro, scopusAuthorId, autorNuevo);
            int procesados = resumen.autoresProcesados();
            if (procesados % 100 == 0) {
                System.out.println("Procesados " + procesados + " autores...");
            }
        } catch (SQLException ex) {
            resumen.registrarError();
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("WARN Error al revertir transacción authId="
                        + nuloATexto(registro.authId()) + ": " + rollbackEx.getMessage());
            }
            System.err.println("ERROR SQL autor=" + registro.authorName()
                    + " authId=" + nuloATexto(registro.authId()) + ": " + ex.getMessage());
        }
    }

    private void advertirNombreExistenteSiCorresponde(PreparedStatement selectNombre,
                                                      ScopusAuthorCsvRecord registro) throws SQLException {
        if (registro.authId() == null || registro.authId().isBlank()) {
            return;
        }
        List<Long> idsMismoNombre = repository.buscarIdsPorNombre(selectNombre, registro.authorName());
        if (idsMismoNombre.isEmpty()) {
            return;
        }
        System.err.println("WARN Existe otro autor con mismo nombre pero sin Auth-ID coincidente. "
                + "No se fusiona. authorName=" + registro.authorName()
                + " authId=" + registro.authId()
                + " ids=" + unirIds(idsMismoNombre));
    }

    private void registrarAmbiguo(ScopusAuthorCsvRecord registro,
                                  ScopusAffiliation affiliation,
                                  String nombreArchivo,
                                  AuthorLookupResult lookup,
                                  ScopusAuthorCargaResumen resumen,
                                  List<ScopusAuthorAmbiguoRecord> ambiguos) {
        resumen.registrarNombreAmbiguo();
        ambiguos.add(new ScopusAuthorAmbiguoRecord(
                nombreArchivo,
                affiliation.affiliationName(),
                affiliation.affiliationId(),
                registro.authorName(),
                nuloATexto(registro.authId()),
                lookup.idsEncontrados().size(),
                unirIds(lookup.idsEncontrados()),
                "AMBIGUOUS_AUTHOR_NAME"
        ));
        if (debug) {
            System.out.println("AMBIGUOUS_AUTHOR_NAME");
            System.out.println("Author Name=" + registro.authorName());
            System.out.println("Coincidencias=" + unirIds(lookup.idsEncontrados()));
        }
    }

    private void imprimirDebug(AuthorLookupResult lookup,
                               ScopusAuthorCsvRecord registro,
                               long scopusAuthorId,
                               boolean autorNuevo) {
        if (!debug) {
            return;
        }
        if (lookup.status() == AuthorLookupStatus.FOUND_BY_AUTH_ID) {
            System.out.println("FOUND_BY_AUTH_ID");
            System.out.println("Auth-ID=" + registro.authId());
            System.out.println("scopus_author_id=" + scopusAuthorId);
            System.out.println("Solo se agrega afiliación.");
        } else if (lookup.status() == AuthorLookupStatus.FOUND_BY_NAME) {
            System.out.println("FOUND_BY_NAME");
            System.out.println("Author Name=" + registro.authorName());
            System.out.println("scopus_author_id=" + scopusAuthorId);
            System.out.println("Solo se agrega afiliación.");
        } else if (autorNuevo) {
            System.out.println("NEW_AUTHOR");
            System.out.println("Author Name=" + registro.authorName());
            System.out.println("Auth-ID=" + nuloATexto(registro.authId()));
            System.out.println("scopus_author_id=" + scopusAuthorId);
            System.out.println("Se crea autor.");
        }
    }

    Path generarCsvAmbiguos(Path destino, List<ScopusAuthorAmbiguoRecord> ambiguos) throws IOException {
        Path absoluto = destino.toAbsolutePath().normalize();
        Path parent = absoluto.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(absoluto, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSV_AMBIGUOS)) {
            for (ScopusAuthorAmbiguoRecord fila : ambiguos) {
                printer.printRecord(
                        fila.archivo(),
                        fila.affiliationName(),
                        fila.affiliationId(),
                        fila.authorName(),
                        fila.authId(),
                        fila.cantidadCoincidencias(),
                        fila.scopusAuthorIdsEncontrados(),
                        fila.motivo()
                );
            }
        }
        return absoluto;
    }

    private static String unirIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(";"));
    }

    private static void imprimirInicio(Path archivo, ScopusAffiliation affiliation) {
        System.out.println("===============================================");
        System.out.println("IMPORTACIÓN SCOPUS AUTHORS");
        System.out.println("===============================================");
        System.out.println();
        System.out.println("Archivo:");
        System.out.println(archivo.getFileName());
        System.out.println();
        System.out.println("Afiliación:");
        System.out.println(affiliation.affiliationName());
        System.out.println();
        System.out.println("Scopus Affiliation ID:");
        System.out.println(affiliation.affiliationId());
        System.out.println();
    }

    private static String nuloATexto(String value) {
        return value == null ? "" : value;
    }
}
