-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente si se desea tracking de presencia PURE.
-- El lock e historial de sincronización viven en proposed-sync-lock.sql
-- (sync_execution + sync_control). openscience_sync_run queda obsoleta.
-- last_change_detected_at + sync_change: proposed-sync-traceability.sql.

ALTER TABLE `uss-ca`.`pure_person`
    ADD COLUMN active_in_pure TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN last_seen_in_pure_at DATETIME NULL,
    ADD COLUMN removed_from_pure_at DATETIME NULL,
    ADD COLUMN last_seen_sync_id VARCHAR(64) NULL;

ALTER TABLE `uss-ca`.`pure_research_output`
    ADD COLUMN active_in_pure TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN last_seen_in_pure_at DATETIME NULL,
    ADD COLUMN removed_from_pure_at DATETIME NULL,
    ADD COLUMN last_seen_sync_id VARCHAR(64) NULL;

CREATE INDEX idx_pure_person_active_in_pure
    ON `uss-ca`.`pure_person` (active_in_pure);

CREATE INDEX idx_pure_person_last_seen_sync_id
    ON `uss-ca`.`pure_person` (last_seen_sync_id);

CREATE INDEX idx_pure_ro_active_in_pure
    ON `uss-ca`.`pure_research_output` (active_in_pure);

CREATE INDEX idx_pure_ro_last_seen_sync_id
    ON `uss-ca`.`pure_research_output` (last_seen_sync_id);

CREATE TABLE `uss-ca`.`openscience_sync_run` (
    id VARCHAR(64) NOT NULL,
    entity VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_by VARCHAR(128) NULL,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    total_expected INT NULL,
    processed BIGINT NOT NULL DEFAULT 0,
    created_count BIGINT NOT NULL DEFAULT 0,
    updated_count BIGINT NOT NULL DEFAULT 0,
    unchanged_count BIGINT NOT NULL DEFAULT 0,
    reactivated_count BIGINT NOT NULL DEFAULT 0,
    marked_inactive BIGINT NOT NULL DEFAULT 0,
    error_count BIGINT NOT NULL DEFAULT 0,
    message VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_sync_run_entity_started (entity, started_at),
    KEY idx_sync_run_status (status)
);
