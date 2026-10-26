package cl.uss.ca.orcid;

import cl.uss.ca.db.PersonaContactoBd;
import cl.uss.ca.db.PurePersonContactoRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrcidEmailRutExcelService {
    private static final String COLUMNA_EMAIL = "email";
    private static final String COLUMNA_RUT = "RUT";

    private final PurePersonContactoRepository repository;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public OrcidEmailRutExcelService(PurePersonContactoRepository repository,
                                     String dbUrl, String dbUser, String dbPassword) {
        this.repository = repository;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public Path enriquecer(Path entrada) throws Exception {
        Path absoluto = entrada.toAbsolutePath().normalize();
        if (!Files.exists(absoluto)) {
            throw new IllegalArgumentException("No existe el XLSX de entrada: " + absoluto);
        }
        List<List<String>> filas = leer(absoluto);
        if (filas.isEmpty()) {
            throw new IllegalStateException("El Excel no tiene encabezados: " + absoluto);
        }
        List<String> encabezados = filas.get(0);
        int indiceUuid = indiceUuid(encabezados);
        if (indiceUuid < 0) {
            throw new IllegalStateException("No se encontró la columna uuid en " + absoluto);
        }

        Map<String, PersonaContactoBd> cache = new LinkedHashMap<>();
        int consultados = 0;
        int desdeCache = 0;
        int sinUuid = 0;
        int noEncontrados = 0;
        List<List<String>> salida = new ArrayList<>();
        List<String> encabezadoSalida = new ArrayList<>(encabezados);
        encabezadoSalida.add(COLUMNA_EMAIL);
        encabezadoSalida.add(COLUMNA_RUT);
        salida.add(encabezadoSalida);

        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             var consulta = repository.prepararConsulta(connection)) {
            for (int i = 1; i < filas.size(); i++) {
                List<String> origen = filas.get(i);
                String uuid = valor(origen, indiceUuid);
                PersonaContactoBd contacto;
                if (uuid.isEmpty()) {
                    sinUuid++;
                    contacto = PersonaContactoBd.vacio();
                    System.err.println("WARN fila " + (i + 1) + " sin uuid");
                } else if (cache.containsKey(uuid)) {
                    desdeCache++;
                    contacto = cache.get(uuid);
                } else {
                    consultados++;
                    contacto = buscar(consulta, uuid);
                    if (contacto == null) {
                        noEncontrados++;
                        System.err.println("WARN No se encontró pure_person uuid=" + uuid);
                        contacto = PersonaContactoBd.vacio();
                    }
                    cache.put(uuid, contacto);
                }
                List<String> filaSalida = new ArrayList<>(origen);
                while (filaSalida.size() < encabezados.size()) {
                    filaSalida.add("");
                }
                filaSalida.add(contacto.getEmail());
                filaSalida.add(contacto.getRut());
                salida.add(filaSalida);
            }
        }

        Path destino = destinoSeguro(absoluto);
        Path escrito = escribir(salida, destino);
        imprimirResumen(filas.size() - 1, consultados, desdeCache, sinUuid, noEncontrados, escrito);
        return escrito;
    }

    private PersonaContactoBd buscar(PreparedStatement consulta, String uuid) {
        try {
            return repository.buscarPorUuid(consulta, uuid);
        } catch (SQLException ex) {
            System.err.println("WARN Error MySQL uuid=" + uuid + ": " + ex.getMessage());
            return PersonaContactoBd.vacio();
        }
    }

    private static List<List<String>> leer(Path entrada) throws IOException {
        try (InputStream in = Files.newInputStream(entrada);
             Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<List<String>> filas = new ArrayList<>();
            int first = sheet.getFirstRowNum();
            int last = sheet.getLastRowNum();
            int columnas = 0;
            Row header = sheet.getRow(first);
            if (header != null) {
                columnas = Math.max(0, header.getLastCellNum());
            }
            for (int i = first; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                List<String> valores = new ArrayList<>();
                int lastCell = Math.max(columnas, row.getLastCellNum());
                for (int c = 0; c < lastCell; c++) {
                    valores.add(valorCelda(row.getCell(c), formatter, evaluator));
                }
                if (i > first && filaVacia(valores)) {
                    continue;
                }
                filas.add(valores);
            }
            return filas;
        }
    }

    private static Path escribir(List<List<String>> filas, Path destino) throws IOException {
        Path absoluto = destino.toAbsolutePath();
        Path temporal = absoluto.resolveSibling(absoluto.getFileName().toString() + ".tmp");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("ORCID validados");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            int maxCols = 0;
            for (int r = 0; r < filas.size(); r++) {
                List<String> valores = filas.get(r);
                Row row = sheet.createRow(r);
                maxCols = Math.max(maxCols, valores.size());
                for (int c = 0; c < valores.size(); c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(valores.get(c) == null ? "" : valores.get(c));
                    if (r == 0) {
                        cell.setCellStyle(headerStyle);
                    }
                }
            }
            if (maxCols > 0) {
                sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, filas.size() - 1), 0, maxCols - 1));
            }
            sheet.createFreezePane(0, 1);
            for (int c = 0; c < maxCols; c++) {
                sheet.setColumnWidth(c, 22 * 256);
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

    private static Path destinoSeguro(Path entrada) {
        Path absoluto = entrada.toAbsolutePath().normalize();
        Path dir = absoluto.getParent() == null ? Path.of(".").toAbsolutePath() : absoluto.getParent();
        String nombre = absoluto.getFileName().toString();
        String base = nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                ? nombre.substring(0, nombre.length() - 5)
                : nombre;
        Path candidato = dir.resolve(base + "_email_rut.xlsx");
        if (!Files.exists(candidato)) {
            return candidato;
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return dir.resolve(base + "_email_rut-" + stamp + ".xlsx");
    }

    private static int indiceUuid(List<String> encabezados) {
        for (int i = 0; i < encabezados.size(); i++) {
            if ("uuid".equals(claveEncabezado(encabezados.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private static String valor(List<String> fila, int indice) {
        if (indice < 0 || indice >= fila.size() || fila.get(indice) == null) {
            return "";
        }
        return fila.get(indice).trim();
    }

    private static boolean filaVacia(List<String> valores) {
        for (String value : valores) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
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

    private static void imprimirResumen(int filas, int consultados, int desdeCache,
                                        int sinUuid, int noEncontrados, Path destino) {
        System.out.println("========================================");
        System.out.println("ENRIQUECIMIENTO EMAIL / RUT FINALIZADO");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Filas procesadas:                 " + filas);
        System.out.println("Consultas MySQL por uuid:         " + consultados);
        System.out.println("Respuestas desde cache:           " + desdeCache);
        System.out.println("Filas sin uuid:                   " + sinUuid);
        System.out.println("UUID no encontrados en MySQL:     " + noEncontrados);
        System.out.println();
        System.out.println("Archivo generado:");
        System.out.println(destino.toAbsolutePath());
    }
}
