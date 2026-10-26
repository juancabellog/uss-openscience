-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente UNA SOLA VEZ en el catálogo `uss-ca`.
--
-- Crossref es enriquecimiento/evidencia por DOI. NO escribe en PURE.
-- Identidad: UNIQUE(normalized_doi). Un DOI compartido = 1 work + N mappings.
-- Las referencias se guardan como reference_count + raw_json (sin child table en fase 1).
--
-- FASE A — tablas nuevas
-- FASE B — lock sync_control
-- FASE C — reglas Data Quality CROSSREF-001..010

-- =============================================================================
-- FASE A — tablas nuevas
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`crossref_work` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    original_doi VARCHAR(500) NULL,
    normalized_doi VARCHAR(500) NOT NULL,
    canonical_doi VARCHAR(500) NULL,
    status VARCHAR(24) NOT NULL,
    registration_agency VARCHAR(64) NULL,
    registration_agency_id VARCHAR(64) NULL,
    work_type VARCHAR(128) NULL,
    title TEXT NULL,
    subtitle TEXT NULL,
    short_title VARCHAR(500) NULL,
    container_title VARCHAR(1000) NULL,
    publisher VARCHAR(500) NULL,
    volume VARCHAR(64) NULL,
    issue VARCHAR(64) NULL,
    page_range VARCHAR(128) NULL,
    article_number VARCHAR(64) NULL,
    language VARCHAR(32) NULL,
    reference_count INT NULL,
    is_referenced_by_count INT NULL,
    published_date VARCHAR(32) NULL,
    published_print_date VARCHAR(32) NULL,
    published_online_date VARCHAR(32) NULL,
    issued_date VARCHAR(32) NULL,
    created_crossref_at DATETIME(6) NULL,
    deposited_crossref_at DATETIME(6) NULL,
    indexed_crossref_at DATETIME(6) NULL,
    has_orcid TINYINT(1) NOT NULL DEFAULT 0,
    has_affiliation TINYINT(1) NOT NULL DEFAULT 0,
    has_ror TINYINT(1) NOT NULL DEFAULT 0,
    has_funding TINYINT(1) NOT NULL DEFAULT 0,
    has_license TINYINT(1) NOT NULL DEFAULT 0,
    has_update TINYINT(1) NOT NULL DEFAULT 0,
    has_retraction TINYINT(1) NOT NULL DEFAULT 0,
    comparison_status VARCHAR(32) NULL,
    last_http_status INT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    last_sync_execution_id BIGINT NULL,
    last_crossref_sync_at DATETIME(6) NULL,
    last_change_detected_at DATETIME(6) NULL,
    parent_snapshot VARCHAR(4000) NULL,
    children_snapshot VARCHAR(4000) NULL,
    raw_json LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_crossref_work_normalized_doi (normalized_doi),
    KEY idx_crossref_work_status (status),
    KEY idx_crossref_work_type (work_type),
    KEY idx_crossref_work_published (published_date),
    KEY idx_crossref_work_synced (last_crossref_sync_at),
    KEY idx_crossref_work_agency (registration_agency_id),
    KEY idx_crossref_work_canonical (canonical_doi)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`crossref_author` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    crossref_work_id BIGINT UNSIGNED NOT NULL,
    sequence_no INT NOT NULL DEFAULT 0,
    given_name VARCHAR(255) NULL,
    family_name VARCHAR(255) NULL,
    suffix VARCHAR(64) NULL,
    full_name VARCHAR(500) NULL,
    orcid VARCHAR(32) NULL,
    authenticated_orcid TINYINT(1) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_crossref_author_work (crossref_work_id),
    KEY idx_crossref_author_orcid (orcid),
    CONSTRAINT fk_crossref_author_work
        FOREIGN KEY (crossref_work_id) REFERENCES `uss-ca`.`crossref_work` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`crossref_author_affiliation` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    crossref_author_id BIGINT UNSIGNED NOT NULL,
    sequence_no INT NOT NULL DEFAULT 0,
    name TEXT NULL,
    ror VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_crossref_aff_author (crossref_author_id),
    KEY idx_crossref_aff_ror (ror),
    CONSTRAINT fk_crossref_aff_author
        FOREIGN KEY (crossref_author_id) REFERENCES `uss-ca`.`crossref_author` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`crossref_issn` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    crossref_work_id BIGINT UNSIGNED NOT NULL,
    issn VARCHAR(32) NOT NULL,
    issn_type VARCHAR(32) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_crossref_issn (crossref_work_id, issn, issn_type),
    KEY idx_crossref_issn_value (issn),
    CONSTRAINT fk_crossref_issn_work
        FOREIGN KEY (crossref_work_id) REFERENCES `uss-ca`.`crossref_work` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`crossref_funder` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    crossref_work_id BIGINT UNSIGNED NOT NULL,
    funder_name VARCHAR(500) NULL,
    funder_doi VARCHAR(190) NULL,
    award_number VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_crossref_funder_work (crossref_work_id),
    KEY idx_crossref_funder_doi (funder_doi),
    CONSTRAINT fk_crossref_funder_work
        FOREIGN KEY (crossref_work_id) REFERENCES `uss-ca`.`crossref_work` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`crossref_license` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    crossref_work_id BIGINT UNSIGNED NOT NULL,
    license_url VARCHAR(1000) NULL,
    start_date VARCHAR(32) NULL,
    content_version VARCHAR(64) NULL,
    delay_in_days INT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_crossref_license_work (crossref_work_id),
    CONSTRAINT fk_crossref_license_work
        FOREIGN KEY (crossref_work_id) REFERENCES `uss-ca`.`crossref_work` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`crossref_relation` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    crossref_work_id BIGINT UNSIGNED NOT NULL,
    relation_type VARCHAR(64) NOT NULL,
    related_doi VARCHAR(500) NULL,
    related_id VARCHAR(500) NULL,
    asserted_by VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_crossref_relation_work (crossref_work_id),
    KEY idx_crossref_relation_type (relation_type),
    CONSTRAINT fk_crossref_relation_work
        FOREIGN KEY (crossref_work_id) REFERENCES `uss-ca`.`crossref_work` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`crossref_research_output` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    crossref_work_id BIGINT UNSIGNED NOT NULL,
    research_output_id BIGINT UNSIGNED NOT NULL,
    electronic_version_id BIGINT UNSIGNED NULL,
    active_in_pure TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_crossref_ro_link (crossref_work_id, research_output_id, electronic_version_id),
    KEY idx_crossref_ro_output (research_output_id),
    KEY idx_crossref_ro_version (electronic_version_id),
    CONSTRAINT fk_crossref_ro_work
        FOREIGN KEY (crossref_work_id) REFERENCES `uss-ca`.`crossref_work` (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_crossref_ro_output
        FOREIGN KEY (research_output_id) REFERENCES `uss-ca`.`pure_research_output` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`crossref_sync_stats` (
    sync_execution_id BIGINT NOT NULL,
    found_count BIGINT NOT NULL DEFAULT 0,
    other_agency_count BIGINT NOT NULL DEFAULT 0,
    not_found_count BIGINT NOT NULL DEFAULT 0,
    invalid_count BIGINT NOT NULL DEFAULT 0,
    skipped_fresh_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (sync_execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Candidatos de DOI faltante (discovery preparado; no se escribe a PURE).
CREATE TABLE IF NOT EXISTS `uss-ca`.`crossref_doi_candidate` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    research_output_id BIGINT UNSIGNED NOT NULL,
    normalized_doi VARCHAR(500) NOT NULL,
    original_doi VARCHAR(500) NULL,
    match_level VARCHAR(24) NOT NULL,
    title_score DECIMAL(5,2) NULL,
    author_overlap DECIMAL(5,2) NULL,
    year_match TINYINT(1) NULL,
    container_match TINYINT(1) NULL,
    issn_match TINYINT(1) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    title VARCHAR(1000) NULL,
    container_title VARCHAR(1000) NULL,
    published_date VARCHAR(32) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_crossref_doi_candidate (research_output_id, normalized_doi),
    KEY idx_crossref_doi_candidate_status (status),
    CONSTRAINT fk_crossref_doi_candidate_ro
        FOREIGN KEY (research_output_id) REFERENCES `uss-ca`.`pure_research_output` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- FASE B — lock
-- =============================================================================

INSERT IGNORE INTO `uss-ca`.`sync_control` (entity, active_sync_id, locked_at, locked_by)
VALUES ('CROSSREF', NULL, NULL, NULL);

-- =============================================================================
-- FASE C — reglas Data Quality (source_family CROSSREF, VIEW_ONLY)
-- También se upsertan al arrancar el scan vía DataQualityCatalog.
-- =============================================================================

INSERT INTO `uss-ca`.`data_quality_rule` (
    code, source_family, entity_type, field_name, name, description, rule_text, alert_text,
    bulk_procedure, category, correction_mode, enabled
) VALUES
('CROSSREF-001', 'CROSSREF', 'RESEARCH_OUTPUT', 'doi',
 'DOI con formato inválido',
 'El valor informado como DOI no cumple el formato 10.xxxx/...',
 'Un DOI informado debe tener formato válido. No se consulta Crossref.',
 'DOI con formato inválido.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('CROSSREF-002', 'CROSSREF', 'RESEARCH_OUTPUT', 'doi',
 'DOI no encontrado',
 'El DOI válido no aparece en Crossref ni en otra agencia conocida.',
 'No encontrado no implica inválido. OTHER_AGENCY se trata aparte.',
 'DOI no encontrado en Crossref ni en otra agencia.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('CROSSREF-003', 'CROSSREF', 'RESEARCH_OUTPUT', 'title',
 'Título PURE difiere de Crossref',
 'Tras normalizar Unicode, entidades, espacios y puntuación, el título institucional difiere de forma relevante.',
 'Umbral: igualdad normalizada o Jaccard de tokens < 0.55.',
 'El título PURE difiere significativamente del título Crossref.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('CROSSREF-004', 'CROSSREF', 'RESEARCH_OUTPUT', 'container_title',
 'Revista/container difiere',
 'El container-title de Crossref no coincide con la revista PURE asociada.',
 'No usar ISSN como identidad de revista. UUID PURE sigue siendo la identidad.',
 'Container/journal Crossref difiere de la revista PURE.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('CROSSREF-005', 'CROSSREF', 'RESEARCH_OUTPUT', 'issn',
 'ISSN Crossref no coincide',
 'Ningún ISSN Crossref coincide con los ISSN de la revista PURE relacionada.',
 'ISSN es evidencia, no identidad.',
 'ISSN Crossref no coincide con la revista PURE.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('CROSSREF-006', 'CROSSREF', 'RESEARCH_OUTPUT', 'authors',
 'Autoría con diferencias relevantes',
 'Los nombres completos normalizados de Crossref y PURE tienen poca superposición.',
 'No vincular personas solo por nombre.',
 'Autoría PURE y Crossref presentan diferencias relevantes.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('CROSSREF-007', 'CROSSREF', 'RESEARCH_OUTPUT', 'orcid',
 'ORCID disponible en Crossref',
 'Crossref informa ORCID de contributor y no hay ORCID institucional equivalente.',
 'Evidencia externa. No escribir en PURE.',
 'ORCID informado por Crossref y ausente institucionalmente.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('CROSSREF-008', 'CROSSREF', 'RESEARCH_OUTPUT', 'affiliation',
 'Afiliación Crossref disponible',
 'Hay afiliación/ROR en Crossref sin evidencia institucional comparable.',
 'No inferir USS por texto «San Sebastián».',
 'Afiliación disponible en Crossref y ausente o diferente en PURE.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('CROSSREF-009', 'CROSSREF', 'RESEARCH_OUTPUT', 'funding',
 'Funding informado en Crossref',
 'Crossref entrega funding y PURE no tiene financiamiento utilizable.',
 'Ausencia de funding Crossref no es error PURE.',
 'Funding informado por Crossref y no disponible en PURE.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('CROSSREF-010', 'CROSSREF', 'RESEARCH_OUTPUT', 'update',
 'Correction/retraction/update Crossref',
 'Metadata estructurada de retraction, correction o update.',
 'No inferir por texto libre.',
 'Crossref informa correction, retraction o update relevante.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1)
ON DUPLICATE KEY UPDATE
    source_family = VALUES(source_family),
    name = VALUES(name),
    description = VALUES(description),
    rule_text = VALUES(rule_text),
    alert_text = VALUES(alert_text),
    category = VALUES(category),
    correction_mode = VALUES(correction_mode),
    enabled = VALUES(enabled);
