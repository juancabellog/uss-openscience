package cl.uss.ca.orcid.email;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;

import javax.xml.namespace.QName;
import java.util.List;
import java.util.Map;

/**
 * Reemplaza placeholders en un DOCX aunque el texto esté partido en varios XWPFRun.
 * Conserva saltos de línea internos del párrafo ({@code w:br}/{@code w:cr}).
 */
public class WordPlaceholderReplacer {

    private static final QName XML_SPACE = new QName("http://www.w3.org/XML/1998/namespace", "space");

    public void reemplazarPlaceholders(XWPFDocument document, Map<String, String> reemplazos) {
        if (document == null || reemplazos == null || reemplazos.isEmpty()) {
            return;
        }
        reemplazarEnParrafos(document.getParagraphs(), reemplazos);
        reemplazarEnTablas(document.getTables(), reemplazos);
        for (XWPFHeader header : document.getHeaderList()) {
            reemplazarEnParrafos(header.getParagraphs(), reemplazos);
            reemplazarEnTablas(header.getTables(), reemplazos);
        }
        for (XWPFFooter footer : document.getFooterList()) {
            reemplazarEnParrafos(footer.getParagraphs(), reemplazos);
            reemplazarEnTablas(footer.getTables(), reemplazos);
        }
    }

    public void reemplazarEnParrafo(XWPFParagraph paragraph, Map<String, String> reemplazos) {
        if (paragraph == null || reemplazos == null || reemplazos.isEmpty()) {
            return;
        }
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            return;
        }
        String original = textoParrafo(runs);
        if (original.isEmpty()) {
            return;
        }
        String reemplazado = original;
        for (Map.Entry<String, String> entry : reemplazos.entrySet()) {
            String placeholder = entry.getKey();
            if (placeholder == null || placeholder.isEmpty()) {
                continue;
            }
            String valor = entry.getValue() == null ? "" : entry.getValue();
            reemplazado = reemplazado.replace(placeholder, valor);
        }
        if (reemplazado.equals(original)) {
            return;
        }
        aplicarTextoAlPrimerRun(runs, reemplazado);
    }

    public void reemplazarEnTabla(XWPFTable table, Map<String, String> reemplazos) {
        if (table == null) {
            return;
        }
        for (XWPFTableRow row : table.getRows()) {
            if (row == null) {
                continue;
            }
            for (XWPFTableCell cell : row.getTableCells()) {
                if (cell == null) {
                    continue;
                }
                reemplazarEnParrafos(cell.getParagraphs(), reemplazos);
                reemplazarEnTablas(cell.getTables(), reemplazos);
            }
        }
    }

    private void reemplazarEnParrafos(List<XWPFParagraph> paragraphs, Map<String, String> reemplazos) {
        if (paragraphs == null) {
            return;
        }
        for (XWPFParagraph paragraph : paragraphs) {
            reemplazarEnParrafo(paragraph, reemplazos);
        }
    }

    private void reemplazarEnTablas(List<XWPFTable> tables, Map<String, String> reemplazos) {
        if (tables == null) {
            return;
        }
        for (XWPFTable table : tables) {
            reemplazarEnTabla(table, reemplazos);
        }
    }

    private static String textoParrafo(List<XWPFRun> runs) {
        StringBuilder texto = new StringBuilder();
        for (XWPFRun run : runs) {
            texto.append(textoRun(run));
        }
        return texto.toString();
    }

    private static String textoRun(XWPFRun run) {
        if (run == null || run.getCTR() == null) {
            return "";
        }
        StringBuilder texto = new StringBuilder();
        XmlCursor cursor = run.getCTR().newCursor();
        try {
            if (!cursor.toFirstChild()) {
                return "";
            }
            do {
                QName name = cursor.getName();
                if (name == null) {
                    continue;
                }
                String local = name.getLocalPart();
                if ("t".equals(local)) {
                    String parte = cursor.getTextValue();
                    if (parte != null) {
                        texto.append(parte);
                    }
                } else if ("br".equals(local) || "cr".equals(local)) {
                    texto.append('\n');
                } else if ("tab".equals(local)) {
                    texto.append('\t');
                }
            } while (cursor.toNextSibling());
        } finally {
            cursor.dispose();
        }
        return texto.toString();
    }

    private static void aplicarTextoAlPrimerRun(List<XWPFRun> runs, String texto) {
        XWPFRun primero = runs.get(0);
        vaciarContenidoVisible(primero);
        for (int i = 1; i < runs.size(); i++) {
            vaciarContenidoVisible(runs.get(i));
        }
        String valor = texto == null ? "" : texto;
        String[] lineas = valor.split("\n", -1);
        CTR ctr = primero.getCTR();
        for (int i = 0; i < lineas.length; i++) {
            if (i > 0) {
                ctr.addNewBr();
            }
            agregarTexto(ctr, lineas[i]);
        }
    }

    private static void vaciarContenidoVisible(XWPFRun run) {
        if (run == null || run.getCTR() == null) {
            return;
        }
        CTR ctr = run.getCTR();
        while (ctr.sizeOfTArray() > 0) {
            ctr.removeT(0);
        }
        while (ctr.sizeOfBrArray() > 0) {
            ctr.removeBr(0);
        }
        while (ctr.sizeOfCrArray() > 0) {
            ctr.removeCr(0);
        }
        while (ctr.sizeOfTabArray() > 0) {
            ctr.removeTab(0);
        }
    }

    private static void agregarTexto(CTR ctr, String valor) {
        CTText ctText = ctr.addNewT();
        String texto = valor == null ? "" : valor;
        ctText.setStringValue(texto);
        preservarEspacios(ctText, texto);
    }

    private static void preservarEspacios(CTText ctText, String texto) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        if (!texto.startsWith(" ") && !texto.endsWith(" ") && !texto.contains("\t")) {
            return;
        }
        XmlCursor cursor = ctText.newCursor();
        cursor.toNextToken();
        cursor.insertAttributeWithValue(XML_SPACE, "preserve");
        cursor.dispose();
    }
}
