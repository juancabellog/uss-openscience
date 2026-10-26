-- VALIDACIÓN MANUAL. NO ejecutar desde el agente.

-- A) Personas activas / inactivas
SELECT
    COALESCE(active_in_pure, 1) AS active_in_pure,
    COUNT(*) AS people
FROM `uss-ca`.`pure_person`
GROUP BY COALESCE(active_in_pure, 1);

-- B) Research Outputs activos / inactivos
SELECT
    COALESCE(active_in_pure, 1) AS active_in_pure,
    COUNT(*) AS research_outputs
FROM `uss-ca`.`pure_research_output`
GROUP BY COALESCE(active_in_pure, 1);

-- C) Personas con removed_from_pure_at
SELECT id, uuid, first_name, last_name, active_in_pure, removed_from_pure_at, last_change_detected_at
FROM `uss-ca`.`pure_person`
WHERE removed_from_pure_at IS NOT NULL
ORDER BY removed_from_pure_at DESC
LIMIT 50;

-- D) Últimos cambios detectados
SELECT id, uuid, first_name, last_name, last_seen_in_pure_at, last_change_detected_at, fecha_modificacion_pure
FROM `uss-ca`.`pure_person`
ORDER BY last_change_detected_at DESC
LIMIT 50;

SELECT id, uuid, title, last_seen_in_pure_at, last_change_detected_at, modified_date
FROM `uss-ca`.`pure_research_output`
ORDER BY last_change_detected_at DESC
LIMIT 50;

-- E) Última sync_execution
SELECT *
FROM `uss-ca`.`sync_execution`
ORDER BY started_at DESC
LIMIT 10;

-- F) sync_change de una ejecución (reemplazar :syncId)
SELECT *
FROM `uss-ca`.`sync_change`
WHERE sync_execution_id = :syncId
ORDER BY id
LIMIT 200;

-- G) Invariante de conteos para la última PEOPLE completa
-- processed ≈ created + updated + unchanged + reactivated
-- marked_inactive_count son padres locales no vistos; no forman parte de processed.
SELECT
    id,
    entity,
    status,
    processed,
    created_count,
    updated_count,
    unchanged_count,
    reactivated_count,
    marked_inactive_count,
    (created_count + updated_count + unchanged_count + reactivated_count) AS seen_sum,
    processed - (created_count + updated_count + unchanged_count + reactivated_count) AS seen_delta
FROM `uss-ca`.`sync_execution`
WHERE entity IN ('PEOPLE', 'RESEARCH_OUTPUT')
  AND status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS')
ORDER BY started_at DESC
LIMIT 10;
