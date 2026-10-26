package cl.uss.ca.scopus.importer;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScopusPublicationCsvParser {
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setIgnoreSurroundingSpaces(true)
            .build();

    public interface FilaListener {
        boolean onFila(ScopusPublicationCsvRow fila) throws Exception;
    }

    public void leer(Path archivo, FilaListener listener) throws Exception {
        try (InputStream in = omitirBomUtf8(Files.newInputStream(archivo));
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, CSV_FORMAT)) {
            Map<String, Integer> columnas = resolverColumnas(parser);
            exigirColumna(columnas, "title", "Title");
            exigirColumna(columnas, "eid", "EID");
            exigirColumna(columnas, "doi", "DOI");
            for (CSVRecord record : parser) {
                ScopusPublicationCsvRow fila = parsearFila(record, columnas);
                if (fila != null && !listener.onFila(fila)) {
                    break;
                }
            }
        }
    }

    ScopusPublicationCsvRow parsearFila(CSVRecord record, Map<String, Integer> columnas) {
        String title = texto(record, columnas, "title");
        String eid = texto(record, columnas, "eid");
        String doiOriginal = texto(record, columnas, "doi");
        if (esFilaVacia(title, eid, doiOriginal, texto(record, columnas, "authors"))
                || esFilaCopyright(title)) {
            return null;
        }

        List<String> warnings = new ArrayList<>();
        String yearCrudo = textoSinTrimInterno(record, columnas, "year");
        String citedByCrudo = textoSinTrimInterno(record, columnas, "citedby");
        Integer year = parsearEntero(yearCrudo, "Year", eid, doiOriginal, warnings);
        Integer citedBy = parsearEntero(citedByCrudo, "Cited by", eid, doiOriginal, warnings);

        String authorsRaw = textoConservado(record, columnas, "authors");
        String authorFullNamesRaw = textoConservado(record, columnas, "authorfullnames");
        String authorIdsRaw = textoConservado(record, columnas, "authorsid");
        String authorsWithAffiliationsRaw = textoConservado(record, columnas, "authorswithaffiliations");
        String affiliationsRaw = textoConservado(record, columnas, "affiliations");
        String authorKeywordsRaw = textoConservado(record, columnas, "authorkeywords");
        String indexKeywordsRaw = textoConservado(record, columnas, "indexkeywords");

        List<String> authors = splitLista(authorsRaw);
        List<String> authorFullNames = splitLista(authorFullNamesRaw);
        List<String> authorIds = splitLista(authorIdsRaw);
        List<String> authorsWithAffiliations = splitLista(authorsWithAffiliationsRaw);
        boolean aligned = authors.size() == authorFullNames.size()
                && authors.size() == authorIds.size()
                && authors.size() == authorsWithAffiliations.size();
        List<ScopusPublicationAuthorItem> authorItems = aligned
                ? construirAutores(authors, authorFullNames, authorIds, authorsWithAffiliations, warnings)
                : List.of();

        return new ScopusPublicationCsvRow(
                record.getRecordNumber(),
                eid,
                doiOriginal,
                normalizarDoi(doiOriginal),
                title,
                year,
                texto(record, columnas, "sourcetitle"),
                texto(record, columnas, "volume"),
                texto(record, columnas, "issue"),
                texto(record, columnas, "artno"),
                texto(record, columnas, "pagestart"),
                texto(record, columnas, "pageend"),
                citedBy,
                texto(record, columnas, "link"),
                texto(record, columnas, "issn"),
                texto(record, columnas, "isbn"),
                texto(record, columnas, "coden"),
                texto(record, columnas, "pubmedid"),
                texto(record, columnas, "documenttype"),
                texto(record, columnas, "publicationstage"),
                texto(record, columnas, "openaccess"),
                texto(record, columnas, "source"),
                authorsRaw,
                authorFullNamesRaw,
                authorIdsRaw,
                affiliationsRaw,
                authorsWithAffiliationsRaw,
                authorKeywordsRaw,
                indexKeywordsRaw,
                textoConservado(record, columnas, "fundingdetails"),
                textoConservado(record, columnas, "fundingtexts"),
                textoConservado(record, columnas, "abstract"),
                textoConservado(record, columnas, "correspondenceaddress"),
                textoConservado(record, columnas, "editors"),
                authors,
                authorFullNames,
                authorIds,
                authorsWithAffiliations,
                splitLista(affiliationsRaw),
                splitLista(authorKeywordsRaw),
                splitLista(indexKeywordsRaw),
                authorItems,
                aligned,
                List.copyOf(warnings)
        );
    }

    static List<String> splitLista(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] partes = raw.split(";", -1);
        List<String> valores = new ArrayList<>();
        for (String parte : partes) {
            String valor = blancoANull(parte);
            if (valor != null) {
                valores.add(valor);
            }
        }
        return List.copyOf(valores);
    }

    static String normalizarDoi(String doi) {
        String valor = blancoANull(doi);
        return valor == null ? null : valor.toLowerCase(Locale.ROOT);
    }

    private static List<ScopusPublicationAuthorItem> construirAutores(
            List<String> authors,
            List<String> fullNames,
            List<String> ids,
            List<String> withAffiliations,
            List<String> warnings
    ) {
        List<ScopusPublicationAuthorItem> items = new ArrayList<>(authors.size());
        for (int i = 0; i < authors.size(); i++) {
            String shortName = authors.get(i);
            String fullRaw = fullNames.get(i);
            String authId = ids.get(i);
            NombreCompletoParseado parsed = parsearNombreCompleto(fullRaw);
            boolean mismatch = parsed.authId() != null
                    && authId != null
                    && !parsed.authId().equals(authId);
            if (mismatch) {
                warnings.add("auth_id de Author(s) ID=" + authId
                        + " no coincide con ID en Author full names=" + parsed.authId()
                        + " posición=" + (i + 1));
            }
            items.add(new ScopusPublicationAuthorItem(
                    i + 1,
                    authId,
                    shortName,
                    parsed.nombre() != null ? parsed.nombre() : fullRaw,
                    withAffiliations.get(i),
                    parsed.authId(),
                    mismatch
            ));
        }
        return List.copyOf(items);
    }

    static NombreCompletoParseado parsearNombreCompleto(String value) {
        String texto = blancoANull(value);
        if (texto == null) {
            return new NombreCompletoParseado(null, null);
        }
        int open = texto.lastIndexOf('(');
        int close = texto.lastIndexOf(')');
        if (open >= 0 && close > open) {
            String maybeId = texto.substring(open + 1, close).trim();
            if (maybeId.matches("\\d+")) {
                String nombre = blancoANull(texto.substring(0, open));
                return new NombreCompletoParseado(nombre != null ? nombre : texto, maybeId);
            }
        }
        return new NombreCompletoParseado(texto, null);
    }

    private static Integer parsearEntero(String crudo, String campo, String eid, String doi,
                                         List<String> warnings) {
        String texto = blancoANull(crudo);
        if (texto == null) {
            return null;
        }
        String normalizado = texto.replace(" ", "").replace("\u00a0", "");
        try {
            return Integer.parseInt(normalizado);
        } catch (NumberFormatException ex) {
            warnings.add(campo + " inválido: " + texto
                    + " EID=" + nuloATexto(eid) + " DOI=" + nuloATexto(doi));
            return null;
        }
    }

    private static boolean esFilaVacia(String title, String eid, String doi, String authors) {
        return title == null && eid == null && doi == null && authors == null;
    }

    private static boolean esFilaCopyright(String title) {
        if (title == null) {
            return false;
        }
        String nombre = title.trim();
        if (nombre.startsWith("©") || nombre.startsWith("\u00a9")) {
            return true;
        }
        String lower = nombre.toLowerCase(Locale.ROOT);
        return lower.contains("elsevier b.v.") || lower.contains("all rights reserved");
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

    private static String textoConservado(CSVRecord record, Map<String, Integer> columnas, String clave) {
        String value = textoSinTrimInterno(record, columnas, clave);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : value.trim();
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

    static String blancoANull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    record NombreCompletoParseado(String nombre, String authId) {
    }
}
