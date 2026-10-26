# Inventario de fuentes de metadatos e interoperabilidad

**Naturaleza de este documento:** extracción técnica del código, esquemas propuestos y configuración de OPENSCIENCE-USS. No es un modelo canónico institucional.

**Método:** inspección estática de `backend/`, `src/app/`, `backend/sql/`, `tools/`, DTOs, entidades, clientes HTTP, mappers y tests. No se ejecutó SQL, sync, build ni llamadas a APIs.

**Convenciones de estado**

| Estado | Significado |
|---|---|
| IMPLEMENTED | Cliente, sync y tablas locales existen en el código de la aplicación |
| IMPLEMENTED_CSV | Datos locales existen; carga por CSV/herramientas, no por API en runtime |
| CONFIGURABLE | Implementado; salud depende de flags/tablas (`OPERATIONAL` vs `NOT_CONFIGURED`) |
| NOT_IMPLEMENTED | Código de integración ausente o solo tarjeta UI |
| PLANNED_STANDARD | Estándar citado o usado como vocabulario/keys, no como plataforma |
| LEGACY | Copia o columna no usada por el flujo vigente |
| NOT_PRESENT | No hay tabla/cliente/entidad en el repositorio |

---

## 1. Sistemas / integraciones encontrados

| Código en UI/API | Estado real | Rol observado | Escritura a PURE | Notas |
|---|---|---|---|---|
| PURE | IMPLEMENTED / CONFIGURABLE | Fuente institucional de personas, research outputs y journals | No desde este código (solo GET a `/persons`, `/research-outputs`, `/journals`) | Espejo local `pure_*` |
| OPENSCIENCE_USS | IMPLEMENTED | Almacén MySQL `uss-ca` + API Jakarta + UI Angular | N/A | No hay tabla canónica aparte del espejo PURE y tablas de enriquecimiento |
| ORCID | IMPLEMENTED / CONFIGURABLE | Identificador en persona PURE + API pública de works/employments/search + candidatos | No | Separar `pure_person.orcid` de datos ORCID API |
| UNPAYWALL | IMPLEMENTED / CONFIGURABLE | Evidencia OA por DOI | No | Comentario SQL: Unpaywall es la fuente OA; OpenAlex OA es secundaria |
| SCOPUS | IMPLEMENTED_CSV / CONFIGURABLE | Tablas `scopus_*` + identificador PURE `"Scopus ID"` | No | `ScopusClient` es interfaz vacía; no hay HTTP Scopus |
| OPENALEX | IMPLEMENTED / CONFIGURABLE | Enriquecimiento Work/Author + matching persona | No | Lookup `GET /works/doi:{doi}` con `select=` reducido |
| CROSSREF | IMPLEMENTED / CONFIGURABLE | Enriquecimiento/evidencia por DOI + comparación vs PURE | No | `GET /works/{doi}` y `/agency`; journal URL existe pero no hay catálogo Crossref |
| DSPACE | IMPLEMENTED / CONFIGURABLE | Repositorio institucional solo lectura | No | HAL REST; persiste todas las keys de `metadata`; matching usa subconjunto |
| SCIVAL | NOT_IMPLEMENTED | Tarjeta de integraciones `notConfigured("SCIVAL")` | No | `AuditCatalog` lista SCIVAL sin tipos de evento |
| DataCite | NO es integración | Valor de agencia Crossref (`registration_agency_id`) | No | `CrossrefMapper.parseAgency` |
| OpenAIRE | NOT_PRESENT | — | — | Sin cliente, tabla ni mapper |
| COAR | NOT_PRESENT como mapping | — | — | Sin crosswalk de tipos |
| Dublin Core | PLANNED_STANDARD / keys DSpace | Keys `dc.*` tal como las entrega DSpace | No | No hay export DC institucional |
| CERIF | NOT_PRESENT | — | — | Sin implementación |
| ROR | Campo, no plataforma | OpenAlex institutions, Crossref affiliation id | No | Sin cliente ROR |

Fuente de estados de tarjeta: `backend/src/main/java/cl/uss/ca/service/IntegrationService.java`.

---

## 2. Principales tablas por sistema

Registro de tablas conocidas por el runtime: `JdbcRepository.KNOWN_TABLES`.

### PURE remoto → espejo local

No existe tabla `pure_organization`. Las organizaciones se guardan como UUID de referencia.

| Tabla | Entidad | Evidencia de columnas (INSERT / SQL propuesto) |
|---|---|---|
| `pure_person` | Person | `pure_id`, `uuid`, `first_name`, `last_name`, `orcid`, `orcid_authenticated`, `email`, `fecha_ingreso`, `cargo`, `titulo`, `created_by`, `modified_by`, `fecha_creacion_pure`, `fecha_modificacion_pure`; columna opcional `active_in_pure` |
| `pure_person_identifiers` | Person identifier | `identifier_typeDiscriminator`, `identifier_type`, `identifier_value` |
| `pure_person_organization` | Staff org association | `organization_uuid`, `employment_type`, `email`, `fecha_inicio`, `fecha_termino`, `primary_association`, `contract_type`, `fte`, `job_description`, `job_title`, `staff_type` |
| `pure_person_title` | Person title | `title_type`, `title_value` |
| `pure_research_output` | Research output | Ver INSERT: **no incluye `publication_year` ni `doi`** |
| `pure_research_output_status` | Publication status + date parts | `current_status`, `status_uri/es/en`, `publication_year`, `publication_month`, `publication_day` |
| `pure_research_output_contributor` | Contributor (no Person) | `first_name`, `last_name`, `role_*`, `person_uuid`, `external_person_uuid`, `corresponding_author` |
| `pure_research_output_contributor_organization` | Contributor affiliation | `organization_uuid` |
| `pure_research_output_electronic_version` | File/EV + DOI | `type_discriminator`, `doi`, `access_type_*`, `version_type_*` |
| `pure_research_output_identifier` | Other RO IDs | `type_discriminator`, `id_source`, `value` |
| `pure_research_output_link` | Related URL | `url`, `description_*`, `link_type_*` |
| `pure_research_output_journal` | RO ↔ Journal | `journal_id`, `journal_uuid` (si columna existe), `pure_id` |
| `pure_research_output_external_organization` | External org UUID | `organization_uuid` |
| `pure_research_output_keyword_group` / `_keyword` | Keywords | grupos + `keyword_es/en`, `free_keyword`, `keyword_uri` |
| `pure_journal` | Journal catalog | `uuid`, `title`, `issn`, `publisher_uuid`, `publisher_system_name`, type/workflow |
| `pure_journal_title` | Alternate titles | `title` |
| `pure_journal_issn` | ISSN list | `issn` (sin distinguir print/eISSN en columnas) |
| `pure_journal_identifier` | Journal IDs | `type_discriminator`, `id_source`, `identifier_value` |
| `pure_journal_keyword_group` / `_classification` / `_keyword` | Journal subjects | URI + términos |

Columna condicional no escrita por el UPSERT vigente: `pure_research_output.funding_org_uuid` (leída en `CrossrefRepository` si existe). Marcar LEGACY / UNKNOWN si está en BD productiva.

### OpenAlex

`openalex_work`, `openalex_author`, `openalex_authorship`, `openalex_authorship_institution`, `openalex_work_topic`, `openalex_research_output`, `openalex_person_match`, `openalex_person_match_work`, `openalex_person_match_work_evidence`, `openalex_sync_stats`.

SQL: `backend/sql/proposed-openalex.sql`.

### Crossref

`crossref_work`, `crossref_author`, `crossref_author_affiliation`, `crossref_issn`, `crossref_funder`, `crossref_license`, `crossref_relation`, `crossref_research_output`, `crossref_sync_stats`, `crossref_doi_candidate`.

SQL: `backend/sql/proposed-crossref.sql`.

### Unpaywall

`unpaywall_work`, `unpaywall_oa_location`, `unpaywall_research_output`, `unpaywall_sync_stats`.

SQL: `backend/sql/proposed-unpaywall.sql`.

### DSpace

`dspace_community`, `dspace_collection`, `dspace_item`, `dspace_item_metadata`, `dspace_bitstream`, `dspace_item_match`, `dspace_sync_checkpoint`, `dspace_sync_stats`.

SQL: `backend/sql/proposed-dspace.sql`.

### ORCID

`orcid_person_sync`, `orcid_work_group`, `orcid_work_group_external_id`, `orcid_work`, `orcid_work_external_id`, `orcid_work_contributor`, `orcid_employment`, `orcid_discovery_execution`, `orcid_discovery_person`, `orcid_candidate`, `orcid_candidate_evidence`.

SQL: `backend/sql/proposed-orcid-work*.sql`.

### Scopus (CSV)

Runtime conoce `scopus_author`, `scopus_publication`, `scopus_publication_author`. El script de herramientas también define `scopus_publication_affiliation`, `scopus_publication_keyword` (`tools/sql/scopus_publication_tables.sql`). `scopus_author` no tiene CREATE en los SQL propuestos del backend; columnas usadas en JOIN: `id`, `auth_id`, `author_name`, `number_documents`, `orcid`.

### Provenance / sync / app

`sync_execution`, `sync_control`, `sync_change`, `sync_error`, `openscience_sync_run`, `pure_correction_audit`, `data_quality_*`, `app_*`.

---

## 3. Clientes / servicios / endpoints

Credenciales: no documentadas. Config keys existen (`pure.api.key`, etc.); valores = `<REDACTED>`.

| Sistema | Cliente | Endpoints / recursos usados | Mapper | Job / servicio |
|---|---|---|---|---|
| PURE | `HttpPureClient` / `PureClient` | GET `{pure.api.url}/persons`, `/research-outputs`, `/journals` (paginado) | `PersonMapper`, `ResearchOutputMapper`, `JournalMapper` | `PersonPureSyncJob`, `ResearchOutputPureSyncJob`, `JournalPureSyncJob`, `PureSyncService` |
| OpenAlex | `HttpOpenAlexClient` | `GET /works/doi:{doi}?select=WORK_SELECT`; `GET /authors/{id}?select=AUTHOR_SELECT` | `OpenAlexMapper` | sync OpenAlex (repositorio `OpenAlexRepository`) |
| Crossref | `HttpCrossrefClient` | `GET /works/{doi}`; agency; `searchWorks` (candidatos, no auto-aplicados); `journalUrl(issn)` construye URL **sin sync de journals** | `CrossrefMapper` | `CrossrefRepository` + job Crossref |
| Unpaywall | `HttpUnpaywallClient` | `GET /{doi}?email=` | `UnpaywallMapper` | `UnpaywallRepository` |
| ORCID | `HttpOrcidClient` | `{orcid}/works`, `{orcid}/employments`, `/search/?q=` | `OrcidWorksMapper` (+ employment mapper en el mismo paquete) | ORCID sync / discovery / candidates |
| DSpace | `HttpDspaceClient` | `discover/search/objects`, `core/communities`, `core/collections`, `core/items/{uuid}`, `accessStatus`, bundles, bitstreams, `pid/find` | `DspaceHalParser` | DSpace sync + `DspaceMatcher` |
| Scopus | `ScopusClient` (vacío) | Ninguno en runtime | CSV en `tools/` | `tools` importer; health = existencia de `scopus_author` |
| SciVal | Ninguno | — | — | `IntegrationService.notConfigured("SCIVAL")` |

`OpenAlexUrls.WORK_SELECT`: `id,doi,display_name,publication_year,publication_date,type,cited_by_count,referenced_works_count,language,primary_location,primary_topic,topics,authorships,open_access,updated_date`.

`OpenAlexUrls.AUTHOR_SELECT`: `id,display_name,orcid,works_count,cited_by_count,updated_date`.

Cualquier otro campo OpenAlex de la API pública **no se solicita** en el `select=` (salvo lo que venga anidado en los objetos seleccionados). `raw_json` guarda el payload recibido (posible truncado: `raw_json_truncated`).

---

## 4. Entidades conceptuales identificadas

| Grupo | Representaciones reales | No encontrado |
|---|---|---|
| RESEARCH_OUTPUT | PURE research output; OpenAlex work; Crossref work; Unpaywall work; DSpace item; ORCID work/group; Scopus publication | Dataset como entidad; Software como entidad; tabla canónica USS aparte de `pure_research_output` |
| PERSON | `pure_person`; ORCID person (vía ORCID iD + API); OpenAlex author; Scopus author | Persona canónica USS |
| AUTHORSHIP / CONTRIBUTOR | PURE contributor; OpenAlex authorship; Crossref author; ORCID work contributor; DSpace `dc.contributor.author`; Scopus publication author | Unificación Person=Author |
| ORGANIZATION | UUID refs PURE; OpenAlex institution (+ ROR); Crossref affiliation name/ROR; ORCID employment; Scopus affiliation raw | Catálogo `pure_organization`; sync `/organizations` |
| JOURNAL / SOURCE | `pure_journal` desde `/journals`; OpenAlex `primary_location.source`; Crossref `container-title`+ISSN; Unpaywall journal_*; DSpace `dc.source` | eISSN vs print como columnas distintas en PURE ISSN child |
| PROJECT / FUNDING | Crossref `funder`/`award`; Scopus CSV `funding_*`; columna opcional `funding_org_uuid` | Entidad Project/Grant PURE; tabla local de proyectos |
| FILE / FULL TEXT | PURE electronic version (metadatos, DOI); DSpace bitstream (metadatos, no PDF); Unpaywall URLs | Almacenamiento de PDF institucional |
| ACCESS | PURE EV access_type; Unpaywall is_oa/oa_status/locations; OpenAlex open_access (secundario); DSpace accessStatus/embargo/fulltext_availability | Status OA institucional único |
| PROVENANCE | `sync_execution`, `last_sync_*`, `raw_json`, `last_outcome`, snapshots, `dspace_item_match`, `*_research_output` links | Modelo de precedencia documental |

---

## 5. ¿Existe un crosswalk centralizado?

**No.** No hay clase, tabla ni archivo único de equivalencias.

Lo más cercano son mappers y comparadores por fuente:

- `ResearchOutputMapper` / `PersonMapper` / `JournalMapper` (PURE JSON → POJO → SQL)
- `OpenAlexMapper`, `CrossrefMapper`, `UnpaywallMapper`, `OrcidWorksMapper`, `DspaceHalParser`
- `DoiNormalizer`, `OrcidFormat`, `OrcidExternalIdNormalizer`, `OpenAlexId`, `AuthorNameMatcher`, `CrossrefComparison`, `PublishedYearExtractor`, `DataQualityIdentifiers`
- `DspaceMatcher`, `OpenAlexPersonMatcher`, `DspaceEvidenceProvider`, `CrossrefEvidenceProvider`

`tools/src/main/java/cl/uss/ca/pure/*Mapper.java` es copia de herramientas (LEGACY / duplicado), no el runtime de OpenLiberty.

---

## 6. ¿Existe un modelo canónico USS?

**No.** `canonical_concept_candidate` en la matriz es solo agrupación técnica.

La fuente bibliográfica institucional persistida es el **espejo PURE** (`pure_*`). OpenAlex, Crossref, Unpaywall, DSpace, ORCID works y Scopus son **enriquecimiento, evidencia, matching o carga CSV**. Los comentarios SQL lo declaran explícitamente (p. ej. OpenAlex/Crossref/Unpaywall «NO escribe en PURE»).

---

## 7. Sistemas realmente implementados vs futuros

**Implementados en runtime (si configurados y tablas presentes):** PURE, ORCID, Unpaywall, OpenAlex, Crossref, DSpace.

**Implementados como datos locales sin API:** Scopus (`scopus_author` + publicaciones CSV).

**Solo UI / catálogo de fuentes:** SciVal (`NOT_CONFIGURED`).

**Estándares sin plataforma:** Dublin Core (keys DSpace), DataCite (agency id Crossref), ROR (campo).

**No implementados:** OpenAIRE, COAR mapping, CERIF, cliente DataCite, cliente ROR, cliente SciVal, cliente Scopus HTTP, sync PURE organizations/projects/datasets.

---

## 8. DTOs / clases Java principales revisadas

- PURE JSON: `PersonItem`, `ResearchOutputItem`, `JournalItem`; modelo `PureJournal`, `JournalIssnData`, `PersonIdentifier`
- PURE persistencia: `*SyncRepository`
- API: `ResearchOutputElectronicVersionDto`, `IdentifierDto`, `IntegrationStatusDto`
- OpenAlex: `OpenAlexWorkRecord`, `OpenAlexAuthorshipRecord`, `OpenAlexAuthorRecord`
- Crossref: `CrossrefWorkRecord`, `CrossrefAuthorRecord`
- Unpaywall: `UnpaywallWorkRecord`, `UnpaywallLocationRecord`
- DSpace: `DspaceItemRecord`, `DspaceMetadataValue`, `DspaceBitstreamRecord`
- ORCID: records en `cl.uss.ca.external.orcid`
- Calidad: `DataQualityIdentifiers`, `ConsistencyField`, scanners Crossref/DSpace

Frontend: módulo Integraciones (`IntegrationService` via API), DSpace manage/consult, OpenAlex, Auditoría (`AUDIT_SOURCES` incluye SCIVAL como etiqueta).

---

## 9. Limitaciones de esta matriz

1. Los `CREATE TABLE` de `pure_*` no están en `backend/sql/proposed-*`; las columnas se infieren de INSERT/SELECT del código. Si la BD productiva tiene columnas extra no leídas, quedan **UNKNOWN**.
2. `JdbcRepository` comprueba columnas opcionales en runtime (`active_in_pure`, `journal_uuid`, `funding_org_uuid`). La matriz marca esas como condicionales.
3. DSpace persiste **todas** las metadata keys; la matriz no puede listar el vocabulario completo sin consultar la BD. Se documentan las keys **usadas por código** (`dc.identifier.doi`, `dc.type`, `dc.identifier.uri`, y en evidencia/matching: `dc.title`, `dc.date.issued`, `dc.date`, `dc.source`, `dc.identifier.issn`, `dc.publisher`, `dc.rights`, `dc.rights.uri`, `dc.contributor.author`). Otras keys: `REMOTE_AVAILABLE` + `LOCAL_PERSISTED` genérico, valor UNKNOWN hasta inspección de datos.
4. OpenAlex `select=` reduce el JSON; no se documentan campos OpenAlex no solicitados.
5. Crossref `searchWorks` recibe payload pero «candidates are not auto-applied».
6. Scopus `scopus_author` schema completo: NO DETERMINADO más allá de columnas usadas en JOIN.
7. Tests aportan evidencia de mapping pero no se ejecutaron.
8. No hay fuente de verdad única declarada para título/tipo/OA entre sistemas; el rol se infiere del uso (SOURCE vs EVIDENCE).
9. Cardinalidades 0..N de EV/DOI y status se demuestran por tablas hijas; 1 vs 0..1 de status actual: el INSERT reemplaza todos los status del RO (delete+insert), cardinalidad persistida **0..N filas**, cuál es “current” por `current_status` — detalles de selección de año: `PublishedYearExtractor` sobre valores de status, no columna en el padre.
