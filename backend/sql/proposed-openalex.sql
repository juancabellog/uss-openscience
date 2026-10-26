-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente UNA SOLA VEZ en el catálogo `uss-ca`.
--
-- OpenAlex es enriquecimiento/evidencia. NO escribe en PURE.
-- Identidad Work: UNIQUE(normalized_doi) y UNIQUE(openalex_work_id).
-- Identidad Author: UNIQUE(openalex_author_id).
-- Asociación persona: openalex_person_match, NUNCA pure_person ni
-- pure_person_identifiers (esos espejan PURE).
--
-- Entity de lock: OPENALEX (un solo lock para Works + Authors + matching).
-- FULL e INCREMENTAL no corren a la vez.
--
-- referenced_works: solo referenced_works_count (sin child table).
-- OA OpenAlex es evidencia secundaria; Unpaywall sigue siendo la fuente OA.

-- =============================================================================
-- FASE A — tablas
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`openalex_work` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    openalex_work_id VARCHAR(32) NULL,
    doi VARCHAR(500) NULL,
    normalized_doi VARCHAR(500) NOT NULL,
    display_name TEXT NULL,
    publication_year SMALLINT NULL,
    publication_date VARCHAR(32) NULL,
    work_type VARCHAR(64) NULL,
    cited_by_count INT NULL,
    referenced_works_count INT NULL,
    is_oa TINYINT(1) NULL,
    oa_status VARCHAR(32) NULL,
    language VARCHAR(16) NULL,
    primary_source_id VARCHAR(32) NULL,
    primary_source_display_name VARCHAR(500) NULL,
    primary_source_issn_l VARCHAR(32) NULL,
    primary_topic_id VARCHAR(32) NULL,
    primary_topic_display_name VARCHAR(500) NULL,
    primary_topic_score DECIMAL(8,6) NULL,
    domain_id VARCHAR(32) NULL,
    domain_display_name VARCHAR(255) NULL,
    field_id VARCHAR(32) NULL,
    field_display_name VARCHAR(255) NULL,
    subfield_id VARCHAR(32) NULL,
    subfield_display_name VARCHAR(255) NULL,
    authorships_count INT NULL,
    authorships_truncated TINYINT(1) NOT NULL DEFAULT 0,
    raw_json_truncated TINYINT(1) NOT NULL DEFAULT 0,
    raw_json_original_size INT NULL,
    openalex_updated_date DATETIME(6) NULL,
    last_outcome VARCHAR(24) NOT NULL,
    last_http_status INT NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(1000) NULL,
    last_sync_execution_id BIGINT NULL,
    last_sync_at DATETIME(6) NULL,
    last_change_detected_at DATETIME(6) NULL,
    needs_refresh TINYINT(1) NOT NULL DEFAULT 0,
    parent_snapshot VARCHAR(4000) NULL,
    authorship_snapshot longtext NULL,
    raw_json LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_openalex_work_normalized_doi (normalized_doi),
    UNIQUE KEY uq_openalex_work_oa_id (openalex_work_id),
    KEY idx_openalex_work_outcome (last_outcome),
    KEY idx_openalex_work_synced (last_sync_at),
    KEY idx_openalex_work_needs_refresh (needs_refresh)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`openalex_author` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    openalex_author_id VARCHAR(32) NOT NULL,
    display_name VARCHAR(500) NULL,
    orcid VARCHAR(19) NULL,
    works_count INT NULL,
    cited_by_count INT NULL,
    openalex_updated_date DATETIME(6) NULL,
    last_outcome VARCHAR(24) NULL,
    last_http_status INT NULL,
    last_error_code VARCHAR(64) NULL,
    last_sync_execution_id BIGINT NULL,
    last_sync_at DATETIME(6) NULL,
    needs_refresh TINYINT(1) NOT NULL DEFAULT 0,
    parent_snapshot VARCHAR(4000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_openalex_author_oa_id (openalex_author_id),
    KEY idx_openalex_author_orcid (orcid),
    KEY idx_openalex_author_synced (last_sync_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`openalex_authorship` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    openalex_work_id BIGINT UNSIGNED NOT NULL,
    source_order INT NOT NULL DEFAULT 0,
    author_position VARCHAR(16) NULL,
    is_corresponding TINYINT(1) NOT NULL DEFAULT 0,
    raw_author_name VARCHAR(500) NULL,
    raw_affiliation_string TEXT NULL,
    openalex_author_id VARCHAR(32) NULL,
    author_display_name VARCHAR(500) NULL,
    author_orcid VARCHAR(19) NULL,
    observed_orcid VARCHAR(19) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_openalex_authorship_order (openalex_work_id, source_order),
    KEY idx_openalex_authorship_author (openalex_author_id),
    CONSTRAINT fk_openalex_authorship_work
        FOREIGN KEY (openalex_work_id) REFERENCES `uss-ca`.`openalex_work` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`openalex_authorship_institution` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    authorship_id BIGINT UNSIGNED NOT NULL,
    source_order INT NOT NULL DEFAULT 0,
    openalex_institution_id VARCHAR(32) NULL,
    display_name VARCHAR(500) NULL,
    ror VARCHAR(64) NULL,
    country_code VARCHAR(8) NULL,
    type VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_openalex_auth_inst_authorship (authorship_id),
    KEY idx_openalex_auth_inst_ror (ror),
    CONSTRAINT fk_openalex_auth_inst_authorship
        FOREIGN KEY (authorship_id) REFERENCES `uss-ca`.`openalex_authorship` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`openalex_work_topic` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    openalex_work_id BIGINT UNSIGNED NOT NULL,
    topic_id VARCHAR(32) NOT NULL,
    display_name VARCHAR(500) NULL,
    score DECIMAL(8,6) NULL,
    is_primary TINYINT(1) NOT NULL DEFAULT 0,
    domain_id VARCHAR(32) NULL,
    domain_display_name VARCHAR(255) NULL,
    field_id VARCHAR(32) NULL,
    field_display_name VARCHAR(255) NULL,
    subfield_id VARCHAR(32) NULL,
    subfield_display_name VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_openalex_work_topic (openalex_work_id, topic_id),
    CONSTRAINT fk_openalex_work_topic_work
        FOREIGN KEY (openalex_work_id) REFERENCES `uss-ca`.`openalex_work` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`openalex_research_output` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    openalex_work_id BIGINT UNSIGNED NOT NULL,
    research_output_id BIGINT UNSIGNED NOT NULL,
    electronic_version_id BIGINT UNSIGNED NULL,
    active_in_pure TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_openalex_ro_link (openalex_work_id, research_output_id, electronic_version_id),
    KEY idx_openalex_ro_output (research_output_id),
    CONSTRAINT fk_openalex_ro_work
        FOREIGN KEY (openalex_work_id) REFERENCES `uss-ca`.`openalex_work` (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_openalex_ro_output
        FOREIGN KEY (research_output_id) REFERENCES `uss-ca`.`pure_research_output` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`openalex_person_match` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    person_id BIGINT UNSIGNED NOT NULL,
    openalex_author_id VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    match_method VARCHAR(64) NULL,
    selected TINYINT(1) NOT NULL DEFAULT 0,
    selected_manually TINYINT(1) NOT NULL DEFAULT 0,
    matching_work_count INT NOT NULL DEFAULT 0,
    orcid_match TINYINT(1) NOT NULL DEFAULT 0,
    uss_affiliation TINYINT(1) NOT NULL DEFAULT 0,
    review_requested TINYINT(1) NOT NULL DEFAULT 0,
    evidence_json JSON NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_by VARCHAR(128) NULL,
    rejected_at DATETIME(6) NULL,
    rejected_by VARCHAR(128) NULL,
    last_evaluated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_openalex_person_author (person_id, openalex_author_id),
    KEY idx_openalex_match_status (status),
    KEY idx_openalex_match_author (openalex_author_id),
    KEY idx_openalex_match_works (matching_work_count),
    CONSTRAINT fk_openalex_match_person
        FOREIGN KEY (person_id) REFERENCES `uss-ca`.`pure_person` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`openalex_person_match_work` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    match_id BIGINT UNSIGNED NOT NULL,
    research_output_id BIGINT UNSIGNED NOT NULL,
    normalized_doi VARCHAR(500) NOT NULL,
    openalex_work_oa_id VARCHAR(32) NULL,
    contributor_id BIGINT UNSIGNED NULL,
    contributor_first_name VARCHAR(255) NULL,
    contributor_last_name VARCHAR(255) NULL,
    contributor_position INT NULL,
    contributor_count INT NULL,
    raw_author_name VARCHAR(500) NULL,
    author_position VARCHAR(16) NULL,
    source_order INT NULL,
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
    UNIQUE KEY uq_openalex_match_work (match_id, research_output_id),
    KEY idx_openalex_match_work_doi (normalized_doi),
    CONSTRAINT fk_openalex_match_work_match
        FOREIGN KEY (match_id) REFERENCES `uss-ca`.`openalex_person_match` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1 match_work : N evidencias de authorship. OpenAlex puede repetir el mismo
-- Author ID en un Work con raw_author_name distintos. NO UNIQUE(work, author_id).
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

CREATE TABLE IF NOT EXISTS `uss-ca`.`openalex_sync_stats` (
    sync_execution_id BIGINT NOT NULL,
    found_count BIGINT NOT NULL DEFAULT 0,
    not_found_count BIGINT NOT NULL DEFAULT 0,
    invalid_count BIGINT NOT NULL DEFAULT 0,
    authors_upserted BIGINT NOT NULL DEFAULT 0,
    matches_strong BIGINT NOT NULL DEFAULT 0,
    matches_review BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (sync_execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- FASE B — lock
-- =============================================================================

INSERT IGNORE INTO `uss-ca`.`sync_control` (entity, active_sync_id, locked_at, locked_by)
VALUES ('OPENALEX', NULL, NULL, NULL);

-- =============================================================================
-- FASE C — Data Quality VIEW_ONLY
-- =============================================================================

INSERT INTO `uss-ca`.`data_quality_rule` (
    code, source_family, entity_type, field_name, name, description, rule_text, alert_text,
    bulk_procedure, category, correction_mode, enabled
) VALUES
('OPENALEX-001', 'OPENALEX', 'PERSON', 'openalex_author_id',
 'Match fuerte OpenAlex sin confirmar',
 'La persona tiene un candidato OpenAlex STRONG y aún no hay identidad confirmada localmente.',
 'No se escribe en PURE. Revisar evidencia y confirmar o rechazar.',
 'Hay un match OpenAlex fuerte pendiente de confirmación.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('OPENALEX-002', 'OPENALEX', 'PERSON', 'orcid',
 'ORCID PURE y OpenAlex en conflicto',
 'El ORCID institucional y el ORCID observado en OpenAlex no coinciden.',
 'Evidencia contradictoria. No elegir automáticamente.',
 'ORCID PURE y OpenAlex presentan conflicto.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('OPENALEX-003', 'OPENALEX', 'PERSON', 'openalex_author_id',
 'Varios OpenAlex Authors para una persona',
 'Una persona PURE tiene evidencia significativa con más de un OpenAlex Author.',
 'OpenAlex puede overmerge/split. Revisión manual.',
 'Una persona coincide con múltiples OpenAlex Authors.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('OPENALEX-004', 'OPENALEX', 'PERSON', 'openalex_author_id',
 'Un OpenAlex Author coincide con varias personas',
 'Un OpenAlex Author ID tiene evidencia significativa con más de una persona PURE.',
 'No asignar automáticamente.',
 'Un OpenAlex Author coincide con múltiples personas PURE.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    rule_text = VALUES(rule_text),
    alert_text = VALUES(alert_text);

-- =============================================================================
-- FASE D — alinear columnas si `openalex_work` ya existía
-- =============================================================================
-- CREATE TABLE IF NOT EXISTS no modifica una tabla previa. Si quedó un stub
-- sin last_outcome, GET /openalex/stats falla con Unknown column.
--
-- Comprobar:
--   SHOW COLUMNS FROM `uss-ca`.`openalex_work` LIKE 'last_outcome';
--
-- Si las tablas OpenAlex están vacías, es más simple DROP (en este orden)
-- y volver a FASE A:
--   DROP TABLE IF EXISTS `uss-ca`.`openalex_sync_stats`;
--   DROP TABLE IF EXISTS `uss-ca`.`openalex_person_match_work_evidence`;
--   DROP TABLE IF EXISTS `uss-ca`.`openalex_person_match_work`;
--   DROP TABLE IF EXISTS `uss-ca`.`openalex_person_match`;
--   DROP TABLE IF EXISTS `uss-ca`.`openalex_research_output`;
--   DROP TABLE IF EXISTS `uss-ca`.`openalex_work_topic`;
--   DROP TABLE IF EXISTS `uss-ca`.`openalex_authorship_institution`;
--   DROP TABLE IF EXISTS `uss-ca`.`openalex_authorship`;
--   DROP TABLE IF EXISTS `uss-ca`.`openalex_author`;
--   DROP TABLE IF EXISTS `uss-ca`.`openalex_work`;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN last_outcome VARCHAR(24) NOT NULL DEFAULT ''PENDING''',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'last_outcome'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN last_http_status INT NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'last_http_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN last_error_code VARCHAR(64) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'last_error_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN last_error_message VARCHAR(1000) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'last_error_message'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN last_sync_execution_id BIGINT NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'last_sync_execution_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN last_sync_at DATETIME(6) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'last_sync_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN last_change_detected_at DATETIME(6) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'last_change_detected_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN needs_refresh TINYINT(1) NOT NULL DEFAULT 0',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'needs_refresh'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN parent_snapshot VARCHAR(4000) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'parent_snapshot'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD COLUMN authorship_snapshot VARCHAR(4000) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND COLUMN_NAME = 'authorship_snapshot'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_author` ADD COLUMN last_outcome VARCHAR(24) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_author' AND COLUMN_NAME = 'last_outcome'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_author` ADD COLUMN needs_refresh TINYINT(1) NOT NULL DEFAULT 0',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_author' AND COLUMN_NAME = 'needs_refresh'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_person_match_work` ADD COLUMN affiliation_display VARCHAR(500) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_person_match_work' AND COLUMN_NAME = 'affiliation_display'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_person_match_work` ADD COLUMN ror VARCHAR(128) NULL',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_person_match_work' AND COLUMN_NAME = 'ror'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `uss-ca`.`openalex_work` ADD KEY idx_openalex_work_outcome (last_outcome)',
        'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'uss-ca' AND TABLE_NAME = 'openalex_work' AND INDEX_NAME = 'idx_openalex_work_outcome'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
