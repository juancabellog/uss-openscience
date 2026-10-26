-- PROPUESTO. NO ejecutar desde el agente.
-- Catálogo: uss-ca
--
-- Capa transversal de métricas PURE:
--   Journal      → scopusJournal + scopusCitescore
--   Research Output → sciVal (FWCI)
--   Person       → hIndex (Scopus / PlumX / SciVal × ventanas)
--
-- Decisión: modelo conceptual común + tablas específicas por entidad
-- (FK reales a pure_journal / pure_research_output / pure_person).
-- NO tabla polimórfica genérica.
-- NO columnas CiteScore/FWCI/h-index en las tablas padre.
--
-- UNIQUE: identity_key = SHA2 de owner|collection|metric_id|year|source|category
-- con sentinels vacíos. MySQL UNIQUE con varios NULL no es idempotente.
--
-- value_kind INTEGER | DECIMAL | STRING: exactamente un valor no nulo.
-- 0 es un valor. Ausencia = no hay fila.
--
-- Fetch state por owner+collection distingue EMPTY vs FAILED vs INVALID.

-- ============================================================
-- Journal metrics
-- ============================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_journal_metric` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    journal_id BIGINT UNSIGNED NOT NULL,
    collection_id VARCHAR(64) NOT NULL,
    source_uri VARCHAR(512) NULL,
    source_term_es VARCHAR(255) NULL,
    source_term_en VARCHAR(255) NULL,
    category_uri VARCHAR(512) NULL,
    category_term_es VARCHAR(255) NULL,
    category_term_en VARCHAR(255) NULL,
    metric_year SMALLINT NULL,
    metric_id VARCHAR(128) NOT NULL,
    value_kind VARCHAR(16) NOT NULL,
    integer_value BIGINT NULL,
    decimal_value DECIMAL(18,6) NULL,
    string_value VARCHAR(1000) NULL,
    identity_key CHAR(64) NOT NULL,
    last_sync_execution_id BIGINT NULL,
    last_synced_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_journal_metric_identity (identity_key),
    KEY idx_pure_journal_metric_owner (journal_id, collection_id, metric_id, metric_year),
    KEY idx_pure_journal_metric_citescore (journal_id, metric_id, metric_year),
    KEY idx_pure_journal_metric_quartile (journal_id, collection_id, metric_id, metric_year, integer_value),
    CONSTRAINT fk_pure_journal_metric_journal
        FOREIGN KEY (journal_id) REFERENCES `uss-ca`.`pure_journal` (id),
    CONSTRAINT chk_pure_journal_metric_value CHECK (
        (value_kind = 'INTEGER' AND integer_value IS NOT NULL AND decimal_value IS NULL AND string_value IS NULL)
        OR (value_kind = 'DECIMAL' AND decimal_value IS NOT NULL AND integer_value IS NULL AND string_value IS NULL)
        OR (value_kind = 'STRING' AND string_value IS NOT NULL AND integer_value IS NULL AND decimal_value IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_journal_metric_fetch` (
    journal_id BIGINT UNSIGNED NOT NULL,
    collection_id VARCHAR(64) NOT NULL,
    last_outcome VARCHAR(16) NOT NULL,
    last_http_status INT NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    observation_count INT NOT NULL DEFAULT 0,
    last_sync_execution_id BIGINT NULL,
    last_sync_at DATETIME(6) NULL,
    last_success_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (journal_id, collection_id),
    KEY idx_pure_journal_metric_fetch_stale (last_outcome, last_sync_at),
    CONSTRAINT fk_pure_journal_metric_fetch_journal
        FOREIGN KEY (journal_id) REFERENCES `uss-ca`.`pure_journal` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Research output metrics
-- ============================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_research_output_metric` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    research_output_id BIGINT UNSIGNED NOT NULL,
    collection_id VARCHAR(64) NOT NULL,
    source_uri VARCHAR(512) NULL,
    source_term_es VARCHAR(255) NULL,
    source_term_en VARCHAR(255) NULL,
    category_uri VARCHAR(512) NULL,
    category_term_es VARCHAR(255) NULL,
    category_term_en VARCHAR(255) NULL,
    metric_year SMALLINT NULL,
    metric_id VARCHAR(128) NOT NULL,
    value_kind VARCHAR(16) NOT NULL,
    integer_value BIGINT NULL,
    decimal_value DECIMAL(18,6) NULL,
    string_value VARCHAR(1000) NULL,
    identity_key CHAR(64) NOT NULL,
    last_sync_execution_id BIGINT NULL,
    last_synced_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_ro_metric_identity (identity_key),
    KEY idx_pure_ro_metric_owner (research_output_id, collection_id, metric_id, metric_year),
    KEY idx_pure_ro_metric_fwci (research_output_id, collection_id, metric_id),
    CONSTRAINT fk_pure_ro_metric_output
        FOREIGN KEY (research_output_id) REFERENCES `uss-ca`.`pure_research_output` (id),
    CONSTRAINT chk_pure_ro_metric_value CHECK (
        (value_kind = 'INTEGER' AND integer_value IS NOT NULL AND decimal_value IS NULL AND string_value IS NULL)
        OR (value_kind = 'DECIMAL' AND decimal_value IS NOT NULL AND integer_value IS NULL AND string_value IS NULL)
        OR (value_kind = 'STRING' AND string_value IS NOT NULL AND integer_value IS NULL AND decimal_value IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_research_output_metric_fetch` (
    research_output_id BIGINT UNSIGNED NOT NULL,
    collection_id VARCHAR(64) NOT NULL,
    last_outcome VARCHAR(16) NOT NULL,
    last_http_status INT NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    observation_count INT NOT NULL DEFAULT 0,
    last_sync_execution_id BIGINT NULL,
    last_sync_at DATETIME(6) NULL,
    last_success_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (research_output_id, collection_id),
    KEY idx_pure_ro_metric_fetch_stale (last_outcome, last_sync_at),
    CONSTRAINT fk_pure_ro_metric_fetch_output
        FOREIGN KEY (research_output_id) REFERENCES `uss-ca`.`pure_research_output` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Person metrics
-- ============================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_person_metric` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    person_id BIGINT UNSIGNED NOT NULL,
    collection_id VARCHAR(64) NOT NULL,
    source_uri VARCHAR(512) NULL,
    source_term_es VARCHAR(255) NULL,
    source_term_en VARCHAR(255) NULL,
    category_uri VARCHAR(512) NULL,
    category_term_es VARCHAR(255) NULL,
    category_term_en VARCHAR(255) NULL,
    metric_year SMALLINT NULL,
    metric_id VARCHAR(128) NOT NULL,
    value_kind VARCHAR(16) NOT NULL,
    integer_value BIGINT NULL,
    decimal_value DECIMAL(18,6) NULL,
    string_value VARCHAR(1000) NULL,
    identity_key CHAR(64) NOT NULL,
    last_sync_execution_id BIGINT NULL,
    last_synced_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_person_metric_identity (identity_key),
    KEY idx_pure_person_metric_owner (person_id, collection_id, metric_id),
    KEY idx_pure_person_metric_hindex (person_id, collection_id, metric_id, source_uri(191), category_uri(191)),
    CONSTRAINT fk_pure_person_metric_person
        FOREIGN KEY (person_id) REFERENCES `uss-ca`.`pure_person` (id),
    CONSTRAINT chk_pure_person_metric_value CHECK (
        (value_kind = 'INTEGER' AND integer_value IS NOT NULL AND decimal_value IS NULL AND string_value IS NULL)
        OR (value_kind = 'DECIMAL' AND decimal_value IS NOT NULL AND integer_value IS NULL AND string_value IS NULL)
        OR (value_kind = 'STRING' AND string_value IS NOT NULL AND integer_value IS NULL AND decimal_value IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_person_metric_fetch` (
    person_id BIGINT UNSIGNED NOT NULL,
    collection_id VARCHAR(64) NOT NULL,
    last_outcome VARCHAR(16) NOT NULL,
    last_http_status INT NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    observation_count INT NOT NULL DEFAULT 0,
    last_sync_execution_id BIGINT NULL,
    last_sync_at DATETIME(6) NULL,
    last_success_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (person_id, collection_id),
    KEY idx_pure_person_metric_fetch_stale (last_outcome, last_sync_at),
    CONSTRAINT fk_pure_person_metric_fetch_person
        FOREIGN KEY (person_id) REFERENCES `uss-ca`.`pure_person` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- last_outcome: SUCCESS | EMPTY | FAILED | INVALID
-- SUCCESS = HTTP 2xx, JSON válido, items con observaciones.
-- EMPTY   = HTTP 2xx, JSON válido, items=[]. Reemplaza snapshot previo.
-- FAILED  = HTTP error / timeout. Conserva snapshot previo.
-- INVALID = JSON/owner/collection no coherente. Conserva snapshot previo.
