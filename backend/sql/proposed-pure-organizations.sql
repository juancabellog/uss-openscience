-- PROPUESTO. NO ejecutar desde el agente.
-- Catálogo: `uss-ca` (el schema tiene guion: usar backticks).
--
-- Organizaciones PURE como entidad de primer nivel, paralelo a Personas /
-- Producción / Revistas / Proyectos.
-- Identidad estable: uuid (UNIQUE). pure_id se conserva si PURE lo entrega.
-- NO hay FK desde tablas existentes (pure_person_organization, proyectos, etc.)
-- hacia esta tabla: esas relaciones ya guardan organization_uuid y deben
-- seguir persistiendo aunque el catálogo todavía no exista.
--
-- INCREMENTAL: no. HttpPureClient pagina GET ?size=&offset= y no hay
-- modifiedSince en el cliente actual. ORGANIZATIONS es FULL-only.
-- last_seen_sync_id es el id de ejecución de presencia (mismo patrón que
-- Personas/Proyectos/Revistas). No se duplica last_sync_execution_id.
--
-- Columnas descriptivas incluidas SOLO porque ya existen en código:
--   OrganizationRef { uuid, systemName, LocalizedText name }
--   envelope de content-item PURE (JournalItem/ProjectItem): pureId, created*,
--   modified*, version, typeDiscriminator
--   ClassificationRef type (mismo tipo usado en journals/projects; nullable)
--   OrganizationRef parentOrganization / parents[0] (UUID estable; sin FK)
-- NO se crean address/email/phone/website/raw_json ni child tables de arrays
-- hasta que un JSON real de GET /organizations lo confirme.

-- =============================================================================
-- Padre
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_organization` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pure_id BIGINT UNSIGNED NULL,
    uuid CHAR(36) NOT NULL,
    type_discriminator VARCHAR(64) NULL,
    name_es VARCHAR(2000) NULL,
    name_en VARCHAR(2000) NULL,
    created_by VARCHAR(128) NULL,
    created_date_pure DATETIME(6) NULL,
    modified_by VARCHAR(128) NULL,
    modified_date_pure DATETIME(6) NULL,
    pure_version VARCHAR(80) NULL,
    system_name VARCHAR(64) NULL,
    type_uri VARCHAR(255) NULL,
    type_en VARCHAR(255) NULL,
    type_es VARCHAR(255) NULL,
    parent_uuid CHAR(36) NULL,
    active_in_pure TINYINT(1) NOT NULL DEFAULT 1,
    last_seen_in_pure_at DATETIME(6) NULL,
    last_seen_sync_id VARCHAR(64) NULL,
    last_change_detected_at DATETIME(6) NULL,
    removed_from_pure_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_organization_uuid (uuid),
    UNIQUE KEY uq_pure_organization_pure_id (pure_id),
    KEY idx_pure_organization_active (active_in_pure),
    KEY idx_pure_organization_parent (parent_uuid),
    KEY idx_pure_organization_type (type_uri),
    KEY idx_pure_organization_modified (modified_date_pure)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- Índices de join sobre UUID ya persistidos (sin FK)
-- Skip si el índice ya existe.
-- =============================================================================
-- ALTER TABLE `uss-ca`.`pure_person_organization`
--     ADD INDEX idx_pure_person_org_uuid (organization_uuid);

-- =============================================================================
-- RBAC: módulo ORGANIZATIONS
-- =============================================================================
INSERT IGNORE INTO `uss-ca`.`app_module` (code, name_es, name_en, sort_order) VALUES
    ('ORGANIZATIONS', 'Organizaciones', 'Organizations', 47);

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, 'ORGANIZATIONS', p.code
FROM `uss-ca`.`app_role` r
CROSS JOIN `uss-ca`.`app_permission` p
WHERE r.code = 'PLATFORM_ADMIN'
  AND p.code IN ('CONSULT', 'ADMINISTER');

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, 'ORGANIZATIONS', x.permission_code
FROM `uss-ca`.`app_role` r
JOIN (
    SELECT 'CONSULT' AS permission_code
    UNION ALL SELECT 'ADMINISTER'
) x
WHERE r.code = 'DATA_STEWARD';

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, 'ORGANIZATIONS', 'CONSULT'
FROM `uss-ca`.`app_role` r
WHERE r.code IN ('ANALYST', 'CONSULTA');

INSERT IGNORE INTO `uss-ca`.`sync_control` (entity, active_sync_id, locked_at, locked_by)
VALUES ('ORGANIZATIONS', NULL, NULL, NULL);
