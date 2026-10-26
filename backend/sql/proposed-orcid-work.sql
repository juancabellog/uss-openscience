-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente UNA SOLA VEZ en el catálogo `uss-ca`
-- SI TODAVÍA NO existen tablas ORCID.
--
-- Si ya ejecutaste el borrador v1 (orcid_work plano + orcid_work_external_id),
-- NO uses este archivo: usa proposed-orcid-work-migrate.sql.
--
-- Modelo:
--   orcid_work_group     = trabajo conceptual ORCID (group[])
--   orcid_work           = assertion / registro de una fuente (put-code)
-- Un DOI con Crossref + MDPI + Scopus = 1 group + 3 assertions.
-- NO son 3 publicaciones.
--
-- Identidad assertion: UNIQUE(orcid, put_code)
-- Identidad group local: UNIQUE(orcid, group_key)  -- group_key es técnica, no oficial ORCID
-- Un DOI puede repetirse entre personas (coautores). NO hay UNIQUE global de DOI.

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

CREATE TABLE IF NOT EXISTS `uss-ca`.`orcid_work` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    work_group_id BIGINT UNSIGNED NOT NULL,
    person_id BIGINT UNSIGNED NOT NULL,
    orcid VARCHAR(19) NOT NULL,
    put_code BIGINT NOT NULL,
    created_date_orcid BIGINT NULL,
    last_modified_orcid BIGINT NULL,
    title TEXT NULL,
    subtitle TEXT NULL,
    translated_title TEXT NULL,
    translated_title_language VARCHAR(16) NULL,
    journal_title VARCHAR(500) NULL,
    short_description TEXT NULL,
    citation_type VARCHAR(64) NULL,
    citation_value TEXT NULL,
    work_type VARCHAR(128) NULL,
    publication_year SMALLINT NULL,
    publication_month TINYINT NULL,
    publication_day TINYINT NULL,
    url VARCHAR(1000) NULL,
    language_code VARCHAR(16) NULL,
    country_code VARCHAR(8) NULL,
    visibility VARCHAR(32) NULL,
    path VARCHAR(255) NULL,
    display_index INT NULL,
    source_orcid_uri VARCHAR(255) NULL,
    source_orcid_path VARCHAR(32) NULL,
    source_client_id_uri VARCHAR(255) NULL,
    source_client_id_path VARCHAR(128) NULL,
    source_name VARCHAR(255) NULL,
    assertion_origin_orcid_uri VARCHAR(255) NULL,
    assertion_origin_orcid_path VARCHAR(32) NULL,
    assertion_origin_client_id_uri VARCHAR(255) NULL,
    assertion_origin_client_id_path VARCHAR(128) NULL,
    assertion_origin_name VARCHAR(255) NULL,
    active_in_orcid TINYINT(1) NOT NULL DEFAULT 1,
    last_seen_in_orcid_at DATETIME(6) NULL,
    last_seen_sync_id VARCHAR(64) NULL,
    removed_from_orcid_at DATETIME(6) NULL,
    detail_loaded TINYINT(1) NOT NULL DEFAULT 0,
    detail_loaded_at DATETIME(6) NULL,
    raw_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_orcid_work_orcid_put_code (orcid, put_code),
    KEY idx_orcid_work_group (work_group_id),
    KEY idx_orcid_work_person (person_id),
    KEY idx_orcid_work_put_code (put_code),
    KEY idx_orcid_work_active (active_in_orcid),
    CONSTRAINT fk_orcid_work_group
        FOREIGN KEY (work_group_id) REFERENCES `uss-ca`.`orcid_work_group` (id),
    CONSTRAINT fk_orcid_work_person
        FOREIGN KEY (person_id) REFERENCES `uss-ca`.`pure_person` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`orcid_work_external_id` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    orcid_work_id BIGINT UNSIGNED NOT NULL,
    identifier_type VARCHAR(64) NOT NULL,
    identifier_value VARCHAR(500) NOT NULL,
    normalized_value VARCHAR(500) NULL,
    normalized_transient TINYINT(1) NOT NULL DEFAULT 0,
    normalization_error VARCHAR(255) NULL,
    identifier_url VARCHAR(1000) NULL,
    relationship VARCHAR(32) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_orcid_ext_work (orcid_work_id),
    KEY idx_orcid_ext_type_norm (identifier_type, normalized_value(191)),
    CONSTRAINT fk_orcid_ext_work
        FOREIGN KEY (orcid_work_id) REFERENCES `uss-ca`.`orcid_work` (id)
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
-- FASE B — columnas legacy en pure_person (compatibilidad People Sync)
-- No DROP. Preferir orcid_person_sync para estado ORCID.
-- Si MySQL responde Duplicate column name, omita FASE B y continue.
-- =============================================================================

ALTER TABLE `uss-ca`.`pure_person`
    ADD COLUMN orcid_sync_pending TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE `uss-ca`.`pure_person`
    ADD COLUMN last_orcid_sync_at DATETIME(6) NULL;
ALTER TABLE `uss-ca`.`pure_person`
    ADD COLUMN last_orcid_sync_status VARCHAR(32) NULL;

-- =============================================================================
-- FASE C — lock
-- =============================================================================

INSERT INTO `uss-ca`.`sync_control` (entity, active_sync_id, locked_at, locked_by)
VALUES ('ORCID', NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE entity = entity;

-- =============================================================================
-- FASE D — backfill: no aplica en instalación limpia.
-- =============================================================================

-- =============================================================================
-- FASE E — DROP legacy OPCIONAL. NO ejecutar.
-- Cuando orcid_person_sync esté poblado:
-- ALTER TABLE `uss-ca`.`pure_person` DROP COLUMN orcid_sync_pending;
-- ALTER TABLE `uss-ca`.`pure_person` DROP COLUMN last_orcid_sync_at;
-- ALTER TABLE `uss-ca`.`pure_person` DROP COLUMN last_orcid_sync_status;
