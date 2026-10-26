package cl.uss.ca.openalex;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class OrcidExcelWriter {
    private static final String[] COLUMNAS = {"id", "nombre", "Cargo", "orcid", "doi", "posicion"};

    public Path escribir(List<OrcidEncontrado> encontrados, Path destino) throws IOException {
        Path absoluto = destino.toAbsolutePath();
        Path temporal = absoluto.resolveSibling(absoluto.getFileName().toString() + ".tmp");
        int filas = 0;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("ORCID encontrados");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            for (int i = 0; i < COLUMNAS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(COLUMNAS[i]);
                cell.setCellStyle(headerStyle);
            }

            int fila = 1;
            if (encontrados != null) {
                for (OrcidEncontrado item : encontrados) {
                    if (item == null) {
                        continue;
                    }
                    Row row = sheet.createRow(fila++);
                    String cargo = item.getCargo() == null ? "" : item.getCargo();
                    setNumero(row, 0, item.getPersonId());
                    setTexto(row, 1, item.getNombrePersona());
                    row.createCell(2).setCellValue(cargo);
                    setTexto(row, 3, item.getOrcid());
                    setTexto(row, 4, item.getDoi());
                    setNumero(row, 5, item.getPosicion());
                }
            }
            filas = fila - 1;
            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 40 * 256);
            sheet.setColumnWidth(2, 35 * 256);
            sheet.setColumnWidth(3, 22 * 256);
            sheet.setColumnWidth(4, 40 * 256);
            sheet.setColumnWidth(5, 12 * 256);

            Files.createDirectories(absoluto.getParent() == null
                    ? Path.of(".").toAbsolutePath()
                    : absoluto.getParent());
            try (OutputStream out = Files.newOutputStream(temporal)) {
                workbook.write(out);
            }
        }

        Path escrito = reemplazarDestino(temporal, absoluto);
        System.out.println("Excel actualizado (" + filas + " ORCID): " + escrito);
        return escrito;
    }

    private static Path reemplazarDestino(Path temporal, Path destino) throws IOException {
        try {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return destino;
        } catch (IOException ignored) {
            try {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
                return destino;
            } catch (IOException ex) {
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                String nombre = destino.getFileName().toString().replace(".xlsx", "-" + stamp + ".xlsx");
                Path alternativo = destino.resolveSibling(nombre);
                Files.move(temporal, alternativo, StandardCopyOption.REPLACE_EXISTING);
                System.err.println("WARN no se pudo sobrescribir " + destino
                        + " (¿está abierto en Excel?). Se escribió: " + alternativo);
                return alternativo;
            }
        }
    }

    private static void setTexto(Row row, int col, String value) {
        row.createCell(col).setCellValue(value == null ? "" : value);
    }

    private static void setNumero(Row row, int col, Number value) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setBlank();
        } else {
            cell.setCellValue(value.longValue());
        }
    }
}
