-- PROPUESTO. NO ejecutar desde el agente.
-- Si ya ejecutaste proposed-pure-sync.sql, omite el primer bloque ALTER
-- (active_in_pure, last_seen_in_pure_at, removed_from_pure_at, last_seen_sync_id)
-- y ejecuta solo last_change_detected_at + sync_change.

-- Bloque A. Presencia PURE (omitir si las columnas ya existen).
ALTER TABLE `uss-ca`.`pure_person`
    ADD COLUMN active_in_pure TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN last_seen_in_pure_at DATETIME(6) NULL,
    ADD COLUMN removed_from_pure_at DATETIME(6) NULL,
    ADD COLUMN last_seen_sync_id VARCHAR(64) NULL;

ALTER TABLE `uss-ca`.`pure_research_output`
    ADD COLUMN active_in_pure TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN last_seen_in_pure_at DATETIME(6) NULL,
    ADD COLUMN removed_from_pure_at DATETIME(6) NULL,
    ADD COLUMN last_seen_sync_id VARCHAR(64) NULL;

CREATE INDEX idx_pure_person_active_in_pure
    ON `uss-ca`.`pure_person` (active_in_pure);
CREATE INDEX idx_pure_person_last_seen_sync_id
    ON `uss-ca`.`pure_person` (last_seen_sync_id);
CREATE INDEX idx_pure_ro_active_in_pure
    ON `uss-ca`.`pure_research_output` (active_in_pure);
CREATE INDEX idx_pure_ro_last_seen_sync_id
    ON `uss-ca`.`pure_research_output` (last_seen_sync_id);

-- Bloque B. Detección local de cambio (siempre, si no existe).
ALTER TABLE `uss-ca`.`pure_person`
    ADD COLUMN last_change_detected_at DATETIME(6) NULL;
ALTER TABLE `uss-ca`.`pure_research_output`
    ADD COLUMN last_change_detected_at DATETIME(6) NULL;

CREATE INDEX idx_pure_person_last_change
    ON `uss-ca`.`pure_person` (last_change_detected_at);
CREATE INDEX idx_pure_ro_last_change
    ON `uss-ca`.`pure_research_output` (last_change_detected_at);

-- Bloque C. Auditoría de cambios reales. No registrar UNCHANGED.
CREATE TABLE IF NOT EXISTS `uss-ca`.`sync_change` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sync_execution_id BIGINT UNSIGNED NOT NULL,
    entity VARCHAR(32) NOT NULL,
    local_record_id BIGINT NULL,
    pure_id BIGINT NULL,
    record_uuid VARCHAR(255) NULL,
    change_type VARCHAR(32) NOT NULL,
    change_scope VARCHAR(32) NOT NULL,
    description VARCHAR(255) NULL,
    old_value VARCHAR(1000) NULL,
    new_value VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_sync_change_execution (sync_execution_id),
    KEY idx_sync_change_entity_record (entity, local_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
