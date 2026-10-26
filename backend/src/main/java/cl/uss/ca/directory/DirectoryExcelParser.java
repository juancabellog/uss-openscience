package cl.uss.ca.directory;

import cl.uss.ca.exception.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class DirectoryExcelParser {
    public static final String EMPRESA = "Empresa";
    public static final String RUT = "RUT";
    public static final String NOMBRE = "Nombre";
    public static final String SEGUNDO_NOMBRE = "Segundo nombre";
    public static final String APELLIDO = "Apellido";
    public static final String SEGUNDO_APELLIDO = "Segundo Apellido";
    public static final String SEDE = "Nombre Sede";
    public static final String FACULTAD = "Nombre Facultad y Vicerrectoría";
    public static final String CARGO = "Cargo";
    public static final String EMAIL = "Email";

    private static final Set<String> REQUIRED = Set.of(RUT, NOMBRE, APELLIDO, EMAIL);
    private static final DataFormatter FORMATTER = new DataFormatter();

    public DirectoryParseResult parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ValidationException("DIRECTORY_FILE_EMPTY", "The directory file is empty");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw new ValidationException("DIRECTORY_SHEET_MISSING", "The workbook has no sheets");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new ValidationException("DIRECTORY_HEADER_MISSING", "The first sheet has no header row");
            }
            Map<String, Integer> columns = headers(headerRow);
            for (String required : REQUIRED) {
                if (!columns.containsKey(normalizeHeader(required))) {
                    throw new ValidationException("DIRECTORY_HEADER_MISSING", "Missing column: " + required);
                }
            }
            List<String> unmapped = new ArrayList<>();
            Set<String> known = Set.of(
                    normalizeHeader(EMPRESA),
                    normalizeHeader(RUT),
                    normalizeHeader(NOMBRE),
                    normalizeHeader(SEGUNDO_NOMBRE),
                    normalizeHeader(APELLIDO),
                    normalizeHeader(SEGUNDO_APELLIDO),
                    normalizeHeader(SEDE),
                    normalizeHeader(FACULTAD),
                    normalizeHeader(CARGO),
                    normalizeHeader(EMAIL)
            );
            for (String header : columns.keySet()) {
                if (!known.contains(header)) {
                    unmapped.add(header);
                }
            }
            List<DirectoryExcelRow> rows = new ArrayList<>();
            int first = sheet.getFirstRowNum() + 1;
            int last = sheet.getLastRowNum();
            for (int r = first; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isEmpty(row, columns)) {
                    continue;
                }
                rows.add(new DirectoryExcelRow(
                        r + 1,
                        cell(row, columns, EMPRESA),
                        cell(row, columns, RUT),
                        cell(row, columns, NOMBRE),
                        cell(row, columns, SEGUNDO_NOMBRE),
                        cell(row, columns, APELLIDO),
                        cell(row, columns, SEGUNDO_APELLIDO),
                        cell(row, columns, SEDE),
                        cell(row, columns, FACULTAD),
                        cell(row, columns, CARGO),
                        cell(row, columns, EMAIL)
                ));
            }
            return new DirectoryParseResult(sheet.getSheetName(), List.copyOf(columns.keySet()), unmapped, rows);
        } catch (ValidationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ValidationException("DIRECTORY_FILE_INVALID", "The directory file could not be read");
        }
    }

    private static Map<String, Integer> headers(Row row) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        short last = row.getLastCellNum();
        for (int i = 0; i < last; i++) {
            String header = text(row.getCell(i));
            if (header == null) {
                continue;
            }
            columns.putIfAbsent(normalizeHeader(header), i);
        }
        return columns;
    }

    private static boolean isEmpty(Row row, Map<String, Integer> columns) {
        for (Integer index : columns.values()) {
            if (text(row.getCell(index)) != null) {
                return false;
            }
        }
        return true;
    }

    private static String cell(Row row, Map<String, Integer> columns, String header) {
        Integer index = columns.get(normalizeHeader(header));
        if (index == null) {
            return null;
        }
        return text(row.getCell(index));
    }

    private static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        String value = FORMATTER.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    static String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT);
    }

    public record DirectoryParseResult(
            String sheetName,
            List<String> headers,
            List<String> unmappedHeaders,
            List<DirectoryExcelRow> rows
    ) {
    }
}
