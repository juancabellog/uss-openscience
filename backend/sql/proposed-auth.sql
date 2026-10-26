-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente UNA SOLA VEZ en el catálogo `uss-ca`.
--
-- Autenticación local + RBAC por módulo.
-- NO inserta password ni usuario con credenciales conocidas.
-- El primer administrador se crea con AUTH_BOOTSTRAP_EMAIL (PENDING_ACTIVATION)
-- o con el alta desde Administración una vez exista un admin.

-- =============================================================================
-- FASE A — catálogo
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`app_module` (
    code VARCHAR(64) NOT NULL,
    name_es VARCHAR(128) NOT NULL,
    name_en VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`app_permission` (
    code VARCHAR(32) NOT NULL,
    name_es VARCHAR(128) NOT NULL,
    name_en VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`app_role` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    system_role TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`app_user` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    email_normalized VARCHAR(255) NOT NULL,
    first_name VARCHAR(128) NOT NULL,
    last_name VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    auth_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    preferred_language VARCHAR(8) NOT NULL DEFAULT 'es',
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    last_login_at DATETIME(6) NULL,
    activated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT UNSIGNED NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_user_email (email_normalized),
    KEY idx_app_user_status (status),
    KEY idx_app_user_created (created_at),
    KEY idx_app_user_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`app_user_role` (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, role_id),
    KEY idx_app_user_role_role (role_id),
    CONSTRAINT fk_app_user_role_user
        FOREIGN KEY (user_id) REFERENCES `uss-ca`.`app_user` (id),
    CONSTRAINT fk_app_user_role_role
        FOREIGN KEY (role_id) REFERENCES `uss-ca`.`app_role` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`app_role_permission` (
    role_id BIGINT UNSIGNED NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    permission_code VARCHAR(32) NOT NULL,
    PRIMARY KEY (role_id, module_code, permission_code),
    KEY idx_app_role_permission_module (module_code, permission_code),
    CONSTRAINT fk_app_role_permission_role
        FOREIGN KEY (role_id) REFERENCES `uss-ca`.`app_role` (id),
    CONSTRAINT fk_app_role_permission_module
        FOREIGN KEY (module_code) REFERENCES `uss-ca`.`app_module` (code),
    CONSTRAINT fk_app_role_permission_perm
        FOREIGN KEY (permission_code) REFERENCES `uss-ca`.`app_permission` (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`app_account_token` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_account_token_hash (token_hash),
    KEY idx_app_account_token_user_purpose (user_id, purpose, used_at),
    KEY idx_app_account_token_expires (expires_at),
    CONSTRAINT fk_app_account_token_user
        FOREIGN KEY (user_id) REFERENCES `uss-ca`.`app_user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`app_session` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_session_hash (token_hash),
    KEY idx_app_session_user (user_id, revoked_at, expires_at),
    CONSTRAINT fk_app_session_user
        FOREIGN KEY (user_id) REFERENCES `uss-ca`.`app_user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`app_security_event` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(64) NOT NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    target_user_id BIGINT UNSIGNED NULL,
    target_role_id BIGINT UNSIGNED NULL,
    detail VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_app_security_event_type (event_type, created_at),
    KEY idx_app_security_event_target (target_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- FASE B — seed catálogo
-- =============================================================================

INSERT IGNORE INTO `uss-ca`.`app_module` (code, name_es, name_en, sort_order) VALUES
    ('DASHBOARD', 'Dashboard', 'Dashboard', 10),
    ('PEOPLE', 'Personas', 'People', 20),
    ('RESEARCH_OUTPUTS', 'Producción científica', 'Research outputs', 30),
    ('JOURNALS', 'Revistas', 'Journals', 40),
    ('PROJECTS', 'Proyectos', 'Projects', 45),
    ('ORGANIZATIONS', 'Organizaciones', 'Organizations', 47),
    ('DIRECTORY', 'Directorio USS', 'USS directory', 48),
    ('DATA_QUALITY', 'Calidad de datos', 'Data quality', 50),
    ('INDICATORS', 'Indicadores', 'Indicators', 60),
    ('INTEGRATIONS', 'Integraciones', 'Integrations', 70),
    ('AUDIT', 'Auditoría', 'Audit', 80),
    ('ADMINISTRATION', 'Administración', 'Administration', 90);

INSERT IGNORE INTO `uss-ca`.`app_permission` (code, name_es, name_en, sort_order) VALUES
    ('CONSULT', 'Consultar', 'View', 10),
    ('ADMINISTER', 'Administrar', 'Administer', 20),
    ('EXPORT', 'Exportar', 'Export', 30),
    ('EXECUTE', 'Ejecutar', 'Execute', 40),
    ('CORRECT', 'Corregir', 'Correct', 50),
    ('SYNC', 'Sincronizar', 'Synchronize', 60),
    ('APPROVE', 'Aprobar', 'Approve', 70);

INSERT IGNORE INTO `uss-ca`.`app_role` (code, name, description, active, system_role) VALUES
    ('PLATFORM_ADMIN', 'Administrador plataforma', 'Acceso completo a la plataforma, incluida Administración.', 1, 1),
    ('DATA_STEWARD', 'Data Steward', 'Consulta y administración operativa de personas, producción, calidad e integraciones.', 1, 1),
    ('ANALYST', 'Analista', 'Consulta de los módulos operativos. Sin administración.', 1, 1),
    ('READ_ONLY', 'Solo lectura', 'Consulta de dashboard, indicadores y auditoría.', 1, 1),
    ('CONSULTA', 'Consulta', 'Consulta de los módulos funcionales. Sin administración ni auditoría.', 1, 1);

-- PLATFORM_ADMIN: todos los módulos CONSULT + ADMINISTER
INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, m.code, p.code
FROM `uss-ca`.`app_role` r
CROSS JOIN `uss-ca`.`app_module` m
CROSS JOIN `uss-ca`.`app_permission` p
WHERE r.code = 'PLATFORM_ADMIN'
  AND p.code IN ('CONSULT', 'ADMINISTER');

-- DATA_STEWARD
INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, x.module_code, x.permission_code
FROM `uss-ca`.`app_role` r
JOIN (
    SELECT 'DASHBOARD' AS module_code, 'CONSULT' AS permission_code
    UNION ALL SELECT 'PEOPLE', 'CONSULT' UNION ALL SELECT 'PEOPLE', 'ADMINISTER'
    UNION ALL SELECT 'RESEARCH_OUTPUTS', 'CONSULT' UNION ALL SELECT 'RESEARCH_OUTPUTS', 'ADMINISTER'
    UNION ALL SELECT 'JOURNALS', 'CONSULT' UNION ALL SELECT 'JOURNALS', 'ADMINISTER'
    UNION ALL SELECT 'PROJECTS', 'CONSULT' UNION ALL SELECT 'PROJECTS', 'ADMINISTER'
    UNION ALL SELECT 'ORGANIZATIONS', 'CONSULT' UNION ALL SELECT 'ORGANIZATIONS', 'ADMINISTER'
    UNION ALL SELECT 'DIRECTORY', 'CONSULT' UNION ALL SELECT 'DIRECTORY', 'ADMINISTER'
    UNION ALL SELECT 'DATA_QUALITY', 'CONSULT' UNION ALL SELECT 'DATA_QUALITY', 'ADMINISTER'
    UNION ALL SELECT 'INDICATORS', 'CONSULT'
    UNION ALL SELECT 'INTEGRATIONS', 'CONSULT' UNION ALL SELECT 'INTEGRATIONS', 'ADMINISTER'
    UNION ALL SELECT 'AUDIT', 'CONSULT'
) x
WHERE r.code = 'DATA_STEWARD';

-- ANALYST: consultar módulos operativos, sin Administración
INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, m.code, 'CONSULT'
FROM `uss-ca`.`app_role` r
CROSS JOIN `uss-ca`.`app_module` m
WHERE r.code = 'ANALYST'
  AND m.code <> 'ADMINISTRATION';

-- READ_ONLY
INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, x.module_code, 'CONSULT'
FROM `uss-ca`.`app_role` r
JOIN (
    SELECT 'DASHBOARD' AS module_code
    UNION ALL SELECT 'INDICATORS'
    UNION ALL SELECT 'AUDIT'
) x
WHERE r.code = 'READ_ONLY';

-- CONSULTA: leer módulos funcionales. Sin ADMINISTRAR, sin AUDIT, sin ADMINISTRATION.
INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, x.module_code, 'CONSULT'
FROM `uss-ca`.`app_role` r
JOIN (
    SELECT 'DASHBOARD' AS module_code
    UNION ALL SELECT 'PEOPLE'
    UNION ALL SELECT 'RESEARCH_OUTPUTS'
    UNION ALL SELECT 'JOURNALS'
    UNION ALL SELECT 'PROJECTS'
    UNION ALL SELECT 'ORGANIZATIONS'
    UNION ALL SELECT 'DIRECTORY'
    UNION ALL SELECT 'DATA_QUALITY'
    UNION ALL SELECT 'INDICATORS'
    UNION ALL SELECT 'INTEGRATIONS'
) x
WHERE r.code = 'CONSULTA';
