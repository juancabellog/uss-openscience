-- PROPUESTO. NO ejecutar desde el agente.
-- Catálogo: `uss-ca` (el schema tiene guion: usar backticks).
--
-- Proyectos PURE como entidad de primer nivel, paralelo a Personas / Producción / Revistas.
-- Identidad estable: uuid (UNIQUE). pure_id se conserva como identificador adicional.
-- NO escribe en PURE. NO hay columna en pure_person.
-- person_uuid y organization_uuid se guardan sin FK fuerte para no fallar
-- si Personas u Organization todavía no están cargadas.
--
-- INCREMENTAL: no. GET /projects del cliente actual solo pagina offset/size,
-- igual que Personas, Research Outputs y Journals.

-- =============================================================================
-- Padre
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_project` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pure_id BIGINT UNSIGNED NULL,
    uuid CHAR(36) NOT NULL,
    type_discriminator VARCHAR(64) NULL,
    title_es VARCHAR(2000) NULL,
    title_en VARCHAR(2000) NULL,
    acronym VARCHAR(255) NULL,
    created_by VARCHAR(128) NULL,
    created_date_pure DATETIME(6) NULL,
    modified_by VARCHAR(128) NULL,
    modified_date_pure DATETIME(6) NULL,
    portal_url VARCHAR(1000) NULL,
    pure_version VARCHAR(80) NULL,
    period_start_date DATE NULL,
    period_end_date DATE NULL,
    type_uri VARCHAR(255) NULL,
    type_en VARCHAR(255) NULL,
    type_es VARCHAR(255) NULL,
    workflow_step VARCHAR(64) NULL,
    workflow_description_en VARCHAR(255) NULL,
    workflow_description_es VARCHAR(255) NULL,
    visibility_key VARCHAR(64) NULL,
    visibility_en VARCHAR(255) NULL,
    visibility_es VARCHAR(255) NULL,
    managing_organization_uuid CHAR(36) NULL,
    system_name VARCHAR(64) NULL,
    active_in_pure TINYINT(1) NOT NULL DEFAULT 1,
    last_seen_in_pure_at DATETIME(6) NULL,
    last_seen_sync_id VARCHAR(64) NULL,
    last_change_detected_at DATETIME(6) NULL,
    removed_from_pure_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_project_uuid (uuid),
    UNIQUE KEY uq_pure_project_pure_id (pure_id),
    KEY idx_pure_project_active (active_in_pure),
    KEY idx_pure_project_workflow (workflow_step),
    KEY idx_pure_project_type (type_uri),
    KEY idx_pure_project_visibility (visibility_key),
    KEY idx_pure_project_period (period_start_date, period_end_date),
    KEY idx_pure_project_managing_org (managing_organization_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_project_participant` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    pure_id BIGINT UNSIGNED NULL,
    type_discriminator VARCHAR(64) NULL,
    first_name VARCHAR(255) NULL,
    last_name VARCHAR(255) NULL,
    role_uri VARCHAR(255) NULL,
    role_term_en VARCHAR(255) NULL,
    role_term_es VARCHAR(255) NULL,
    association_hidden TINYINT(1) NULL,
    person_uuid CHAR(36) NULL,
    person_id BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_project_participant_pure (project_id, pure_id),
    KEY idx_pure_project_participant_project (project_id),
    KEY idx_pure_project_participant_person_uuid (person_uuid),
    KEY idx_pure_project_participant_person_id (person_id),
    KEY idx_pure_project_participant_role (role_uri),
    CONSTRAINT fk_pure_project_participant_project
        FOREIGN KEY (project_id) REFERENCES `uss-ca`.`pure_project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_project_participant_organization` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    participant_id BIGINT UNSIGNED NOT NULL,
    organization_uuid CHAR(36) NOT NULL,
    organization_system_name VARCHAR(64) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_project_part_org (participant_id, organization_uuid),
    KEY idx_pure_project_part_org_participant (participant_id),
    KEY idx_pure_project_part_org_uuid (organization_uuid),
    CONSTRAINT fk_pure_project_part_org_participant
        FOREIGN KEY (participant_id) REFERENCES `uss-ca`.`pure_project_participant` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_project_organization` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    organization_uuid CHAR(36) NOT NULL,
    organization_system_name VARCHAR(64) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_project_org (project_id, organization_uuid),
    KEY idx_pure_project_org_project (project_id),
    KEY idx_pure_project_org_uuid (organization_uuid),
    CONSTRAINT fk_pure_project_org_project
        FOREIGN KEY (project_id) REFERENCES `uss-ca`.`pure_project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_project_identifier` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    pure_id BIGINT UNSIGNED NULL,
    type_discriminator VARCHAR(64) NULL,
    identifier_value VARCHAR(255) NULL,
    type_uri VARCHAR(255) NULL,
    type_en VARCHAR(255) NULL,
    type_es VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_project_identifier_pure (project_id, pure_id),
    KEY idx_pure_project_identifier_project (project_id),
    KEY idx_pure_project_identifier_value (identifier_value),
    KEY idx_pure_project_identifier_type (type_uri),
    CONSTRAINT fk_pure_project_identifier_project
        FOREIGN KEY (project_id) REFERENCES `uss-ca`.`pure_project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- El GET de ejemplo no trae texto de descripción. Solo metadatos de tipo.
CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_project_description` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    pure_id BIGINT UNSIGNED NULL,
    type_uri VARCHAR(255) NULL,
    type_en VARCHAR(255) NULL,
    type_es VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_project_description_pure (project_id, pure_id),
    KEY idx_pure_project_description_project (project_id),
    CONSTRAINT fk_pure_project_description_project
        FOREIGN KEY (project_id) REFERENCES `uss-ca`.`pure_project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_project_nature_type` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    uri VARCHAR(255) NOT NULL,
    term_en VARCHAR(255) NULL,
    term_es VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_project_nature (project_id, uri),
    KEY idx_pure_project_nature_project (project_id),
    CONSTRAINT fk_pure_project_nature_project
        FOREIGN KEY (project_id) REFERENCES `uss-ca`.`pure_project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_project_pretty_url_identifier` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    identifier VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_project_pretty_url (project_id, identifier),
    KEY idx_pure_project_pretty_url_project (project_id),
    CONSTRAINT fk_pure_project_pretty_url_project
        FOREIGN KEY (project_id) REFERENCES `uss-ca`.`pure_project` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- RBAC: módulo PROJECTS
-- sort_order 45 = entre Revistas (40) y Calidad de datos (50)
-- =============================================================================
INSERT IGNORE INTO `uss-ca`.`app_module` (code, name_es, name_en, sort_order) VALUES
    ('PROJECTS', 'Proyectos', 'Projects', 45);

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, 'PROJECTS', p.code
FROM `uss-ca`.`app_role` r
CROSS JOIN `uss-ca`.`app_permission` p
WHERE r.code = 'PLATFORM_ADMIN'
  AND p.code IN ('CONSULT', 'ADMINISTER');

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, 'PROJECTS', x.permission_code
FROM `uss-ca`.`app_role` r
JOIN (
    SELECT 'CONSULT' AS permission_code
    UNION ALL SELECT 'ADMINISTER'
) x
WHERE r.code = 'DATA_STEWARD';

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, 'PROJECTS', 'CONSULT'
FROM `uss-ca`.`app_role` r
WHERE r.code IN ('ANALYST', 'CONSULTA');

INSERT IGNORE INTO `uss-ca`.`sync_control` (entity, active_sync_id, locked_at, locked_by)
VALUES ('PROJECTS', NULL, NULL, NULL);
