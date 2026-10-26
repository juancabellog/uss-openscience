# Gaps y ambigüedades (evaluación técnica, no normativa)

Severidades **CRITICAL / IMPORTANT / MINOR** son propuestas técnicas del auditor, no dictamen institucional USS.

Estados de ausencia: `NOT_APPLICABLE`, `NOT_PRESENT_IN_SOURCE`, `NOT_PERSISTED`, `NOT_USED_BY_CURRENT_IMPLEMENTATION`, `NOT_IMPLEMENTED`, `UNKNOWN`, `LEGACY`.

---

## NO_SOURCE_OF_TRUTH_DEFINED

| Concepto | Hallazgo | Clase | Severity |
|---|---|---|---|
| Título | PURE local es el valor institucional persistido; Crossref/OpenAlex/Unpaywall/DSpace/ORCID/Scopus guardan títulos propios para evidencia/enriquecimiento. No hay campo canónico USS ni regla de precedencia documentada en código. | NO_SOURCE_OF_TRUTH_DEFINED | IMPORTANT |
| Tipo de producto | PURE `type_uri/type_es/type_en`; OpenAlex `type`; Crossref `type`; Unpaywall `genre`; DSpace `dc.type`; ORCID `work_type`; Scopus `document_type`. Sin mapping entre vocabularios. | MISSING_MAPPING + NO_CANONICAL_VALUE | IMPORTANT |
| Open Access | Unpaywall declarado fuente OA en SQL OpenAlex; PURE `access_type_*` en electronic version; DSpace `access_status`. No hay campo único institucional de OA. | MULTIPLE_REPRESENTATIONS | CRITICAL |
| Año de publicación | No está en `pure_research_output`; vive en `pure_research_output_status` (0..N) y se deriva con regex en matching. Otras fuentes tienen `year`/`publication_year` propio. | NO_CANONICAL_VALUE | CRITICAL |
| DOI | 0..N en EV PURE; un `normalized_doi` por work externo. No hay “DOI canónico del RO” en el padre. | MULTIPLE_REPRESENTATIONS | IMPORTANT |

---

## MISSING_MAPPING

| Concepto | Hallazgo | Severity |
|---|---|---|
| Tipo → COAR | No hay clase/tabla COAR. | IMPORTANT |
| Tipo → OpenAIRE | No hay cliente ni crosswalk OpenAIRE. | IMPORTANT |
| PURE type → OpenAlex type → Crossref type | Comparación de calidad campo a campo, no tabla de equivalencias. | IMPORTANT |
| ISSN print / eISSN / ISSN-L en PURE | `pure_journal.issn` + `pure_journal_issn.issn` sin `issn_type`. Crossref sí tiene `issn_type`. OpenAlex/Unpaywall tienen ISSN-L. | IMPORTANT |
| Project / Grant PURE | No implementado. Funding solo evidencia Crossref/Scopus CSV. | IMPORTANT |
| Organization catalog | No `pure_organization`, no `/organizations`. UUID opacos. | CRITICAL |
| Dataset / Software | No entidades locales. | MINOR (si el dominio no los usa) |
| SciVal | `SYSTEM_STATUS = NOT_IMPLEMENTED`. | MINOR para matriz actual |
| Scopus HTTP API | `ScopusClient` vacío. | IMPORTANT si se espera sync vivo |
| Local → Dublin Core export | DC solo como keys DSpace de entrada. | MINOR |

---

## AMBIGUOUS_MAPPING

| Concepto | Hallazgo | Severity |
|---|---|---|
| Person vs Author vs Contributor | Estructuras distintas; matching por evidencia. Unificarlos sería incorrecto. | CRITICAL |
| `raw_author_name` vs `author_display_name` | Conceptos distintos; si raw vacío el mapper **copia** display_name a raw. | IMPORTANT |
| `pure_person.orcid` vs `pure_person_identifiers` vs ORCID API | Tres capas. | IMPORTANT |
| `"Scopus ID"` PURE vs `scopus_author.auth_id` vs Scopus publication `eid` | Tres identificadores distintos. | IMPORTANT |
| DSpace `name` vs `dc.title` | Item HAL `name` usado en matching de título; evidencia también lee `dc.title`. Equivalencia no demostrada como siempre iguales. | IMPORTANT |
| `DataQualityIdentifiers.normalizeDoi` vs `DoiNormalizer` | Uno no fuerza lowercase. | IMPORTANT |
| Status PURE 0..N vs “fecha de publicación” | Qué fila de status alimenta el año de matching: debe leerse el código que arma `DspacePureCandidate.year`. | IMPORTANT |
| Crossref `registration_agency_id = datacite` | No es integración DataCite. | MINOR |
| `funding_org_uuid` | Leído si la columna existe; no escrito por UPSERT actual. LEGACY/UNKNOWN. | MINOR |

---

## MULTIPLE_REPRESENTATIONS

Documentado a propósito en la matriz long-form: DOI, título, ISSN, OA, autores, keywords, provenance `raw_json` por fuente. No es un error; es el estado del sistema.

---

## UNUSED_DATA / REMOTE_AVAILABLE no persistido selectivamente

| Dato | Hallazgo |
|---|---|
| OpenAlex campos fuera de `WORK_SELECT`/`AUTHOR_SELECT` | No solicitados. |
| Crossref `title[1..n]` | Solo el primero. |
| Crossref `journal()` URL | Cliente construye URL; no hay sync de journal Crossref. |
| Crossref `searchBibliographic` | Payload no aplicado. |
| DSpace metadata keys no usadas en matcher | Persisten en `dspace_item_metadata`; matching usa subconjunto. |
| DSpace PDF | Comentario SQL: no almacena PDF; bitstream metadatos/URLs sí. |
| PURE JSON completo | No hay `raw_json` en `pure_research_output` (NO DETERMINADO si algún snapshot existe en otra tabla). Snapshots sí en OpenAlex/Crossref (`parent_snapshot`, `raw_json`). |

---

## LOSSY_TRANSFORMATION

Ver `metadata-transformations.md`. Principales: locale de título PURE, first title Crossref, primera afiliación OpenAlex, DOI strip URL, año regex, Jaccard de título no persistido, `MIN(id)` Scopus duplicados, fallback raw←display OpenAlex.

---

## NO_VALIDATION

| Identificador | Validador | Dónde |
|---|---|---|
| DOI | `DataQualityIdentifiers.isValidDoi` + `DoiNormalizer` | Crossref fetch; calidad |
| ORCID | `OrcidFormat` / `DataQualityIdentifiers.isValidOrcid` | OpenAlex author, calidad |
| ISSN | `OrcidExternalIdNormalizer.normalizeIssn` | IDs ORCID, no catálogo PURE journals |
| Handle | normalización path, no checksum | `DspaceMatcher` |
| ROR | heurística `contains("ror.org")` en Crossref | No validador ROR |
| UUID PURE | usado como string | No validador de formato visto en esta auditoría |

---

## NO_PROVENANCE

- Espejo PURE: `created_date`/`modified_date` del JSON PURE, no `raw_json` del RO.
- Enriquecimiento: `last_sync_execution_id`, `last_outcome`, `raw_json` en OpenAlex/Crossref/Unpaywall (Unpaywall work no lista `raw_json` en el CREATE inspeccionado → **NOT_PERSISTED** payload completo Unpaywall salvo columnas).
- DSpace: `last_sync_at`, `seen_in_execution_id`; metadata values sí.

---

## LEGACY

- `tools/src/main/java/cl/uss/ca/pure/*Mapper.java` duplica mappers del backend.
- `openscience_sync_run` vs `sync_execution` (ambas en KNOWN_TABLES).
- `AUDIT_SOURCES` incluye `SCIVAL` sin backend de sync.
- `INSERT_RO_JOURNAL_LEGACY_SQL` si falta `journal_uuid`.

---

## Cobertura (conteo desde la matriz long-form, no SQL)

Fuente: `metadata-crosswalk-technical.csv` (212 filas de representación + 1 encabezado). Conteos de `equivalence_type` con patrón de fin de fila.

| Métrica | Valor |
|---|---|
| Representaciones técnicas | 212 |
| Conceptos candidatos (aprox., agrupación) | ~85 |
| DIRECT | 139 |
| NORMALIZED | 19 |
| DERIVED | 6 |
| EVIDENCE_ONLY | 11 |
| IDENTITY_LINK | 18 |
| NO_EQUIVALENT | 9 |
| UNKNOWN | 10 |
| MAPPED_VOCABULARY | 0 |

`MAPPED_VOCABULARY = 0` porque no existe clase/tabla de crosswalk de vocabularios (COAR, OpenAIRE, tipos PURE↔OpenAlex↔Crossref↔DSpace).

Estos números **no** provienen de consultas SQL ejecutadas.
