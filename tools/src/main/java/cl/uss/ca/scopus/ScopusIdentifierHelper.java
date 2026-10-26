package cl.uss.ca.scopus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScopusIdentifierHelper {
    static final String SCOPUS_URI = "/dk/atira/pure/person/personsources/scopusauthor";
    static final String TIPO_CLASSIFIED = "ClassifiedId";
    static final String TIPO_PRIMARY = "PrimaryId";
    static final String TERMINO_SCOPUS = "Scopus ID";

    private ScopusIdentifierHelper() {
    }

    public static boolean esScopusIdentifier(JsonNode identifier) {
        if (identifier == null || identifier.isNull() || !identifier.isObject()) {
            return false;
        }
        if (!TIPO_CLASSIFIED.equals(texto(identifier, "typeDiscriminator"))) {
            return false;
        }
        JsonNode type = identifier.get("type");
        if (type == null || type.isNull() || !type.isObject()) {
            return false;
        }
        String uri = texto(type, "uri");
        if (esUriScopus(uri)) {
            return true;
        }
        JsonNode term = type.get("term");
        if (term == null || term.isNull() || !term.isObject()) {
            return false;
        }
        return TERMINO_SCOPUS.equalsIgnoreCase(texto(term, "es_CL"))
                || TERMINO_SCOPUS.equalsIgnoreCase(texto(term, "en_US"));
    }

    public static boolean esUriScopus(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String normalizada = uri.trim();
        if (normalizada.endsWith("/")) {
            normalizada = normalizada.substring(0, normalizada.length() - 1);
        }
        return SCOPUS_URI.equalsIgnoreCase(normalizada)
                || normalizada.toLowerCase().endsWith("/scopusauthor");
    }

    public static String scopusIdDe(JsonNode identifier) {
        if (!esScopusIdentifier(identifier)) {
            return null;
        }
        return texto(identifier, "id");
    }

    public static String identifierPureIdDe(JsonNode identifier) {
        if (identifier == null || identifier.isNull()) {
            return "";
        }
        JsonNode pureId = identifier.get("pureId");
        if (pureId == null || pureId.isNull()) {
            return "";
        }
        return pureId.asText();
    }

    public static boolean mismoScopusId(String esperado, JsonNode identifier) {
        String actual = scopusIdDe(identifier);
        return actual != null && actual.equals(esperado == null ? null : esperado.trim());
    }

    public static List<JsonNode> identifiersDe(JsonNode personJson) {
        List<JsonNode> resultado = new ArrayList<>();
        JsonNode identifiers = personJson == null ? null : personJson.get("identifiers");
        if (identifiers == null || !identifiers.isArray()) {
            return resultado;
        }
        for (JsonNode identifier : identifiers) {
            resultado.add(identifier);
        }
        return resultado;
    }

    public static Set<String> obtenerScopusIdsPure(JsonNode personJson) {
        return new LinkedHashSet<>(agruparScopusPorId(personJson).keySet());
    }

    /**
     * Agrupa identifiers Scopus por identifier.id, en el mismo orden de identifiers[].
     * Cada lista conserva el orden de aparición en Pure.
     */
    public static Map<String, List<JsonNode>> agruparScopusPorId(JsonNode personJson) {
        Map<String, List<JsonNode>> scopusPorId = new LinkedHashMap<>();
        for (JsonNode identifier : identifiersDe(personJson)) {
            String scopusId = scopusIdDe(identifier);
            if (scopusId != null) {
                scopusPorId.computeIfAbsent(scopusId, key -> new ArrayList<>()).add(identifier);
            }
        }
        return scopusPorId;
    }

    public static boolean esPrimaryId(JsonNode identifier) {
        return identifier != null
                && identifier.isObject()
                && TIPO_PRIMARY.equals(texto(identifier, "typeDiscriminator"));
    }

    public static boolean tienePrimaryId(JsonNode personJson) {
        for (JsonNode identifier : identifiersDe(personJson)) {
            if (esPrimaryId(identifier)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> firmasIdentifiersNoScopus(JsonNode personJson) {
        List<String> firmas = new ArrayList<>();
        for (JsonNode identifier : identifiersDe(personJson)) {
            if (!esScopusIdentifier(identifier)) {
                firmas.add(identifier.toString());
            }
        }
        return firmas;
    }

    public static List<JsonNode> buscarIdentifiersScopus(JsonNode personJson, String scopusId) {
        List<JsonNode> coincidencias = new ArrayList<>();
        for (JsonNode identifier : identifiersDe(personJson)) {
            if (mismoScopusId(scopusId, identifier)) {
                coincidencias.add(identifier);
            }
        }
        return coincidencias;
    }

    public static JsonNode buscarIdentifierScopus(JsonNode personJson, String scopusId) {
        List<JsonNode> coincidencias = buscarIdentifiersScopus(personJson, scopusId);
        return coincidencias.size() == 1 ? coincidencias.get(0) : null;
    }

    public static JsonNode seleccionarSegundaOcurrencia(List<JsonNode> coincidencias) {
        if (coincidencias == null || coincidencias.size() < 2) {
            return null;
        }
        return coincidencias.get(1);
    }

    public static int indiceSegundaOcurrencia(JsonNode personJson, String scopusId) {
        int coincidencias = 0;
        List<JsonNode> identifiers = identifiersDe(personJson);
        for (int i = 0; i < identifiers.size(); i++) {
            if (mismoScopusId(scopusId, identifiers.get(i))) {
                if (coincidencias == 1) {
                    return i;
                }
                coincidencias++;
            }
        }
        return -1;
    }

    /**
     * Elimina ocurrencias del MISMO Scopus ID, conservando solo la segunda.
     * Nunca elimina un identifier Scopus con otro identifier.id, ni PrimaryId,
     * ni ClassifiedId que no sean Scopus.
     */
    public static int eliminarTodasExceptoSegunda(ObjectNode personJson, String scopusId) {
        JsonNode identifiersNode = personJson.get("identifiers");
        if (!(identifiersNode instanceof ArrayNode identifiers)) {
            return 0;
        }
        int indiceConservar = indiceSegundaOcurrencia(personJson, scopusId);
        if (indiceConservar < 0) {
            return 0;
        }
        int eliminados = 0;
        for (int i = identifiers.size() - 1; i >= 0; i--) {
            JsonNode actual = identifiers.get(i);
            if (i != indiceConservar
                    && esScopusIdentifier(actual)
                    && mismoScopusId(scopusId, actual)) {
                identifiers.remove(i);
                eliminados++;
            }
        }
        return eliminados;
    }

    public static int eliminarIdentifierScopus(ObjectNode personJson, String scopusId) {
        JsonNode identifiersNode = personJson.get("identifiers");
        if (!(identifiersNode instanceof ArrayNode identifiers)) {
            return 0;
        }
        int eliminados = 0;
        for (int i = identifiers.size() - 1; i >= 0; i--) {
            if (mismoScopusId(scopusId, identifiers.get(i))) {
                identifiers.remove(i);
                eliminados++;
            }
        }
        return eliminados;
    }

    public static String texto(JsonNode node, String campo) {
        if (node == null || campo == null) {
            return null;
        }
        JsonNode valor = node.get(campo);
        if (valor == null || valor.isNull()) {
            return null;
        }
        String texto = valor.asText();
        if (texto == null) {
            return null;
        }
        String trimmed = texto.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
