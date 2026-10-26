-- PROPUESTO. NO ejecutar desde el agente.
-- Candidatos ORCID para personas sin ORCID en PURE.
-- No modifica pure_person.orcid.

CREATE TABLE IF NOT EXISTS `uss-ca`.`orcid_discovery_execution` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    started_by VARCHAR(128) NULL,
    total_people INT NOT NULL DEFAULT 0,
    processed_people INT NOT NULL DEFAULT 0,
    people_with_candidates INT NOT NULL DEFAULT 0,
    strong_match_count INT NOT NULL DEFAULT 0,
    manual_review_count INT NOT NULL DEFAULT 0,
    no_candidate_count INT NOT NULL DEFAULT 0,
    too_many_count INT NOT NULL DEFAULT 0,
    conflict_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_orcid_disc_exec_status (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`orcid_discovery_person` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    execution_id BIGINT UNSIGNED NOT NULL,
    person_id BIGINT UNSIGNED NOT NULL,
    person_uuid VARCHAR(36) NULL,
    pure_id BIGINT NULL,
    first_name VARCHAR(255) NULL,
    last_name VARCHAR(255) NULL,
    cargo VARCHAR(255) NULL,
    search_given_name VARCHAR(128) NULL,
    search_family_name VARCHAR(128) NULL,
    person_status VARCHAR(32) NOT NULL,
    candidate_count INT NOT NULL DEFAULT 0,
    doi_match_count INT NOT NULL DEFAULT 0,
    selected_candidate_id BIGINT UNSIGNED NULL,
    apply_queued TINYINT(1) NOT NULL DEFAULT 0,
    selection_origin VARCHAR(16) NULL,
    selected_by VARCHAR(128) NULL,
    selected_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL COMMENT 'TOO_MANY_CANDIDATES stores num-found from ORCID search, e.g. num-found=124',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_orcid_disc_person (execution_id, person_id),
    KEY idx_orcid_disc_person_status (execution_id, person_status),
    CONSTRAINT fk_orcid_disc_person_exec
        FOREIGN KEY (execution_id) REFERENCES `uss-ca`.`orcid_discovery_execution` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`orcid_candidate` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    execution_id BIGINT UNSIGNED NOT NULL,
    person_id BIGINT UNSIGNED NOT NULL,
    discovery_person_id BIGINT UNSIGNED NOT NULL,
    candidate_orcid VARCHAR(19) NOT NULL,
    search_given_name VARCHAR(128) NULL,
    search_family_name VARCHAR(128) NULL,
    score INT NOT NULL DEFAULT 0,
    match_level VARCHAR(32) NOT NULL,
    name_match TINYINT(1) NULL,
    doi_match_count INT NOT NULL DEFAULT 0,
    author_match TINYINT(1) NULL,
    position_match TINYINT(1) NULL,
    already_used_person_id BIGINT UNSIGNED NULL,
    status VARCHAR(32) NOT NULL,
    first_detected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_checked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_orcid_candidate (execution_id, person_id, candidate_orcid),
    KEY idx_orcid_candidate_orcid (candidate_orcid),
    KEY idx_orcid_candidate_level (execution_id, match_level),
    CONSTRAINT fk_orcid_candidate_exec
        FOREIGN KEY (execution_id) REFERENCES `uss-ca`.`orcid_discovery_execution` (id),
    CONSTRAINT fk_orcid_candidate_person
        FOREIGN KEY (discovery_person_id) REFERENCES `uss-ca`.`orcid_discovery_person` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`orcid_candidate_evidence` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    candidate_id BIGINT UNSIGNED NOT NULL,
    research_output_id BIGINT UNSIGNED NULL,
    doi VARCHAR(500) NULL,
    local_author VARCHAR(255) NULL,
    contributor_first_name VARCHAR(255) NULL,
    contributor_last_name VARCHAR(255) NULL,
    person_first_name VARCHAR(255) NULL,
    person_last_name VARCHAR(255) NULL,
    person_full_name VARCHAR(255) NULL,
    contributor_full_name VARCHAR(255) NULL,
    local_position INT NULL,
    orcid_author VARCHAR(255) NULL,
    orcid_position INT NULL,
    name_match TINYINT(1) NULL,
    position_match TINYINT(1) NULL,
    name_partition_different TINYINT(1) NULL,
    note VARCHAR(500) NULL,
    reason_codes VARCHAR(255) NULL,
    PRIMARY KEY (id),
    KEY idx_orcid_evidence_candidate (candidate_id),
    CONSTRAINT fk_orcid_evidence_candidate
        FOREIGN KEY (candidate_id) REFERENCES `uss-ca`.`orcid_candidate` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PROPUESTO. Evidencia estructurada de partición de nombre y razones de matching.
-- No es obligatorio para que STRONG funcione: las notas humanas se guardan en `note`.
-- ALTER TABLE `uss-ca`.`orcid_candidate_evidence`
--     ADD COLUMN contributor_first_name VARCHAR(255) NULL,
--     ADD COLUMN contributor_last_name VARCHAR(255) NULL,
--     ADD COLUMN person_first_name VARCHAR(255) NULL,
--     ADD COLUMN person_last_name VARCHAR(255) NULL,
--     ADD COLUMN person_full_name VARCHAR(255) NULL,
--     ADD COLUMN contributor_full_name VARCHAR(255) NULL,
--     ADD COLUMN name_partition_different TINYINT(1) NULL,
--     ADD COLUMN reason_codes VARCHAR(255) NULL;

-- PROPUESTO. Cola de sincronización vs candidato elegido, y auditoría de selección.
-- ALTER TABLE `uss-ca`.`orcid_discovery_person`
--     ADD COLUMN apply_queued TINYINT(1) NOT NULL DEFAULT 0,
--     ADD COLUMN selection_origin VARCHAR(16) NULL,
--     ADD COLUMN selected_by VARCHAR(128) NULL,
--     ADD COLUMN selected_at DATETIME(6) NULL;

-- Recomendaciones de índices (NO crear a ciegas; revisar EXPLAIN en su volumen):
-- 1) La consulta batch persona→DOI usa c.person_uuid y ev.research_output_id + type_discriminator + doi.
--    Si el EXPLAIN no usa índice en contributor: KEY (person_uuid, research_output_id)
--    sobre `pure_research_output_contributor`.
-- 2) Si el JOIN a electronic_version filtra mal: KEY (research_output_id, type_discriminator)
--    sobre `pure_research_output_electronic_version`.
-- 3) Un índice solo sobre doi suele ser poco selectivo; no lo cree salvo evidencia.
