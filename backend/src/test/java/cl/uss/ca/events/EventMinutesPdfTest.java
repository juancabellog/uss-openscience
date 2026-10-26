package cl.uss.ca.events;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class EventMinutesPdfTest {
    private EventMinutesPdfTest() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void run(String[] args) throws Exception {
        testWrapKeepsWordsInsideColumn();
        testLongTokenBreaksWithoutLosingCharacters();
        EventMinutesPdf.MinutesDocument basic = document("Seminario Ciencia Abierta", 1, 10);
        byte[] spanish = EventMinutesPdf.render(basic, EventLanguage.ES);
        String spanishText = decode(spanish);
        assert spanishText.startsWith("%PDF-1.4") : "PDF header";
        assert spanish.length > 800 : "non-empty PDF";
        assert spanishText.contains("ACTA DE ASISTENCIA") : "spanish title";
        assert spanishText.contains("Nombre completo") : "spanish name column";
        assert spanishText.contains("Facultad / Instituci") : "spanish faculty column";
        assert spanishText.contains("Firma") : "spanish signature column";
        assert spanishText.contains("/MediaBox [0 0 842.00 595.00]") : "landscape A4";
        assert !spanishText.contains("Los resultados propuestos por IA") : "no AI disclaimer";
        assert !spanishText.contains("AI-proposed results") : "no English AI disclaimer";
        assert spanishText.contains("P") && spanishText.contains("gina") : "page label";

        byte[] english = EventMinutesPdf.render(basic, EventLanguage.EN);
        String englishText = decode(english);
        assert englishText.contains("Attendance sheet") : "english title";
        assert englishText.contains("Faculty / Institution") : "english faculty column";
        assert englishText.contains("Full name") : "english name column";
        assert englishText.contains("Signature") : "english signature column";
        assert englishText.contains("Page") : "english page label";

        Path outDir = Path.of("target/minutes-review");
        Files.createDirectories(outDir);
        write(outDir.resolve("01-one-person-10-blank-es.pdf"), EventMinutesPdf.render(document(
                "Seminario Ciencia Abierta", 1, 10), EventLanguage.ES));
        write(outDir.resolve("02-twenty-participants-es.pdf"), EventMinutesPdf.render(many(
                "Encuentro de investigacion", 20, 10, false), EventLanguage.ES));
        write(outDir.resolve("03-hundred-participants-es.pdf"), EventMinutesPdf.render(many(
                "Congreso OPENSCIENCE", 100, 10, false), EventLanguage.ES));
        write(outDir.resolve("04-long-fields-es.pdf"), EventMinutesPdf.render(stress(EventLanguage.ES), EventLanguage.ES));
        write(outDir.resolve("05-accents-es.pdf"), EventMinutesPdf.render(accents(), EventLanguage.ES));
        write(outDir.resolve("06-long-fields-en.pdf"), EventMinutesPdf.render(stress(EventLanguage.EN), EventLanguage.EN));
        write(outDir.resolve("07-zero-extra-rows-es.pdf"), EventMinutesPdf.render(document(
                "Taller de datos", 1, 0), EventLanguage.ES));
        write(outDir.resolve("08-twenty-extra-rows-es.pdf"), EventMinutesPdf.render(document(
                "Jornada de vinculacion", 1, 20), EventLanguage.ES));

        String longPdf = decode(Files.readAllBytes(outDir.resolve("04-long-fields-es.pdf")));
        assert longPdf.contains("Maria Fernanda") : "long name is not truncated";
        assert longPdf.contains("Valenzuela") : "long name second line survives";
        assert longPdf.contains("Ciencias de la") || longPdf.contains("Rehabilitaci") : "long faculty wraps";
        assert count(longPdf, " re S") > 20 : "table grid is drawn";

        String accentPdf = decode(Files.readAllBytes(outDir.resolve("05-accents-es.pdf")));
        assert accentPdf.indexOf('\u00f1') >= 0 : "ñ encoded as WinAnsi";
        assert accentPdf.indexOf('\u00d1') >= 0 : "Ñ encoded as WinAnsi";
        assert accentPdf.indexOf('\u00e9') >= 0 : "é encoded as WinAnsi";

        System.out.println("EventMinutesPdfTest OK " + outDir.toAbsolutePath());
    }

    private static void testWrapKeepsWordsInsideColumn() {
        float width = 120f;
        List<String> lines = PdfType1Metrics.wrap(
                "Facultad de Medicina y Ciencia de la Universidad San Sebastian",
                width,
                8.5f,
                false
        );
        assert lines.size() > 1 : "long faculty wraps";
        for (String line : lines) {
            assert PdfType1Metrics.width(line, 8.5f, false) <= width + 0.05f : "line exceeds column: " + line;
        }
    }

    private static void testLongTokenBreaksWithoutLosingCharacters() {
        String token = "SupercalifragilisticoespialidosoInvestigacionAvanzada";
        List<String> lines = PdfType1Metrics.wrap(token, 70f, 8.5f, false);
        assert lines.size() > 1 : "oversized token must break";
        assert String.join("", lines).equals(token) : "character-break must keep every letter";
    }

    private static EventMinutesPdf.MinutesDocument document(String title, int people, int blanks) {
        EventMinutesPdf.MinutesDocument document = new EventMinutesPdf.MinutesDocument();
        document.title = title;
        document.when = "20-10-2026 09:00 a 11:00";
        document.place = "Campus Bellavista";
        document.unit = "Vicerrectoria de Investigacion y Doctorados";
        document.identifier = "evento-test";
        document.version = 2;
        int number = 1;
        for (int i = 0; i < people; i++) {
            document.rows.add(person(number++, "Ana Perez", "Medicina", "Profesora"));
        }
        addBlanks(document, number, blanks);
        return document;
    }

    private static EventMinutesPdf.MinutesDocument many(String title, int people, int blanks, boolean longNames) {
        EventMinutesPdf.MinutesDocument document = document(title, 0, 0);
        document.rows.clear();
        int number = 1;
        for (int i = 0; i < people; i++) {
            String name = longNames
                    ? "Participante " + (i + 1) + " con nombre compuesto extendido"
                    : "Participante " + (i + 1) + " Gomez";
            document.rows.add(person(number++, name, "Facultad de Ingenieria y Tecnologia", "Investigador"));
        }
        addBlanks(document, number, blanks);
        return document;
    }

    private static EventMinutesPdf.MinutesDocument stress(String language) {
        EventMinutesPdf.MinutesDocument document = new EventMinutesPdf.MinutesDocument();
        boolean en = EventLanguage.isEnglish(language);
        document.title = en
                ? "International seminar on open research infrastructures and scholarly communication"
                : "Seminario internacional sobre infraestructuras de investigacion abierta y comunicacion academica";
        document.when = en ? "20-10-2026 09:00 to 18:30" : "20-10-2026 09:00 a 18:30";
        document.place = "Aula Magna Justicia Espada, Campus Los Leones, Santiago de Chile";
        document.unit = "Vicerrectoria de Investigacion y Doctorados / Direccion de Ciencia Abierta";
        document.identifier = "evento-test";
        document.version = 2;
        document.rows.add(person(
                1,
                "Maria Fernanda de los Angeles Contreras Valenzuela Sepulveda",
                "Facultad de Ciencias de la Rehabilitacion y Calidad de Vida",
                "Directora de Investigacion, Innovacion y Transferencia Tecnologica"
        ));
        document.rows.add(person(
                2,
                "Juan Pablo Rodriguez",
                "Facultad de Economia y Negocios",
                "Profesor titular"
        ));
        addBlanks(document, 3, 10);
        return document;
    }

    private static EventMinutesPdf.MinutesDocument accents() {
        EventMinutesPdf.MinutesDocument document = document("Jornada Ñuble y Bio-Bío", 0, 0);
        document.rows.clear();
        document.rows.add(person(1, "José Muñoz Ñancucheo", "Facultad de Educación", "Profesor asociado"));
        document.rows.add(person(2, "María Inés Peña", "Ciencias de la Salud", "Investigadora"));
        addBlanks(document, 3, 10);
        return document;
    }

    private static EventMinutesPdf.Row person(int number, String name, String faculty, String position) {
        EventMinutesPdf.Row row = new EventMinutesPdf.Row();
        row.number = number;
        row.name = name;
        row.faculty = faculty;
        row.position = position;
        row.publicCode = "EV1-" + number + "-AA";
        row.registrationId = (long) number;
        return row;
    }

    private static void addBlanks(EventMinutesPdf.MinutesDocument document, int start, int count) {
        int number = start;
        for (int i = 0; i < count; i++) {
            EventMinutesPdf.Row blank = new EventMinutesPdf.Row();
            blank.number = number++;
            blank.blank = true;
            document.rows.add(blank);
        }
    }

    private static void write(Path path, byte[] pdf) throws Exception {
        Files.write(path, pdf);
    }

    private static String decode(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    private static int count(String text, String token) {
        int n = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(token, from);
            if (at < 0) {
                return n;
            }
            n++;
            from = at + token.length();
        }
    }
}
