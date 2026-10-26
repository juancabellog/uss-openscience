-- PROPUESTO. NO ejecutar desde el agente.
-- Validación manual POST backfill de bitstreams DSpace.
-- No altera datos. No lanza FULL ni backfill.

-- 1. total dspace_bitstream
SELECT COUNT(*) AS bitstream_count
FROM `uss-ca`.`dspace_bitstream`;

-- 2. items distintos con ORIGINAL
SELECT COUNT(DISTINCT item_uuid) AS items_with_original
FROM `uss-ca`.`dspace_bitstream`
WHERE bundle_name = 'ORIGINAL';

-- 3. distribución número archivos/item (FOUND)
SELECT
    file_count,
    COUNT(*) AS items
FROM (
    SELECT i.uuid, COUNT(b.uuid) AS file_count
    FROM `uss-ca`.`dspace_item` i
    LEFT JOIN `uss-ca`.`dspace_bitstream` b
      ON b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL'
    WHERE i.last_outcome = 'FOUND'
    GROUP BY i.uuid
) d
GROUP BY file_count
ORDER BY file_count;

-- 4. open.access con/sin archivo
SELECT
    SUM(EXISTS (
        SELECT 1 FROM `uss-ca`.`dspace_bitstream` b
        WHERE b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL'
    )) AS with_file,
    SUM(NOT EXISTS (
        SELECT 1 FROM `uss-ca`.`dspace_bitstream` b
        WHERE b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL'
    )) AS without_file
FROM `uss-ca`.`dspace_item` i
WHERE i.last_outcome = 'FOUND'
  AND i.access_status = 'open.access';

-- 5. metadata.only con archivo
SELECT COUNT(*) AS metadata_only_with_file
FROM `uss-ca`.`dspace_item` i
WHERE i.last_outcome = 'FOUND'
  AND i.access_status = 'metadata.only'
  AND EXISTS (
      SELECT 1 FROM `uss-ca`.`dspace_bitstream` b
      WHERE b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL'
  );

-- 6. restricted con archivo
SELECT COUNT(*) AS restricted_with_file
FROM `uss-ca`.`dspace_item` i
WHERE i.last_outcome = 'FOUND'
  AND i.access_status = 'restricted'
  AND EXISTS (
      SELECT 1 FROM `uss-ca`.`dspace_bitstream` b
      WHERE b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL'
  );

-- 7. embargo con archivo
SELECT COUNT(*) AS embargo_with_file
FROM `uss-ca`.`dspace_item` i
WHERE i.last_outcome = 'FOUND'
  AND i.access_status = 'embargo'
  AND EXISTS (
      SELECT 1 FROM `uss-ca`.`dspace_bitstream` b
      WHERE b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL'
  );

-- 8. withFile + withoutFile = FOUND
SELECT
    SUM(i.last_outcome = 'FOUND') AS found_items,
    SUM(i.last_outcome = 'FOUND' AND EXISTS (
        SELECT 1 FROM `uss-ca`.`dspace_bitstream` b
        WHERE b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL'
    )) AS with_file,
    SUM(i.last_outcome = 'FOUND' AND NOT EXISTS (
        SELECT 1 FROM `uss-ca`.`dspace_bitstream` b
        WHERE b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL'
    )) AS without_file,
    SUM(i.last_outcome = 'FOUND' AND EXISTS (
        SELECT 1 FROM `uss-ca`.`dspace_bitstream` b
        WHERE b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL'
    )) + SUM(i.last_outcome = 'FOUND' AND NOT EXISTS (
        SELECT 1 FROM `uss-ca`.`dspace_bitstream` b
        WHERE b.item_uuid = i.uuid AND b.bundle_name = 'ORIGINAL'
    )) AS with_plus_without
FROM `uss-ca`.`dspace_item` i;

-- 9. últimos stats del backfill (entity=DSPACE, sync_mode=RECOVERY)
SELECT
    e.id,
    e.entity,
    e.sync_mode,
    e.status,
    e.started_at,
    e.finished_at,
    e.total_expected,
    e.processed,
    e.updated_count,
    e.unchanged_count,
    e.error_count,
    e.error_code,
    e.error_message,
    s.items_found,
    s.open_access_count,
    s.metadata_only_count,
    s.restricted_count,
    s.embargo_count
FROM `uss-ca`.`sync_execution` e
LEFT JOIN `uss-ca`.`dspace_sync_stats` s ON s.sync_execution_id = e.id
WHERE e.entity = 'DSPACE'
  AND e.sync_mode = 'RECOVERY'
ORDER BY e.id DESC
LIMIT 5;

-- 10. errores del backfill más reciente
SELECT
    err.id,
    err.sync_execution_id,
    err.record_uuid,
    err.stage,
    err.error_code,
    err.error_message,
    err.created_at
FROM `uss-ca`.`sync_error` err
WHERE err.sync_execution_id = (
    SELECT e.id
    FROM `uss-ca`.`sync_execution` e
    WHERE e.entity = 'DSPACE'
      AND e.sync_mode = 'RECOVERY'
    ORDER BY e.id DESC
    LIMIT 1
)
ORDER BY err.id;
