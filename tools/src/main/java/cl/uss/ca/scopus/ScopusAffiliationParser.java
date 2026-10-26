package cl.uss.ca.scopus;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScopusAffiliationParser {
    static final Pattern FILENAME_PATTERN =
            Pattern.compile("^Scopus-Author-(.+)-([0-9]+)\\.csv$", Pattern.CASE_INSENSITIVE);

    private ScopusAffiliationParser() {
    }

    public static ScopusAffiliation parseAffiliationFromFilename(Path csvFile) {
        String nombreRecibido = nombreArchivo(csvFile);
        Matcher matcher = FILENAME_PATTERN.matcher(nombreRecibido);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(mensajeNombreInvalido(nombreRecibido));
        }
        String affiliationName = matcher.group(1) == null ? "" : matcher.group(1).trim();
        String affiliationId = matcher.group(2) == null ? "" : matcher.group(2).trim();
        if (affiliationName.isEmpty() || affiliationId.isEmpty()) {
            throw new IllegalArgumentException(mensajeNombreInvalido(nombreRecibido));
        }
        return new ScopusAffiliation(affiliationName, affiliationId);
    }

    private static String nombreArchivo(Path csvFile) {
        if (csvFile == null) {
            return "";
        }
        Path fileName = csvFile.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    static String mensajeNombreInvalido(String nombreRecibido) {
        return """
                No fue posible determinar la afiliación desde el nombre del archivo.

                Nombre recibido:
                %s

                Formato esperado:
                Scopus-Author-[NOMBRE AFILIACION]-[AFFILIATION_ID].csv
                """.formatted(nombreRecibido == null || nombreRecibido.isBlank()
                ? "(vacío)"
                : nombreRecibido);
    }
}
