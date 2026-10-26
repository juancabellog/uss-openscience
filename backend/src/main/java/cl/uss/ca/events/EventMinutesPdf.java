package cl.uss.ca.events;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Acta de asistencia A4 horizontal. El ensamblador PDF es el mismo escritor Type1
 * del proyecto; el layout mide anchos reales, envuelve texto y dibuja una grilla.
 * No incrusta logotipos: el branding del repo es SVG y este escritor no puede
 * rasterizarlo sin deformar las proporciones institucionales.
 */
public final class EventMinutesPdf {
    static final float PAGE_WIDTH = 842f;
    static final float PAGE_HEIGHT = 595f;
    private static final float MARGIN_X = 36f;
    private static final float MARGIN_TOP = 30f;
    private static final float MARGIN_BOTTOM = 34f;
    private static final float BODY_SIZE = 8.5f;
    private static final float HEADER_SIZE = 8f;
    private static final float TITLE_SIZE = 14f;
    private static final float EVENT_SIZE = 12f;
    private static final float META_SIZE = 8.5f;
    private static final float FOOTER_SIZE = 7.5f;
    private static final float LEADING = 1.25f;
    private static final float CELL_PAD_X = 5f;
    private static final float CELL_PAD_Y = 5f;
    private static final float MIN_FILLED_ROW = 28f;
    private static final float MIN_BLANK_ROW = 40f;
    private static final float[] COLUMN_RATIOS = {0.05f, 0.29f, 0.26f, 0.20f, 0.20f};

    private EventMinutesPdf() {
    }

    public static byte[] render(MinutesDocument document) {
        return render(document, document == null || document.language == null ? EventLanguage.ES : document.language);
    }

    public static byte[] render(MinutesDocument document, String language) {
        MinutesDocument safe = document == null ? new MinutesDocument() : document;
        Labels labels = Labels.of(language);
        Layout layout = Layout.create();
        List<PreparedRow> rows = prepareRows(safe.rows, layout);
        List<PagePlan> pages = paginate(safe, labels, layout, rows);
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder("[");
        int fontRegular = 3 + pages.size() * 2;
        int fontBold = fontRegular + 1;
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) {
                kids.append(' ');
            }
            kids.append(3 + i * 2).append(" 0 R");
        }
        kids.append(']');
        objects.add("<< /Type /Pages /Kids " + kids + " /Count " + pages.size() + " >>");
        for (int i = 0; i < pages.size(); i++) {
            int pageObj = 3 + i * 2;
            int contentObj = pageObj + 1;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + n(PAGE_WIDTH) + " " + n(PAGE_HEIGHT)
                    + "] /Contents " + contentObj + " 0 R /Resources << /Font << /F1 " + fontRegular
                    + " 0 R /F2 " + fontBold + " 0 R >> >> >>");
            String content = pageContent(safe, labels, layout, pages.get(i), i + 1, pages.size());
            byte[] stream = content.getBytes(StandardCharsets.ISO_8859_1);
            objects.add("<< /Length " + stream.length + " >>\nstream\n" + content + "\nendstream");
        }
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        return assemble(objects);
    }

    private static List<PreparedRow> prepareRows(List<Row> source, Layout layout) {
        List<PreparedRow> rows = new ArrayList<>();
        if (source == null) {
            return rows;
        }
        for (Row row : source) {
            boolean blank = row != null && row.blank;
            String name = blank ? "" : clean(row == null ? null : row.name);
            String faculty = blank ? "" : clean(row == null ? null : row.faculty);
            String position = blank ? "" : clean(row == null ? null : row.position);
            List<String> nameLines = PdfType1Metrics.wrap(name, layout.textWidth(1), BODY_SIZE, false);
            List<String> facultyLines = PdfType1Metrics.wrap(faculty, layout.textWidth(2), BODY_SIZE, false);
            List<String> positionLines = PdfType1Metrics.wrap(position, layout.textWidth(3), BODY_SIZE, false);
            int lineCount = Math.max(1, Math.max(nameLines.size(), Math.max(facultyLines.size(), positionLines.size())));
            float textHeight = lineCount * BODY_SIZE * LEADING;
            float min = blank ? MIN_BLANK_ROW : MIN_FILLED_ROW;
            float height = Math.max(min, textHeight + CELL_PAD_Y * 2f);
            rows.add(new PreparedRow(
                    row == null ? 0 : row.number,
                    blank,
                    String.valueOf(row == null ? 0 : row.number),
                    nameLines,
                    facultyLines,
                    positionLines,
                    height
            ));
        }
        return rows;
    }

    private static List<PagePlan> paginate(MinutesDocument document, Labels labels, Layout layout, List<PreparedRow> rows) {
        List<PagePlan> pages = new ArrayList<>();
        int index = 0;
        boolean first = true;
        if (rows.isEmpty()) {
            pages.add(new PagePlan(List.of(), firstPageHeaderHeight(document, labels, layout)));
            return pages;
        }
        while (index < rows.size()) {
            float header = first ? firstPageHeaderHeight(document, labels, layout) : continuationHeaderHeight();
            float tableHeader = tableHeaderHeight(labels, layout);
            float usableBottom = MARGIN_BOTTOM + 16f;
            float y = PAGE_HEIGHT - MARGIN_TOP - header - 8f - tableHeader;
            List<PreparedRow> pageRows = new ArrayList<>();
            while (index < rows.size()) {
                PreparedRow row = rows.get(index);
                if (y - row.height < usableBottom) {
                    if (pageRows.isEmpty()) {
                        pageRows.add(row);
                        index++;
                    }
                    break;
                }
                pageRows.add(row);
                y -= row.height;
                index++;
            }
            pages.add(new PagePlan(pageRows, header));
            first = false;
        }
        return pages;
    }

    private static String pageContent(
            MinutesDocument document,
            Labels labels,
            Layout layout,
            PagePlan page,
            int pageNumber,
            int totalPages
    ) {
        StringBuilder out = new StringBuilder(4096);
        float y = PAGE_HEIGHT - MARGIN_TOP;
        if (pageNumber == 1) {
            y = drawFirstHeader(out, document, labels, layout, y);
        } else {
            y = drawContinuationHeader(out, document, labels, layout, y);
        }
        y -= 8f;
        float tableTop = y;
        float headerH = tableHeaderHeight(labels, layout);
        drawTableHeader(out, labels, layout, tableTop, headerH);
        y = tableTop - headerH;
        for (PreparedRow row : page.rows) {
            drawBodyRow(out, layout, row, y);
            y -= row.height;
        }
        drawFooter(out, document, labels, pageNumber, totalPages);
        return out.toString();
    }

    private static float drawFirstHeader(
            StringBuilder out,
            MinutesDocument document,
            Labels labels,
            Layout layout,
            float y
    ) {
        y = drawLines(out, labels.institution, y, HEADER_SIZE, false, 0.25f);
        y -= 4f;
        y = drawLines(out, labels.documentTitle, y, TITLE_SIZE, true, 0f);
        y -= 3f;
        y = drawLines(out, labels.product, y, HEADER_SIZE, false, 0.25f);
        y -= 10f;
        y = drawWrapped(out, clean(document.title), layout.contentWidth, y, EVENT_SIZE, true, 0f);
        y -= 8f;
        stroke(out, layout.left, y, layout.left + layout.contentWidth, y);
        y -= 10f;
        return drawMeta(out, document, labels, layout, y);
    }

    private static float drawContinuationHeader(
            StringBuilder out,
            MinutesDocument document,
            Labels labels,
            Layout layout,
            float y
    ) {
        String top = labels.institution + "  ·  " + labels.documentTitle;
        y = drawLines(out, top, y, HEADER_SIZE, false, 0.25f);
        y -= 3f;
        StringBuilder brief = new StringBuilder(clean(document.title));
        if (document.version > 0) {
            if (brief.length() > 0) {
                brief.append("  ·  ");
            }
            brief.append(labels.version).append(' ').append(document.version);
        }
        if (hasText(document.identifier)) {
            brief.append("  ·  ").append(document.identifier);
        }
        return drawWrapped(out, brief.toString(), layout.contentWidth, y, HEADER_SIZE, false, 0.2f);
    }

    private static float drawMeta(
            StringBuilder out,
            MinutesDocument document,
            Labels labels,
            Layout layout,
            float y
    ) {
        float gutter = 20f;
        float colW = (layout.contentWidth - gutter) / 2f;
        float leftX = layout.left;
        float rightX = layout.left + colW + gutter;
        float labelW = metaLabelWidth(labels);
        List<MetaLine> left = new ArrayList<>();
        left.add(new MetaLine(labels.date, clean(document.when)));
        left.add(new MetaLine(labels.place, clean(document.place)));
        left.add(new MetaLine(labels.unit, clean(document.unit)));
        List<MetaLine> right = new ArrayList<>();
        if (document.version > 0) {
            right.add(new MetaLine(labels.version, String.valueOf(document.version)));
        }
        if (hasText(document.identifier)) {
            right.add(new MetaLine(labels.identifier, document.identifier.trim()));
        }
        int rows = Math.max(left.size(), right.size());
        float cursor = y;
        for (int i = 0; i < rows; i++) {
            float rowTop = cursor;
            float leftH = 0f;
            float rightH = 0f;
            if (i < left.size()) {
                leftH = drawMetaPair(out, left.get(i), leftX, colW, labelW, rowTop);
            }
            if (i < right.size()) {
                rightH = drawMetaPair(out, right.get(i), rightX, colW, labelW, rowTop);
            }
            cursor -= Math.max(leftH, rightH) + 3f;
        }
        return cursor;
    }

    private static float drawMetaPair(StringBuilder out, MetaLine line, float x, float colW, float labelW, float top) {
        float valueW = Math.max(24f, colW - labelW - 8f);
        List<String> values = PdfType1Metrics.wrap(line.value, valueW, META_SIZE, false);
        if (values.isEmpty()) {
            values = List.of("");
        }
        fillText(out, "F2", META_SIZE, x, top - META_SIZE, line.label, 0.2f);
        float y = top;
        for (String value : values) {
            fillText(out, "F1", META_SIZE, x + labelW + 8f, y - META_SIZE, value, 0f);
            y -= META_SIZE * LEADING;
        }
        return Math.max(META_SIZE * LEADING, values.size() * META_SIZE * LEADING);
    }

    private static void drawTableHeader(StringBuilder out, Labels labels, Layout layout, float top, float height) {
        String[] titles = {labels.number, labels.name, labels.faculty, labels.position, labels.signature};
        rect(out, layout.left, top - height, layout.contentWidth, height, true, 0.93f);
        for (int i = 0; i < layout.columns.length; i++) {
            Column col = layout.columns[i];
            strokeRect(out, col.x, top - height, col.width, height);
            List<String> lines = PdfType1Metrics.wrap(titles[i], col.width - CELL_PAD_X * 2f, HEADER_SIZE, true);
            drawCellLines(out, col, top, height, lines, HEADER_SIZE, true, i == 0);
        }
    }

    private static void drawBodyRow(StringBuilder out, Layout layout, PreparedRow row, float top) {
        for (Column col : layout.columns) {
            strokeRect(out, col.x, top - row.height, col.width, row.height);
        }
        drawCellLines(out, layout.columns[0], top, row.height, List.of(row.numberLabel), BODY_SIZE, false, true);
        drawCellLines(out, layout.columns[1], top, row.height, row.name, BODY_SIZE, false, false);
        drawCellLines(out, layout.columns[2], top, row.height, row.faculty, BODY_SIZE, false, false);
        drawCellLines(out, layout.columns[3], top, row.height, row.position, BODY_SIZE, false, false);
    }

    private static void drawCellLines(
            StringBuilder out,
            Column col,
            float top,
            float height,
            List<String> lines,
            float fontSize,
            boolean bold,
            boolean center
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        float leading = fontSize * LEADING;
        float block = lines.size() * leading;
        float start = top - ((height - block) / 2f) - fontSize;
        String font = bold ? "F2" : "F1";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) {
                continue;
            }
            float textW = PdfType1Metrics.width(line, fontSize, bold);
            float x;
            if (center) {
                x = col.x + Math.max(CELL_PAD_X, (col.width - textW) / 2f);
            } else {
                x = col.x + CELL_PAD_X;
            }
            fillText(out, font, fontSize, x, start - i * leading, line, 0f);
        }
    }

    private static void drawFooter(StringBuilder out, MinutesDocument document, Labels labels, int page, int total) {
        StringBuilder line = new StringBuilder(labels.product);
        if (document.version > 0) {
            line.append("  ·  ").append(labels.version).append(' ').append(document.version);
        }
        line.append("  ·  ").append(labels.page).append(' ').append(page).append(' ').append(labels.of).append(' ').append(total);
        float y = 20f;
        stroke(out, MARGIN_X, 28f, PAGE_WIDTH - MARGIN_X, 28f);
        fillText(out, "F1", FOOTER_SIZE, MARGIN_X, y, line.toString(), 0.3f);
    }

    private static float firstPageHeaderHeight(MinutesDocument document, Labels labels, Layout layout) {
        float y = PAGE_HEIGHT - MARGIN_TOP;
        y -= HEADER_SIZE * LEADING + 4f;
        y -= wrapHeight(labels.documentTitle, layout.contentWidth, TITLE_SIZE, true);
        y -= 3f + HEADER_SIZE * LEADING + 10f;
        y -= wrapHeight(clean(document.title), layout.contentWidth, EVENT_SIZE, true);
        y -= 18f;
        float gutter = 20f;
        float colW = (layout.contentWidth - gutter) / 2f;
        float labelW = metaLabelWidth(labels);
        float valueW = Math.max(24f, colW - labelW - 8f);
        int left = 3;
        int right = (document.version > 0 ? 1 : 0) + (hasText(document.identifier) ? 1 : 0);
        int rows = Math.max(left, right);
        float meta = 0f;
        meta += wrapHeight(clean(document.when), valueW, META_SIZE, false) + 3f;
        meta += wrapHeight(clean(document.place), valueW, META_SIZE, false) + 3f;
        meta += wrapHeight(clean(document.unit), valueW, META_SIZE, false) + 3f;
        if (rows > 3) {
            meta += (rows - 3) * (META_SIZE * LEADING + 3f);
        }
        return PAGE_HEIGHT - MARGIN_TOP - (y - meta);
    }

    private static float continuationHeaderHeight() {
        return HEADER_SIZE * LEADING + 3f + HEADER_SIZE * LEADING;
    }

    private static float tableHeaderHeight(Labels labels, Layout layout) {
        String[] titles = {labels.number, labels.name, labels.faculty, labels.position, labels.signature};
        int lines = 1;
        for (int i = 0; i < titles.length; i++) {
            lines = Math.max(lines, Math.max(1, PdfType1Metrics.wrap(titles[i], layout.textWidth(i), HEADER_SIZE, true).size()));
        }
        return Math.max(20f, lines * HEADER_SIZE * LEADING + CELL_PAD_Y * 2f);
    }

    private static float metaLabelWidth(Labels labels) {
        float max = 0f;
        for (String label : List.of(labels.date, labels.place, labels.unit, labels.version, labels.identifier)) {
            max = Math.max(max, PdfType1Metrics.width(label, META_SIZE, true));
        }
        return max + 4f;
    }

    private static float wrapHeight(String text, float width, float fontSize, boolean bold) {
        int lines = Math.max(1, PdfType1Metrics.wrap(text, width, fontSize, bold).size());
        if (!hasText(text)) {
            lines = 1;
        }
        return lines * fontSize * LEADING;
    }

    private static float drawLines(StringBuilder out, String text, float y, float fontSize, boolean bold, float gray) {
        fillText(out, bold ? "F2" : "F1", fontSize, MARGIN_X, y - fontSize, text, gray);
        return y - fontSize * LEADING;
    }

    private static float drawWrapped(
            StringBuilder out,
            String text,
            float width,
            float y,
            float fontSize,
            boolean bold,
            float gray
    ) {
        List<String> lines = PdfType1Metrics.wrap(text, width, fontSize, bold);
        if (lines.isEmpty()) {
            return y - fontSize * LEADING;
        }
        for (String line : lines) {
            fillText(out, bold ? "F2" : "F1", fontSize, MARGIN_X, y - fontSize, line, gray);
            y -= fontSize * LEADING;
        }
        return y;
    }

    private static void fillText(StringBuilder out, String font, float size, float x, float y, String value, float gray) {
        if (value == null || value.isEmpty()) {
            return;
        }
        out.append(n(gray)).append(" g\nBT\n/")
                .append(font).append(' ').append(n(size)).append(" Tf\n")
                .append("1 0 0 1 ").append(n(x)).append(' ').append(n(y)).append(" Tm\n(")
                .append(PdfType1Metrics.encode(value)).append(") Tj\nET\n0 g\n");
    }

    private static void rect(StringBuilder out, float x, float y, float w, float h, boolean fill, float gray) {
        if (fill) {
            out.append(n(gray)).append(" g\n")
                    .append(n(x)).append(' ').append(n(y)).append(' ').append(n(w)).append(' ').append(n(h))
                    .append(" re f\n0 g\n");
        }
    }

    private static void strokeRect(StringBuilder out, float x, float y, float w, float h) {
        out.append("0.32 0.32 0.32 RG\n0.45 w\n")
                .append(n(x)).append(' ').append(n(y)).append(' ').append(n(w)).append(' ').append(n(h))
                .append(" re S\n0 G\n");
    }

    private static void stroke(StringBuilder out, float x1, float y1, float x2, float y2) {
        out.append("0.55 0.55 0.55 RG\n0.4 w\n")
                .append(n(x1)).append(' ').append(n(y1)).append(" m ")
                .append(n(x2)).append(' ').append(n(y2)).append(" l S\n0 G\n");
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').replaceAll(" +", " ").trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String n(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static byte[] assemble(List<String> objects) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n");
        write(out, "0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) {
            write(out, String.format(Locale.US, "%010d 00000 n \n", offsets.get(i)));
        }
        write(out, "trailer << /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    static final class Layout {
        final float left;
        final float contentWidth;
        final Column[] columns;

        private Layout(float left, float contentWidth, Column[] columns) {
            this.left = left;
            this.contentWidth = contentWidth;
            this.columns = columns;
        }

        static Layout create() {
            float width = PAGE_WIDTH - MARGIN_X * 2f;
            Column[] columns = new Column[COLUMN_RATIOS.length];
            float x = MARGIN_X;
            for (int i = 0; i < COLUMN_RATIOS.length; i++) {
                float w = width * COLUMN_RATIOS[i];
                columns[i] = new Column(x, w);
                x += w;
            }
            return new Layout(MARGIN_X, width, columns);
        }

        float textWidth(int index) {
            return Math.max(8f, columns[index].width - CELL_PAD_X * 2f);
        }
    }

    private record Column(float x, float width) {
    }

    private record PreparedRow(
            int number,
            boolean blank,
            String numberLabel,
            List<String> name,
            List<String> faculty,
            List<String> position,
            float height
    ) {
    }

    private record PagePlan(List<PreparedRow> rows, float headerHeight) {
    }

    private record MetaLine(String label, String value) {
    }

    public static final class Labels {
        public final String institution;
        public final String documentTitle;
        public final String product;
        public final String date;
        public final String place;
        public final String unit;
        public final String version;
        public final String identifier;
        public final String number;
        public final String name;
        public final String faculty;
        public final String position;
        public final String signature;
        public final String page;
        public final String of;

        private Labels(
                String institution,
                String documentTitle,
                String product,
                String date,
                String place,
                String unit,
                String version,
                String identifier,
                String number,
                String name,
                String faculty,
                String position,
                String signature,
                String page,
                String of
        ) {
            this.institution = institution;
            this.documentTitle = documentTitle;
            this.product = product;
            this.date = date;
            this.place = place;
            this.unit = unit;
            this.version = version;
            this.identifier = identifier;
            this.number = number;
            this.name = name;
            this.faculty = faculty;
            this.position = position;
            this.signature = signature;
            this.page = page;
            this.of = of;
        }

        public static Labels of(String language) {
            if (EventLanguage.isEnglish(language)) {
                return new Labels(
                        "Universidad San Sebastián",
                        "Attendance sheet",
                        "OPENSCIENCE Events",
                        "Date",
                        "Location",
                        "Organizing unit",
                        "Version",
                        "Identifier",
                        "No.",
                        "Full name",
                        "Faculty / Institution",
                        "Position",
                        "Signature",
                        "Page",
                        "of"
                );
            }
            return new Labels(
                    "Universidad San Sebastián",
                    "ACTA DE ASISTENCIA",
                    "OPENSCIENCE Events",
                    "Fecha",
                    "Lugar",
                    "Unidad organizadora",
                    "Versión",
                    "Identificador",
                    "N.º",
                    "Nombre completo",
                    "Facultad / Institución",
                    "Cargo",
                    "Firma",
                    "Página",
                    "de"
            );
        }
    }

    public static final class MinutesDocument {
        public String title;
        public String language;
        public String when;
        public String place;
        public String unit;
        public String identifier;
        public int version;
        public final List<Row> rows = new ArrayList<>();
    }

    public static final class Row {
        public int number;
        public String name;
        public String faculty;
        public String position;
        public String email;
        public String publicCode;
        public Long registrationId;
        public boolean blank;
    }
}
