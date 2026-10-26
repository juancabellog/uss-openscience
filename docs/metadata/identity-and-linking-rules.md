# Reglas de identidad y linking (solo las existentes)

Este documento **no** define política institucional. Extrae reglas del código. Separar:

- **METADATA EQUIVALENCE** (mismo concepto de campo)
- **IDENTITY MATCH EVIDENCE** (¿es la misma persona/ítem?)

No se asumen pesos numéricos salvo los literales del código (`confidence` 1.0 en matches DSpace exactos; Jaccard 0.55 en títulos).

---

## Research Output identity

### Identificador estable (espejo institucional)

- **UUID PURE** → `pure_research_output.uuid` (UPSERT `WHERE uuid = ?`).
- **PURE id** → `pure_research_output.pure_id`.

### DOI (no es columna del padre)

- Origen remoto: electronic versions del JSON PURE.
- Local: `pure_research_output_electronic_version.doi` (**0..N**).
- Lookup hacia OpenAlex/Crossref/Unpaywall: DOI **normalizado** (`DoiNormalizer`).
- Enlaces evidencia: `openalex_research_output`, `crossref_research_output`, `unpaywall_research_output` (work + `research_output_id` + opcional `electronic_version_id`).
- Identidad Crossref/Unpaywall/OpenAlex work: `UNIQUE(normalized_doi)` (OpenAlex también `UNIQUE(openalex_work_id)`).

### DSpace ↔ PURE research output (`DspaceMatcher`)

| match_method | match_status | canonical | Evidencia | Aceptación |
|---|---|---|---|---|
| `UUID` | `CONFIRMED` | true | UUID ítem DSpace = UUID candidato PURE (lowercase) | Automática si hit único |
| `HANDLE` | `CONFIRMED` | true | Handle normalizado | Automática si hit único |
| `DOI` | `CONFIRMED` o `SECONDARY` | `canonical` true si único ítem por DOI | DOI normalizado | Automática; SECONDARY si varios ítems comparten DOI |
| `TITLE_AUTHOR_YEAR` | `CANDIDATE` | false | Título (Jaccard vía `CrossrefComparison.titlesDiffer`), año (`PublishedYearExtractor`), overlap autores (`AuthorNameMatcher`) | **No confirmado** si ambiguo; solo candidatos. Se **salta** si el RO ya tiene `normalizedDoi`. |

Persistencia: `dspace_item_match` (`match_method`, `match_status`, `canonical`, `confidence`, `evidence_json`).

Año PURE usado en matching: **DERIVED** desde `pure_research_output_status` (no desde el padre).

### ORCID work ↔ persona

- Works se cuelgan de `person_id` + `orcid` ya asociado a la persona PURE.
- Identificadores de work: `orcid_work_external_id` / group external ids; identity types `doi`, `eid`, `wosuid` (`OrcidExternalIdNormalizer.isIdentityType`).
- Relación `self` vs other: `isSelf(relationship)`.

### Crossref DOI candidate

- Tabla `crossref_doi_candidate` («discovery preparado; no se escribe a PURE»). No es aceptación automática de DOI faltante.

### Conflicto RO

- No hay merge de dos UUID PURE. Sync es upsert por UUID.
- Un mismo DOI puede mapear N research outputs vía tablas puente (electronic versions compartidas).

---

## Person identity

### Estable

- `pure_person.uuid` (clave de sync).
- `pure_person.orcid` (columna) **y** identificadores en `pure_person_identifiers`.

**No** se escribe ORCID descubierto en `pure_person` / `pure_person_identifiers` desde OpenAlex (comentario SQL OpenAlex).

### PURE person ↔ OpenAlex author (`OpenAlexPersonMatcher`)

Evidencias literales:

- `SAME_DOI` — co-ocurrencia en un work con DOI compartido (**no** identidad por sí sola; comentario en código).
- `NAME_COMPATIBLE` — `AuthorNameMatcher.coinciden`
- `ORCID_MATCH` — ORCID normalizado igual
- `USS_AFFILIATION` — institución USS (`UssOrganizationIdentity`)
- `POSITION_MATCH` / `POSITION_DIFFERENT` / `POSITION_NOT_COMPARABLE` agrupados como `POSITION_INFO`

`match_method` persistido (prioridad observada en el matcher):

- `ORCID_MATCH` si ORCID coincide
- `SAME_DOI+NAME_COMPATIBLE` si DOI+nombre
- si no, `SAME_DOI`

Tabla: `openalex_person_match` (`status`, `match_method`, `selected`, `selected_manually`, `orcid_match`, `uss_affiliation`, `review_requested`, `evidence_json`, confirmed/rejected by).

Hijos: `openalex_person_match_work`, `openalex_person_match_work_evidence`.

Esto es **IDENTITY_LINK / EVIDENCE**, no equivalencia de campo Author=Person.

### PURE person ↔ Scopus author

- Identifier PURE `identifier_type = "Scopus ID"` (`JdbcRepository.SCOPUS_IDENTIFIER_TYPE`).
- JOIN `TRIM(scopus_author.auth_id) = TRIM(identifier_value)`; si hay varios `scopus_author`, `MIN(id)`.
- No hay cliente Scopus API.

### PURE person ↔ ORCID record

- ORCID iD en persona vs API `{orcid}/works` y `/employments`.
- Discovery: `orcid_candidate`, `orcid_candidate_evidence` (search API). Detalle fino de pesos de candidatos: ver clases ORCID candidate matcher (comparten constantes de posición con OpenAlex).

### Person vs Contributor vs Author

| Sistema | Entidad | Relación a Person |
|---|---|---|
| PURE | `pure_research_output_contributor` | `person_uuid` / `external_person_uuid` opcionales; nombre propio en el contributor |
| OpenAlex | `openalex_authorship` | `raw_author_name` (firma) ≠ `author_display_name` (author resuelto) ≠ `openalex_author` |
| Crossref | `crossref_author` | given/family/ORCID; no FK a `pure_person` |
| DSpace | metadata `dc.contributor.author` | string; no entidad persona |
| Scopus | `scopus_publication_author` | `auth_id` + opcional FK `scopus_author_id` |

---

## Organization identity

- **No hay** entidad local `pure_organization` ni sync `/organizations`.
- Identidad PURE: UUID opaco (`managing_organization_uuid`, `organization_uuid` en staff/contributor/external org).
- OpenAlex: `openalex_institution_id` + `ror` + `country_code` + `display_name`.
- Crossref: texto `name` + ROR heurístico.
- ORCID: employment (tabla `orcid_employment`) — campos exactos en SQL ORCID.
- USS affiliation en matching OpenAlex: `UssOrganizationIdentity` (código de org USS, no catálogo ROR institucional único).

No hay matching automático Organization ↔ ROR como entidad canónica.

---

## Journal identity

- Sync PURE `GET /journals` → `pure_journal.uuid`.
- Relación RO: `pure_research_output_journal` (`journal_id`, `journal_uuid` si columna existe).
- ISSN: columna `pure_journal.issn` **y** filas `pure_journal_issn.issn` (lista; tipo print/e no está en columnas del INSERT).
- Identificadores extra: `pure_journal_identifier`.
- OpenAlex: `primary_location.source.id` / `display_name` / `issn_l` (no tabla journal OpenAlex).
- Crossref: `container_title` + `crossref_issn` (`issn`, `issn_type`).
- Unpaywall: `journal_name`, `journal_issns`, `journal_issn_l`.
- Crossref `journalUrl(issn)` **no** alimenta un catálogo.

No hay matcher Journal PURE ↔ Crossref journal entity.

---

## Project identity

**NOT_IMPLEMENTED.** No hay tabla `pure_project` ni cliente. Funding observado:

- Crossref `crossref_funder` (`funder_name`, `funder_doi`, `award_number`)
- Scopus CSV `funding_details` / `funding_texts`
- Columna opcional `pure_research_output.funding_org_uuid` (no está en el UPSERT de `ResearchOutputSyncRepository`)

---

## Aceptación automática vs revisión manual (resumen)

| Link | Automático | Revisión |
|---|---|---|
| PURE upsert por UUID | Sí | N/A |
| DSpace UUID/HANDLE/DOI único | Sí CONFIRMED | Ambiguos DOI → SECONDARY; título/año/autor → CANDIDATE |
| OpenAlex person ORCID | match_method ORCID_MATCH | `review_requested`, `selected_manually`, reject/confirm fields |
| OpenAlex SAME_DOI | se persiste; código advierte que no es identidad | revisión implícita |
| Crossref DOI candidate | No escribe PURE | tabla de candidatos |
| Scopus auth_id | JOIN de consulta | duplicados → MIN(id) (pérdida / arbitrario) |
| SciVal | — | NOT_IMPLEMENTED |
