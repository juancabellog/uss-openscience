package cl.uss.ca.directory;

import cl.uss.ca.exception.ValidationException;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class DirectoryExcelParserTest {
    private DirectoryExcelParserTest() {
    }

    public static void main(String[] args) throws Exception {
        readsRealHeaders();
        missingRequiredHeaderFails();
        emptyFileFails();
        System.out.println("DirectoryExcelParserTest OK");
    }

    static void readsRealHeaders() throws Exception {
        byte[] bytes = workbook(true, List.of(
                List.of("ana@uss.cl", "123456785", "Ana", "", "Pérez", "", "Bellavista", "c. Facultad de Medicina", "Docente", "ana@uss.cl")
        ));
        DirectoryExcelParser.DirectoryParseResult parsed = new DirectoryExcelParser().parse(bytes);
        assertEquals(1, parsed.rows().size());
        assertEquals("Ana", parsed.rows().get(0).givenName);
        assertEquals("ana@uss.cl", parsed.rows().get(0).email);
        assertEquals(true, parsed.unmappedHeaders().isEmpty());
    }

    static void missingRequiredHeaderFails() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Hoja1");
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("Email");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            try {
                new DirectoryExcelParser().parse(out.toByteArray());
                throw new AssertionError("expected header validation");
            } catch (ValidationException ex) {
                assertEquals("DIRECTORY_HEADER_MISSING", ex.getCode());
            }
        }
    }

    static void emptyFileFails() {
        try {
            new DirectoryExcelParser().parse(new byte[0]);
            throw new AssertionError("expected empty file validation");
        } catch (ValidationException ex) {
            assertEquals("DIRECTORY_FILE_EMPTY", ex.getCode());
        }
    }

    static byte[] workbook(boolean withHeaders, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Hoja1");
            int r = 0;
            if (withHeaders) {
                XSSFRow header = sheet.createRow(r++);
                String[] headers = {
                        DirectoryExcelParser.EMPRESA,
                        DirectoryExcelParser.RUT,
                        DirectoryExcelParser.NOMBRE,
                        DirectoryExcelParser.SEGUNDO_NOMBRE,
                        DirectoryExcelParser.APELLIDO,
                        DirectoryExcelParser.SEGUNDO_APELLIDO,
                        DirectoryExcelParser.SEDE,
                        DirectoryExcelParser.FACULTAD,
                        DirectoryExcelParser.CARGO,
                        DirectoryExcelParser.EMAIL
                };
                for (int i = 0; i < headers.length; i++) {
                    header.createCell(i).setCellValue(headers[i]);
                }
            }
            for (List<String> values : rows) {
                XSSFRow row = sheet.createRow(r++);
                for (int i = 0; i < values.size(); i++) {
                    row.createCell(i).setCellValue(values.get(i));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
