-- PROPUESTO. NO ejecutar desde el agente.
-- Regularización de sync_execution.sync_mode para entidades que ANTES de SyncMode
-- solo existían como recorrido FULL: PEOPLE, RESEARCH_OUTPUT, JOURNALS, ORCID, UNPAYWALL.
--
-- NO tocar:
--   CROSSREF  → ya podía saltar DOI frescos (incremental de facto) con sync_mode NULL.
--   OPENALEX / DSPACE → nacieron con la columna; NULL no se interpreta.
--
-- No agrega baseline_valid. La baseline se deriva de:
--   sync_mode FULL (o NULL histórico de las entidades de arriba)
--   AND status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS') para dual-mode
--   AND status = 'COMPLETED' para PURE.

-- =============================================================================
-- 0. ¿Existe la columna?
-- =============================================================================

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'uss-ca'
  AND TABLE_NAME = 'sync_execution'
  AND COLUMN_NAME = 'sync_mode';

-- Si la consulta 0 no devuelve filas: ejecutar primero backend/sql/proposed-sync-mode.sql
-- (ALTER + índice). Este archivo asume que la columna ya existe.

-- =============================================================================
-- 1. DIAGNÓSTICO — últimas ejecuciones ORCID
-- =============================================================================

SELECT
    id,
    entity,
    COALESCE(sync_mode, '(NULL)') AS sync_mode,
    status,
    started_at,
    finished_at,
    processed,
    error_count,
    error_code
FROM `uss-ca`.`sync_execution`
WHERE entity = 'ORCID'
ORDER BY started_at DESC, id DESC
LIMIT 20;

-- =============================================================================
-- 2. DIAGNÓSTICO — últimas ejecuciones UNPAYWALL
-- =============================================================================

SELECT
    id,
    entity,
    COALESCE(sync_mode, '(NULL)') AS sync_mode,
    status,
    started_at,
    finished_at,
    processed,
    error_count,
    error_code
FROM `uss-ca`.`sync_execution`
WHERE entity = 'UNPAYWALL'
ORDER BY started_at DESC, id DESC
LIMIT 20;

-- =============================================================================
-- 3. DIAGNÓSTICO — NULL por entidad (candidatos al backfill)
-- =============================================================================

SELECT
    entity,
    status,
    COUNT(*) AS null_mode_runs,
    MIN(started_at) AS first_started_at,
    MAX(COALESCE(finished_at, started_at)) AS last_at
FROM `uss-ca`.`sync_execution`
WHERE sync_mode IS NULL
GROUP BY entity, status
ORDER BY entity, status;

-- =============================================================================
-- 4. SELECT PREVIO al UPDATE (solo entidades de recorrido FULL histórico)
-- =============================================================================

SELECT
    id,
    entity,
    sync_mode,
    status,
    started_at,
    finished_at
FROM `uss-ca`.`sync_execution`
WHERE sync_mode IS NULL
  AND entity IN ('PEOPLE', 'RESEARCH_OUTPUT', 'JOURNALS', 'ORCID', 'UNPAYWALL')
ORDER BY entity, started_at, id;

-- =============================================================================
-- 5. UPDATE preciso — clasifica como FULL únicamente lo que el código histórico
--    siempre recorrió completo. No incluye CROSSREF / OPENALEX / DSPACE.
-- =============================================================================

UPDATE `uss-ca`.`sync_execution`
SET sync_mode = 'FULL'
WHERE sync_mode IS NULL
  AND entity IN ('PEOPLE', 'RESEARCH_OUTPUT', 'JOURNALS', 'ORCID', 'UNPAYWALL');

-- =============================================================================
-- 6. SELECT POSTERIOR
-- =============================================================================

SELECT
    entity,
    COALESCE(sync_mode, '(NULL)') AS sync_mode,
    status,
    COUNT(*) AS executions
FROM `uss-ca`.`sync_execution`
WHERE entity IN ('ORCID', 'UNPAYWALL', 'PEOPLE', 'RESEARCH_OUTPUT', 'JOURNALS', 'CROSSREF')
GROUP BY entity, sync_mode, status
ORDER BY entity, sync_mode, status;

-- =============================================================================
-- 7. Baseline efectiva que debería ver el backend tras el UPDATE (o con el JAR
--    que interpreta NULL de ORCID/UNPAYWALL como FULL)
-- =============================================================================

SELECT
    e.entity,
    MAX(CASE
        WHEN (e.sync_mode = 'FULL' OR e.sync_mode IS NULL)
         AND e.status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS')
        THEN COALESCE(e.finished_at, e.started_at)
    END) AS last_valid_full_at,
    (SELECT s.sync_mode FROM `uss-ca`.`sync_execution` s
     WHERE s.entity = e.entity
     ORDER BY s.started_at DESC, s.id DESC LIMIT 1) AS last_mode,
    (SELECT s.status FROM `uss-ca`.`sync_execution` s
     WHERE s.entity = e.entity
     ORDER BY s.started_at DESC, s.id DESC LIMIT 1) AS last_status
FROM `uss-ca`.`sync_execution` e
WHERE e.entity IN ('ORCID', 'UNPAYWALL')
GROUP BY e.entity;
