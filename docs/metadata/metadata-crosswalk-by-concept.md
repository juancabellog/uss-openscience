# Matriz de equivalencias por concepto (wide-form)

Agrupación técnica (`canonical_concept_candidate`). **No** es el modelo canónico USS.

Leyenda de celdas:

- `NOT_IMPLEMENTED` — sistema sin integración
- `NOT_PRESENT` — no hay campo/entidad en esta implementación
- `NOT_PERSISTED` — aparece en JSON/API pero no hay columna
- `NOT_USED_BY_CURRENT_IMPLEMENTATION` — código auxiliar sin flujo
- `UNKNOWN` / `NO DETERMINADO`
- `LEGACY`

Capas: PURE remoto ≠ `pure_*` local. OPENSCIENCE-USS no tiene tabla bibliográfica propia distinta del espejo PURE.

---

## Research Output — identificación

| Concepto | PURE remoto | OPENSCIENCE-USS (`pure_*`) | OpenAlex | Crossref | DSpace | ORCID | Unpaywall | Scopus |
|---|---|---|---|---|---|---|---|---|
| UUID | JSON uuid del research output | `pure_research_output.uuid` | NOT_PRESENT (usa OpenAlex work id) | NOT_PRESENT | `dspace_item.uuid` (item, no PURE) | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT |
| PURE ID | JSON pureId | `pure_research_output.pure_id` | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT |
| DOI | Electronic version `doi` (hijo) | `pure_research_output_electronic_version.doi` 0..N; **no** columna en el padre | JSON `doi` → `openalex_work.doi` + `normalized_doi` | `message.DOI` → `original_doi` / `normalized_doi` / `canonical_doi` | `dc.identifier.doi` → `doi_raw` + `normalized_doi` | external-id type doi | `doi` + `normalized_doi` + `doi_url` | `doi` + `doi_normalized` (CSV) |
| Handle | NOT_PRESENT en sync RO inspeccionado | NOT_PRESENT en INSERT RO | NOT_PRESENT | NOT_PRESENT | `handle` + `dc.identifier.uri` | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT |
| OpenAlex Work ID | NOT_PRESENT | Enlace `openalex_research_output` | JSON `id` → `openalex_work_id` | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT |
| Scopus EID | posible identifier hijo (ver `pure_research_output_identifier`) | `type_discriminator`/`value` si PURE lo envía | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | external-id `eid` | NOT_PRESENT | `scopus_publication.eid` |
| PMID | identifier hijo si PURE lo envía | `pure_research_output_identifier.value` | NOT_PRESENT en WORK_SELECT | NOT_PRESENT en mapper | metadata key UNKNOWN (persistida si DSpace la envía) | external-id posible | NOT_PRESENT en mapper | `pubmed_id` |

SciVal: `NOT_IMPLEMENTED` para todos los conceptos de esta tabla.

---

## Research Output — descripción bibliográfica

| Concepto | PURE remoto | OPENSCIENCE-USS | OpenAlex | Crossref | DSpace | ORCID | Unpaywall | Scopus |
|---|---|---|---|---|---|---|---|---|
| Título | `title` value\|es_CL\|en_US | `pure_research_output.title` | `display_name` | `message.title[0]` | HAL `name`; evidencia `dc.title`; matching usa `name` | `title.title.value` | `title` | `title` |
| Subtítulo | NO DETERMINADO en mapper RO | NOT_PRESENT en INSERT padre | NOT_PRESENT en WORK_SELECT | `subtitle` | UNKNOWN (key DSpace si existe se persiste) | `title.subtitle.value` | NOT_PRESENT | NOT_PRESENT en CREATE inspeccionado |
| Título alternativo/traducido | locales no ganadoras de `tituloDe` se pierden | NOT_PERSISTED extra | NOT_PRESENT | `short-title` | UNKNOWN | `title.translated-title.value` | NOT_PRESENT | NOT_PRESENT |
| Resumen | abstract locales | `abstract_es`, `abstract_en` | NOT_PRESENT en WORK_SELECT | NOT_PRESENT en mapper | UNKNOWN (`dc.description.abstract` no visto en matcher; persistido si DSpace lo envía) | NOT_PRESENT en mapper works básico | NOT_PRESENT | `abstract` |
| Idioma | language uri/es/en | `language_uri/es/en` | `language` | `language` | UNKNOWN | NOT_PRESENT en columnas group vistas | NOT_PRESENT | NOT_PRESENT |
| Tipo | type uri/es/en + `typeDiscriminator` | `type_uri/es/en`, `type_discriminator` | `type` → `work_type` | `type` → `work_type` | `dc.type` → `dc_type` | `work_type` | `genre` | `document_type` |
| Subtipo / categoría | category uri/es/en | `category_*` | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | `publication_stage` (otro concepto) |
| Fecha publicación | status date parts | `pure_research_output_status.publication_year/month/day` | `publication_date` | `published*` / `issued` date-parts | `dc.date.issued` / `dc.date` (evidencia) | year/month/day en group | `published_date` | NOT_PRESENT aparte del año |
| Año publicación | **DERIVED** status | **no** en padre; `publication_year` en status | `publication_year` | DERIVED via `PublishedYearExtractor` en evidencia | DERIVED regex | `publication_year` | `year` | `publication_year` |
| Estado publicación | status uri/es/en, current | `pure_research_output_status.*` | NOT_PRESENT | NOT_PRESENT | `in_archive` / `withdrawn` (ítem, no status PURE) | `active_in_orcid` | NOT_PRESENT | `publication_stage` |
| Peer review | peerReview | `peer_review` | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT |
| Páginas | pages / numberOfPages | `pages`, `number_of_pages` | NOT_PRESENT | `page` | UNKNOWN | NOT_PRESENT | NOT_PRESENT | `page_start`, `page_end` |
| Volumen | volume | `volume` | NOT_PRESENT | `volume` | UNKNOWN | NOT_PRESENT | NOT_PRESENT | `volume` |
| Número | journalNumber | `journal_number` | NOT_PRESENT | `issue` | UNKNOWN | NOT_PRESENT | NOT_PRESENT | `issue` |
| Article number | NO DETERMINADO en INSERT RO | NOT_PRESENT padre | NOT_PRESENT | `article-number` | UNKNOWN | NOT_PRESENT | NOT_PRESENT | `article_number` |

---

## Journal / fuente

| Concepto | PURE | OPENSCIENCE-USS | OpenAlex | Crossref | DSpace | ORCID | Unpaywall | Scopus |
|---|---|---|---|---|---|---|---|---|
| Journal identity | `/journals` uuid | `pure_journal.uuid` + link `pure_research_output_journal` | `primary_location.source.id` (no tabla journal) | no entidad journal persistida | ítem metadata | `journal-title` string | strings en work | `source_title` |
| Título revista | journal title | `pure_journal.title` + `pure_journal_title` | `primary_source_display_name` | `container-title` | `dc.source` evidencia | `journal_title` | `journal_name` | `source_title` |
| ISSN | issn + lista | `pure_journal.issn`, `pure_journal_issn.issn` | `issn_l` en source | `ISSN` / `issn-type` → `crossref_issn` | `dc.identifier.issn` evidencia | ISSN external-id | `journal_issns` | `issn` |
| ISSN-L | NO DETERMINADO | NOT_PRESENT columna | `primary_source_issn_l` | NOT_PRESENT columna ISSN-L | NOT_PRESENT | NOT_PRESENT | `journal_issn_l` | NOT_PRESENT |
| eISSN | no columna distinta | NOT_PRESENT | NOT_PRESENT | `issn_type` puede distinguirlo | UNKNOWN | UNKNOWN | mezclado en `journal_issns` | UNKNOWN |
| Publisher | `publisher_uuid`, `publisher_system_name` | mismas columnas | NOT_PRESENT (source ≠ publisher) | `publisher` | `dc.publisher` evidencia | NOT_PRESENT | `publisher` | NOT_PRESENT columna específica |

---

## Autoría / personas

| Concepto | PURE Person | PURE Contributor | OpenAlex | Crossref | DSpace | ORCID | Unpaywall | Scopus |
|---|---|---|---|---|---|---|---|---|
| First name | `first_name` | `first_name` | NOT_PRESENT (display) | `given` | NOT_PRESENT (string autor) | NOT_PRESENT en work contributor básico NO DETERMINADO | NOT_PRESENT | `author_name_short` / full CSV |
| Last name | `last_name` | `last_name` | NOT_PRESENT | `family` | NOT_PRESENT | NO DETERMINADO | NOT_PRESENT | ver CSV |
| Full / display | concat matching | concat matching | `author.display_name` **y** `raw_author_name` | `full_name` derivado | `dc.contributor.author` | ORCID iD persona | NOT_PRESENT | `authors_raw`, `author_full_names_raw` |
| Orden | NO DETERMINADO columna | orden de insert / no `position` en INSERT | `source_order`, `author_position` | `sequence_no` | `place` metadata | `orcid_work_contributor.position` | NOT_PRESENT | `author_position` |
| Corresponding | NOT_PRESENT persona | `corresponding_author` | `is_corresponding` | NOT_PRESENT en columnas author | NOT_PRESENT | NO DETERMINADO | NOT_PRESENT | `correspondence_address` (otro) |
| Role | staff `cargo` | `role_uri/es/en` | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NO DETERMINADO | NOT_PRESENT | NOT_PRESENT |
| ORCID | `pure_person.orcid` | NOT_PRESENT en INSERT contributor | `author.orcid`, `raw_orcid` → `author_orcid` / `observed_orcid`; author entity `orcid` | `ORCID` + `authenticated-orcid` | UNKNOWN | iD de la cuenta + API | NOT_PRESENT | `scopus_author.orcid` |
| OpenAlex Author ID | NOT_PRESENT | NOT_PRESENT | `openalex_author_id` | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT |
| Scopus Author ID | identifier type `Scopus ID` | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | `eid` es de work no author | NOT_PRESENT | `auth_id` / `scopus_author` |

---

## Afiliación / organización

| Concepto | PURE | OpenAlex | Crossref | DSpace | ORCID | Unpaywall | Scopus |
|---|---|---|---|---|---|---|---|
| Entidad institución | UUID only; **no** tabla org | `openalex_authorship_institution` | no entidad | no | `orcid_employment` | NOT_PRESENT | affiliation raw CSV |
| Texto afiliación | NOT_PRESENT (UUID) | `raw_affiliation_string` | `crossref_author_affiliation.name` | UNKNOWN | employment | NOT_PRESENT | `affiliations_raw` |
| ROR | NOT_PRESENT | `ror` | heurística `id` contains ror.org | NOT_PRESENT | NO DETERMINADO | NOT_PRESENT | NOT_PRESENT |
| Country | NOT_PRESENT | `country_code` | NOT_PRESENT en INSERT affiliation | NOT_PRESENT | NO DETERMINADO | NOT_PRESENT | NOT_PRESENT |

---

## Acceso / archivos / OA

| Concepto | PURE EV | OpenAlex | Crossref | DSpace | Unpaywall | ORCID | Scopus |
|---|---|---|---|---|---|---|---|
| Access type / OA | `access_type_*` | `open_access.is_oa`, `oa_status` (secundario) | licenses child | `access_status`, embargo | **fuente OA** `is_oa`, `oa_status` | NOT_PRESENT | `open_access` TEXT CSV |
| License | NOT_PRESENT EV INSERT | NOT_PRESENT WORK_SELECT | `crossref_license.license_url` | `dc.rights` / `dc.rights.uri` evidencia | `best_oa_license` / location.license | NOT_PRESENT | NOT_PRESENT |
| Version (AM/VoR) | `version_type_*` | NOT_PRESENT | `content_version` | bitstream/item UNKNOWN | location `version` | NOT_PRESENT | NOT_PRESENT |
| Embargo | NOT_PRESENT EV INSERT | NOT_PRESENT | `delay_in_days` | `embargo_date` | `oa_date` | NOT_PRESENT | NOT_PRESENT |
| Full text URL / PDF | NOT_PRESENT (DOI/access only) | NOT_PRESENT | NOT_PRESENT | bitstream `content_url`; **no PDF binario** | `url`, `url_for_pdf`, `url_for_landing_page` | NOT_PRESENT | `link` |
| File/bitstream | EV metadatos | NOT_PRESENT | NOT_PRESENT | `dspace_bitstream` | locations | NOT_PRESENT | NOT_PRESENT |
| Repository | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | comunidades/colecciones | `host_type`, `repository_institution` | NOT_PRESENT | NOT_PRESENT |

---

## Financiamiento / keywords / provenance

| Concepto | PURE | OpenAlex | Crossref | DSpace | ORCID | Unpaywall | Scopus |
|---|---|---|---|---|---|---|---|
| Funder / award | `funding_org_uuid` LEGACY/condicional | NOT_PRESENT WORK_SELECT | `crossref_funder` | UNKNOWN | NOT_PRESENT | NOT_PRESENT | `funding_details`, `funding_texts` |
| Project entity | NOT_IMPLEMENTED | NOT_IMPLEMENTED | NOT_IMPLEMENTED | NOT_IMPLEMENTED | NOT_IMPLEMENTED | NOT_IMPLEMENTED | NOT_IMPLEMENTED |
| Keywords | `pure_research_output_keyword*` | topics (otro vocabulario) | NOT_PRESENT | UNKNOWN | NOT_PRESENT | NOT_PRESENT | `author_keywords_raw`, `index_keywords_raw` |
| Topics OpenAlex | NOT_PRESENT | `openalex_work_topic` + primary_* | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT |
| OCDE | NO DETERMINADO (podría ser keyword URI) | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT | NOT_PRESENT |
| raw_json | NOT_PRESENT en INSERT RO | `openalex_work.raw_json` | `crossref_work.raw_json` | metadata relacional no blob | `orcid_work_group.raw_json` | NOT_PRESENT en CREATE work | CSV source_file |
| last_sync / outcome | fechas PURE created/modified | `last_sync_*`, `last_outcome` | `last_crossref_sync_at`, `status` | `last_sync_at`, `last_outcome` | `orcid_person_sync` | `retrieved_at`, `last_outcome` | `updated_at` |

---

## Relaciones persistidas

| Relación | Implementación |
|---|---|
| Publication → Person | `contributor.person_uuid` (no FK SQL demostrada a `pure_person` en el INSERT) |
| Publication → Contributor | `pure_research_output_contributor` |
| Publication → Organization | UUID en managing/contributor_org/external_org |
| Publication → Journal | `pure_research_output_journal` |
| Publication → Project | NOT_IMPLEMENTED |
| Publication → Dataset | NOT_IMPLEMENTED |
| Publication → File | electronic_version; DSpace bitstream |
| Publication → OpenAlex work | `openalex_research_output` |
| Publication → Crossref work | `crossref_research_output` |
| Publication → Unpaywall work | `unpaywall_research_output` |
| Publication → DSpace item | `dspace_item_match` |
| Person → OpenAlex author | `openalex_person_match` |
| Person → Scopus author | JOIN por Scopus ID |
| Person → ORCID works | `orcid_work*` por `person_id` |
