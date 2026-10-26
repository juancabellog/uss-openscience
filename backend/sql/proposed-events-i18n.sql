-- PROPUESTO. NO ejecutar desde el agente.
-- Campos opcionales de internacionalización para OPENSCIENCE Events.
-- No ALTER/INSERT/DELETE sobre `pure_*` ni `uss_directory_*`.
-- No modifica filas existentes: title_en/description_en son NULL y primary_language queda en 'es'.
-- preferred_language en inscripción es opcional; hoy el idioma ya se guarda en snapshot_json.

SET @events_title_en_col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca'
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'title_en'
);
SET @events_title_en_sql := IF(
    @events_title_en_col = 0,
    'ALTER TABLE `uss-ca`.`event` ADD COLUMN title_en VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE events_title_en_stmt FROM @events_title_en_sql;
EXECUTE events_title_en_stmt;
DEALLOCATE PREPARE events_title_en_stmt;

SET @events_description_en_col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca'
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'description_en'
);
SET @events_description_en_sql := IF(
    @events_description_en_col = 0,
    'ALTER TABLE `uss-ca`.`event` ADD COLUMN description_en TEXT NULL',
    'SELECT 1'
);
PREPARE events_description_en_stmt FROM @events_description_en_sql;
EXECUTE events_description_en_stmt;
DEALLOCATE PREPARE events_description_en_stmt;

SET @events_primary_lang_col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca'
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'primary_language'
);
SET @events_primary_lang_sql := IF(
    @events_primary_lang_col = 0,
    'ALTER TABLE `uss-ca`.`event` ADD COLUMN primary_language VARCHAR(8) NOT NULL DEFAULT ''es''',
    'SELECT 1'
);
PREPARE events_primary_lang_stmt FROM @events_primary_lang_sql;
EXECUTE events_primary_lang_stmt;
DEALLOCATE PREPARE events_primary_lang_stmt;

SET @events_reg_lang_col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca'
      AND TABLE_NAME = 'event_registration'
      AND COLUMN_NAME = 'preferred_language'
);
SET @events_reg_lang_sql := IF(
    @events_reg_lang_col = 0,
    'ALTER TABLE `uss-ca`.`event_registration` ADD COLUMN preferred_language VARCHAR(8) NULL',
    'SELECT 1'
);
PREPARE events_reg_lang_stmt FROM @events_reg_lang_sql;
EXECUTE events_reg_lang_stmt;
DEALLOCATE PREPARE events_reg_lang_stmt;

SET @events_artifact_lang_col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca'
      AND TABLE_NAME = 'event_generated_artifact'
      AND COLUMN_NAME = 'language'
);
SET @events_artifact_lang_sql := IF(
    @events_artifact_lang_col = 0,
    'ALTER TABLE `uss-ca`.`event_generated_artifact` ADD COLUMN language VARCHAR(8) NULL',
    'SELECT 1'
);
PREPARE events_artifact_lang_stmt FROM @events_artifact_lang_sql;
EXECUTE events_artifact_lang_stmt;
DEALLOCATE PREPARE events_artifact_lang_stmt;
