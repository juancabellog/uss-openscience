-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente UNA SOLA VEZ en el catálogo `uss-ca`.
--
-- Progreso por fase OpenAlex y vínculo de recovery AUTHORS.
-- No modifica sync_execution #48.
-- RECOVERY se persiste en sync_execution.sync_mode (VARCHAR existente).
-- No habilita incremental: RECOVERY no es FULL.

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_sync_stats` ADD COLUMN phase VARCHAR(16) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_sync_stats' AND COLUMN_NAME = 'phase'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_sync_stats` ADD COLUMN authors_total BIGINT NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_sync_stats' AND COLUMN_NAME = 'authors_total'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_sync_stats` ADD COLUMN authors_processed BIGINT NOT NULL DEFAULT 0',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_sync_stats' AND COLUMN_NAME = 'authors_processed'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_sync_stats` ADD COLUMN authors_created BIGINT NOT NULL DEFAULT 0',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_sync_stats' AND COLUMN_NAME = 'authors_created'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_sync_stats` ADD COLUMN authors_updated BIGINT NOT NULL DEFAULT 0',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_sync_stats' AND COLUMN_NAME = 'authors_updated'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_sync_stats` ADD COLUMN source_sync_execution_id BIGINT NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_sync_stats' AND COLUMN_NAME = 'source_sync_execution_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD KEY idx_openalex_work_last_sync_execution (last_sync_execution_id)',
        'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work'
      AND INDEX_NAME = 'idx_openalex_work_last_sync_execution'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_author` ADD KEY idx_openalex_author_needs_refresh (needs_refresh)',
        'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_author'
      AND INDEX_NAME = 'idx_openalex_author_needs_refresh'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
