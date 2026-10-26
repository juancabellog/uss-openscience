-- PROPUESTO. NO ejecutar desde el agente.
-- SOLO si ya ejecutaste el SQL v1 (orcid_work plano + orcid_work_external_id).
-- Instalación limpia: usar proposed-orcid-work.sql y NO este archivo.
--
-- Cada ALTER es una sentencia independiente. Si una columna ya existe,
-- omita esa sentencia y continue.

-- =============================================================================
-- FASE A — tablas nuevas
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`orcid_person_sync` (
    person_id BIGINT UNSIGNED NOT NULL,
    orcid VARCHAR(19) NULL,
    pending TINYINT(1) NOT NULL DEFAULT 0,
    last_sync_at DATETIME(6) NULL,
    last_sync_status VARCHAR(32) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (person_id),
    KEY idx_orcid_person_sync_pending (pending),
    CONSTRAINT fk_orcid_person_sync_person
        FOREIGN KEY (person_id) REFERENCES `uss-ca`.`pure_person` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`orcid_work_group` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    person_id BIGINT UNSIGNED NOT NULL,
    orcid VARCHAR(19) NOT NULL,
    group_key VARCHAR(80) NOT NULL,
    group_key_kind VARCHAR(16) NOT NULL,
    last_modified_orcid BIGINT NULL,
    title TEXT NULL,
    journal_title VARCHAR(500) NULL,
    work_type VARCHAR(128) NULL,
    publication_year SMALLINT NULL,
    publication_month TINYINT NULL,
    publication_day TINYINT NULL,
    display_doi VARCHAR(500) NULL,
    active_in_orcid TINYINT(1) NOT NULL DEFAULT 1,
    last_seen_in_orcid_at DATETIME(6) NULL,
    last_seen_sync_id VARCHAR(64) NULL,
    removed_from_orcid_at DATETIME(6) NULL,
    raw_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_orcid_work_group_key (orcid, group_key),
    KEY idx_orcid_work_group_person (person_id),
    KEY idx_orcid_work_group_orcid (orcid),
    KEY idx_orcid_work_group_active (active_in_orcid),
    CONSTRAINT fk_orcid_work_group_person
        FOREIGN KEY (person_id) REFERENCES `uss-ca`.`pure_person` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`orcid_work_group_external_id` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    work_group_id BIGINT UNSIGNED NOT NULL,
    identifier_type VARCHAR(64) NOT NULL,
    identifier_value VARCHAR(500) NOT NULL,
    normalized_value VARCHAR(500) NULL,
    normalized_transient TINYINT(1) NOT NULL DEFAULT 0,
    normalization_error VARCHAR(255) NULL,
    identifier_url VARCHAR(1000) NULL,
    relationship VARCHAR(32) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_orcid_group_ext_group (work_group_id),
    KEY idx_orcid_group_ext_type_norm (identifier_type, normalized_value(191)),
    CONSTRAINT fk_orcid_group_ext_group
        FOREIGN KEY (work_group_id) REFERENCES `uss-ca`.`orcid_work_group` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`orcid_work_contributor` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    orcid_work_id BIGINT UNSIGNED NOT NULL,
    position INT NOT NULL,
    contributor_orcid VARCHAR(19) NULL,
    credit_name VARCHAR(500) NULL,
    contributor_email VARCHAR(320) NULL,
    attributes_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_orcid_contrib_work (orcid_work_id),
    CONSTRAINT fk_orcid_contrib_work
        FOREIGN KEY (orcid_work_id) REFERENCES `uss-ca`.`orcid_work` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- FASE B — ALTER orcid_work existente
-- =============================================================================

ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN work_group_id BIGINT UNSIGNED NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN created_date_orcid BIGINT NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN subtitle TEXT NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN translated_title TEXT NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN translated_title_language VARCHAR(16) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN short_description TEXT NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN citation_type VARCHAR(64) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN citation_value TEXT NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN language_code VARCHAR(16) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN country_code VARCHAR(8) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN path VARCHAR(255) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN display_index INT NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN source_orcid_uri VARCHAR(255) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN source_orcid_path VARCHAR(32) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN source_client_id_uri VARCHAR(255) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN source_client_id_path VARCHAR(128) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN assertion_origin_orcid_uri VARCHAR(255) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN assertion_origin_orcid_path VARCHAR(32) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN assertion_origin_client_id_uri VARCHAR(255) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN assertion_origin_client_id_path VARCHAR(128) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN assertion_origin_name VARCHAR(255) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN removed_from_orcid_at DATETIME(6) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN detail_loaded TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN detail_loaded_at DATETIME(6) NULL;
ALTER TABLE `uss-ca`.`orcid_work` ADD COLUMN raw_json JSON NULL;

ALTER TABLE `uss-ca`.`orcid_work_external_id` ADD COLUMN normalized_value VARCHAR(500) NULL;
ALTER TABLE `uss-ca`.`orcid_work_external_id` ADD COLUMN normalized_transient TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE `uss-ca`.`orcid_work_external_id` ADD COLUMN normalization_error VARCHAR(255) NULL;

-- =============================================================================
-- FASE C — índices / FK
-- =============================================================================

ALTER TABLE `uss-ca`.`orcid_work` ADD KEY idx_orcid_work_group (work_group_id);
ALTER TABLE `uss-ca`.`orcid_work` ADD KEY idx_orcid_work_put_code (put_code);
ALTER TABLE `uss-ca`.`orcid_work_external_id` ADD KEY idx_orcid_ext_type_norm (identifier_type, normalized_value(191));

-- =============================================================================
-- FASE D — backfill: un group temporal por assertion (put-code).
-- La siguiente sync ORCID recalcula group_key real y fusiona por DOI self.
-- =============================================================================

INSERT INTO `uss-ca`.`orcid_work_group` (
    person_id, orcid, group_key, group_key_kind, last_modified_orcid, title, journal_title,
    work_type, publication_year, publication_month, publication_day, display_doi,
    active_in_orcid, last_seen_in_orcid_at, last_seen_sync_id
)
SELECT
    w.person_id,
    w.orcid,
    SHA2(CONCAT(w.orcid, '\nput:', w.put_code), 256),
    'PUT',
    w.last_modified_orcid,
    w.title,
    w.journal_title,
    w.work_type,
    w.publication_year,
    w.publication_month,
    w.publication_day,
    NULL,
    w.active_in_orcid,
    w.last_seen_in_orcid_at,
    w.last_seen_sync_id
FROM `uss-ca`.`orcid_work` w
WHERE w.work_group_id IS NULL;

UPDATE `uss-ca`.`orcid_work` w
JOIN `uss-ca`.`orcid_work_group` g
  ON g.orcid = w.orcid
 AND g.group_key = SHA2(CONCAT(w.orcid, '\nput:', w.put_code), 256)
SET w.work_group_id = g.id
WHERE w.work_group_id IS NULL;

ALTER TABLE `uss-ca`.`orcid_work`
    MODIFY work_group_id BIGINT UNSIGNED NOT NULL;

ALTER TABLE `uss-ca`.`orcid_work`
    ADD CONSTRAINT fk_orcid_work_group
        FOREIGN KEY (work_group_id) REFERENCES `uss-ca`.`orcid_work_group` (id);

INSERT INTO `uss-ca`.`sync_control` (entity, active_sync_id, locked_at, locked_by)
VALUES ('ORCID', NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE entity = entity;

-- =============================================================================
-- FASE E — DROP legacy OPCIONAL. NO ejecutar.
-- source_client_id (columna v1) queda deprecated si ya existe source_client_id_path.
-- ALTER TABLE `uss-ca`.`orcid_work` DROP COLUMN source_client_id;
-- ALTER TABLE `uss-ca`.`pure_person` DROP COLUMN orcid_sync_pending;
-- ALTER TABLE `uss-ca`.`pure_person` DROP COLUMN last_orcid_sync_at;
-- ALTER TABLE `uss-ca`.`pure_person` DROP COLUMN last_orcid_sync_status;
