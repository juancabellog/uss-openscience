package cl.uss.ca.scopus;

import cl.uss.ca.openalex.OpenAlexService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScopusAuthorCsvParser {
    static final int AUTHOR_NAME_MAX_LENGTH = 255;
    static final int AUTH_ID_MAX_LENGTH = 30;
    static final int ORCID_MAX_LENGTH = 19;
    static final int AREA_MAX_LENGTH = 255;

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setIgnoreSurroundingSpaces(true)
            .build();

    public interface FilaListener {
        void onFilaLeida();

        void onFilaValida(ScopusAuthorCsvRecord registro);

        void onFilaIgnorada(String authorName, String motivo);
    }

    public void leer(Path archivo, FilaListener listener) throws IOException {
        try (InputStream in = omitirBomUtf8(Files.newInputStream(archivo));
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, CSV_FORMAT)) {
            Map<String, Integer> columnas = resolverColumnas(parser);
            exigirColumna(columnas, "authorname", "Author Name");
            for (CSVRecord record : parser) {
                listener.onFilaLeida();
                FilaParseada fila = parsearFila(record, columnas);
                if (fila.registro() != null) {
                    listener.onFilaValida(fila.registro());
                } else {
                    listener.onFilaIgnorada(fila.authorName(), fila.motivo());
                }
            }
        }
    }

    FilaParseada parsearFila(CSVRecord record, Map<String, Integer> columnas) {
        String authorName = texto(record, columnas, "authorname");
        String authId = normalizarAuthId(texto(record, columnas, "authid"));
        String documentosCrudo = texto(record, columnas, "numberofdocuments");
        String subjectArea = textoSinTrimInterno(record, columnas, "subjectarea");
        String orcid = normalizarOrcid(texto(record, columnas, "orcid"));
        List<String> areas = parsearAreas(subjectArea);
        if (authorName == null) {
            return FilaParseada.ignorada("", "sin Author Name");
        }
        if (esFilaCopyright(authorName)) {
            return FilaParseada.ignorada(authorName, "linea de copyright Elsevier");
        }
        if (!esFilaValida(authorName, authId, documentosCrudo, subjectArea, orcid, areas)) {
            return FilaParseada.ignorada(authorName, "fila incompleta");
        }
        Integer numberDocuments = parsearNumberDocuments(documentosCrudo, authorName, authId);
        ScopusAuthorCsvRecord registro = new ScopusAuthorCsvRecord(
                truncar(authorName, AUTHOR_NAME_MAX_LENGTH),
                truncar(authId, AUTH_ID_MAX_LENGTH),
                numberDocuments,
                truncar(orcid, ORCID_MAX_LENGTH),
                areas
        );
        return FilaParseada.valida(registro);
    }

    boolean esFilaValida(String authorName, String authId, String documentosCrudo,
                         String subjectArea, String orcid, List<String> areas) {
        if (authorName == null || authorName.isBlank()) {
            return false;
        }
        if (esFilaCopyright(authorName)) {
            return false;
        }
        boolean tieneAuthId = authId != null;
        boolean tieneDocumentos = documentosCrudo != null && !documentosCrudo.isBlank();
        boolean tieneAreas = (subjectArea != null && !subjectArea.isBlank())
                || (areas != null && !areas.isEmpty());
        boolean tieneOrcid = orcid != null;
        return tieneAuthId || tieneDocumentos || tieneAreas || tieneOrcid;
    }

    boolean esFilaCopyright(String authorName) {
        if (authorName == null) {
            return false;
        }
        String nombre = authorName.trim();
        if (nombre.startsWith("©") || nombre.startsWith("\u00a9")) {
            return true;
        }
        String lower = nombre.toLowerCase(Locale.ROOT);
        return lower.contains("elsevier b.v.") || lower.contains("all rights reserved");
    }

    String normalizarAuthId(String value) {
        return blancoANull(value);
    }

    String normalizarOrcid(String value) {
        return OpenAlexService.normalizarOrcid(value);
    }

    Integer parsearNumberDocuments(String value, String authorName, String authId) {
        String texto = blancoANull(value);
        if (texto == null) {
            return null;
        }
        String normalizado = texto.replace(" ", "").replace("\u00a0", "");
        try {
            int numero = Integer.parseInt(normalizado);
            if (numero < 0) {
                System.err.println("WARN Number of Documents negativo. authorName=" + authorName
                        + " authId=" + nuloATexto(authId) + " valor=" + value);
                return null;
            }
            return numero;
        } catch (NumberFormatException ex) {
            System.err.println("WARN Number of Documents no numérico. authorName=" + authorName
                    + " authId=" + nuloATexto(authId) + " valor=" + value);
            return null;
        }
    }

    List<String> parsearAreas(String subjectArea) {
        if (subjectArea == null || subjectArea.isBlank()) {
            return List.of();
        }
        String[] lineas = subjectArea.split("\\r\\n|\\n|\\r", -1);
        Map<String, String> unicas = new LinkedHashMap<>();
        for (String linea : lineas) {
            String area = blancoANull(linea);
            if (area == null) {
                continue;
            }
            area = truncar(area, AREA_MAX_LENGTH);
            unicas.putIfAbsent(area.toLowerCase(Locale.ROOT), area);
        }
        return List.copyOf(unicas.values());
    }

    record FilaParseada(ScopusAuthorCsvRecord registro, String authorName, String motivo) {
        static FilaParseada valida(ScopusAuthorCsvRecord registro) {
            return new FilaParseada(registro, registro.authorName(), null);
        }

        static FilaParseada ignorada(String authorName, String motivo) {
            return new FilaParseada(null, authorName, motivo);
        }
    }

    private static Map<String, Integer> resolverColumnas(CSVParser parser) {
        Map<String, Integer> columnas = new HashMap<>();
        Map<String, Integer> headers = parser.getHeaderMap();
        if (headers == null) {
            return columnas;
        }
        for (Map.Entry<String, Integer> entry : headers.entrySet()) {
            String clave = claveEncabezado(entry.getKey());
            if (!clave.isEmpty() && !columnas.containsKey(clave)) {
                columnas.put(clave, entry.getValue());
            }
        }
        return columnas;
    }

    private static void exigirColumna(Map<String, Integer> columnas, String clave, String nombre) {
        if (!columnas.containsKey(clave)) {
            throw new IllegalStateException("El CSV no contiene la columna '" + nombre + "'.");
        }
    }

    private static String texto(CSVRecord record, Map<String, Integer> columnas, String clave) {
        return blancoANull(textoSinTrimInterno(record, columnas, clave));
    }

    private static String textoSinTrimInterno(CSVRecord record, Map<String, Integer> columnas, String clave) {
        Integer indice = columnas.get(clave);
        if (indice == null || indice < 0 || indice >= record.size()) {
            return "";
        }
        String value = record.get(indice);
        return value == null ? "" : value;
    }

    static String claveEncabezado(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String t = value.replace("\uFEFF", "").toLowerCase(Locale.ROOT).trim();
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        t = t.replaceAll("[^a-z0-9]", "");
        return t;
    }

    private static String blancoANull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncar(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String nuloATexto(String value) {
        return value == null ? "" : value;
    }

    private static InputStream omitirBomUtf8(InputStream in) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(in, 3);
        byte[] bom = new byte[3];
        int leidos = pushback.read(bom, 0, 3);
        if (leidos == 3
                && bom[0] == (byte) 0xEF
                && bom[1] == (byte) 0xBB
                && bom[2] == (byte) 0xBF) {
            return pushback;
        }
        if (leidos > 0) {
            pushback.unread(bom, 0, leidos);
        }
        return pushback;
    }
}
