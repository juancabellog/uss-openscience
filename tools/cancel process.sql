create view v_status_processes as
SELECT c.entity, c.active_sync_id, c.locked_by, c.locked_at,
       e.status, e.started_at, e.heartbeat_at, e.processed, e.error_code, e.error_message
FROM `uss-ca`.`sync_control` c
LEFT JOIN `uss-ca`.`sync_execution` e ON e.id = c.active_sync_id;

UPDATE `uss-ca`.`sync_control` c
INNER JOIN `uss-ca`.`sync_execution` e ON e.id = c.active_sync_id
SET
    c.active_sync_id = NULL,
    c.locked_at = NULL,
    c.locked_by = NULL,
    e.status = 'FAILED',
    e.finished_at = NOW(6),
    e.heartbeat_at = NOW(6),
    e.error_code = 'STALE_SYNC_RECOVERED',
    e.error_message = 'Abandoned synchronization recovered because heartbeat expired. Inactive marks were not applied.'
WHERE c.entity = 'OPENALEX'
  AND e.status IN ('PENDING', 'RUNNING');


SELECT
    table_name AS tabla,
    ROUND(data_length / 1024 / 1024, 2) AS datos_mb,
    ROUND(index_length / 1024 / 1024, 2) AS indices_mb,
    ROUND((data_length + index_length) / 1024 / 1024, 2) AS total_mb,
    table_rows AS filas_aprox
FROM information_schema.tables
WHERE table_schema = 'uss-ca'
ORDER BY (data_length + index_length) DESC;

Si prefieres verlo en GB para las tablas grandes:
SELECT
    table_name AS tabla,
    ROUND(data_length / 1024 / 1024 / 1024, 3) AS datos_gb,
    ROUND(index_length / 1024 / 1024 / 1024, 3) AS indices_gb,
    ROUND((data_length + index_length) / 1024 / 1024 / 1024, 3) AS total_gb,
    table_rows AS filas_aprox
FROM information_schema.tables
WHERE table_schema = 'uss-ca'
ORDER BY (data_length + index_length) DESC;

Y para ver además el total completo de la base:
SELECT
    table_schema AS base_datos,
    ROUND(SUM(data_length) / 1024 / 1024 / 1024, 3) AS datos_gb,
    ROUND(SUM(index_length) / 1024 / 1024 / 1024, 3) AS indices_gb,
    ROUND(SUM(data_length + index_length) / 1024 / 1024 / 1024, 3) AS total_gb
FROM information_schema.tables
WHERE table_schema = 'uss-ca'
GROUP BY table_schema;