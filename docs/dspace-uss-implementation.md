# DSpace USS — implementación OPENSCIENCE-USS

Integración de **solo lectura** con https://repositorio.uss.cl/server/api (DSpace 9.2).
No hay POST/PUT/PATCH/DELETE contra DSpace, no hay login, no se persisten cookies ni XSRF, no se descargan PDF y no se escribe en PURE.

## Diagnóstico de compatibilidad (Fase 0)

La integración clona el vertical Unpaywall / Crossref / OpenAlex:

| Capacidad | Reutilizado |
| --- | --- |
| HTTP | `java.net.http.HttpClient` + Jackson |
| Jobs | `PureSyncService.startExternal`, `sync_execution`, `sync_control` |
| Política de modos | `SyncEntityPolicy.DSPACE` FULL+INCREMENTAL, baseline `ITEM_WARNINGS_OK` |
| Integration Center | `GET /integrations` + tarjeta Angular |
| Data Quality | `ExternalEvidenceProvider` + `ConsistencyEvaluator` |
| RBAC | `AppModule.INTEGRATIONS`, CONSULT lectura / ADMINISTER sync |

**Incremental:** re-recorre `discover` y omite enrich si `lastModified` ≤ watermark local. No se declara un filtro Solr/OAI por fecha: `/server/oai/request` no expone OAI-PMH usable desde la SPA.

**Inventario:** `GET /core/items` = 401. La recolección usa `GET /discover/search/objects?query=*&dsoType=item`.

## Arquitectura

```
HttpDspaceClient (GET HAL)
        │
DspaceSyncJob  FULL / INCREMENTAL + checkpoint
        │
DspaceRepository  upsert JDBC  →  dspace_*
        │
   ┌────┴────┐
DspaceMatcher   DspaceEvidenceProvider
   (PURE)       (DQ, tablas locales)
```

## Esquema SQL

Archivo: `backend/sql/proposed-dspace.sql` (propuesto; no ejecutar desde el agente).

Tablas: `dspace_community`, `dspace_collection`, `dspace_item`, `dspace_item_metadata`, `dspace_bitstream`, `dspace_item_match`, `dspace_sync_checkpoint`, `dspace_sync_stats`. Lock: fila `sync_control.entity = 'DSPACE'`.

UUID DSpace es PK. Handle único. DOI normalizado indexado. Metadata Dublin Core y `uss.*` en filas (campo, place, language, LONGTEXT). No hay BLOB de PDF.

## Componentes Java

- `cl.uss.ca.external.dspace.*` — cliente HAL, parser, clasificador OA, matcher, rate limiter.
- `DspaceRepository`, `DspaceSyncJob`, `DspaceService`, `DspaceResource`.
- `DspaceEvidenceProvider` — fuente `DSPACE`; `loadByDois` lee MySQL, no la API.

## Endpoints internos

| Método | Ruta | Permiso |
| --- | --- | --- |
| POST | `/sync/dspace?mode=FULL\|INCREMENTAL` | INTEGRATIONS / ADMINISTER |
| GET | `/sync/latest?entity=DSPACE` | INTEGRATIONS / CONSULT |
| GET | `/sync/policy?entity=DSPACE` | INTEGRATIONS / CONSULT |
| GET | `/dspace/stats` | INTEGRATIONS / CONSULT |
| GET | `/dspace/by-research-output/{id}` | INTEGRATIONS / CONSULT |
| GET | `/research-output/{id}/dspace` | RESEARCH_OUTPUTS / CONSULT |

## Configuración (`microprofile-config.properties`)

| Clave | Default |
| --- | --- |
| `dspace.enabled` | `true` |
| `dspace.api.url` | `https://repositorio.uss.cl/server/api` |
| `dspace.user.agent` | `OPENSCIENCE-USS/dspace-sync (Universidad San Sebastian)` |
| `dspace.connectTimeoutSeconds` | `10` |
| `dspace.readTimeoutSeconds` | `30` |
| `dspace.retryMaxAttempts` | `2` (429/5xx, backoff exponencial) |
| `dspace.sync.delayMs` | `200` |
| `dspace.pageSize` | `50` |
| `dspace.maxPageSize` | `100` |

## Jobs

FULL: comunidades → subcomunidades → colecciones → discover paginado → enrich (ítem, accessStatus, owningCollection, bitstreams ORIGINAL) → matching → `markMissing` solo si el crawl terminó sin errores.

INCREMENTAL: mismo recorrido; skip enrich si `lastModified` ≤ máximo local. No marca ausencias.

Checkpoint en `dspace_sync_checkpoint` permite reanudar FULL. Lock `sync_control` impide ejecuciones duplicadas.

## Matching PURE / DSpace

1. DOI normalizado (exacto, canónico si hay varios ítems).
2. Handle institucional.
3. UUID DSpace previamente conocido.
4. Título + año + autores → `CANDIDATE` (no se confirma si es ambiguo).

No crea publicaciones en PURE. No asigna ORCID por homonimia.

## Acceso abierto

Estados DSpace: `open.access`, `restricted`, `metadata.only`, `embargo` (+ `embargoDate`).

Texto completo institucional = bundle ORIGINAL + bitstream público. `metadata.only` no es OA full-text. EXT-09 mapea `metadata.only` / `open.access` sin archivo a `UNKNOWN` (no comparable). Un DOI no implica OA.

## Errores y reintentos

401/403/404/405 no se reintentan (`DSPACE_UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`). 429 y 5xx: hasta `retryMaxAttempts` con backoff. Ausencia temporal de la API no marca ítems `MISSING`.

## Limitaciones de la API pública

- `/core/items` exige autenticación.
- PID `doi:` 404; handle 302.
- OAI no validado como incremental.
- Tesis sin DOI no entran a Data Quality vía `loadByDois`.
- `discover query=*` es el inventario público, no un dump autenticado.

## Primera sincronización

1. Ejecutar **manualmente** `backend/sql/proposed-dspace.sql` en el catálogo `uss-ca` (no producción desde el agente).
2. Confirmar `DSPACE_API_URL` / `DSPACE_ENABLED` en OpenLiberty.
3. Reiniciar.
4. Integration Center → DSpace USS → sincronización FULL (rol ADMINISTER).
5. Validar: `GET /dspace/stats` (versión, ítems, OA, metadata.only), último job, matches y ficha de publicación PURE.

## Cómo validar

- Suite local: `DspaceIntegrationTest`, `AuthSecurityTest`, `ConsistencyRulesTest`, `SyncModePolicyTest`, `ExternalEvidenceCapabilityTest` (mains, no Surefire).
- Live opcional: `-Ddspace.live.test=true` en `DspaceLiveConnectivityTest` (GET raíz + 1 página discover).
