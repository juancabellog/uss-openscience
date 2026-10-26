-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente UNA SOLA VEZ en el catálogo `uss-ca`.
--
-- Unpaywall v2 es enriquecimiento OA por DOI. NO escribe en PURE.
-- Identidad: UNIQUE(normalized_doi). Un DOI compartido = 1 work + N mappings.
--
-- FASE A — tablas nuevas
-- FASE B — lock sync_control
-- FASE C — índices de consulta (además de UNIQUE y FKs)

-- =============================================================================
-- FASE A — tablas nuevas
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`unpaywall_work` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    doi VARCHAR(500) NULL,
    normalized_doi VARCHAR(500) NOT NULL,
    doi_url VARCHAR(1000) NULL,
    last_outcome VARCHAR(16) NOT NULL,
    is_oa TINYINT(1) NULL,
    oa_status VARCHAR(32) NULL,
    genre VARCHAR(128) NULL,
    title TEXT NULL,
    year SMALLINT NULL,
    published_date VARCHAR(32) NULL,
    publisher VARCHAR(500) NULL,
    journal_name VARCHAR(500) NULL,
    journal_issns VARCHAR(255) NULL,
    journal_issn_l VARCHAR(32) NULL,
    journal_is_oa TINYINT(1) NULL,
    journal_is_in_doaj TINYINT(1) NULL,
    has_repository_copy TINYINT(1) NULL,
    best_oa_host_type VARCHAR(32) NULL,
    best_oa_license VARCHAR(128) NULL,
    best_oa_version VARCHAR(64) NULL,
    best_oa_url VARCHAR(2000) NULL,
    best_oa_landing_page_url VARCHAR(2000) NULL,
    best_oa_pdf_url VARCHAR(2000) NULL,
    api_updated_at DATETIME(6) NULL,
    retrieved_at DATETIME(6) NULL,
    last_http_status INT NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(1000) NULL,
    last_sync_execution_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_unpaywall_work_normalized_doi (normalized_doi),
    KEY idx_unpaywall_work_is_oa (is_oa),
    KEY idx_unpaywall_work_oa_status (oa_status),
    KEY idx_unpaywall_work_outcome (last_outcome),
    KEY idx_unpaywall_work_retrieved (retrieved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`unpaywall_oa_location` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    unpaywall_work_id BIGINT UNSIGNED NOT NULL,
    location_key VARCHAR(190) NOT NULL,
    endpoint_id VARCHAR(128) NULL,
    host_type VARCHAR(32) NULL,
    license VARCHAR(128) NULL,
    version VARCHAR(64) NULL,
    url VARCHAR(2000) NULL,
    url_for_landing_page VARCHAR(2000) NULL,
    url_for_pdf VARCHAR(2000) NULL,
    oa_date VARCHAR(32) NULL,
    is_best TINYINT(1) NOT NULL DEFAULT 0,
    evidence VARCHAR(255) NULL,
    repository_institution VARCHAR(500) NULL,
    pmh_id VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_unpaywall_oa_location_key (unpaywall_work_id, location_key),
    KEY idx_unpaywall_oa_location_work (unpaywall_work_id),
    KEY idx_unpaywall_oa_location_host (host_type),
    CONSTRAINT fk_unpaywall_oa_location_work
        FOREIGN KEY (unpaywall_work_id) REFERENCES `uss-ca`.`unpaywall_work` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`unpaywall_research_output` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    unpaywall_work_id BIGINT UNSIGNED NOT NULL,
    research_output_id BIGINT UNSIGNED NOT NULL,
    electronic_version_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_unpaywall_ro_link (unpaywall_work_id, research_output_id, electronic_version_id),
    KEY idx_unpaywall_ro_output (research_output_id),
    KEY idx_unpaywall_ro_version (electronic_version_id),
    CONSTRAINT fk_unpaywall_ro_work
        FOREIGN KEY (unpaywall_work_id) REFERENCES `uss-ca`.`unpaywall_work` (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_unpaywall_ro_output
        FOREIGN KEY (research_output_id) REFERENCES `uss-ca`.`pure_research_output` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`unpaywall_sync_stats` (
    sync_execution_id BIGINT NOT NULL,
    not_found_count BIGINT NOT NULL DEFAULT 0,
    oa_count BIGINT NOT NULL DEFAULT 0,
    non_oa_count BIGINT NOT NULL DEFAULT 0,
    skipped_fresh_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (sync_execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- FASE B — lock
-- =============================================================================

INSERT IGNORE INTO `uss-ca`.`sync_control` (entity, active_sync_id, locked_at, locked_by)
VALUES ('UNPAYWALL', NULL, NULL, NULL);

-- =============================================================================
-- FASE C — índices en fuente DOI (solo si NO existen)
-- Revisar primero:
--   SHOW INDEX FROM `uss-ca`.`pure_research_output_electronic_version`;
-- Si ya hay índice sobre (research_output_id, type_discriminator) o doi, no crear otro.
--
-- CREATE INDEX idx_pro_ev_doi_type
--     ON `uss-ca`.`pure_research_output_electronic_version` (type_discriminator, research_output_id);
