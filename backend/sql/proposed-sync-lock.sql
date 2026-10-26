-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente en el catálogo uss-ca antes de usar el lock MySQL.
--
-- sync_execution: historial y progreso de cada intento.
-- sync_control: UNA fila por entidad. active_sync_id IS NULL = libre.
--
-- RESEARCH_OUTPUT es la entidad canónica en código (alias HTTP: RESEARCH_OUTPUTS).
-- No crear una segunda fila RESEARCH_OUTPUTS: permitiría dos locks de la misma sync.

-- Schema real en uss-ca (no VARCHAR UUID):
-- sync_execution.id BIGINT UNSIGNED AUTO_INCREMENT
-- sync_control.active_sync_id BIGINT UNSIGNED NULL
-- sync_control no tiene created_at / updated_at
--
CREATE TABLE IF NOT EXISTS `uss-ca`.`sync_execution` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    entity VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    heartbeat_at DATETIME(6) NOT NULL,
    started_by VARCHAR(128) NULL,
    total_expected INT NULL,
    processed BIGINT NOT NULL DEFAULT 0,
    created_count BIGINT NOT NULL DEFAULT 0,
    updated_count BIGINT NOT NULL DEFAULT 0,
    unchanged_count BIGINT NOT NULL DEFAULT 0,
    reactivated_count BIGINT NOT NULL DEFAULT 0,
    marked_inactive_count BIGINT NOT NULL DEFAULT 0,
    error_count BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_sync_execution_entity_started (entity, started_at),
    KEY idx_sync_execution_status (status),
    KEY idx_sync_execution_heartbeat (heartbeat_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`sync_control` (
    entity VARCHAR(50) NOT NULL,
    active_sync_id BIGINT UNSIGNED NULL,
    locked_at DATETIME(6) NULL,
    locked_by VARCHAR(255) NULL,
    PRIMARY KEY (entity),
    UNIQUE KEY idx_sync_control_active (active_sync_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `uss-ca`.`sync_control` (entity, active_sync_id, locked_at, locked_by)
VALUES
    ('PEOPLE', NULL, NULL, NULL),
    ('RESEARCH_OUTPUT', NULL, NULL, NULL),
    ('ORCID', NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE entity = entity;
