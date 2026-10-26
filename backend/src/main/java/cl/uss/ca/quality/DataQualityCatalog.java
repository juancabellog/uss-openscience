package cl.uss.ca.quality;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catálogo PURE-001..PURE-022, CROSSREF-001..010, OPENALEX-001..004 y EXT-01..EXT-12.
 * CROSSREF-003/004/005/006 quedan superadas por EXT-01/03/04/12 (N fuentes).
 */
public final class DataQualityCatalog {
    public static final String SOURCE_PURE = "PURE";
    public static final String SOURCE_CROSSREF = "CROSSREF";
    public static final String SOURCE_OPENALEX = "OPENALEX";
    public static final String SOURCE_EXTERNAL = "EXTERNAL";
    public static final String MODE_SAFE = "SAFE_SUGGESTION";
    public static final String MODE_MANUAL = "MANUAL_REVIEW";
    public static final String MODE_VIEW = "VIEW_ONLY";

    public static final String CAT_IDENTIFICATION = "IDENTIFICATION";
    public static final String CAT_COMPLETENESS = "COMPLETENESS";
    public static final String CAT_CONSISTENCY = "CONSISTENCY";
    public static final String CAT_TEXT = "TEXT_QUALITY";
    public static final String CAT_IDENTITY = "IDENTITY";
    public static final String CAT_BIBLIOGRAPHY = "BIBLIOGRAPHY";
    public static final String CAT_ACCESS = "ACCESS_WORKFLOW";
    public static final String CAT_EXTERNAL = "EXTERNAL_CONSISTENCY";

    public static final List<RuleDef> RULES = List.of(
            rule("PURE-001", "RESEARCH_OUTPUT", "uuid", "UUID duplicado",
                    "Detectar UUID duplicado en publicaciones.",
                    "El UUID de una publicación no debe repetirse.",
                    "UUID duplicado en publicaciones.",
                    null, CAT_IDENTIFICATION, MODE_VIEW),
            rule("PURE-002", "RESEARCH_OUTPUT", "title", "Calidad del título",
                    "Título nulo/vacío y anomalías de texto.",
                    "El título no puede ser nulo ni vacío. Detectar espacios repetidos y anomalías de texto.",
                    "Título vacío o con anomalías de texto.",
                    "Normalización de espacios (trim y colapso de espacios repetidos). No eliminar símbolos científicos.",
                    CAT_TEXT, MODE_SAFE),
            rule("PURE-003", "RESEARCH_OUTPUT", "submission_year", "Año PURE",
                    "Año entre 1900 y el año actual.",
                    "año_pure debe existir y estar entre 1900 y el año actual.",
                    "Año PURE vacío o fuera de rango.",
                    null, CAT_COMPLETENESS, MODE_MANUAL),
            rule("PURE-004", "RESEARCH_OUTPUT", "estado_actual", "Estado actual",
                    "estado_actual no nulo/vacío. Categorías válidas de PURE.",
                    "Debe existir un estado actual con una categoría reconocida.",
                    "Estado actual vacío o no reconocido.",
                    null, CAT_COMPLETENESS, MODE_MANUAL),
            rule("PURE-005", "RESEARCH_OUTPUT", "año_publicada", "Año de publicación",
                    "Cuando estado_actual = Publicada, debe existir año válido entre 1900 y el año actual.",
                    "Si el estado actual es Publicada, año_publicada debe estar entre 1900 y el año actual.",
                    "Publicación en estado Publicada sin año válido.",
                    null, CAT_COMPLETENESS, MODE_MANUAL),
            rule("PURE-006", "RESEARCH_OUTPUT", "año_publicada", "Año publicada vs aceptada",
                    "año_publicada no puede ser anterior a año_aceptada.",
                    "El año de publicación no puede ser anterior al año de aceptación.",
                    "El año de publicación es anterior al año de aceptación.",
                    null, CAT_CONSISTENCY, MODE_MANUAL),
            rule("PURE-007", "RESEARCH_OUTPUT", "año_publicada_electronica", "Año electrónica vs impresa",
                    "año_publicada_electronica no debe ser posterior a año_publicada.",
                    "El año de publicación electrónica no debe ser posterior al año de publicación.",
                    "El año electrónico es posterior al año de publicación.",
                    null, CAT_CONSISTENCY, MODE_MANUAL),
            rule("PURE-008", "RESEARCH_OUTPUT", "journal_uuid", "Journal UUID sin revista",
                    "Si journal_uuid existe, la revista debe existir.",
                    "Un journal_uuid informado debe corresponder a una revista presente en la réplica.",
                    "No se pudo asociar journal_uuid.",
                    null, CAT_BIBLIOGRAPHY, MODE_VIEW),
            rule("PURE-009", "RESEARCH_OUTPUT", "journal_uuid", "Revista sin UUID",
                    "Si revista existe, journal_uuid debe existir.",
                    "Una revista asociada debe tener journal_uuid.",
                    "Revista asociada sin journal_uuid.",
                    null, CAT_BIBLIOGRAPHY, MODE_VIEW),
            rule("PURE-010", "RESEARCH_OUTPUT", "journal", "Artículo sin revista",
                    "Si tipo = Artículo, debe existir revista, salvo tipos del modelo real que no la requieren.",
                    "Los artículos (ContributionToJournal / Artículo) deben tener revista asociada.",
                    "Artículo sin revista.",
                    null, CAT_BIBLIOGRAPHY, MODE_VIEW),
            rule("PURE-011", "RESEARCH_OUTPUT", "author_name", "Nombres de autores",
                    "Calidad de first_name / last_name de autores: espacios, HTML, mojibake, artefactos y truncamiento.",
                    "Detectar espacios dobles, entidades HTML, mojibake, artefactos de código/scraping y truncamiento.",
                    "Nombre de autor con anomalía de texto.",
                    "Normalización segura solo de espacios y HTML inequívocamente decodificable. Mojibake, artefactos y truncamientos: revisión manual.",
                    CAT_IDENTITY, MODE_SAFE),
            rule("PURE-012", "RESEARCH_OUTPUT", "authors", "Autores duplicados",
                    "Autores duplicados dentro de una misma publicación.",
                    "No debe haber autores duplicados exactos en la misma publicación.",
                    "Autores duplicados en la publicación.",
                    "Deduplicar automáticamente SOLO cuando la duplicidad sea EXACTA. Casos ambiguos: revisión manual.",
                    CAT_IDENTITY, MODE_SAFE),
            rule("PURE-013", "PERSON", "orcid", "ORCID de autores",
                    "Si existe ORCID, debe cumplir el formato esperado.",
                    "ORCID informado debe tener formato válido. No corregir por aproximación.",
                    "ORCID con formato inválido.",
                    null, CAT_IDENTITY, MODE_MANUAL),
            rule("PURE-014", "PERSON", "scopus_author_id", "Múltiples Scopus Author ID",
                    "Múltiples Scopus ID distintos son señal de revisión de identidad.",
                    "Una persona no debería tener múltiples Scopus Author ID distintos sin revisión.",
                    "Revisión de identidad: múltiples Scopus Author ID distintos.",
                    "NO eliminar ni fusionar automáticamente.",
                    CAT_IDENTITY, MODE_VIEW),
            rule("PURE-015", "RESEARCH_OUTPUT", "contributor_name", "Nombres de contribuidores",
                    "Validaciones básicas de texto y duplicidad de contribuidores.",
                    "Detectar anomalías de texto y duplicados exactos en contribuidores que no son autores.",
                    "Nombre de contribuidor con anomalía o duplicado.",
                    "Normalización segura de espacios y duplicados exactos.",
                    CAT_TEXT, MODE_SAFE),
            rule("PURE-016", "RESEARCH_OUTPUT", "funding_org_uuid", "Financiamiento",
                    "Si funding_org_uuid existe, debe existir sigla/nombre utilizable.",
                    "Resolver únicamente mediante el mismo UUID o fuente autorizada.",
                    "Financiamiento con UUID y sin sigla/nombre utilizable.",
                    null, CAT_COMPLETENESS, MODE_VIEW),
            rule("PURE-017", "RESEARCH_OUTPUT", "doi", "Formato de DOI",
                    "DOI debe cumplir formato válido.",
                    "Un DOI informado debe tener formato válido. No reemplazar por coincidencia probabilística.",
                    "DOI con formato inválido.",
                    null, CAT_BIBLIOGRAPHY, MODE_MANUAL),
            rule("PURE-018", "RESEARCH_OUTPUT", "abstract", "Calidad del resumen",
                    "Detectar HTML, entidades sin decodificar, texto corrupto, extremadamente corto o contenido ajeno.",
                    "El resumen no debe contener HTML crudo, entidades sin decodificar, texto corrupto ni contenido ajeno.",
                    "Resumen con anomalía de texto.",
                    "Normalización segura: espacios. El resto: revisión manual.",
                    CAT_TEXT, MODE_SAFE),
            rule("PURE-019", "RESEARCH_OUTPUT", "number_of_pages", "Número de páginas",
                    "Si está informado, debe poder interpretarse como cantidad de páginas.",
                    "Detectar letras y caracteres inválidos en el número de páginas.",
                    "Número de páginas no interpretable.",
                    null, CAT_TEXT, MODE_MANUAL),
            rule("PURE-020", "RESEARCH_OUTPUT", "pages", "Rango de páginas",
                    "Página final no menor a inicial. No confundir article number con rango de páginas.",
                    "Si pages es un rango, la página final no puede ser menor a la inicial.",
                    "Página final menor que la inicial.",
                    null, CAT_CONSISTENCY, MODE_MANUAL),
            rule("PURE-021", "RESEARCH_OUTPUT", "embargoPeriod", "Embargo sin período",
                    "Cuando accessType = Embargoed / equivalente real, embargoPeriod debe existir.",
                    "Un archivo en embargo debe informar período de embargo.",
                    "Archivo en embargo sin período.",
                    null, CAT_ACCESS, MODE_VIEW),
            rule("PURE-022", "RESEARCH_OUTPUT", "workflow", "Workflow para aprobación",
                    "Registros con workflow «para su aprobación» deben generar alerta de revisión.",
                    "Las publicaciones en workflow forApproval / para su aprobación requieren revisión.",
                    "Publicación en workflow para su aprobación.",
                    null, CAT_ACCESS, MODE_VIEW),
            crossref("CROSSREF-001", "doi", "DOI con formato inválido",
                    "El valor informado como DOI no cumple el formato 10.xxxx/...",
                    "Un DOI informado debe tener formato válido. No se consulta Crossref.",
                    "DOI con formato inválido."),
            crossref("CROSSREF-002", "doi", "DOI no encontrado",
                    "El DOI válido no aparece en Crossref ni en otra agencia conocida.",
                    "No encontrado no implica inválido. OTHER_AGENCY se trata aparte.",
                    "DOI no encontrado en Crossref ni en otra agencia."),
            crossref("CROSSREF-003", "title", "Título PURE difiere de Crossref",
                    "Tras normalizar Unicode, entidades, espacios y puntuación, el título institucional difiere de forma relevante.",
                    "Umbral: igualdad normalizada o Jaccard de tokens < 0.55.",
                    "El título PURE difiere significativamente del título Crossref."),
            crossref("CROSSREF-004", "container_title", "Revista/container difiere",
                    "El container-title de Crossref no coincide con la revista PURE asociada.",
                    "No usar ISSN como identidad de revista.",
                    "Container/journal Crossref difiere de la revista PURE."),
            crossref("CROSSREF-005", "issn", "ISSN Crossref no coincide",
                    "Ningún ISSN Crossref coincide con los ISSN de la revista PURE relacionada.",
                    "ISSN es evidencia, no identidad.",
                    "ISSN Crossref no coincide con la revista PURE."),
            crossref("CROSSREF-006", "authors", "Autoría con diferencias relevantes",
                    "Los nombres completos normalizados de Crossref y PURE tienen poca superposición.",
                    "No vincular personas solo por nombre.",
                    "Autoría PURE y Crossref presentan diferencias relevantes."),
            crossref("CROSSREF-007", "orcid", "ORCID disponible en Crossref",
                    "Crossref informa ORCID de contributor y no hay ORCID institucional equivalente.",
                    "Evidencia externa. No escribir en PURE.",
                    "ORCID informado por Crossref y ausente institucionalmente."),
            crossref("CROSSREF-008", "affiliation", "Afiliación Crossref disponible",
                    "Hay afiliación/ROR en Crossref sin evidencia institucional comparable.",
                    "No inferir USS por texto San Sebastián.",
                    "Afiliación disponible en Crossref y ausente o diferente en PURE."),
            crossref("CROSSREF-009", "funding", "Funding informado en Crossref",
                    "Crossref entrega funding y PURE no tiene financiamiento utilizable.",
                    "Ausencia de funding Crossref no es error PURE.",
                    "Funding informado por Crossref y no disponible en PURE."),
            crossref("CROSSREF-010", "update", "Correction/retraction/update Crossref",
                    "Metadata estructurada de retraction, correction o update.",
                    "No inferir por texto libre.",
                    "Crossref informa correction, retraction o update relevante."),
            openalex("OPENALEX-001", "openalex_author_id", "Match fuerte OpenAlex sin confirmar",
                    "La persona tiene un candidato OpenAlex STRONG y aún no hay identidad confirmada localmente.",
                    "No se escribe en PURE. Revisar evidencia y confirmar o rechazar.",
                    "Hay un match OpenAlex fuerte pendiente de confirmación."),
            openalex("OPENALEX-002", "orcid", "ORCID PURE y OpenAlex en conflicto",
                    "El ORCID institucional y el ORCID observado en OpenAlex no coinciden.",
                    "Evidencia contradictoria. No elegir automáticamente.",
                    "ORCID PURE y OpenAlex presentan conflicto."),
            openalex("OPENALEX-003", "openalex_author_id", "Varios OpenAlex Authors para una persona",
                    "Una persona PURE tiene evidencia significativa con más de un OpenAlex Author.",
                    "OpenAlex puede overmerge/split. Revisión manual.",
                    "Una persona coincide con múltiples OpenAlex Authors."),
            openalex("OPENALEX-004", "openalex_author_id", "Un OpenAlex Author coincide con varias personas",
                    "Un OpenAlex Author ID tiene evidencia significativa con más de una persona PURE.",
                    "No asignar automáticamente.",
                    "Un OpenAlex Author coincide con múltiples personas PURE."),
            ext("EXT-01", "title", "Título vs fuentes externas",
                    "Comparar el título PURE con el título asociado al mismo DOI en fuentes locales.",
                    "DOI exacto localizado. Conservar título PURE y títulos externos. No reemplazar automáticamente.",
                    "El título institucional difiere de al menos una fuente externa comparable."),
            ext("EXT-02", "publication_year", "Año de publicación vs fuentes externas",
                    "El año de publicación PURE debe coincidir con el año publicado del mismo DOI.",
                    "Usar solo fechas de publicación (no deposited/indexed/created). Revisión manual; no reemplazar.",
                    "El año de publicación PURE difiere de una fuente externa comparable."),
            ext("EXT-03", "journal_title", "Revista vs fuente/container externo",
                    "Contrastar la revista PURE con journal/source/container asociado al DOI.",
                    "No comparar institución contra revista. Conservar ambos valores.",
                    "La revista PURE difiere de una fuente externa comparable."),
            ext("EXT-04", "journal_issn", "ISSN vs ISSN externos",
                    "El ISSN PURE debe coincidir con al menos uno de los ISSN externos del mismo DOI.",
                    "Normalizar guion/caso; conservar originales. No exigir arrays idénticos.",
                    "Ningún ISSN PURE coincide con los ISSN externos comparables."),
            ext("EXT-05", "citation_volume", "Volumen vs fuentes externas",
                    "Comparar volumen cuando ambas fuentes tengan dato comparable.",
                    "Trim y normalización mínima. No inventar volumen desde citation string.",
                    "El volumen PURE difiere de una fuente externa comparable."),
            ext("EXT-06", "citation_issue", "Número/issue vs fuentes externas",
                    "Comparar issue/número cuando ambas fuentes tengan dato comparable.",
                    "No interpretar Supplement 1 como 1.",
                    "El número/issue PURE difiere de una fuente externa comparable."),
            ext("EXT-07", "citation_pages", "Páginas / article number",
                    "Comparar únicamente el mismo tipo de dato: pages↔pages o articleNumber↔articleNumber.",
                    "No equiparar pages con article number. Eso es NOT_COMPARABLE, no inconsistencia.",
                    "Páginas o article number PURE no son comparables o difieren de la evidencia externa."),
            ext("EXT-08", "publisher", "Publisher vs fuentes externas",
                    "Comparar publisher cuando ambas fuentes lo informen, sin alias no aprobado.",
                    "Trim, Unicode y case. Springer Nature ≠ Springer Nature Switzerland AG.",
                    "El publisher PURE difiere de una fuente externa comparable."),
            ext("EXT-09", "access_type", "Tipo de acceso vs OA externo",
                    "Contrastar accessType PURE con OA persistido (Unpaywall principal; OpenAlex si existe).",
                    "Conservar granularidad gold/green/hybrid/bronze/closed. No modificar PURE.",
                    "El tipo de acceso PURE difiere de la evidencia OA externa."),
            ext("EXT-10", "license_type", "Licencia vs licencia externa",
                    "Contrastar licencia PURE con licencia externa del mismo DOI.",
                    "Normalizar CC conocidas y URLs Creative Commons equivalentes. Conservar original.",
                    "La licencia PURE difiere de una fuente externa comparable."),
            ext("EXT-11", "funding_award", "Número de adjudicación vs fuentes externas",
                    "Comparar award number exacto cuando esté en PURE y en la fuente externa.",
                    "No equivaler nombres de financiador. Sin award estructurado en PURE no hay finding.",
                    "El número de adjudicación PURE difiere de una fuente externa comparable."),
            ext("EXT-12", "authors", "Autores vs fuentes externas",
                    "Comparar autores entre PURE y fuentes externas del mismo DOI.",
                    "Condición funcional (corrección de copy/paste de la planilla): DOI exacto y datos de autores en PURE y en la fuente externa. Posición distinta es informativa, no inconsistencia. Listas truncadas no generan finding grave por autores faltantes.",
                    "La autoría PURE presenta diferencias relevantes respecto de fuentes externas comparables.")
    );

    private static final Map<String, RuleDef> BY_CODE = new LinkedHashMap<>();

    static {
        for (RuleDef rule : RULES) {
            BY_CODE.put(rule.code, rule);
        }
    }

    private DataQualityCatalog() {
    }

    public static RuleDef require(String code) {
        RuleDef rule = BY_CODE.get(code);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown data-quality rule " + code);
        }
        return rule;
    }

    public static List<String> implementableCodes() {
        return List.of(
                "PURE-001", "PURE-002", "PURE-003", "PURE-004", "PURE-005",
                "PURE-006", "PURE-007", "PURE-008", "PURE-009", "PURE-010",
                "PURE-011", "PURE-012", "PURE-013", "PURE-014", "PURE-015",
                "PURE-017", "PURE-018", "PURE-019", "PURE-020", "PURE-021", "PURE-022",
                "CROSSREF-001", "CROSSREF-002", "CROSSREF-003", "CROSSREF-004", "CROSSREF-005",
                "CROSSREF-006", "CROSSREF-007", "CROSSREF-008", "CROSSREF-009", "CROSSREF-010",
                "OPENALEX-001", "OPENALEX-002", "OPENALEX-003", "OPENALEX-004",
                "EXT-01", "EXT-02", "EXT-03", "EXT-04", "EXT-05", "EXT-06",
                "EXT-07", "EXT-08", "EXT-09", "EXT-10", "EXT-11", "EXT-12"
        );
    }

    public static List<String> supersededByExtConsistency() {
        return List.of("CROSSREF-003", "CROSSREF-004", "CROSSREF-005", "CROSSREF-006");
    }

    public static List<String> skippedForMissingData() {
        return List.of("PURE-016");
    }

    public static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return source.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * True when {@code source} is {@code data_quality_rule.source_family}
     * (PURE, CROSSREF, OPENALEX, EXTERNAL), not an evidence provider.
     */
    public static boolean isRuleSourceFamily(String source) {
        String value = normalizeSource(source);
        return SOURCE_PURE.equals(value)
                || SOURCE_CROSSREF.equals(value)
                || SOURCE_OPENALEX.equals(value)
                || SOURCE_EXTERNAL.equals(value);
    }

    private static RuleDef rule(
            String code,
            String entityType,
            String fieldName,
            String name,
            String description,
            String ruleText,
            String alertText,
            String bulkProcedure,
            String category,
            String correctionMode
    ) {
        return new RuleDef(
                code, SOURCE_PURE, entityType, fieldName, name, description, ruleText, alertText,
                bulkProcedure, category, correctionMode
        );
    }

    private static RuleDef crossref(
            String code,
            String fieldName,
            String name,
            String description,
            String ruleText,
            String alertText
    ) {
        return new RuleDef(
                code, SOURCE_CROSSREF, "RESEARCH_OUTPUT", fieldName, name, description, ruleText, alertText,
                null, CAT_EXTERNAL, MODE_VIEW
        );
    }

    private static RuleDef ext(
            String code,
            String fieldName,
            String name,
            String description,
            String ruleText,
            String alertText
    ) {
        return new RuleDef(
                code, SOURCE_EXTERNAL, "RESEARCH_OUTPUT", fieldName, name, description, ruleText, alertText,
                null, CAT_EXTERNAL, MODE_VIEW
        );
    }

    private static RuleDef openalex(
            String code,
            String fieldName,
            String name,
            String description,
            String ruleText,
            String alertText
    ) {
        return new RuleDef(
                code, SOURCE_OPENALEX, "PERSON", fieldName, name, description, ruleText, alertText,
                null, CAT_EXTERNAL, MODE_VIEW
        );
    }

    public record RuleDef(
            String code,
            String sourceFamily,
            String entityType,
            String fieldName,
            String name,
            String description,
            String ruleText,
            String alertText,
            String bulkProcedure,
            String category,
            String correctionMode
    ) {
    }
}
