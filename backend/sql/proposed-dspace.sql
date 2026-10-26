-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente UNA SOLA VEZ en el catálogo `uss-ca`.
--
-- DSpace 9.2 USS es fuente institucional de SOLO LECTURA.
-- No escribe en PURE ni en el repositorio. No almacena PDF.
-- Identidad de ítem: UUID DSpace (CHAR 36). Handle uss/{n} UNIQUE.
-- Join con PURE: dspace_item_match (DOI / handle / UUID / título).
--
-- FASE A — tablas nuevas
-- FASE B — lock sync_control
-- FASE C — índices de consulta

-- =============================================================================
-- FASE A — tablas nuevas
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`dspace_community` (
    uuid CHAR(36) NOT NULL,
    handle VARCHAR(64) NULL,
    name TEXT NULL,
    parent_uuid CHAR(36) NULL,
    archived_items_count INT NULL,
    last_sync_at DATETIME(6) NULL,
    last_sync_execution_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (uuid),
    UNIQUE KEY uq_dspace_community_handle (handle),
    KEY idx_dspace_community_parent (parent_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`dspace_collection` (
    uuid CHAR(36) NOT NULL,
    handle VARCHAR(64) NULL,
    name TEXT NULL,
    parent_community_uuid CHAR(36) NULL,
    archived_items_count INT NULL,
    last_sync_at DATETIME(6) NULL,
    last_sync_execution_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (uuid),
    UNIQUE KEY uq_dspace_collection_handle (handle),
    KEY idx_dspace_collection_community (parent_community_uuid),
    CONSTRAINT fk_dspace_collection_community
        FOREIGN KEY (parent_community_uuid) REFERENCES `uss-ca`.`dspace_community` (uuid)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`dspace_item` (
    uuid CHAR(36) NOT NULL,
    handle VARCHAR(64) NULL,
    handle_url VARCHAR(500) NULL,
    name TEXT NULL,
    dc_type VARCHAR(191) NULL,
    normalized_doi VARCHAR(500) NULL,
    doi_raw VARCHAR(500) NULL,
    access_status VARCHAR(32) NULL,
    embargo_date DATE NULL,
    fulltext_availability VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    in_archive TINYINT(1) NULL,
    discoverable TINYINT(1) NULL,
    withdrawn TINYINT(1) NULL,
    owning_collection_uuid CHAR(36) NULL,
    last_modified_remote DATETIME(6) NULL,
    last_outcome VARCHAR(16) NOT NULL DEFAULT 'FOUND',
    last_http_status INT NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(1000) NULL,
    last_sync_at DATETIME(6) NULL,
    last_sync_execution_id BIGINT NULL,
    seen_in_execution_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (uuid),
    UNIQUE KEY uq_dspace_item_handle (handle),
    KEY idx_dspace_item_doi (normalized_doi),
    KEY idx_dspace_item_access (access_status),
    KEY idx_dspace_item_fulltext (fulltext_availability),
    KEY idx_dspace_item_type (dc_type),
    KEY idx_dspace_item_collection (owning_collection_uuid),
    KEY idx_dspace_item_modified (last_modified_remote),
    KEY idx_dspace_item_outcome (last_outcome),
    KEY idx_dspace_item_seen (seen_in_execution_id),
    CONSTRAINT fk_dspace_item_collection
        FOREIGN KEY (owning_collection_uuid) REFERENCES `uss-ca`.`dspace_collection` (uuid)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`dspace_item_metadata` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_uuid CHAR(36) NOT NULL,
    field VARCHAR(191) NOT NULL,
    place INT NOT NULL DEFAULT 0,
    language VARCHAR(32) NOT NULL DEFAULT '',
    value LONGTEXT NOT NULL,
    authority VARCHAR(191) NULL,
    confidence INT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_dspace_item_metadata (item_uuid, field, place, language),
    KEY idx_dspace_item_metadata_field (field),
    CONSTRAINT fk_dspace_item_metadata_item
        FOREIGN KEY (item_uuid) REFERENCES `uss-ca`.`dspace_item` (uuid)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`dspace_bitstream` (
    uuid CHAR(36) NOT NULL,
    item_uuid CHAR(36) NOT NULL,
    bundle_uuid CHAR(36) NULL,
    bundle_name VARCHAR(32) NOT NULL,
    name VARCHAR(512) NULL,
    size_bytes BIGINT NULL,
    checksum_md5 CHAR(32) NULL,
    mime_type VARCHAR(128) NULL,
    sequence_id INT NULL,
    access_status VARCHAR(32) NULL,
    content_url VARCHAR(1000) NULL,
    public_download TINYINT(1) NOT NULL DEFAULT 0,
    last_sync_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (uuid),
    KEY idx_dspace_bitstream_item (item_uuid),
    KEY idx_dspace_bitstream_bundle (bundle_name),
    CONSTRAINT fk_dspace_bitstream_item
        FOREIGN KEY (item_uuid) REFERENCES `uss-ca`.`dspace_item` (uuid)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`dspace_item_match` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_uuid CHAR(36) NOT NULL,
    research_output_id BIGINT UNSIGNED NOT NULL,
    match_method VARCHAR(32) NOT NULL,
    match_status VARCHAR(32) NOT NULL,
    canonical TINYINT(1) NOT NULL DEFAULT 0,
    confidence DECIMAL(6,4) NULL,
    evidence_json TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_dspace_item_match (item_uuid, research_output_id),
    KEY idx_dspace_item_match_output (research_output_id),
    KEY idx_dspace_item_match_status (match_status),
    KEY idx_dspace_item_match_method (match_method),
    CONSTRAINT fk_dspace_item_match_item
        FOREIGN KEY (item_uuid) REFERENCES `uss-ca`.`dspace_item` (uuid)
        ON DELETE CASCADE,
    CONSTRAINT fk_dspace_item_match_output
        FOREIGN KEY (research_output_id) REFERENCES `uss-ca`.`pure_research_output` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`dspace_item_collection` (
    item_uuid CHAR(36) NOT NULL,
    collection_uuid CHAR(36) NOT NULL,
    relation VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (item_uuid, collection_uuid, relation),
    KEY idx_dspace_item_collection_col (collection_uuid, relation),
    CONSTRAINT fk_dspace_item_collection_item
        FOREIGN KEY (item_uuid) REFERENCES `uss-ca`.`dspace_item` (uuid)
        ON DELETE CASCADE,
    CONSTRAINT fk_dspace_item_collection_col
        FOREIGN KEY (collection_uuid) REFERENCES `uss-ca`.`dspace_collection` (uuid)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`dspace_sync_checkpoint` (
    entity VARCHAR(32) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    page_number INT NOT NULL DEFAULT 0,
    page_size INT NOT NULL DEFAULT 50,
    last_uuid CHAR(36) NULL,
    payload_json TEXT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (entity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`dspace_sync_stats` (
    sync_execution_id BIGINT NOT NULL,
    dspace_version VARCHAR(64) NULL,
    communities_count BIGINT NOT NULL DEFAULT 0,
    collections_count BIGINT NOT NULL DEFAULT 0,
    items_found BIGINT NOT NULL DEFAULT 0,
    open_access_count BIGINT NOT NULL DEFAULT 0,
    metadata_only_count BIGINT NOT NULL DEFAULT 0,
    restricted_count BIGINT NOT NULL DEFAULT 0,
    embargo_count BIGINT NOT NULL DEFAULT 0,
    fulltext_public_count BIGINT NOT NULL DEFAULT 0,
    matched_count BIGINT NOT NULL DEFAULT 0,
    candidate_count BIGINT NOT NULL DEFAULT 0,
    discover_total_elements BIGINT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (sync_execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- FASE B — lock
-- =============================================================================

INSERT IGNORE INTO `uss-ca`.`sync_control` (entity, active_sync_id, locked_at, locked_by)
VALUES ('DSPACE', NULL, NULL, NULL);

-- =============================================================================
-- FASE C — notas
-- =============================================================================
-- last_outcome: FOUND | MISSING | ERROR
-- dspace_item_collection.relation: OWNING | MAPPED
-- fulltext_availability: FULLTEXT_PUBLIC | METADATA_ONLY | RESTRICTED | EMBARGOED | UNKNOWN
-- match_method: DOI | HANDLE | UUID | TITLE_AUTHOR_YEAR | MANUAL
-- match_status: CONFIRMED | CANDIDATE | SECONDARY | REJECTED
-- INCREMENTAL no marca MISSING. FULL solo marca MISSING tras crawl discover completo sin error fatal.
