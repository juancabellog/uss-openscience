package cl.uss.ca.scopus.importer;

import cl.uss.ca.db.ScopusPublicationAffiliationRepository;
import cl.uss.ca.db.ScopusPublicationAuthorRepository;
import cl.uss.ca.db.ScopusPublicationKeywordRepository;
import cl.uss.ca.db.ScopusPublicationRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScopusPublicationImportService {
    private static final List<String> TABLAS_REQUERIDAS = List.of(
            ScopusPublicationRepository.TABLE,
            ScopusPublicationAuthorRepository.TABLE,
            ScopusPublicationAffiliationRepository.TABLE,
            ScopusPublicationKeywordRepository.TABLE
    );

    private final ScopusPublicationCsvParser parser;
    private final ScopusPublicationRepository publicationRepository;
    private final ScopusPublicationAuthorRepository authorRepository;
    private final ScopusPublicationAffiliationRepository affiliationRepository;
    private final ScopusPublicationKeywordRepository keywordRepository;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public ScopusPublicationImportService(ScopusPublicationCsvParser parser,
                                          ScopusPublicationRepository publicationRepository,
                                          ScopusPublicationAuthorRepository authorRepository,
                                          ScopusPublicationAffiliationRepository affiliationRepository,
                                          ScopusPublicationKeywordRepository keywordRepository,
                                          String dbUrl,
                                          String dbUser,
                                          String dbPassword) {
        this.parser = parser;
        this.publicationRepository = publicationRepository;
        this.authorRepository = authorRepository;
        this.affiliationRepository = affiliationRepository;
        this.keywordRepository = keywordRepository;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public ScopusPublicationImportSummary importar(ScopusPublicationImportOptions opciones)
            throws Exception {
        opciones.validar();
        Path absoluto = opciones.getArchivo().toAbsolutePath().normalize();
        if (!Files.isRegularFile(absoluto)) {
            throw new IOException("No se encontró el archivo CSV: " + absoluto);
        }

        String sourceFile = absoluto.getFileName().toString();
        ScopusPublicationImportSummary resumen = new ScopusPublicationImportSummary();
        resumen.setArchivo(sourceFile);
        resumen.setDryRun(opciones.isDryRun());

        System.out.println("Archivo: " + sourceFile);
        System.out.println("DRY_RUN: " + opciones.isDryRun());
        if (opciones.getLimit() != null) {
            System.out.println("limit: " + opciones.getLimit());
        }
        System.out.println();

        Map<String, AuthLookup> authCache = new HashMap<>();
        try (Connection connection = abrirConexion();
             PreparedStatement selectEid = publicationRepository.prepararSelectPorEid(connection);
             PreparedStatement selectDoi = publicationRepository.prepararSelectPorDoi(connection);
             PreparedStatement insertPublication = publicationRepository.prepararInsert(connection);
             PreparedStatement updatePublication = publicationRepository.prepararUpdate(connection);
             PreparedStatement selectScopusAuthor = authorRepository.prepararSelectScopusAuthor(connection);
             PreparedStatement deleteAuthors = authorRepository.prepararDeletePorPublicacion(connection);
             PreparedStatement insertAuthor = authorRepository.prepararInsert(connection);
             PreparedStatement deleteAffiliations = affiliationRepository.prepararDeletePorPublicacion(connection);
             PreparedStatement insertAffiliation = affiliationRepository.prepararInsert(connection);
             PreparedStatement deleteKeywords = keywordRepository.prepararDeletePorPublicacion(connection);
             PreparedStatement insertKeyword = keywordRepository.prepararInsert(connection)) {
            verificarTablas(connection);
            Statements statements = new Statements(
                    selectEid, selectDoi, insertPublication, updatePublication,
                    selectScopusAuthor, deleteAuthors, insertAuthor,
                    deleteAffiliations, insertAffiliation, deleteKeywords, insertKeyword
            );
            parser.leer(absoluto, fila -> {
                if (opciones.getLimit() != null && resumen.getRegistrosLeidos() >= opciones.getLimit()) {
                    return false;
                }
                resumen.registrarLeido();
                procesarFila(connection, statements, fila, sourceFile, opciones.isDryRun(), resumen, authCache);
                return true;
            });
        }
        resumen.imprimir();
        return resumen;
    }

    private void procesarFila(Connection connection,
                              Statements statements,
                              ScopusPublicationCsvRow fila,
                              String sourceFile,
                              boolean dryRun,
                              ScopusPublicationImportSummary resumen,
                              Map<String, AuthLookup> authCache) throws SQLException {
        try {
            if (!fila.tieneClave()) {
                resumen.registrarError();
                System.err.println("ERROR | fila=" + fila.fila()
                        + " EID=" + nulo(fila.eid())
                        + " DOI=" + nulo(fila.doi())
                        + " motivo=faltan EID y DOI");
                connection.rollback();
                return;
            }

            LookupPublicacion lookup = resolverPublicacion(statements, fila, resumen);
            if (lookup.error != null) {
                resumen.registrarError();
                System.err.println("ERROR | fila=" + fila.fila()
                        + " EID=" + nulo(fila.eid())
                        + " DOI=" + nulo(fila.doi())
                        + " motivo=" + lookup.error);
                connection.rollback();
                return;
            }

            for (String warning : fila.warnings()) {
                resumen.registrarWarning();
                System.err.println("WARN | EID=" + nulo(fila.eid()) + " " + warning);
            }
            if (!fila.authorsAligned()) {
                resumen.registrarWarning();
                System.err.println("WARN | EID=" + nulo(fila.eid())
                        + " Authors=" + fila.authors().size()
                        + " Author full names=" + fila.authorFullNames().size()
                        + " Author IDs=" + fila.authorIds().size()
                        + " Authors with affiliations=" + fila.authorsWithAffiliations().size()
                        + " Motivo=desalineación de listas");
            }

            AuthorStats authorStats = resolverAutores(statements, fila, authCache, resumen);
            if (!dryRun) {
                long publicationId;
                if (lookup.existente) {
                    publicationId = lookup.id;
                    publicationRepository.actualizar(statements.updatePublication, publicationId, fila, sourceFile);
                } else {
                    publicationId = publicationRepository.insertar(statements.insertPublication, fila, sourceFile);
                }
                if (fila.authorsAligned()) {
                    authorRepository.eliminarPorPublicacion(statements.deleteAuthors, publicationId);
                    for (int i = 0; i < fila.authorItems().size(); i++) {
                        ScopusPublicationAuthorItem item = fila.authorItems().get(i);
                        authorRepository.insertar(
                                statements.insertAuthor,
                                publicationId,
                                item,
                                authorStats.scopusAuthorIds.get(i)
                        );
                    }
                }
                affiliationRepository.eliminarPorPublicacion(statements.deleteAffiliations, publicationId);
                for (int i = 0; i < fila.affiliations().size(); i++) {
                    affiliationRepository.insertar(
                            statements.insertAffiliation,
                            publicationId,
                            i + 1,
                            fila.affiliations().get(i)
                    );
                }
                keywordRepository.eliminarPorPublicacion(statements.deleteKeywords, publicationId);
                insertarKeywords(statements.insertKeyword, publicationId, fila);
                connection.commit();
            } else {
                connection.rollback();
            }

            if (lookup.existente) {
                resumen.registrarActualizada();
            } else {
                resumen.registrarNueva();
            }
            if (fila.authorsAligned()) {
                resumen.registrarAutores(authorStats.total, authorStats.unlinked);
            }
            resumen.registrarAfiliaciones(fila.affiliations().size());
            resumen.registrarKeywordsAuthor(fila.authorKeywords().size());
            resumen.registrarKeywordsIndex(fila.indexKeywords().size());
            imprimirFila(resumen.getRegistrosLeidos(), fila, lookup.existente, authorStats);
        } catch (Exception ex) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                ex.addSuppressed(rollbackEx);
            }
            resumen.registrarError();
            System.err.println("ERROR | fila=" + fila.fila()
                    + " EID=" + nulo(fila.eid())
                    + " DOI=" + nulo(fila.doi())
                    + " motivo=" + motivo(ex));
            ex.printStackTrace(System.err);
        }
    }

    private LookupPublicacion resolverPublicacion(Statements statements,
                                                  ScopusPublicationCsvRow fila,
                                                  ScopusPublicationImportSummary resumen) throws SQLException {
        if (fila.tieneEid()) {
            List<Long> ids = publicationRepository.buscarIdsPorEid(statements.selectEid, fila.eid());
            if (ids.size() > 1) {
                return LookupPublicacion.error("EID duplicado en scopus_publication: " + fila.eid() + " ids=" + ids);
            }
            if (ids.size() == 1) {
                return LookupPublicacion.existente(ids.get(0));
            }
            return LookupPublicacion.nuevo();
        }

        resumen.registrarWarning();
        System.err.println("WARN | EID= DOI=" + nulo(fila.doi())
                + " Motivo=se utilizó DOI como fallback");
        List<Long> ids = publicationRepository.buscarIdsPorDoiNormalizado(
                statements.selectDoi, fila.doiNormalized());
        if (ids.size() > 1) {
            return LookupPublicacion.error("DOI duplicado en scopus_publication: " + fila.doiNormalized()
                    + " ids=" + ids);
        }
        if (ids.size() == 1) {
            return LookupPublicacion.existente(ids.get(0));
        }
        return LookupPublicacion.nuevo();
    }

    private AuthorStats resolverAutores(Statements statements,
                                        ScopusPublicationCsvRow fila,
                                        Map<String, AuthLookup> authCache,
                                        ScopusPublicationImportSummary resumen) throws SQLException {
        if (!fila.authorsAligned()) {
            return AuthorStats.vacio();
        }
        List<Long> scopusAuthorIds = new java.util.ArrayList<>(fila.authorItems().size());
        int linked = 0;
        int unlinked = 0;
        for (ScopusPublicationAuthorItem item : fila.authorItems()) {
            AuthLookup lookup = buscarAutor(statements.selectScopusAuthor, item.authId(), authCache, resumen, fila);
            scopusAuthorIds.add(lookup.id);
            if (lookup.status == AuthStatus.FOUND) {
                linked++;
            } else {
                unlinked++;
            }
        }
        return new AuthorStats(fila.authorItems().size(), linked, unlinked, scopusAuthorIds);
    }

    private AuthLookup buscarAutor(PreparedStatement select,
                                   String authId,
                                   Map<String, AuthLookup> cache,
                                   ScopusPublicationImportSummary resumen,
                                   ScopusPublicationCsvRow fila) throws SQLException {
        if (authId == null || authId.isBlank()) {
            return AuthLookup.notFound();
        }
        AuthLookup cached = cache.get(authId);
        if (cached != null) {
            return cached;
        }
        List<Long> ids = authorRepository.buscarIdsPorAuthId(select, authId);
        AuthLookup lookup;
        if (ids.size() == 1) {
            lookup = AuthLookup.found(ids.get(0));
        } else if (ids.isEmpty()) {
            lookup = AuthLookup.notFound();
        } else {
            resumen.registrarWarning();
            System.err.println("WARN | EID=" + nulo(fila.eid())
                    + " Motivo=auth_id duplicado en scopus_author"
                    + " auth_id=" + authId
                    + " ids=" + ids
                    + ". No se asocia scopus_author_id.");
            lookup = AuthLookup.ambiguous();
        }
        cache.put(authId, lookup);
        return lookup;
    }

    private void insertarKeywords(PreparedStatement insert, long publicationId, ScopusPublicationCsvRow fila)
            throws SQLException {
        for (int i = 0; i < fila.authorKeywords().size(); i++) {
            keywordRepository.insertar(
                    insert,
                    publicationId,
                    ScopusPublicationKeywordRepository.TYPE_AUTHOR,
                    i + 1,
                    fila.authorKeywords().get(i)
            );
        }
        for (int i = 0; i < fila.indexKeywords().size(); i++) {
            keywordRepository.insertar(
                    insert,
                    publicationId,
                    ScopusPublicationKeywordRepository.TYPE_INDEX,
                    i + 1,
                    fila.indexKeywords().get(i)
            );
        }
    }

    private void imprimirFila(int indice,
                              ScopusPublicationCsvRow fila,
                              boolean existente,
                              AuthorStats authorStats) {
        System.out.println("[" + indice + "] EID=" + nulo(fila.eid()) + " DOI=" + nulo(fila.doi()));
        System.out.println(existente ? "UPDATE" : "INSERT");
        System.out.println("authors=" + authorStats.total);
        System.out.println("linkedAuthors=" + authorStats.linked);
        System.out.println("unlinkedAuthors=" + authorStats.unlinked);
        System.out.println("affiliations=" + fila.affiliations().size());
        System.out.println("authorKeywords=" + fila.authorKeywords().size());
        System.out.println("indexKeywords=" + fila.indexKeywords().size());
        System.out.println("OK");
        System.out.println();
    }

    private Connection abrirConexion() throws SQLException {
        Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        connection.setAutoCommit(false);
        return connection;
    }

    private void verificarTablas(Connection connection) throws SQLException {
        for (String tabla : TABLAS_REQUERIDAS) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM " + tabla + " WHERE 1=0")) {
                statement.executeQuery();
            } catch (SQLException ex) {
                throw new SQLException(
                        "No existe o no se puede consultar la tabla " + tabla
                                + ". Créela antes de importar.",
                        ex
                );
            }
        }
    }

    private static String nulo(String value) {
        return value == null ? "" : value;
    }

    private static String motivo(Throwable ex) {
        String mensaje = ex.getMessage();
        if (mensaje == null || mensaje.isBlank()) {
            return ex.getClass().getName();
        }
        return ex.getClass().getSimpleName() + ": " + mensaje;
    }

    private record Statements(
            PreparedStatement selectEid,
            PreparedStatement selectDoi,
            PreparedStatement insertPublication,
            PreparedStatement updatePublication,
            PreparedStatement selectScopusAuthor,
            PreparedStatement deleteAuthors,
            PreparedStatement insertAuthor,
            PreparedStatement deleteAffiliations,
            PreparedStatement insertAffiliation,
            PreparedStatement deleteKeywords,
            PreparedStatement insertKeyword
    ) {
    }

    private record LookupPublicacion(boolean existente, Long id, String error) {
        static LookupPublicacion existente(long id) {
            return new LookupPublicacion(true, id, null);
        }

        static LookupPublicacion nuevo() {
            return new LookupPublicacion(false, null, null);
        }

        static LookupPublicacion error(String mensaje) {
            return new LookupPublicacion(false, null, mensaje);
        }
    }

    private enum AuthStatus {
        FOUND, NOT_FOUND, AMBIGUOUS
    }

    private record AuthLookup(AuthStatus status, Long id) {
        static AuthLookup found(long id) {
            return new AuthLookup(AuthStatus.FOUND, id);
        }

        static AuthLookup notFound() {
            return new AuthLookup(AuthStatus.NOT_FOUND, null);
        }

        static AuthLookup ambiguous() {
            return new AuthLookup(AuthStatus.AMBIGUOUS, null);
        }
    }

    private record AuthorStats(int total, int linked, int unlinked, List<Long> scopusAuthorIds) {
        static AuthorStats vacio() {
            return new AuthorStats(0, 0, 0, List.of());
        }
    }
}
