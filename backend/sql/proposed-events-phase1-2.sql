-- PROPUESTO. NO ejecutar desde el agente.
-- Catálogo: `uss-ca` (el schema tiene guion: usar backticks).
--
-- Fases 1 y 2 — OPENSCIENCE Events (gestión de eventos + inscripción pública).
-- Tablas independientes. NO ALTER/INSERT/DELETE sobre `pure_*` ni `uss_directory_*`.
-- El Directorio USS se consulta por correo/UUID; no se duplican las 2.943 personas.
--
-- Inspección previa (código y SQL del workspace, sin aplicar cambios en MySQL):
--   * El esqueleto `proposed-events.sql` de la fase 0 no se ejecutó.
--   * Este script lo reemplaza para las fases 1 y 2.
--   * No crea `event_attendance` ni artefactos PDF: quedan para fases posteriores.
--
-- publication_status: DRAFT | PUBLISHED | FINISHED | CANCELLED
-- event_type: TALK | SEMINAR | WORK_TABLE | WORKSHOP | JOURNEY | OTHER
-- modality: PRESENTIAL | VIRTUAL | HYBRID
-- affiliation_origin: DIRECTORY_MATCH | SELF_DECLARED
--   Nunca se asigna EXTERNA por ausencia en la matriz.
-- registration.status: CONFIRMED | CANCELLED

-- =============================================================================
-- Evento
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`event` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_slug VARCHAR(80) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description MEDIUMTEXT NULL,
    event_type VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    starts_at DATETIME(6) NULL,
    ends_at DATETIME(6) NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'America/Santiago',
    modality VARCHAR(32) NULL,
    location_name VARCHAR(255) NULL,
    campus_name VARCHAR(128) NULL,
    organizing_unit VARCHAR(255) NULL,
    publication_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    registration_opens_at DATETIME(6) NULL,
    registration_closes_at DATETIME(6) NULL,
    capacity INT UNSIGNED NULL,
    blank_signature_rows INT UNSIGNED NOT NULL DEFAULT 10,
    created_by VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_event_public_slug (public_slug),
    KEY idx_event_publication (publication_status, starts_at),
    KEY idx_event_type (event_type, publication_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- Participante (persona que interviene en eventos; no es un usuario de la plataforma)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`event_participant` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_uuid CHAR(36) NOT NULL,
    email_normalized VARCHAR(320) NOT NULL,
    display_name VARCHAR(512) NULL,
    faculty_or_institution VARCHAR(255) NULL,
    campus_name VARCHAR(128) NULL,
    position_title VARCHAR(255) NULL,
    affiliation_origin VARCHAR(32) NOT NULL,
    directory_person_uuid CHAR(36) NULL,
    pure_person_uuid CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_event_participant_uuid (public_uuid),
    UNIQUE KEY uq_event_participant_email (email_normalized),
    KEY idx_event_participant_directory (directory_person_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- directory_person_uuid y pure_person_uuid son referencias débiles, sin FK.

-- =============================================================================
-- Inscripción (separada de la futura asistencia)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`event_registration` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NOT NULL,
    participant_id BIGINT UNSIGNED NOT NULL,
    public_code VARCHAR(32) NOT NULL,
    seq_no INT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    email_normalized VARCHAR(320) NOT NULL,
    display_name VARCHAR(512) NULL,
    faculty_or_institution VARCHAR(255) NULL,
    campus_name VARCHAR(128) NULL,
    position_title VARCHAR(255) NULL,
    affiliation_origin VARCHAR(32) NOT NULL,
    directory_person_uuid CHAR(36) NULL,
    snapshot_json MEDIUMTEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    cancelled_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_event_registration_code (public_code),
    UNIQUE KEY uq_event_registration_seq (event_id, seq_no),
    UNIQUE KEY uq_event_registration_email (event_id, email_normalized),
    KEY idx_event_registration_event_status (event_id, status),
    KEY idx_event_registration_participant (participant_id),
    CONSTRAINT fk_event_registration_event
        FOREIGN KEY (event_id) REFERENCES `uss-ca`.`event` (id),
    CONSTRAINT fk_event_registration_participant
        FOREIGN KEY (participant_id) REFERENCES `uss-ca`.`event_participant` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`event_registration_status_history` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    registration_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    actor VARCHAR(128) NULL,
    reason VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_event_registration_history (registration_id, created_at),
    CONSTRAINT fk_event_registration_history_registration
        FOREIGN KEY (registration_id) REFERENCES `uss-ca`.`event_registration` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- Verificación de correo (token de un solo uso)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`event_email_challenge` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NOT NULL,
    email_normalized VARCHAR(320) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_event_email_challenge_token (token_hash),
    KEY idx_event_email_challenge_email (event_id, email_normalized, created_at),
    CONSTRAINT fk_event_email_challenge_event
        FOREIGN KEY (event_id) REFERENCES `uss-ca`.`event` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`event_verified_session` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NOT NULL,
    email_normalized VARCHAR(320) NOT NULL,
    session_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_event_verified_session (session_hash),
    KEY idx_event_verified_session_lookup (event_id, email_normalized, expires_at),
    CONSTRAINT fk_event_verified_session_event
        FOREIGN KEY (event_id) REFERENCES `uss-ca`.`event` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- RBAC: módulo EVENTS. No se asigna a CONSULTA.
-- =============================================================================
INSERT IGNORE INTO `uss-ca`.`app_module` (code, name_es, name_en, sort_order) VALUES
    ('EVENTS', 'Eventos académicos', 'Academic events', 49);

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, 'EVENTS', p.code
FROM `uss-ca`.`app_role` r
JOIN `uss-ca`.`app_permission` p ON p.code IN ('CONSULT', 'ADMINISTER')
WHERE r.code = 'PLATFORM_ADMIN';

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, 'EVENTS', p.code
FROM `uss-ca`.`app_role` r
JOIN `uss-ca`.`app_permission` p ON p.code IN ('CONSULT', 'ADMINISTER')
WHERE r.code = 'DATA_STEWARD';
