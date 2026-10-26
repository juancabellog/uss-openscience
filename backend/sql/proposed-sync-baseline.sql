-- PROPUESTO. NO ejecutar desde el agente.
-- Inspección de baseline FULL/INCREMENTAL sobre sync_execution.
-- No altera datos. No agrega baseline_valid: se deriva de entity + sync_mode + status.
--
-- Regla actual:
--   Dual-mode (ORCID, UNPAYWALL, CROSSREF, OPENALEX):
--     FULL + COMPLETED | COMPLETED_WITH_WARNINGS  →  puede establecer baseline
--   PURE (PEOPLE, RESEARCH_OUTPUT, JOURNALS):
--     solo FULL + COMPLETED  →  establece baseline
--   INCREMENTAL nunca establece baseline.
--   sync_mode NULL (CROSSREF histórico) NO se trata como FULL.
--   Sin baseline la recomendación es FULL; INCREMENTAL sigue seleccionable.
--
-- Si hace falta invalidar baseline por un cambio de lógica futuro,
-- se puede agregar baseline_version. No se implementa ahora.

-- =============================================================================
-- 1. Ejecuciones por entidad, modo y estado
-- =============================================================================

SELECT
    entity,
    COALESCE(sync_mode, '(NULL)') AS sync_mode,
    status,
    COUNT(*) AS executions,
    MIN(COALESCE(finished_at, started_at)) AS first_at,
    MAX(COALESCE(finished_at, started_at)) AS last_at
FROM `uss-ca`.`sync_execution`
GROUP BY entity, sync_mode, status
ORDER BY entity, sync_mode, status;

-- =============================================================================
-- 2. Candidatas a baseline (FULL completada / con advertencias)
-- =============================================================================

SELECT
    entity,
    id,
    sync_mode,
    status,
    started_at,
    finished_at,
    processed,
    error_count,
    error_code
FROM `uss-ca`.`sync_execution`
WHERE sync_mode = 'FULL'
  AND status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS')
ORDER BY entity, COALESCE(finished_at, started_at) DESC, id DESC;

-- =============================================================================
-- 3. Entidades dual-mode sin ninguna FULL que pueda ser baseline
-- =============================================================================

SELECT e.entity
FROM (
    SELECT 'ORCID' AS entity
    UNION ALL SELECT 'UNPAYWALL'
    UNION ALL SELECT 'CROSSREF'
    UNION ALL SELECT 'OPENALEX'
) e
WHERE NOT EXISTS (
    SELECT 1
    FROM `uss-ca`.`sync_execution` s
    WHERE s.entity = e.entity
      AND s.sync_mode = 'FULL'
      AND s.status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS')
);

-- =============================================================================
-- 4. Solo INCREMENTAL históricas (nunca FULL) — no son baseline
-- =============================================================================

SELECT
    entity,
    COUNT(*) AS incremental_runs
FROM `uss-ca`.`sync_execution`
WHERE sync_mode = 'INCREMENTAL'
  AND entity IN ('ORCID', 'UNPAYWALL', 'CROSSREF', 'OPENALEX')
  AND entity NOT IN (
      SELECT DISTINCT entity
      FROM `uss-ca`.`sync_execution`
      WHERE sync_mode = 'FULL'
        AND status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS')
  )
GROUP BY entity;

-- =============================================================================
-- 5. CROSSREF / filas sin sync_mode (legacy). No inventar FULL.
-- =============================================================================

SELECT
    entity,
    status,
    COUNT(*) AS null_mode_runs,
    MAX(COALESCE(finished_at, started_at)) AS last_at
FROM `uss-ca`.`sync_execution`
WHERE sync_mode IS NULL
GROUP BY entity, status
ORDER BY entity, status;

-- =============================================================================
-- 6. Última FULL exitosa vs última ejecución, por entidad
-- =============================================================================

SELECT
    latest.entity,
    latest.sync_mode AS last_mode,
    latest.status AS last_status,
    latest.finished_at AS last_finished_at,
    baseline.id AS baseline_id,
    baseline.status AS baseline_status,
    baseline.finished_at AS last_successful_full_at
FROM `uss-ca`.`sync_execution` latest
LEFT JOIN `uss-ca`.`sync_execution` baseline
    ON baseline.entity = latest.entity
   AND baseline.sync_mode = 'FULL'
   AND baseline.status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS')
   AND baseline.id = (
        SELECT s2.id
        FROM `uss-ca`.`sync_execution` s2
        WHERE s2.entity = latest.entity
          AND s2.sync_mode = 'FULL'
          AND s2.status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS')
        ORDER BY COALESCE(s2.finished_at, s2.started_at) DESC, s2.id DESC
        LIMIT 1
   )
WHERE latest.id = (
    SELECT s3.id
    FROM `uss-ca`.`sync_execution` s3
    WHERE s3.entity = latest.entity
    ORDER BY s3.started_at DESC, s3.id DESC
    LIMIT 1
)
ORDER BY latest.entity;

-- No hay UPDATE. Si la inspección confirma FULL + COMPLETED (o warnings dual-mode)
-- esas filas ya sirven como baseline al desplegar el JAR. No backfill automático
-- de CROSSREF NULL → FULL.
