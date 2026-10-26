package cl.uss.ca.orcid;

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
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrcidValidationExcelIo {
    private static final String[] ENCABEZADOS = {
            "Id", "uuid", "Nombre", "Cargo", "Orcid", "DOI", "Posición",
            "Nombre Orcid", "Ok", "Afiliación", "DOI's en común"
    };

    public List<OrcidExcelFila> leer(Path entrada) throws IOException {
        try (InputStream in = Files.newInputStream(entrada);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int[] columnas = resolverColumnas(sheet.getRow(sheet.getFirstRowNum()), formatter, evaluator);
            List<OrcidExcelFila> filas = new ArrayList<>();
            int first = sheet.getFirstRowNum();
            int last = sheet.getLastRowNum();
            for (int i = first + 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                OrcidExcelFila fila = mapearFila(i + 1, row, columnas, formatter, evaluator);
                if (fila == null) {
                    continue;
                }
                filas.add(fila);
            }
            return filas;
        }
    }

    public Path escribirExcelResultado(List<OrcidFilaValidada> filas, Path destino) throws IOException {
        Path absoluto = destino.toAbsolutePath();
        Path temporal = absoluto.resolveSibling(absoluto.getFileName().toString() + ".tmp");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("ORCID validados");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(ENCABEZADOS[i]);
                cell.setCellStyle(headerStyle);
            }

            int filaIdx = 1;
            if (filas != null) {
                for (OrcidFilaValidada item : filas) {
                    if (item == null || item.getOrigen() == null) {
                        continue;
                    }
                    OrcidExcelFila origen = item.getOrigen();
                    Row row = sheet.createRow(filaIdx++);
                    setTexto(row, 0, origen.getId());
                    setTexto(row, 1, item.getUuid());
                    setTexto(row, 2, origen.getNombre());
                    setTexto(row, 3, origen.getCargo());
                    setTexto(row, 4, origen.getOrcid());
                    setTexto(row, 5, origen.getDoi());
                    setTexto(row, 6, origen.getPosicion());
                    setTexto(row, 7, item.getNombreOrcid());
                    setTexto(row, 8, item.getOk());
                    setTexto(row, 9, item.getAfiliacion());
                    setTexto(row, 10, item.getDoisEnComun());
                }
            }

            int lastRow = Math.max(0, filaIdx - 1);
            sheet.setAutoFilter(new CellRangeAddress(0, lastRow, 0, ENCABEZADOS.length - 1));
            sheet.createFreezePane(0, 1);
            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 40 * 256);
            sheet.setColumnWidth(2, 40 * 256);
            sheet.setColumnWidth(3, 28 * 256);
            sheet.setColumnWidth(4, 22 * 256);
            sheet.setColumnWidth(5, 36 * 256);
            sheet.setColumnWidth(6, 12 * 256);
            sheet.setColumnWidth(7, 40 * 256);
            sheet.setColumnWidth(8, 10 * 256);
            sheet.setColumnWidth(9, 14 * 256);
            sheet.setColumnWidth(10, 50 * 256);

            Path parent = absoluto.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(temporal)) {
                workbook.write(out);
            }
        }
        return reemplazarDestino(temporal, absoluto);
    }

    public Path destinoSeguro(Path entrada) {
        Path absoluto = entrada.toAbsolutePath().normalize();
        Path dir = absoluto.getParent() == null ? Path.of(".").toAbsolutePath() : absoluto.getParent();
        String nombre = absoluto.getFileName().toString();
        String base = nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                ? nombre.substring(0, nombre.length() - 5)
                : nombre;
        Path candidato = dir.resolve(base + "_validados.xlsx");
        if (!Files.exists(candidato)) {
            return candidato;
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return dir.resolve(base + "_validados-" + stamp + ".xlsx");
    }

    private static int[] resolverColumnas(Row header, DataFormatter formatter, FormulaEvaluator evaluator) {
        int[] columnas = new int[] {0, 1, 2, 3, 4, 5};
        if (header == null) {
            return columnas;
        }
        Integer id = null;
        Integer nombre = null;
        Integer cargo = null;
        Integer orcid = null;
        Integer doi = null;
        Integer posicion = null;
        short last = header.getLastCellNum();
        for (int i = 0; i < last; i++) {
            String clave = claveEncabezado(valorCelda(header.getCell(i), formatter, evaluator));
            if (clave.isEmpty()) {
                continue;
            }
            if (id == null && clave.equals("id")) {
                id = i;
            } else if (nombre == null && (clave.equals("nombre") || clave.equals("name"))) {
                nombre = i;
            } else if (cargo == null && clave.equals("cargo")) {
                cargo = i;
            } else if (orcid == null && clave.equals("orcid")) {
                orcid = i;
            } else if (doi == null && clave.equals("doi")) {
                doi = i;
            } else if (posicion == null && (clave.equals("posicion") || clave.equals("position"))) {
                posicion = i;
            }
        }
        if (id != null && nombre != null && orcid != null) {
            columnas[0] = id;
            columnas[1] = nombre;
            columnas[2] = cargo == null ? 2 : cargo;
            columnas[3] = orcid;
            columnas[4] = doi == null ? 4 : doi;
            columnas[5] = posicion == null ? 5 : posicion;
        }
        return columnas;
    }

    private static OrcidExcelFila mapearFila(int numeroFila, Row row, int[] columnas,
                                             DataFormatter formatter, FormulaEvaluator evaluator) {
        String id = valorCelda(row.getCell(columnas[0]), formatter, evaluator);
        String nombre = valorCelda(row.getCell(columnas[1]), formatter, evaluator);
        String cargo = valorCelda(row.getCell(columnas[2]), formatter, evaluator);
        String orcid = valorCelda(row.getCell(columnas[3]), formatter, evaluator);
        String doi = valorCelda(row.getCell(columnas[4]), formatter, evaluator);
        String posicion = valorCelda(row.getCell(columnas[5]), formatter, evaluator);
        if (id.isEmpty() && nombre.isEmpty() && orcid.isEmpty() && doi.isEmpty()) {
            return null;
        }
        return new OrcidExcelFila(numeroFila, id, nombre, cargo, orcid, doi, posicion);
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

    private static Path reemplazarDestino(Path temporal, Path destino) throws IOException {
        try {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return destino;
        } catch (IOException ignored) {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            return destino;
        }
    }
}
