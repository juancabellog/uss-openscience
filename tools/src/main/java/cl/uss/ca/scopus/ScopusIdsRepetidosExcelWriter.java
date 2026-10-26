package cl.uss.ca.scopus;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class ScopusIdsRepetidosExcelWriter {
    static final String[] ENCABEZADOS = {
            "idPerson",
            "uuid",
            "Nombre Completo",
            "#Scopus_Id",
            "idPerson2",
            "uuid2",
            "Nombre Completo2",
            "#Scopus_Id",
            "Scopus_Id (repetido)"
    };

    public Path escribir(List<ScopusDuplicadoRow> filas, Path destino) throws IOException {
        Path absoluto = destino.toAbsolutePath().normalize();
        Path temporal = absoluto.resolveSibling(absoluto.getFileName().toString() + ".tmp");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Scopus Repetidos");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            CellStyle textoStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            textoStyle.setDataFormat(format.getFormat("@"));

            Row header = sheet.createRow(0);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(ENCABEZADOS[i]);
                cell.setCellStyle(headerStyle);
            }

            int filaIdx = 1;
            if (filas != null) {
                for (ScopusDuplicadoRow item : filas) {
                    if (item == null) {
                        continue;
                    }
                    Row row = sheet.createRow(filaIdx++);
                    setNumero(row, 0, item.idPerson());
                    setTexto(row, 1, item.uuid(), textoStyle);
                    setTexto(row, 2, item.nombreCompleto(), null);
                    setNumero(row, 3, item.cantidadScopus());
                    setNumero(row, 4, item.idPerson2());
                    setTexto(row, 5, item.uuid2(), textoStyle);
                    setTexto(row, 6, item.nombreCompleto2(), null);
                    setNumero(row, 7, item.cantidadScopus2());
                    setTexto(row, 8, item.scopusIdRepetido(), textoStyle);
                }
            }

            int lastRow = Math.max(0, filaIdx - 1);
            sheet.setAutoFilter(new CellRangeAddress(0, lastRow, 0, ENCABEZADOS.length - 1));
            sheet.createFreezePane(0, 1);
            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 40 * 256);
            sheet.setColumnWidth(2, 36 * 256);
            sheet.setColumnWidth(3, 14 * 256);
            sheet.setColumnWidth(4, 12 * 256);
            sheet.setColumnWidth(5, 40 * 256);
            sheet.setColumnWidth(6, 36 * 256);
            sheet.setColumnWidth(7, 14 * 256);
            sheet.setColumnWidth(8, 22 * 256);

            Path parent = absoluto.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(temporal)) {
                workbook.write(out);
            }
        }
        return moverDestino(temporal, absoluto);
    }

    private static Path moverDestino(Path temporal, Path destino) throws IOException {
        try {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return destino;
        } catch (IOException ignored) {
            try {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
                return destino;
            } catch (IOException ex) {
                String stamp = String.valueOf(System.currentTimeMillis());
                Path alternativo = destino.resolveSibling(
                        destino.getFileName().toString().replace(".xlsx", "-" + stamp + ".xlsx"));
                Files.move(temporal, alternativo, StandardCopyOption.REPLACE_EXISTING);
                System.err.println("WARN no se pudo sobrescribir " + destino
                        + " (¿está abierto en Excel?). Se escribió: " + alternativo);
                return alternativo;
            }
        }
    }

    private static void setTexto(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (style != null) {
            cell.setCellStyle(style);
        }
        cell.setCellValue(value == null ? "" : value);
    }

    private static void setNumero(Row row, int col, long value) {
        row.createCell(col).setCellValue(value);
    }
}
