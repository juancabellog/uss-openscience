# Transformaciones de metadatos detectadas

Solo transformaciones demostradas en código. **Reversible = NO** si hay pérdida (locales descartadas, prefijos eliminados, tokenización, primer año regex).

| Origen | Campo origen | Transformación | Destino | Campo destino | Clase/método | Reversible | Observaciones |
|---|---|---|---|---|---|---|---|
| PURE JSON `title` | `value` \| `es_CL` \| `en_US` \| textual | Precedencia: textual → `value` → `es_CL` → `en_US`; `trimToNull` | `pure_research_output.title` | `title` | `ResearchOutputMapper.tituloDe` | NO | Locales no elegidas se descartan. Un solo string. |
| PURE JSON person name | first/last | trim | `pure_person.first_name` / `last_name` | | `PersonMapper` | PARCIAL | Recorte de espacios |
| PURE / cualquier DOI crudo | `doi`, `dc.identifier.doi`, Crossref `DOI`, OpenAlex `doi`, Unpaywall `doi` | `trim` + `lowercase` + strip `https://doi.org/` `http://doi.org/` `https://dx.doi.org/` `http://dx.doi.org/` `doi:` `doi.org/` | `normalized_doi` / lookup | | `DoiNormalizer.normalize` | NO | **No** se guarda `https://doi.org/` en el valor normalizado. El crudo puede conservarse aparte (`doi`, `doi_raw`, `original_doi`). |
| DOI normalizado | string | regex `10.\d{4,9}/...` (tras strip prefix; **sin** lowercase en `DataQualityIdentifiers.normalizeDoi`) | admisión Crossref fetch | | `DataQualityIdentifiers.isValidDoi` | N/A | Dos normalizadores de DOI coexisten: `DoiNormalizer` (lowercase) vs `DataQualityIdentifiers.normalizeDoi` (no fuerza lowercase). |
| ORCID crudo | URL o iD | strip `https://orcid.org/` / `http://orcid.org/`; uppercase | `orcid` persistido / match | | `OrcidFormat.normalize` / `normalizeValidOrNull`; `DataQualityIdentifiers.normalizeOrcid` | PARCIAL | Validación `\d{4}-\d{4}-\d{4}-\d{3}[\dX]` |
| ORCID work external-id | type+value | `doi` → `DoiNormalizer`; `issn` → dígitos 8 + checksum-ish; else trim+lowercase | `orcid_work*_external_id.normalized_value` | | `OrcidExternalIdNormalizer.normalize` | PARCIAL | Identity types: `doi`, `eid`, `wosuid` |
| OpenAlex `id` | URL o short id | `OpenAlexId.normalize` | `openalex_work_id` / `openalex_author_id` | | `OpenAlexMapper` + `OpenAlexId` | PARCIAL | NO DETERMINADO el detalle si no se lee `OpenAlexId` completo en esta fila; evidencia: llamadas en mapper |
| OpenAlex authorship | `raw_author_name` vacío | fallback a `author.display_name` | `openalex_authorship.raw_author_name` | | `OpenAlexMapper.authorships` | NO | Puede mezclar firma observada con nombre resuelto si raw falta |
| OpenAlex affiliation | `raw_affiliation_strings[0]` o `affiliations` | primer string | `raw_affiliation_string` | | `OpenAlexMapper.firstAffiliationString` | NO | 0..N → 0..1 |
| Person display | first + last | `last + ", " + first` si ambos; else concat | matching / evidencia | | `AuthorNameMatcher.displayName` | PARCIAL | No se persiste como columna PURE |
| Nombres (match) | strings | tokens, Unicode, inicial vs nombre completo (regla J. Abarca) | boolean `coinciden` | | `AuthorNameMatcher.coinciden` | N/A | Solo matching, no almacenamiento |
| Títulos (comparación Crossref/DSpace) | title strings | decode entities, collapse whitespace, NFKC, lowercase, quitar puntuación, Jaccard token 0.55 | `titlesDiffer` | | `CrossrefComparison.normalizeComparable` / `titlesDiffer` | NO | Umbral `TITLE_JACCARD_THRESHOLD = 0.55`. No se persiste el título normalizado. |
| PURE status date parts / strings | `publication_year` o fechas en evidencia | regex `(19\|20)\d{2}` **o** columna `publication_year` del status | año de matching DSpace | `DspacePureCandidate.year` | `PublishedYearExtractor.from` + carga de candidatos DSpace | NO | **No** existe `pure_research_output.publication_year`. Año institucional DERIVED desde `pure_research_output_status`. |
| DSpace `dc.date.issued` / `dc.date` | string | mismo extractor de año | matching TITLE_AUTHOR_YEAR | | `DspaceMatcher.titleYearAuthorHits` | NO | Primer match regex 19xx/20xx |
| DSpace handle URL | `handle` o `dc.identifier.uri` | extrae path tras `/handle/`; fallback `https://repositorio.uss.cl/handle/{handle}` | `dspace_item.handle_url` | | `DspaceHalParser.item`; `DspaceMatcher` normalize handle | PARCIAL | Host hardcodeado solo si falta URI |
| DSpace item id | `uuid` o `id` | firstNonBlank | `dspace_item.uuid` | | `DspaceHalParser.item` | PARCIAL | |
| Crossref dates | `published` / `published-print` / `published-online` / `issued` `date-parts[0]` | concatenación date-parts → string | `crossref_work.*_date` | | `CrossrefMapper.dateParts` | PARCIAL | No Instant canónico |
| Crossref title | `message.title[]` | `firstText` = primer elemento array | `crossref_work.title` | | `CrossrefMapper.firstText` | NO | Títulos adicionales del array no persisten en columna `title` |
| Crossref author | `given` + `family` | `AuthorNameMatcher.displayName` → `full_name` | `crossref_author.full_name` | | `CrossrefMapper.authors` | PARCIAL | |
| Crossref affiliation ROR | `affiliation.id` | si texto contiene `ror.org` | `crossref_author_affiliation.ror` | | `CrossrefMapper` | PARCIAL | Heurística contains, no cliente ROR |
| Unpaywall locations | `oa_locations` + `best_oa_location` | dedup por `UnpaywallLocationIdentity.key`; marca `is_best` | `unpaywall_oa_location` | | `UnpaywallMapper.parse` | PARCIAL | `has_repository_copy` puede derivarse de locations si JSON no lo trae |
| Unpaywall best OA | `best_oa_location.*` | copia a columnas denormalizadas del work | `unpaywall_work.best_oa_*` | | persistencia Unpaywall | PARCIAL | Duplicado work vs child |
| ISSN ORCID | raw | `replaceAll("[^0-9Xx]")` 8 chars | normalized ISSN | | `OrcidExternalIdNormalizer.normalizeIssn` | PARCIAL | No visto el mismo normalizador en `pure_journal_issn` (ISSN PURE se guarda tal cual del mapper) |
| Páginas | `pages` | split guiones; o article number | calidad | | `DataQualityIdentifiers.parsePageRange` | PARCIAL | Uso calidad, no rewrite de `pure_research_output.pages` |
| OpenAlex OA | `open_access.is_oa` / `oa_status` | copia | `openalex_work.is_oa` | | `OpenAlexMapper.parseWork` | EXACT-ish | Comentario SQL: evidencia secundaria vs Unpaywall |
| Sync DSpace metadata | HAL `metadata.{key}[].value` | una fila por key+place+language | `dspace_item_metadata` | | `DspaceHalParser.metadata` | SÍ (estructura) | Keys no se recodifican |
| Scopus CSV DOI | columna CSV | normalización en importer (tools) | `scopus_publication.doi` y `doi_normalized` | | `tools` Scopus importer | NO DETERMINADO detalle sin citar cada línea del importer | Runtime no llama Scopus API |
| Scopus author match | `pure_person_identifiers.identifier_value` vs `scopus_author.auth_id` | `TRIM` igualdad; `MIN(id)` si duplicados | `scopusAuthorId` en DTO persona | | `PersonRepository` JOIN | NO | Tipo identifier PURE `"Scopus ID"` (`JdbcRepository.SCOPUS_IDENTIFIER_TYPE`) |
| Crossref search | query bibliográfica | no mapea candidatos a work | `CrossrefFetchResult.errorCode=CROSSREF_SEARCH_CANDIDATES` | | `HttpCrossrefClient.searchBibliographic` | N/A | Payload no auto-aplicado |
| Electronic version type | `type_discriminator` | filtro DOI version en lecturas RO | selección DOI del RO | | `ResearchOutputRepository` `DOI_ELECTRONIC_VERSION` | N/A | DOI puede ser 0..N EVs |

## Pérdida potencial de información (lossy)

- Título PURE: un locale gana; el resto no se persiste en `pure_research_output.title`.
- Título Crossref: solo el primer `title[]`.
- OpenAlex `raw_affiliation_strings`: solo el primero.
- DOI normalizado: se pierde esquema URL y mayúsculas.
- Año DSpace/PURE matching: regex toma el primer `19xx|20xx`, no un calendario completo.
- Comparación de títulos: puntuación y entidades se eliminan; no hay forma canónica persistida.
- OpenAlex `select=`: campos no listados no llegan (salvo anidados).
- `raw_json` truncado (`raw_json_truncated`) en OpenAlex.

## No encontrado (no inventar)

- Mapping de tipo PURE → COAR / OpenAIRE.
- Normalización ISSN en el catálogo PURE (print vs eISSN vs ISSN-L como columnas).
- Normalización Unicode del título **al persistir** (solo al comparar).
- Concatenación first+last persistida en `pure_person`.
