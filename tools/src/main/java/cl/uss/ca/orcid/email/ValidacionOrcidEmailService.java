package cl.uss.ca.orcid.email;

import cl.uss.ca.db.PersonaEmailBd;
import cl.uss.ca.db.PurePersonEmailRepository;
import cl.uss.ca.orcid.OrcidPublicClient;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ValidacionOrcidEmailService {
    static final String[] ENCABEZADOS_SALIDA = {
            "Id", "uuid", "Nombre", "Cargo", "Orcid", "DOI", "Posición",
            "Nombre Orcid", "Ok", "Afiliación", "DOI's en común", "Email", "gender"
    };
    private static final String PLACEHOLDER_NOMBRE = "[Nombre del investigador]";
    private static final String PLACEHOLDER_ORCID = "[0000-0000-0000-0000]";
    private static final String PLACEHOLDER_ESTIMADO = "[Estimado]";

    private final PurePersonEmailRepository emailRepository;
    private final WordPlaceholderReplacer wordReplacer;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public ValidacionOrcidEmailService(PurePersonEmailRepository emailRepository,
                                       String dbUrl, String dbUser, String dbPassword) {
        this.emailRepository = emailRepository;
        this.wordReplacer = new WordPlaceholderReplacer();
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public ValidacionOrcidEmailResumen procesarExcel(Path excelEntrada, Path plantillaWord,
                                                     Path excelSalida, Path directorioDocx)
            throws Exception {
        System.out.println("Procesando planilla ORCID...");
        System.out.println();
        ValidacionOrcidEmailResumen resumen = new ValidacionOrcidEmailResumen();

        List<RegistroOrcid> registros = leerExcel(excelEntrada);
        resumen.setFilasProcesadas(registros.size());

        consultarEmails(registros, resumen);
        Path escrito = generarExcelConEmails(registros, excelSalida);
        resumen.setExcelGenerado(escrito);

        Files.createDirectories(directorioDocx);
        resumen.setDirectorioDocumentos(directorioDocx);

        generarDocumentos(registros, plantillaWord, directorioDocx, resumen);
        resumen.imprimirProgreso();
        System.out.println();
        resumen.imprimirFinal();
        return resumen;
    }

    List<RegistroOrcid> leerExcel(Path entrada) throws IOException {
        try (InputStream in = Files.newInputStream(entrada);
             Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> columnas = resolverColumnas(header, formatter, evaluator);
            List<RegistroOrcid> registros = new ArrayList<>();
            int first = sheet.getFirstRowNum();
            int last = sheet.getLastRowNum();
            for (int i = first + 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                RegistroOrcid registro = mapearFila(row, columnas, formatter, evaluator);
                if (registro != null) {
                    registros.add(registro);
                }
            }
            return registros;
        }
    }

    void consultarEmails(List<RegistroOrcid> registros, ValidacionOrcidEmailResumen resumen)
            throws SQLException {
        Map<String, PersonaEmailBd> datosPorUuid = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = emailRepository.prepararConsulta(connection)) {
            for (RegistroOrcid registro : registros) {
                String uuid = registro.getUuid();
                if (uuid.isEmpty()) {
                    resumen.registrarEmailNoEncontrado();
                    System.err.println("WARN No se encontró email para:");
                    System.err.println("uuid=");
                    System.err.println("Nombre=" + registro.getNombre());
                    continue;
                }
                if (datosPorUuid.containsKey(uuid)) {
                    aplicarDatosPersona(registro, datosPorUuid.get(uuid));
                    continue;
                }
                PersonaEmailBd datos = obtenerDatosPorUuid(statement, uuid);
                datosPorUuid.put(uuid, datos);
                aplicarDatosPersona(registro, datos);
                if (datos.getEmail().isBlank()) {
                    resumen.registrarEmailNoEncontrado();
                    System.err.println("WARN No se encontró email para:");
                    System.err.println("uuid=" + uuid);
                    System.err.println("Nombre=" + registro.getNombre());
                } else {
                    resumen.registrarEmailEncontrado();
                }
            }
        }
        resumen.setUuidUnicosConsultados(datosPorUuid.size());
    }

    PersonaEmailBd obtenerDatosPorUuid(PreparedStatement statement, String uuid) {
        try {
            PersonaEmailBd datos = emailRepository.buscarPorUuid(statement, uuid);
            return datos == null ? PersonaEmailBd.vacio() : datos;
        } catch (SQLException ex) {
            System.err.println("WARN Error MySQL uuid=" + uuid + ": " + ex.getMessage());
            return PersonaEmailBd.vacio();
        }
    }

    private static void aplicarDatosPersona(RegistroOrcid registro, PersonaEmailBd datos) {
        PersonaEmailBd valor = datos == null ? PersonaEmailBd.vacio() : datos;
        registro.setEmail(valor.getEmail());
        registro.setGender(valor.getGender());
    }

    Path generarExcelConEmails(List<RegistroOrcid> registros, Path destino) throws IOException {
        Path absoluto = destino.toAbsolutePath();
        Path temporal = absoluto.resolveSibling(absoluto.getFileName().toString() + ".tmp");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("ORCID");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < ENCABEZADOS_SALIDA.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(ENCABEZADOS_SALIDA[i]);
                cell.setCellStyle(headerStyle);
            }
            int fila = 1;
            for (RegistroOrcid registro : registros) {
                Row row = sheet.createRow(fila++);
                setTexto(row, 0, registro.getId());
                setTexto(row, 1, registro.getUuid());
                setTexto(row, 2, registro.getNombre());
                setTexto(row, 3, registro.getCargo());
                setTexto(row, 4, registro.getOrcid());
                setTexto(row, 5, registro.getDoi());
                setTexto(row, 6, registro.getPosicion());
                setTexto(row, 7, registro.getNombreOrcid());
                setTexto(row, 8, registro.getOk());
                setTexto(row, 9, registro.getAfiliacion());
                setTexto(row, 10, registro.getDoisEnComun());
                setTexto(row, 11, registro.getEmail());
                setTexto(row, 12, registro.getGender());
            }
            int lastRow = Math.max(0, fila - 1);
            sheet.setAutoFilter(new CellRangeAddress(0, lastRow, 0, ENCABEZADOS_SALIDA.length - 1));
            sheet.createFreezePane(0, 1);
            for (int i = 0; i < ENCABEZADOS_SALIDA.length; i++) {
                sheet.setColumnWidth(i, 22 * 256);
            }
            Path parent = absoluto.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(temporal)) {
                workbook.write(out);
            }
        }
        try {
            Files.move(temporal, absoluto, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return absoluto;
        } catch (IOException ignored) {
            Files.move(temporal, absoluto, StandardCopyOption.REPLACE_EXISTING);
            return absoluto;
        }
    }

    boolean esRegistroValidoParaDocumento(RegistroOrcid registro) {
        if (registro == null) {
            return false;
        }
        return "SI".equalsIgnoreCase(registro.getOk().trim())
                && !registro.getEmail().isBlank()
                && !registro.getNombre().isBlank()
                && !registro.getOrcid().isBlank();
    }

    Map<String, List<RegistroOrcid>> detectarConflictos(List<RegistroOrcid> candidatos) {
        Map<String, List<RegistroOrcid>> porEmail = new LinkedHashMap<>();
        for (RegistroOrcid registro : candidatos) {
            porEmail.computeIfAbsent(normalizarEmail(registro.getEmail()), key -> new ArrayList<>())
                    .add(registro);
        }
        return porEmail;
    }

    void generarDocumentos(List<RegistroOrcid> registros, Path plantilla, Path directorio,
                           ValidacionOrcidEmailResumen resumen) throws IOException {
        int okSi = 0;
        for (RegistroOrcid registro : registros) {
            if ("SI".equalsIgnoreCase(registro.getOk().trim())) {
                okSi++;
            } else {
                resumen.registrarOmitidoOk();
            }
        }
        resumen.setRegistrosOkSi(okSi);

        List<RegistroOrcid> candidatos = new ArrayList<>();
        for (RegistroOrcid registro : registros) {
            if (!"SI".equalsIgnoreCase(registro.getOk().trim())) {
                continue;
            }
            if (registro.getEmail().isBlank()) {
                resumen.registrarOmitidoSinEmail();
                continue;
            }
            if (esRegistroValidoParaDocumento(registro)) {
                candidatos.add(registro);
            }
        }

        Map<String, List<RegistroOrcid>> porEmail = detectarConflictos(candidatos);
        Set<String> emailsDocumentosGenerados = new LinkedHashSet<>();
        for (Map.Entry<String, List<RegistroOrcid>> entry : porEmail.entrySet()) {
            List<RegistroOrcid> grupo = entry.getValue();
            if (tieneOrcidsDistintos(grupo)) {
                resumen.registrarConflicto();
                RegistroOrcid primero = grupo.get(0);
                RegistroOrcid segundo = grupo.get(1);
                System.err.println("WARN Se encontraron ORCID diferentes para el mismo email:");
                System.err.println("email=" + primero.getEmail());
                System.err.println("orcid1=" + primero.getOrcid());
                System.err.println("orcid2=" + segundo.getOrcid());
                continue;
            }
            RegistroOrcid elegido = grupo.get(0);
            String emailClave = entry.getKey();
            if (!emailsDocumentosGenerados.add(emailClave)) {
                continue;
            }
            if (grupo.size() > 1) {
                for (int i = 1; i < grupo.size(); i++) {
                    resumen.registrarDuplicadoEmail();
                }
                System.out.println("DEBUG se omite DOCX duplicado para email=" + elegido.getEmail());
            }
            generarDocumentoPersonalizado(elegido, plantilla, directorio);
            resumen.registrarDocumentoGenerado();
        }
    }

    void generarDocumentoPersonalizado(RegistroOrcid registro, Path plantilla, Path directorio)
            throws IOException {
        String nombreArchivo = sanitizarNombreArchivo(registro.getEmail().trim()) + ".docx";
        Path destino = directorio.resolve(nombreArchivo);
        Path temporal = destino.resolveSibling(destino.getFileName().toString() + ".tmp");
        Files.copy(plantilla, temporal, StandardCopyOption.REPLACE_EXISTING);
        try (InputStream in = Files.newInputStream(temporal);
             XWPFDocument document = new XWPFDocument(in)) {
            Map<String, String> reemplazos = new LinkedHashMap<>();
            reemplazos.put(PLACEHOLDER_NOMBRE, registro.getNombre());
            reemplazos.put(PLACEHOLDER_ORCID, registro.getOrcid());
            reemplazos.put(PLACEHOLDER_ESTIMADO, saludoDesdeGender(registro.getGender()));
            wordReplacer.reemplazarPlaceholders(document, reemplazos);
            Path escrito = destino.resolveSibling(destino.getFileName().toString() + ".out");
            try (OutputStream out = Files.newOutputStream(escrito)) {
                document.write(out);
            }
            Files.move(escrito, destino, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    static String sanitizarNombreArchivo(String valor) {
        if (valor == null || valor.isBlank()) {
            return "sin-email";
        }
        String limpio = valor.trim();
        limpio = limpio.replaceAll("[/\\\\:*?\"<>|]", "_");
        return limpio;
    }

    private static boolean tieneOrcidsDistintos(List<RegistroOrcid> grupo) {
        Set<String> orcids = new LinkedHashSet<>();
        for (RegistroOrcid registro : grupo) {
            String normalizado = OrcidPublicClient.normalizarOrcid(registro.getOrcid());
            if (normalizado == null) {
                normalizado = registro.getOrcid().trim().toUpperCase(Locale.ROOT);
            }
            orcids.add(normalizado);
        }
        return orcids.size() > 1;
    }

    private static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static String saludoDesdeGender(String gender) {
        String clave = claveEncabezado(gender);
        if ("hombre".equals(clave) || "male".equals(clave)) {
            return "Estimado";
        }
        if ("mujer".equals(clave) || "female".equals(clave)) {
            return "Estimada";
        }
        return "Estimado/a";
    }

    private static Map<String, Integer> resolverColumnas(Row header, DataFormatter formatter,
                                                         FormulaEvaluator evaluator) {
        Map<String, Integer> columnas = new HashMap<>();
        if (header == null) {
            return columnas;
        }
        short last = header.getLastCellNum();
        for (int i = 0; i < last; i++) {
            String clave = claveEncabezado(valorCelda(header.getCell(i), formatter, evaluator));
            if (!clave.isEmpty() && !columnas.containsKey(clave)) {
                columnas.put(clave, i);
            }
        }
        return columnas;
    }

    private static RegistroOrcid mapearFila(Row row, Map<String, Integer> columnas,
                                            DataFormatter formatter, FormulaEvaluator evaluator) {
        String id = celda(row, columnas, "id", formatter, evaluator);
        String uuid = celda(row, columnas, "uuid", formatter, evaluator);
        String nombre = celda(row, columnas, "nombre", formatter, evaluator);
        String cargo = celda(row, columnas, "cargo", formatter, evaluator);
        String orcid = celda(row, columnas, "orcid", formatter, evaluator);
        String doi = celda(row, columnas, "doi", formatter, evaluator);
        String posicion = celda(row, columnas, "posicion", formatter, evaluator);
        if (posicion.isEmpty()) {
            posicion = celda(row, columnas, "position", formatter, evaluator);
        }
        String nombreOrcid = celda(row, columnas, "nombreorcid", formatter, evaluator);
        String ok = celda(row, columnas, "ok", formatter, evaluator);
        String afiliacion = celda(row, columnas, "afiliacion", formatter, evaluator);
        String doisEnComun = celda(row, columnas, "doisencomun", formatter, evaluator);
        if (id.isEmpty() && uuid.isEmpty() && nombre.isEmpty() && orcid.isEmpty()) {
            return null;
        }
        return new RegistroOrcid(id, uuid, nombre, cargo, orcid, doi, posicion,
                nombreOrcid, ok, afiliacion, doisEnComun);
    }

    private static String celda(Row row, Map<String, Integer> columnas, String clave,
                                DataFormatter formatter, FormulaEvaluator evaluator) {
        Integer indice = columnas.get(clave);
        if (indice == null) {
            return "";
        }
        return valorCelda(row.getCell(indice), formatter, evaluator);
    }

    private static String valorCelda(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            double n = cell.getNumericCellValue();
            if (n == Math.rint(n)) {
                return String.valueOf((long) n);
            }
        }
        String value = formatter.formatCellValue(cell, evaluator);
        return value == null ? "" : value.trim();
    }

    private static String claveEncabezado(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String t = value.toLowerCase(Locale.ROOT).trim();
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        t = t.replaceAll("[^a-z0-9]", "");
        return t;
    }

    private static void setTexto(Row row, int col, String value) {
        row.createCell(col).setCellValue(value == null ? "" : value);
    }

    public static Path destinoExcelSeguro(Path propuesto) {
        if (!Files.exists(propuesto)) {
            return propuesto;
        }
        String nombre = propuesto.getFileName().toString();
        String base = nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                ? nombre.substring(0, nombre.length() - 5)
                : nombre;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return propuesto.resolveSibling(base + "-" + stamp + ".xlsx");
    }
}
