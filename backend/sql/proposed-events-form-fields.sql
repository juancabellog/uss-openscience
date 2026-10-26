-- PROPUESTO. NO ejecutar desde el agente.
-- Campos opcionales del formulario de eventos (acceso virtual e inscripción durante la actividad).
-- No ALTER/INSERT/DELETE sobre `pure_*` ni `uss_directory_*`.
-- No modifica filas existentes: ambas columnas son NULL/0.

SET @events_virtual_col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca'
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'virtual_access'
);
SET @events_virtual_sql := IF(
    @events_virtual_col = 0,
    'ALTER TABLE `uss-ca`.`event` ADD COLUMN virtual_access VARCHAR(1000) NULL',
    'SELECT 1'
);
PREPARE events_virtual_stmt FROM @events_virtual_sql;
EXECUTE events_virtual_stmt;
DEALLOCATE PREPARE events_virtual_stmt;

SET @events_during_col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca'
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'allow_registration_during_event'
);
SET @events_during_sql := IF(
    @events_during_col = 0,
    'ALTER TABLE `uss-ca`.`event` ADD COLUMN allow_registration_during_event TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE events_during_stmt FROM @events_during_sql;
EXECUTE events_during_stmt;
DEALLOCATE PREPARE events_during_stmt;
