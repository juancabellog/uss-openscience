-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente UNA SOLA VEZ en el catálogo `uss-ca`.
--
-- Persona compartida, acceso por aplicación y asignación de Ethics.
-- No crea una cuenta distinta para Ethics: la cuenta sigue siendo app_user.
-- No escribe pure_person y no copia sus filas.

-- =============================================================================
-- Persona compartida. person_kind INSTITUTIONAL puede tener pure_person_id nulo.
-- EXTERNAL, en Ethics, solo describe un revisor creado por Administración.
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`person` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pure_person_id BIGINT UNSIGNED NULL,
    person_kind VARCHAR(32) NOT NULL DEFAULT 'INSTITUTIONAL',
    first_name VARCHAR(128) NULL,
    last_name VARCHAR(128) NULL,
    email VARCHAR(255) NULL,
    email_normalized VARCHAR(255) NULL,
    institution VARCHAR(255) NULL,
    campus VARCHAR(255) NULL,
    faculty VARCHAR(255) NULL,
    unit_name VARCHAR(255) NULL,
    position_title VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT UNSIGNED NULL,
    modified_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    modified_by BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_person_pure (pure_person_id),
    KEY idx_person_email (email_normalized)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- SIN FK a pure_person: el DDL de esa tabla no vive en este repositorio.
-- Varios NULL en pure_person_id son válidos. Un id institucional no se repite.
-- email_normalized es una señal de búsqueda. No es clave primaria.
-- first_name, last_name, email, campus, faculty, unit_name y position_title
-- guardan a la persona local o externa. Si pure_person_id no es nulo, esas
-- columnas no son un override: el perfil efectivo sale solo de pure_person.

ALTER TABLE `uss-ca`.`app_user`
    ADD COLUMN person_id BIGINT UNSIGNED NULL AFTER email_normalized,
    ADD KEY idx_app_user_person (person_id),
    ADD CONSTRAINT fk_app_user_person
        FOREIGN KEY (person_id) REFERENCES `uss-ca`.`person` (id);

-- app_user conserva first_name y last_name porque la autenticación ya los exige.
-- El perfil de Ethics se lee desde person + pure_person, no desde esos campos.

-- =============================================================================
-- Acceso por aplicación. No reutiliza app_module ni app_user_role:
-- esos catálogos son módulos y roles de OpenScience.
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`app_application` (
    code VARCHAR(32) NOT NULL,
    name_es VARCHAR(128) NOT NULL,
    name_en VARCHAR(128) NOT NULL,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `uss-ca`.`app_application` (code, name_es, name_en) VALUES
    ('OPENSCIENCE', 'OpenScience', 'OpenScience'),
    ('ETHICS', 'Ética de proyectos', 'Project ethics');

CREATE TABLE IF NOT EXISTS `uss-ca`.`user_application` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    application_id VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT UNSIGNED NULL,
    modified_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    modified_by BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_application (user_id, application_id),
    KEY idx_user_application_status (application_id, status),
    CONSTRAINT fk_user_application_user
        FOREIGN KEY (user_id) REFERENCES `uss-ca`.`app_user` (id),
    CONSTRAINT fk_user_application_app
        FOREIGN KEY (application_id) REFERENCES `uss-ca`.`app_application` (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- status: ACTIVE | INACTIVE | REVOKED
-- Revocar una fila no borra app_user, person, snapshots ni revisiones.

-- =============================================================================
-- Asignación de rol de Ethics.
-- La tabla ya es de Ethics, así que no repite application_id.
-- role_id es el id de permissions-V2.yml. No guarda traducciones
-- y no apunta a app_role (PLATFORM_ADMIN, DATA_STEWARD, …).
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`ethics_role_assignment` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    valid_from DATE NULL,
    valid_until DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT UNSIGNED NULL,
    modified_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    modified_by BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    KEY idx_ethics_role_user (user_id, role_id, status),
    CONSTRAINT fk_ethics_role_user
        FOREIGN KEY (user_id) REFERENCES `uss-ca`.`app_user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- role_id: ADMINISTRATOR | COMMITTEE_PRESIDENT | EXECUTIVE_SECRETARY | REVIEWER | RESEARCHER
-- scope_type: GLOBAL | COMMITTEE | PROJECT | PROCESS
-- status: ACTIVE | COMPLETED | REVOKED
-- El RESEARCHER de una cuenta nueva es GLOBAL / *.
-- El REVIEWER de una ronda sigue siendo PROCESS y no reemplaza a RESEARCHER.
-- Project.researcherId sigue diciendo quién es responsable de un proyecto.
-- La fila YAML de ese caso no se concede otra vez.

-- =============================================================================
-- Copia de la persona al envío formal de una solicitud.
-- request_id es el id del prototipo hasta que la solicitud viva en MySQL.
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`ethics_request_person_snapshot` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    person_id BIGINT UNSIGNED NULL,
    first_name VARCHAR(128) NULL,
    last_name VARCHAR(128) NULL,
    email VARCHAR(255) NULL,
    institution VARCHAR(255) NULL,
    campus VARCHAR(255) NULL,
    faculty VARCHAR(255) NULL,
    unit_name VARCHAR(255) NULL,
    position_title VARCHAR(255) NULL,
    captured_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ethics_snapshot_request (request_id),
    KEY idx_ethics_snapshot_person (person_id),
    CONSTRAINT fk_ethics_snapshot_person
        FOREIGN KEY (person_id) REFERENCES `uss-ca`.`person` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
