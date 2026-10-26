-- PROPUESTO. NO ejecutar desde el agente.
-- Catálogo: `uss-ca` (el schema tiene guion: usar backticks).
--
-- Fases 3 a 6 — OPENSCIENCE Events (actas PDF, evidencias, asistencia, indicadores).
-- Tablas independientes. NO ALTER/INSERT/DELETE sobre `pure_*` ni `uss_directory_*`.
-- No modifica filas existentes de `event`, `event_participant` ni `event_registration`.
--
-- Requiere las tablas de proposed-events-phase1-2.sql.
--
-- event_generated_artifact.kind: MINUTES_PDF
-- event_document.kind: SIGNED_MINUTES | PHOTO | COMPLEMENTARY
-- event_attendance.status: PENDIENTE | ASISTIO | NO_ASISTIO | REVISAR
-- event_attendance.source: MANUAL | IMPORT | WALK_IN
-- event_attendance_import.status: PREVIEW | CONFIRMED | CANCELLED
-- event_attendance_import_row.match_status: MATCHED | UNMATCHED | AMBIGUOUS
-- event_attendance_import.source: CHATGPT | MANUAL_FILE | OLLAMA_READY

-- =============================================================================
-- Cierre documental opcional del evento (columna nueva, no destructiva)
-- =============================================================================
SET @events_close_col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca'
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'documentation_closed_at'
);
SET @events_close_sql := IF(
    @events_close_col = 0,
    'ALTER TABLE `uss-ca`.`event` ADD COLUMN documentation_closed_at DATETIME(6) NULL',
    'SELECT 1'
);
PREPARE events_close_stmt FROM @events_close_sql;
EXECUTE events_close_stmt;
DEALLOCATE PREPARE events_close_stmt;

-- =============================================================================
-- Acta PDF generada (cada generación es una versión)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`event_generated_artifact` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    kind VARCHAR(32) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    byte_size BIGINT UNSIGNED NOT NULL,
    blank_rows INT UNSIGNED NOT NULL DEFAULT 10,
    generated_by VARCHAR(128) NULL,
    generated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_event_artifact_version (event_id, version_no),
    UNIQUE KEY uq_event_artifact_stored (stored_name),
    KEY idx_event_artifact_event (event_id, generated_at),
    CONSTRAINT fk_event_artifact_event
        FOREIGN KEY (event_id) REFERENCES `uss-ca`.`event` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`event_generated_artifact_row` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    artifact_id BIGINT UNSIGNED NOT NULL,
    row_num INT UNSIGNED NOT NULL,
    registration_id BIGINT UNSIGNED NULL,
    public_code VARCHAR(32) NULL,
    display_name VARCHAR(512) NULL,
    faculty_or_institution VARCHAR(255) NULL,
    position_title VARCHAR(255) NULL,
    email_normalized VARCHAR(320) NULL,
    blank_row TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_event_artifact_row (artifact_id, row_num),
    KEY idx_event_artifact_row_code (public_code),
    CONSTRAINT fk_event_artifact_row_artifact
        FOREIGN KEY (artifact_id) REFERENCES `uss-ca`.`event_generated_artifact` (id),
    CONSTRAINT fk_event_artifact_row_registration
        FOREIGN KEY (registration_id) REFERENCES `uss-ca`.`event_registration` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- Evidencias documentales (archivos privados fuera de MySQL)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`event_document` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NOT NULL,
    kind VARCHAR(32) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    byte_size BIGINT UNSIGNED NOT NULL,
    note VARCHAR(512) NULL,
    uploaded_by VARCHAR(128) NULL,
    uploaded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_event_document_stored (stored_name),
    KEY idx_event_document_event (event_id, kind, uploaded_at),
    CONSTRAINT fk_event_document_event
        FOREIGN KEY (event_id) REFERENCES `uss-ca`.`event` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- Asistencia confirmada (separada de la propuesta por IA)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`event_attendance` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NOT NULL,
    registration_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    artifact_id BIGINT UNSIGNED NULL,
    artifact_row_number INT UNSIGNED NULL,
    walk_in TINYINT(1) NOT NULL DEFAULT 0,
    notes VARCHAR(512) NULL,
    confirmed_by VARCHAR(128) NULL,
    confirmed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_event_attendance_registration (event_id, registration_id),
    KEY idx_event_attendance_status (event_id, status),
    CONSTRAINT fk_event_attendance_event
        FOREIGN KEY (event_id) REFERENCES `uss-ca`.`event` (id),
    CONSTRAINT fk_event_attendance_registration
        FOREIGN KEY (registration_id) REFERENCES `uss-ca`.`event_registration` (id),
    CONSTRAINT fk_event_attendance_artifact
        FOREIGN KEY (artifact_id) REFERENCES `uss-ca`.`event_generated_artifact` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`event_attendance_history` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attendance_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    actor VARCHAR(128) NULL,
    reason VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_event_attendance_history (attendance_id, created_at),
    CONSTRAINT fk_event_attendance_history_attendance
        FOREIGN KEY (attendance_id) REFERENCES `uss-ca`.`event_attendance` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- Importación de asistencia (contrato reutilizable ChatGPT / futuro Ollama)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`event_attendance_import` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NOT NULL,
    artifact_id BIGINT UNSIGNED NULL,
    source VARCHAR(32) NOT NULL,
    original_filename VARCHAR(255) NULL,
    stored_name VARCHAR(255) NULL,
    sha256 CHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_event_attendance_import_event (event_id, status, created_at),
    CONSTRAINT fk_event_attendance_import_event
        FOREIGN KEY (event_id) REFERENCES `uss-ca`.`event` (id),
    CONSTRAINT fk_event_attendance_import_artifact
        FOREIGN KEY (artifact_id) REFERENCES `uss-ca`.`event_generated_artifact` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`event_attendance_import_row` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    import_id BIGINT UNSIGNED NOT NULL,
    row_num INT UNSIGNED NOT NULL,
    registration_code VARCHAR(32) NULL,
    artifact_version INT UNSIGNED NULL,
    proposed_status VARCHAR(32) NOT NULL,
    match_status VARCHAR(32) NOT NULL,
    matched_registration_id BIGINT UNSIGNED NULL,
    display_name VARCHAR(512) NULL,
    notes VARCHAR(512) NULL,
    PRIMARY KEY (id),
    KEY idx_event_attendance_import_row (import_id, match_status),
    CONSTRAINT fk_event_attendance_import_row_import
        FOREIGN KEY (import_id) REFERENCES `uss-ca`.`event_attendance_import` (id),
    CONSTRAINT fk_event_attendance_import_row_registration
        FOREIGN KEY (matched_registration_id) REFERENCES `uss-ca`.`event_registration` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
