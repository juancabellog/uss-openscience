# Propuesta preliminar: integración DSpace USS con OPENSCIENCE-USS

Estado: **diseño, sin implementación**. Lectura pública del repositorio
`https://repositorio.uss.cl` (DSpace 9.2, REST HAL). No se propone login,
escritura en DSpace ni escritura automática en PURE.

La evidencia de exploración está en [`docs/dspace-uss-api-exploration.txt`](dspace-uss-api-exploration.txt).

## 1. Objetivo

Añadir el repositorio institucional como **fuente de evidencia local** al lado
de Crossref, OpenAlex, Unpaywall, ORCID y Scopus, manteniendo PURE como fuente
de verdad institucional.

Usos previstos:

1. **Data Quality / Consistencia EXT-01..EXT-12**: comparar research outputs de
   PURE contra el ítem DSpace cuando exista join (DOI, handle o uuid).
2. **Cobertura OA institucional**: detectar si un output PURE tiene versión
   pública en el repositorio (`accessStatus=open.access` + bitstream ORIGINAL).
3. **Tesis y documentos sin DOI**: inventario local que OpenAlex/Crossref no cubren.
4. **Enriquecimiento de UI** (fase posterior): enlace handle, tipo `dc.type`,
   facultad `uss.facultad`, PDF público. Solo lectura.

Fuera de alcance en esta propuesta: depósito, embargo, workflow, login, sync
hacia DSpace, mutación de PURE, almacenamiento de PDF en MySQL.

## 2. Hallazgos de la API que condicionan el diseño

| Hecho observado | Consecuencia |
|---|---|
| DSpace 9.2, HAL `application/hal+json;charset=UTF-8`, HTTP/2 + Cloudflare SCL | Cliente Jakarta HTTP/2, timeouts, no persistir cookies XSRF |
| `GET /server/api/core/items` = 401 | Harvest por **discover search** o GET por UUID, nunca listado `/core/items` |
| GET ítem `{uuid}` anónimo = 200 | Detalle y metadata `dc.*` / `uss.*` son públicos |
| PID `id=uss/{handle}` = 302 al ítem; `id=doi:...` = 404 | Resolver handle vía `/pid/find`; DOI solo en `dc.identifier.doi` |
| Paginación default 20, máximo observado **1000** | Jobs con `size=100` (discover) o 100–200 (collections) |
| 17 comunidades, 85 colecciones, ~1207 ítems `open.access` (query=`*`) | Volumen acotado; harvest completo viable |
| Artículo con DOI de ejemplo: `metadata.only`, 0 bundles | DOI no implica full-text en DSpace |
| Tesis de ejemplo: `open.access`, PDF ORIGINAL público ~1.4 MiB | Guardar metadatos del bitstream, no el binario |
| `dc.rights` no es universal; sí `accessStatus` | EXT-09 por `accessStatus`; EXT-10 solo si hay `dc.rights` / `dc.rights.uri` |
| Autores sin `authority`/ORCID; a veces nombre de grupo | Reutilizar `AuthorNameMatcher`; no asumir ORCID |
| Esquema `uss` (carrera, facultad, programa, sede) | Columnas locales; no existen en OpenAlex |
| `entityType=null` | Ítems clásicos, no entidades configurables |
| OAI-PMH Identify en `/server/oai/request` | Canal B de harvest; REST es más rico (`uss.*`, accessStatus, bitstreams) |
| `/authn/status` `authenticated=false` | El alcance público no necesita login |
| Actuator `/health` = `DOWN` | No usarlo como probe; probe = GET `/server/api` |

## 3. Principios (alineados con EXT)

- PURE sigue siendo la fuente de verdad. DSpace es evidencia, no maestro.
- Un finding por regla + research output; N evidencias (DSPACE + OPENALEX + …).
- No emitir finding si el resultado es `CONSISTENT` o `NO_EXTERNAL_DATA`.
- Join primario: `DoiNormalizer` sobre `dc.identifier.doi`.
- El scan de Data Quality **no llama** a DSpace: lee tablas locales ya sincronizadas
  (mismo patrón que `OpenAlexEvidenceProvider` / `CrossrefEvidenceProvider`).
- `ExternalEvidenceProvider.available()` = tablas DSpace presentes y con filas.
- Jobs de sync: FULL / INCREMENTAL, heartbeat, `ExceptionDetail` con tabla/columna.
- No guardar tokens, cookies ni `dspace-xsrf-token`.

## 4. Arquitectura propuesta

```
                    ┌─────────────────────┐
                    │ DSpace 9.2 REST HAL │
                    │ repositorio.uss.cl  │
                    └──────────┬──────────┘
                               │ GET discover / items / bundles
                               │ (Jakarta Client, solo lectura)
                    ┌──────────▼──────────┐
                    │ DSpaceSyncService   │  OpenLiberty
                    │ DspaceClient        │
                    └──────────┬──────────┘
                               │ JDBC
                    ┌──────────▼──────────┐
                    │ MySQL uss-ca        │
                    │ dspace_item         │
                    │ dspace_item_metadata│
                    │ dspace_bitstream    │
                    │ dspace_item_match   │
                    └──────────┬──────────┘
           ┌───────────────────┼───────────────────┐
           ▼                   ▼                   ▼
  DspaceEvidenceProvider   IntegrationService   Angular (fase 2)
  (EXT-01,02,03,04,        sync monitor         enlace handle / OA
   09,10,12)               status DSPACE
           │
           ▼
     ConsistencyEvaluator  ←  PURE snapshot + OpenAlex + Crossref + Unpaywall
```

Capas:

1. **`cl.uss.ca.external.dspace.DspaceClient`**: GET HAL, paginación, 302 de
   `/pid/find` y `/dso/find`, errores Spring 401/404/405. Sin login.
2. **`DspaceSyncService`**: orquestación FULL/INCREMENTAL, mismo contrato de
   `sync_run` / `sync_execution` que PURE y OpenAlex.
3. **`DspaceRepository`**: upsert JDBC, textos largos en `TEXT` (evitar el
   precedente de `crossref_author_affiliation.name` VARCHAR corto).
4. **`DspaceEvidenceProvider`**: `ExternalEvidenceProvider` con
   `source() = "DSPACE"`, `loadByDois`, y más adelante `loadByResearchOutputId`
   para tesis sin DOI.
5. **Matching**: reutilizar el patrón `openalex_work` ↔ research_output
   (`dspace_item_match`).

OAI-PMH (`/server/oai/request`) queda como **canal de respaldo** para
inventario `oai_dc`, no como canal primario: pierde `uss.*`, `accessStatus` y
enlaces de bitstream.

## 5. Modelo MySQL (borrador)

Esquema `uss-ca`, tablas nuevas. No ejecutar SQL todavía.

```sql
-- dspace_item: una fila por UUID DSpace
-- uuid CHAR(36) PK
-- handle VARCHAR(64) UNIQUE NULL   -- uss/24032
-- name TEXT
-- dc_type VARCHAR(128)             -- Tesis, Artículo de revisión, Presentación
-- access_status VARCHAR(32)        -- open.access | restricted | metadata.only | embargo
-- embargo_date DATE NULL
-- in_archive TINYINT
-- discoverable TINYINT
-- withdrawn TINYINT
-- owning_collection_uuid CHAR(36) NULL
-- last_modified_remote DATETIME(6) NULL
-- last_sync_at DATETIME(6)
-- last_outcome VARCHAR(32)         -- FOUND | MISSING | ERROR
-- last_error TEXT NULL

-- dspace_item_metadata
-- (item_uuid, field, place) PK     -- field = dc.title | dc.identifier.doi | uss.facultad ...
-- language VARCHAR(16) NULL
-- value TEXT NOT NULL
-- authority VARCHAR(128) NULL
-- confidence INT NULL

-- dspace_bitstream  (solo metadatos, no BLOB)
-- uuid CHAR(36) PK
-- item_uuid CHAR(36)
-- bundle_name VARCHAR(32)          -- ORIGINAL | THUMBNAIL | TEXT | LICENSE
-- name VARCHAR(512)
-- size_bytes BIGINT
-- checksum_md5 CHAR(32) NULL
-- mime_type VARCHAR(128) NULL
-- access_status VARCHAR(32) NULL
-- content_url VARCHAR(512)         -- .../bitstreams/{uuid}/content
-- public_download TINYINT          -- 1 si HEAD/GET anónimo 200

-- dspace_item_match
-- item_uuid, research_output_id
-- match_method VARCHAR(32)         -- DOI | HANDLE | TITLE_AUTHOR_YEAR | MANUAL
-- confidence DECIMAL(5,4) NULL
-- active TINYINT
```

Índices: `normalized_doi` generado o columna materializada en `dspace_item`
(igual que `openalex_work.normalized_doi`); índice `handle`; índice
`(access_status, dc_type)`.

No persistir PDF, TXT extraído ni thumbnails. El TEXT bundle de DSpace es
derivado (Tika) y no es fuente de verdad.

## 6. Estrategia de harvest

### 6.1 FULL

1. GET `/server/api` (health funcional + `dspaceVersion`).
2. Paginar `/core/communities` y `/core/collections` (catálogo, size ≤ 100).
3. Discover:
   - `GET /discover/search/objects?query=*&dsoType=item&size=100`
   - recorrer `page` hasta `totalPages` (tope size 1000, usar 100 para no
     saturar Cloudflare ni el JSON de facetas).
4. Por cada hit: upsert mínimo (uuid, handle, name, metadata embebida).
5. Enriquecer en segundo paso solo si hace falta:
   - GET `/core/items/{uuid}/accessStatus`
   - GET `/bundles` → `/bitstreams` si `access_status=open.access`
6. Matching DOI contra `research_output` PURE (y contra `openalex_work`).

Volumen estimado: miles de ítems, no cientos de miles. Un FULL nocturno es
razonable.

### 6.2 INCREMENTAL

Opciones (elegir una en implementación):

- Discover con filtro de fecha si el índice lo permite (`lastModified` está en
  el ítem; confirmar parámetro Solr en una exploración posterior).
- OAI `ListIdentifiers?from=` (datestamp desde Identify:
  `earliestDatestamp=2020-11-12`).
- FULL diferencial: re-leer páginas y comparar `lastModified`.

Recomendación inicial: FULL diferencial por `lastModified` (simple, API ya
probada). Evaluar OAI `from` en una segunda exploración (`ListIdentifiers`
únicamente, aún sin `ListRecords` masivo).

### 6.3 Lo que no se hace

- Login `/authn/login` ni password grant.
- POST/PUT/PATCH/DELETE.
- GET `/core/items` listado.
- Seguir `submitter`, `epersons`, `adminGroup`.
- Descargar binarios al disco de aplicación salvo un HEAD ocasional para
  marcar `public_download`.
- Llamar DSpace desde el scan DQ.

## 7. Join con PURE / OpenAlex

Orden de matching (un ítem DSpace puede matchear 0..n outputs; un output 0..1
ítem canónico):

1. **DOI**: `DoiNormalizer.normalize(dc.identifier.doi)` =
   `research_output.normalized_doi` = `openalex_work.normalized_doi`.
2. **Handle** si PURE o un campo local guarda la URL
   `https://repositorio.uss.cl/handle/uss/{n}`.
3. **UUID** si se captura a mano o desde un electronic version PURE.
4. **Título + año + autores** (`AuthorNameMatcher` + Jaccard de título, mismo
   umbral que Crossref): solo tesis / presentaciones, `match_method=TITLE_AUTHOR_YEAR`,
   confianza baja, revisión en UI.

Conflictos:

- Varios ítems DSpace con el mismo DOI (versión OA + registro metadata.only):
  preferir el de `open.access` con bitstream ORIGINAL; conservar el otro como
  evidencia secundaria.
- DOI en DSpace y no en PURE: no crear research output. Puede ser finding de
  cobertura (regla nueva, no EXT-01..12) o sugerencia en Integration Center.
- PURE con DOI y DSpace `metadata.only`: EXT-09 puede ser `DIFFERENCE_DETECTED`
  si PURE dice Open Access y el repo no tiene archivo (o al revés con Unpaywall).

## 8. Mapeo a reglas EXT

| Campo EXT | DSpace | Notas |
|---|---|---|
| EXT-01 título | `dc.title` (+ `dc.title.alternative`) | Comparar con PURE/OpenAlex |
| EXT-02 año | `dc.date.issued` / `dc.date` | A menudo solo año (`2019`) |
| EXT-03 revista | `dc.source` | Ausente en tesis |
| EXT-04 ISSN | `dc.identifier.issn` | Normalizar con `IssnNormalizer` |
| EXT-05 volumen | parsear `dc.relation.ispartof` | Frágil (`vol. 147 Issue: no. 9 Pages: …`); `NOT_COMPARABLE` si no parsea |
| EXT-06 número | idem | |
| EXT-07 páginas | idem | |
| EXT-08 publisher | `dc.publisher` | Frecuente en tesis (USS); raro en artículos metadata.only |
| EXT-09 acceso | `accessStatus.status` | Mapear `open.access` → OPEN; `restricted`/`embargo` → CLOSED; `metadata.only` → CLOSED o UNKNOWN (decidir en implementación: no hay archivo, no es OA de full-text) |
| EXT-10 licencia | `dc.rights` + `dc.rights.uri` | Muchos ítems no lo tienen → `NO_EXTERNAL_DATA` |
| EXT-11 funding | no observado | `NO_EXTERNAL_DATA` |
| EXT-12 autores | `dc.contributor.author` | Grupos corporativos; `authority` vacío |

`DspaceEvidenceProvider.capabilities()` sugerido: TITLE, PUBLICATION_YEAR,
JOURNAL_TITLE, ISSN, ACCESS_TYPE, LICENSE, AUTHORS. VOLUME/ISSUE/PAGES solo
si el parser de `dc.relation.ispartof` es estable.

Unpaywall seguirá cubriendo OA editorial; DSpace cubre **versión institucional**
(tesis, presentaciones, depósitos). Pueden discrepar (`EXTERNAL_SOURCE_CONFLICT`):
eso es deseable.

## 9. Cliente Java / OpenLiberty

- MicroProfile REST Client o `java.net.http.HttpClient` (el proyecto ya tiene
  clientes HTTP para OpenAlex/Crossref: copiar timeouts, User-Agent institucional,
  clipping de errores).
- Base URL configurable: `dspace.baseUrl=https://repositorio.uss.cl/server/api`
  (no hardcodear en código de negocio).
- `Accept: application/hal+json`.
- No `Authorization`. No jar de cookies.
- Seguir `_links` de item/bundles/bitstreams.
- 302: leer `Location` (pid/dso) y GET el ítem.
- 401/404: JSON `{timestamp,status,error,message,path}`.
- Rate limit conservador (p.ej. 5 rps) por Cloudflare.
- User-Agent: `OPENSCIENCE-USS/dspace-sync (contacto institucional)`.
- Probe: GET `/server/api` y comprobar `dspaceVersion`. Ignorar actuator health.

## 10. Superficie de producto (sin construir ahora)

- **Integration Center**: tarjeta `DSPACE` junto a PURE/OpenAlex: última sync,
  `dspaceVersion`, conteo de ítems, OA vs metadata.only, errores.
- **Data Quality**: source filter `DSPACE` (el drawer de evidencias ya es N por
  finding).
- **Person / output detail** (fase 2): chip “En repositorio USS” con handle y
  estado de acceso. No incrustar PDF.
- RBAC: sync DSpace = mismo rol que sync OpenAlex; lectura = analistas DQ.

## 11. Riesgos

| Riesgo | Mitigación |
|---|---|
| Cloudflare / bot (`human`, `__cf_bm`) | User-Agent estable, backoff, no paralelismo agresivo |
| Cookie XSRF en cada GET | No persistir; GET no la necesita |
| Listado `/core/items` 401 | Discover only |
| DOI no resoluble por PID | Índice propio `normalized_doi` |
| `dc.contributor.author` sucio | `AuthorNameMatcher`; no forzar ORCID |
| `dc.rights` incompleto | EXT-10 `NO_EXTERNAL_DATA`; no inventar CC |
| PDF grandes | Nunca BLOB; HEAD opcional |
| Health actuator DOWN | Probe HAL root |
| OAI `adminEmail=dspace-help@myu.edu` | Placeholder; no usarlo como contacto |
| Vocabulario `dc.type` local | Tabla de mapeo (Tesis, Artículo de revisión, Presentación, …) |
| Embargo | Guardar `embargoDate`; no marcar OA |

## 12. Fases sugeridas

**Fase 0** (esta entrega): exploración de solo lectura. Hecha.

**Fase 1 — Sync read-only**

- `DspaceClient` + tablas + job FULL.
- Matching DOI.
- Monitor de sync.
- Sin UI de ítem, sin provider EXT.

**Fase 2 — Evidencia DQ**

- `DspaceEvidenceProvider`.
- EXT-01, 02, 03, 04, 09, 12.
- Matching título/autor para tesis.

**Fase 3 — Producto**

- EXT-10 si hay `dc.rights`.
- Enlace handle en ficha de output.
- Filtro OA institucional.
- Evaluar OAI incremental.

**No hay Fase de escritura** en esta propuesta.

## 13. Criterios de aceptación (cuando se implemente)

- Ningún POST/PUT/PATCH/DELETE contra `repositorio.uss.cl`.
- Scan DQ no abre sockets a DSpace.
- Join DOI produce evidencia `DSPACE` en findings EXT cuando hay diferencia.
- Ítem `metadata.only` no se presenta como full-text OA.
- Bitstream ORIGINAL open.access tiene `content_url` y `public_download=1` sin
  almacenar el archivo.
- Logs de error incluyen tabla/columna/uuid DSpace, no solo el mensaje JDBC.
- Ningún secreto en logs ni en `docs/`.

## 14. Decisiones abiertas (no bloquean el diseño)

1. ¿`metadata.only` es CLOSED o UNKNOWN para EXT-09?
2. ¿Un ítem DSpace puede vincularse a varios electronic versions PURE?
3. ¿Se indexan THUMBNAIL/TEXT o solo ORIGINAL?
4. ¿El FULL recorre todas las colecciones o solo “Acceso Abierto USS” más
   tesis de facultades?
5. ¿Contacto operativo del repositorio? OAI aún muestra email de plantilla.

Recomendación por defecto: UNKNOWN para metadata.only; un match canónico por
output; persistir solo ORIGINAL (+ accessStatus del ítem); harvest global
`dsoType=item` (volumen bajo).
