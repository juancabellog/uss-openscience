-- PROPUESTO. NO ejecutar desde el agente.
-- Catálogo: `uss-ca` (el schema tiene guion: usar backticks).
--
-- Fase 0 — Directorio institucional USS (matriz académica).
-- Entidad COMPARTIDA, independiente de Events y de PURE.
--
-- =============================================================================
-- Inspección de `docs/Matriz academica filtrada.xlsx` (Hoja1, 2943 filas)
-- Encabezados reales, en este orden:
--   Empresa
--   RUT
--   Nombre
--   Segundo nombre
--   Apellido
--   Segundo Apellido
--   Nombre Sede
--   Nombre Facultad y Vicerrectoría
--   Cargo
--   Email
--
-- Hallazgos (sin volcar PII):
--   * Empresa es un correo @uss.cl en las 2943 filas y es IDÉNTICO a Email.
--     No es razón social. Se conserva el crudo en la fila de importación.
--     No crea una segunda identidad ni un segundo email si está normalizado igual.
--   * RUT: 2943 valores, todos únicos, 8 o 9 caracteres sin puntos/guion.
--   * Email: 2943 valores únicos @uss.cl.
--   * Nombre Sede: 6 valores.
--   * Nombre Facultad y Vicerrectoría: 28 valores, con prefijo de catálogo
--     del tipo "c. Facultad de Medicina". Se guarda crudo + etiqueta sin prefijo.
--   * Segundo nombre: 159 vacíos. Segundo apellido: 24 vacíos.
--
-- =============================================================================
-- Identidad
-- =============================================================================
-- Identidad permanente: `uss_directory_person.uuid` (CHAR 36, UNIQUE).
-- NO usar como identidad: email, nombre, cargo, sede, facultad, Empresa.
-- Reconciliación entre importaciones: `rut_hmac` UNIQUE nullable
--   (HMAC-SHA256 del RUT normalizado, pepper de aplicación; no reversible).
-- RUT cifrado (AES-GCM) en `rut_encrypted`. Nunca se expone en APIs públicas.
-- Cambio de correo: actualiza `uss_directory_person_email` de LA MISMA persona.
--   No crea otra fila en `uss_directory_person`.
--
-- =============================================================================
-- PURE — no tocar
-- =============================================================================
-- Este script NO hace ALTER/INSERT/DELETE sobre tablas `pure_*`.
-- El vínculo opcional es `uss_directory_person_pure_link.pure_person_uuid`
-- sin FK a `pure_person`. Events/People siguen funcionando si el link está vacío.
--
-- =============================================================================
-- Actualización periódica
-- =============================================================================
-- Cada planilla nueva = importación FULL.
-- Match: 1) rut_hmac  2) email_normalized solo si no hay conflicto de RUT.
-- Ambiguos → `uss_directory_import_issue` (no upsert).
-- Inactivos solo si la FULL termina COMPLETED.
-- Archivo original fuera de MySQL (`storage_key` + sha256).
--
-- NO crear tablas event_* en esta fase.

-- =============================================================================
-- Importaciones
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`uss_directory_import` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NULL,
    sha256 CHAR(64) NULL,
    byte_size BIGINT UNSIGNED NULL,
    sheet_name VARCHAR(128) NULL,
    header_json TEXT NULL,
    status VARCHAR(32) NOT NULL,
    imported_by VARCHAR(128) NOT NULL,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at DATETIME(6) NULL,
    row_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_count INT UNSIGNED NOT NULL DEFAULT 0,
    updated_count INT UNSIGNED NOT NULL DEFAULT 0,
    unchanged_count INT UNSIGNED NOT NULL DEFAULT 0,
    reactivated_count INT UNSIGNED NOT NULL DEFAULT 0,
    inactivated_count INT UNSIGNED NOT NULL DEFAULT 0,
    issue_count INT UNSIGNED NOT NULL DEFAULT 0,
    skipped_count INT UNSIGNED NOT NULL DEFAULT 0,
    error_message VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_uss_directory_import_public (public_id),
    KEY idx_uss_directory_import_status (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- status: PENDING | RUNNING | COMPLETED | FAILED | CANCELLED
-- Inactivar personas SOLO cuando status = COMPLETED.

CREATE TABLE IF NOT EXISTS `uss-ca`.`uss_directory_import_row` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    import_id BIGINT UNSIGNED NOT NULL,
    source_row_number INT UNSIGNED NOT NULL,
    empresa_raw VARCHAR(320) NULL,
    rut_encrypted VARBINARY(256) NULL,
    rut_hmac CHAR(64) NULL,
    given_name VARCHAR(128) NULL,
    middle_name VARCHAR(128) NULL,
    family_name VARCHAR(128) NULL,
    second_family_name VARCHAR(128) NULL,
    campus_name VARCHAR(128) NULL,
    faculty_or_vr_raw VARCHAR(255) NULL,
    faculty_or_vr_label VARCHAR(255) NULL,
    position_title VARCHAR(255) NULL,
    email_raw VARCHAR(320) NULL,
    email_normalized VARCHAR(320) NULL,
    source_row_hash CHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    directory_person_id BIGINT UNSIGNED NULL,
    issue_code VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_uss_directory_import_row (import_id, source_row_number),
    KEY idx_uss_directory_import_row_person (directory_person_id),
    KEY idx_uss_directory_import_row_rut (rut_hmac),
    KEY idx_uss_directory_import_row_email (email_normalized),
    CONSTRAINT fk_uss_directory_import_row_import
        FOREIGN KEY (import_id) REFERENCES `uss-ca`.`uss_directory_import` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- outcome: CREATED | UPDATED | UNCHANGED | REACTIVATED | ISSUE | SKIPPED
-- RUT en esta tabla también va cifrado. El Excel original es la evidencia cruda.

CREATE TABLE IF NOT EXISTS `uss-ca`.`uss_directory_import_issue` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    import_id BIGINT UNSIGNED NOT NULL,
    import_row_id BIGINT UNSIGNED NULL,
    issue_code VARCHAR(64) NOT NULL,
    detail VARCHAR(2000) NULL,
    left_person_id BIGINT UNSIGNED NULL,
    right_person_id BIGINT UNSIGNED NULL,
    resolved TINYINT(1) NOT NULL DEFAULT 0,
    resolved_by VARCHAR(128) NULL,
    resolved_at DATETIME(6) NULL,
    resolution VARCHAR(32) NULL,
    PRIMARY KEY (id),
    KEY idx_uss_directory_issue_import (import_id, issue_code, resolved),
    CONSTRAINT fk_uss_directory_issue_import
        FOREIGN KEY (import_id) REFERENCES `uss-ca`.`uss_directory_import` (id),
    CONSTRAINT fk_uss_directory_issue_row
        FOREIGN KEY (import_row_id) REFERENCES `uss-ca`.`uss_directory_import_row` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- issue_code:
--   DUPLICATE_RUT_IN_FILE
--   DUPLICATE_EMAIL_IN_FILE
--   EMAIL_RUT_CONFLICT
--   RUT_EMAIL_CONFLICT
--   MISSING_RUT
--   MISSING_EMAIL
--   INVALID_RUT
--   INVALID_EMAIL
--   UNMAPPED_HEADER
-- resolution: KEEP_LEFT | KEEP_RIGHT | MERGE | IGNORE | RETRY  (manual, no automático)

-- =============================================================================
-- Persona de directorio
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`uss_directory_person` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid CHAR(36) NOT NULL,
    given_name VARCHAR(128) NULL,
    middle_name VARCHAR(128) NULL,
    family_name VARCHAR(128) NULL,
    second_family_name VARCHAR(128) NULL,
    display_name VARCHAR(512) NULL,
    campus_name VARCHAR(128) NULL,
    faculty_or_vr_raw VARCHAR(255) NULL,
    faculty_or_vr_label VARCHAR(255) NULL,
    position_title VARCHAR(255) NULL,
    primary_email_normalized VARCHAR(320) NULL,
    rut_encrypted VARBINARY(256) NULL,
    rut_hmac CHAR(64) NULL,
    active_in_directory TINYINT(1) NOT NULL DEFAULT 1,
    last_seen_in_directory_at DATETIME(6) NULL,
    last_seen_import_id BIGINT UNSIGNED NULL,
    last_change_detected_at DATETIME(6) NULL,
    removed_from_directory_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_uss_directory_person_uuid (uuid),
    UNIQUE KEY uq_uss_directory_person_rut_hmac (rut_hmac),
    KEY idx_uss_directory_person_active (active_in_directory),
    KEY idx_uss_directory_person_campus (campus_name),
    KEY idx_uss_directory_person_faculty (faculty_or_vr_label),
    KEY idx_uss_directory_person_email (primary_email_normalized),
    KEY idx_uss_directory_person_import (last_seen_import_id),
    CONSTRAINT fk_uss_directory_person_import
        FOREIGN KEY (last_seen_import_id) REFERENCES `uss-ca`.`uss_directory_import` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- primary_email_normalized es DENORMALIZADO para búsqueda. No es identidad.
-- UNIQUE rut_hmac: MySQL permite varios NULL (persona sin RUT usable).

CREATE TABLE IF NOT EXISTS `uss-ca`.`uss_directory_person_email` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    directory_person_id BIGINT UNSIGNED NOT NULL,
    email_original VARCHAR(320) NOT NULL,
    email_normalized VARCHAR(320) NOT NULL,
    source VARCHAR(32) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    first_seen_import_id BIGINT UNSIGNED NULL,
    last_seen_import_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_uss_directory_email_normalized (email_normalized),
    KEY idx_uss_directory_email_person (directory_person_id, kind, active),
    CONSTRAINT fk_uss_directory_email_person
        FOREIGN KEY (directory_person_id) REFERENCES `uss-ca`.`uss_directory_person` (id),
    CONSTRAINT fk_uss_directory_email_first_import
        FOREIGN KEY (first_seen_import_id) REFERENCES `uss-ca`.`uss_directory_import` (id),
    CONSTRAINT fk_uss_directory_email_last_import
        FOREIGN KEY (last_seen_import_id) REFERENCES `uss-ca`.`uss_directory_import` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- source: EMAIL_COLUMN | EMPRESA_COLUMN | MANUAL
-- kind: PRIMARY | ALTERNATE | FORMER
-- UNIQUE(email_normalized) global: un correo no se reasigna a otra persona
-- automáticamente. Si el mismo correo aparece con otro RUT → ISSUE.
-- Si Empresa == Email (caso actual), una sola fila source=EMAIL_COLUMN.
-- Si en una planilla futura Empresa es otro correo válido y distinto, se inserta
-- ALTERNATE con source=EMPRESA_COLUMN, misma persona.

CREATE TABLE IF NOT EXISTS `uss-ca`.`uss_directory_person_history` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    directory_person_id BIGINT UNSIGNED NOT NULL,
    import_id BIGINT UNSIGNED NOT NULL,
    snapshot_json MEDIUMTEXT NOT NULL,
    source_row_hash CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_uss_directory_history_person_import (directory_person_id, import_id),
    KEY idx_uss_directory_history_import (import_id),
    CONSTRAINT fk_uss_directory_history_person
        FOREIGN KEY (directory_person_id) REFERENCES `uss-ca`.`uss_directory_person` (id),
    CONSTRAINT fk_uss_directory_history_import
        FOREIGN KEY (import_id) REFERENCES `uss-ca`.`uss_directory_import` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- snapshot_json: campos visibles (nombres, sede, facultad, cargo, emails).
-- NO incluir RUT en claro. rut_hmac sí puede ir.

CREATE TABLE IF NOT EXISTS `uss-ca`.`uss_directory_person_pure_link` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    directory_person_id BIGINT UNSIGNED NOT NULL,
    pure_person_uuid CHAR(36) NOT NULL,
    match_method VARCHAR(32) NOT NULL,
    confidence VARCHAR(16) NOT NULL,
    confirmed TINYINT(1) NOT NULL DEFAULT 0,
    confirmed_by VARCHAR(128) NULL,
    confirmed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_uss_directory_pure_person (directory_person_id),
    UNIQUE KEY uq_uss_directory_pure_uuid (pure_person_uuid),
    CONSTRAINT fk_uss_directory_pure_person
        FOREIGN KEY (directory_person_id) REFERENCES `uss-ca`.`uss_directory_person` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- SIN FK a `pure_person`. match_method: EMAIL | MANUAL (RUT solo si PURE
-- expone identificador nacional; hoy no hay columna RUT en PURE).
-- Fase 0 puede crear la tabla vacía. El job de link queda para una fase posterior.

-- =============================================================================
-- Índices y restricciones (resumen)
-- =============================================================================
-- UNIQUE persona.uuid                  identidad interna estable
-- UNIQUE persona.rut_hmac              reconciliación entre planillas (NULL permitido)
-- UNIQUE email.email_normalized        un correo → una persona, incluso FORMER
-- UNIQUE import.public_id
-- UNIQUE import_row (import_id, source_row_number)
-- UNIQUE history (directory_person_id, import_id)
-- UNIQUE pure_link.directory_person_id y pure_link.pure_person_uuid
-- FK internas solo entre uss_directory_*.
-- NO hay FK hacia ni desde pure_*.
-- NO hay FK hacia event_* (aún no existen).

-- =============================================================================
-- RBAC: módulo DIRECTORY (no EVENTS)
-- =============================================================================
INSERT IGNORE INTO `uss-ca`.`app_module` (code, name_es, name_en, sort_order) VALUES
    ('DIRECTORY', 'Directorio USS', 'USS directory', 48);

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, 'DIRECTORY', p.code
FROM `uss-ca`.`app_role` r
CROSS JOIN `uss-ca`.`app_permission` p
WHERE r.code = 'PLATFORM_ADMIN'
  AND p.code IN ('CONSULT', 'ADMINISTER');

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, 'DIRECTORY', x.permission_code
FROM `uss-ca`.`app_role` r
JOIN (
    SELECT 'CONSULT' AS permission_code
    UNION ALL SELECT 'ADMINISTER'
) x
WHERE r.code = 'DATA_STEWARD';

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, 'DIRECTORY', 'CONSULT'
FROM `uss-ca`.`app_role` r
WHERE r.code IN ('ANALYST', 'CONSULTA');

-- =============================================================================
-- Reservado para fases posteriores (NO crear ahora)
-- =============================================================================
-- event_participant          identidad de persona entre eventos
-- event                     ficha
-- event_registration         snapshot por inscripción + participant_id
-- event_attendance
-- event_document / artefactos
-- Acta: SIN_ACTA → ACTA_CARGADA → EN_REVISION → VALIDADA → CERRADA
