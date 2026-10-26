-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente en el catálogo `uss-ca`.
--
-- record_uuid = UUID PURE (sigue VARCHAR(128) en producción).
-- record_key  = DOI / URL / lookup key de la sincronización.
-- NO ampliar record_uuid para meter URLs.

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`sync_error` ADD COLUMN record_key VARCHAR(1000) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'sync_error' AND COLUMN_NAME = 'record_key'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`sync_change` ADD COLUMN record_key VARCHAR(1000) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'sync_change' AND COLUMN_NAME = 'record_key'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
