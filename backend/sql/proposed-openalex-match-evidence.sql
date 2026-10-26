-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente UNA SOLA VEZ en el catálogo `uss-ca`.
--
-- Catálogo OpenAlex vacío: solo esquema. Sin backfill ni UPDATE de filas.
-- NO elimina uq_openalex_match_work.
-- NO agrega UNIQUE(openalex_work_id, openalex_author_id) a openalex_authorship.
-- NO borra columnas legacy de openalex_person_match_work.

-- =============================================================================
-- 1. Flags raw_json vs authorships
-- =============================================================================

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN raw_json_truncated TINYINT(1) NOT NULL DEFAULT 0',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'raw_json_truncated'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN raw_json_original_size INT NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'raw_json_original_size'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =============================================================================
-- 2. Tabla hija de evidencias
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`openalex_person_match_work_evidence` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    match_work_id BIGINT UNSIGNED NOT NULL,
    authorship_id BIGINT UNSIGNED NULL,
    source_order INT NOT NULL DEFAULT 0,
    raw_author_name VARCHAR(500) NULL,
    author_display_name VARCHAR(500) NULL,
    openalex_author_id VARCHAR(32) NULL,
    author_position VARCHAR(16) NULL,
    affiliation_display VARCHAR(500) NULL,
    ror VARCHAR(128) NULL,
    name_compatible TINYINT(1) NOT NULL DEFAULT 0,
    name_partition_different TINYINT(1) NOT NULL DEFAULT 0,
    position_code VARCHAR(32) NULL,
    orcid_pure VARCHAR(19) NULL,
    orcid_openalex VARCHAR(19) NULL,
    uss_affiliation TINYINT(1) NOT NULL DEFAULT 0,
    reason_codes VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_openalex_match_work_evidence (match_work_id, source_order),
    KEY idx_openalex_match_work_evidence_author (openalex_author_id),
    KEY idx_openalex_match_work_evidence_authorship (authorship_id),
    CONSTRAINT fk_openalex_match_work_evidence_work
        FOREIGN KEY (match_work_id) REFERENCES `uss-ca`.`openalex_person_match_work` (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_openalex_match_work_evidence_authorship
        FOREIGN KEY (authorship_id) REFERENCES `uss-ca`.`openalex_authorship` (id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
