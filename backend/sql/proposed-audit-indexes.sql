-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente en el catálogo `uss-ca` solo si el listado de Auditoría
-- (ORDER BY started_at DESC, id DESC sin filtro de entity) recorre de más.
--
-- Índices YA existentes en sync_execution (proposed-sync-lock / proposed-sync-mode):
--   PRIMARY KEY (id)
--   idx_sync_execution_entity_started (entity, started_at)
--   idx_sync_execution_status (status)
--   idx_sync_execution_heartbeat (heartbeat_at)
--   idx_sync_execution_entity_mode (entity, sync_mode, started_at)
--
-- sync_change: idx_sync_change_execution (sync_execution_id)
-- sync_error:  idx_sync_error_execution (sync_execution_id)
--
-- El listado padre de Auditoría es 1 fila por sync_execution, ordenada por
-- started_at. El índice compuesto por entity no cubre el listado global.

ALTER TABLE `uss-ca`.`sync_execution`
    ADD KEY idx_sync_execution_started (started_at, id);
