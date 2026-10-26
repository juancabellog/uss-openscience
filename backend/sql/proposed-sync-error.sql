-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente en el catálogo uss-ca.
--
-- sync_error: un registro por ítem que no pudo procesarse en una sync.
-- No guarda stack traces ni secretos.
-- sync_change sigue siendo solo auditoría de cambios exitosos.

CREATE TABLE IF NOT EXISTS `uss-ca`.`sync_error` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sync_execution_id BIGINT UNSIGNED NOT NULL,
    entity VARCHAR(50) NOT NULL,
    local_record_id BIGINT UNSIGNED NULL,
    pure_id BIGINT UNSIGNED NULL,
    record_uuid VARCHAR(255) NULL,
    stage VARCHAR(64) NOT NULL,
    error_code VARCHAR(100) NULL,
    error_message VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_sync_error_execution (sync_execution_id),
    KEY idx_sync_error_stage (sync_execution_id, stage, error_message(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
