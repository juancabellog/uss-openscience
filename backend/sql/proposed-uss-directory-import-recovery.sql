-- PROPUESTO. NO ejecutar desde el agente.
-- Catálogo: `uss-ca`
--
-- Recuperación de la ejecución 1 del Directorio USS (status RUNNING huérfano).
-- NO toca tablas `pure_*`.
-- NO hace DELETE ni TRUNCATE.
-- NO inactiva personas.
--
-- Causa raíz (confirmada con MySQL + messages.log):
--   1. insertImportCommitted() confirma RUNNING en autocommit (id=1, 12:21:06).
--   2. persistPlan() usa OTRA transacción. Al morir el worker esa TX hace rollback:
--      person/row/issue/history = 0.
--   3. failImport() solo corre en catch. Un reinicio de Liberty no ejecuta catch.
--   4. ReentrantLock es solo JVM. No hay GET_LOCK ni sync_control para directorio.
--      El bloqueo que sobrevive es hasRunningImport() WHERE status='RUNNING'.
--   5. messages.log actual empieza a las 12:41:01 (Liberty relanzado). No queda
--      el POST original de las 12:21. A las 12:41:55 y 12:43:57 el POST
--      /directory/imports responde 400 DIRECTORY_IMPORT_RUNNING.
--   6. @PostConstruct del import service es perezoso: no recuperaba al arrancar.
--
-- Diagnóstico MySQL informado por el usuario (sin datos pendientes):
--   uss_directory_person = 0
--   uss_directory_import_row import_id=1 = 0
--   uss_directory_import_issue import_id=1 = 0
--   uss_directory_person_history = 0
--   PROCESSLIST sin trabajador de importación.
-- El bloque B es seguro: no hay filas parciales ni worker activo.
--
-- Ejecutar primero el bloque A (solo SELECT). Enviar el resultado si hay
-- filas parciales inesperadas. El bloque B solo si el diagnóstico confirma
-- RUNNING sin trabajador SQL y se aprueba esta recuperación.
--
-- El bloque C (columnas de ownership) es opcional y no es necesario para
-- desbloquear la ejecución 1.

-- =============================================================================
-- A. Diagnóstico — solo lectura
-- =============================================================================

SELECT id, public_id, original_filename, status, imported_by, started_at, finished_at,
       row_count, created_count, updated_count, unchanged_count, reactivated_count,
       inactivated_count, issue_count, error_message
FROM `uss-ca`.`uss_directory_import`
WHERE id = 1;

SELECT COUNT(*) AS people_total
FROM `uss-ca`.`uss_directory_person`;

SELECT COUNT(*) AS people_from_import_1
FROM `uss-ca`.`uss_directory_person`
WHERE last_seen_import_id = 1;

SELECT COUNT(*) AS import_rows_1
FROM `uss-ca`.`uss_directory_import_row`
WHERE import_id = 1;

SELECT COUNT(*) AS import_issues_1
FROM `uss-ca`.`uss_directory_import_issue`
WHERE import_id = 1;

SELECT COUNT(*) AS history_1
FROM `uss-ca`.`uss_directory_person_history`
WHERE import_id = 1;

SELECT COUNT(*) AS emails_1
FROM `uss-ca`.`uss_directory_person_email`
WHERE first_seen_import_id = 1 OR last_seen_import_id = 1;

SHOW FULL PROCESSLIST;

-- Esperado para la ejecución 1 interrumpida al confirmar:
--   status = RUNNING, finished_at NULL, row_count 0
--   people_total = 0, people_from_import_1 = 0
--   import_rows_1 = 0, import_issues_1 = 0, history_1 = 0, emails_1 = 0
--   PROCESSLIST sin UPDATE/INSERT de directorio (solo Sleep)
-- Si hay filas parciales, NO borrarlas. El reintento reconcilia por rut_hmac.

-- =============================================================================
-- B. Recuperación de la ejecución 1 — requiere aprobación explícita
-- =============================================================================
-- Condiciones:
--   1. PROCESSLIST sin trabajador de importación.
--   2. Liberty no tiene el lock en memoria (proceso reiniciado).
--   3. status sigue siendo RUNNING.
-- Efecto:
--   RUNNING → FAILED, finished_at ahora, motivo ABANDONED.
--   Historial conservado. Personas no inactivadas. PURE intacto.

START TRANSACTION;

UPDATE `uss-ca`.`uss_directory_import`
SET status = 'FAILED',
    finished_at = CURRENT_TIMESTAMP(6),
    error_message = 'DIRECTORY_IMPORT_ABANDONED: the import worker was lost. People from this execution were not committed. History kept. No inactivation. importId=1'
WHERE id = 1
  AND status = 'RUNNING';

SELECT ROW_COUNT() AS recovered_rows;

SELECT id, public_id, status, started_at, finished_at, error_message
FROM `uss-ca`.`uss_directory_import`
WHERE id = 1;

COMMIT;

-- Tras COMMIT, POST /directory/imports deja de responder
-- "A directory import is already running".
-- Volver a Validar y simular y luego Confirmar importación.
-- El reconciliador no duplica personas si aparecen filas previas con el mismo rut_hmac.

-- =============================================================================
-- C. Ownership / heartbeat (opcional, para siguientes importaciones)
-- =============================================================================
-- No es necesario para desbloquear id=1. El backend funciona sin estas columnas.
-- Si se aplican, el worker escribe owner_token y heartbeat_at. Una fila RUNNING
-- solo se recupera si este proceso ya no es el dueño. La antigüedad sola no
-- libera el bloqueo.

-- ALTER TABLE `uss-ca`.`uss_directory_import`
--     ADD COLUMN owner_token CHAR(36) NULL AFTER imported_by,
--     ADD COLUMN heartbeat_at DATETIME(6) NULL AFTER started_at,
--     ADD COLUMN expected_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER skipped_count,
--     ADD COLUMN processed_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER expected_count;
