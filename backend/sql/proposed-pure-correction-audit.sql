-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente en el catálogo uss-ca.
--
-- Auditoría de correcciones manuales en PURE (uno a uno).
-- No es sync_execution / sync_change.
-- No guarda api-key, headers ni payload completo.

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_correction_audit` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    performed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    performed_by VARCHAR(128) NULL,
    entity VARCHAR(50) NOT NULL,
    scope VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    local_parent_id BIGINT UNSIGNED NULL,
    parent_pure_id BIGINT UNSIGNED NULL,
    parent_uuid CHAR(36) NULL,
    child_pure_id BIGINT UNSIGNED NULL,
    child_value VARCHAR(500) NULL,
    extra VARCHAR(500) NULL,
    error_code VARCHAR(100) NULL,
    error_message VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY idx_pure_correction_entity (entity, performed_at),
    KEY idx_pure_correction_parent (local_parent_id, child_pure_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
