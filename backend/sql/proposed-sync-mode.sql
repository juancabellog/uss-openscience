-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente en el catálogo `uss-ca` ANTES de desplegar el JAR con SyncMode.
--
-- sync_mode es independiente de status:
--   mode = FULL | INCREMENTAL
--   status = PENDING | RUNNING | COMPLETED | ...
--
-- VARCHAR(16), no ENUM MySQL. Validación en Java (cl.uss.ca.sync.SyncMode).
--
-- Histórico:
--   PEOPLE, RESEARCH_OUTPUT, JOURNALS, ORCID, UNPAYWALL siempre recorrieron
--   toda la población → se backfillean a FULL.
--   CROSSREF / DSPACE: Java trata sync_mode NULL como FULL para baseline
--   (INCREMENTAL Crossref sigue cubriendo NEVER_SYNCED / STALE; DSpace sin
--   modo siempre recorrió discover). El UPDATE opcional de abajo solo etiqueta.

-- =============================================================================
-- FASE A — columna e índice en sync_execution
-- =============================================================================

ALTER TABLE `uss-ca`.`sync_execution`
    ADD COLUMN sync_mode VARCHAR(16) NULL AFTER entity;

ALTER TABLE `uss-ca`.`sync_execution`
    ADD KEY idx_sync_execution_entity_mode (entity, sync_mode, started_at);

UPDATE `uss-ca`.`sync_execution`
SET sync_mode = 'FULL'
WHERE sync_mode IS NULL
  AND entity IN ('PEOPLE', 'RESEARCH_OUTPUT', 'JOURNALS', 'ORCID', 'UNPAYWALL');

-- Opcional. Etiqueta ejecuciones CROSSREF/DSPACE COMPLETED para lastSyncMode.
-- No es obligatorio: SyncEntityPolicy.treatsNullSyncModeAsFull ya las cuenta
-- como FULL al calcular hasValidBaseline.
UPDATE `uss-ca`.`sync_execution`
SET sync_mode = 'FULL'
WHERE sync_mode IS NULL
  AND entity IN ('CROSSREF', 'DSPACE')
  AND status IN ('COMPLETED', 'COMPLETED_WITH_WARNINGS');

-- =============================================================================
-- FASE B — refresco manual por DOI (equivalente a orcid_person_sync.pending)
-- Omita el ALTER si la columna ya existe.
-- =============================================================================

ALTER TABLE `uss-ca`.`unpaywall_work`
    ADD COLUMN needs_refresh TINYINT(1) NOT NULL DEFAULT 0 AFTER last_sync_execution_id;

ALTER TABLE `uss-ca`.`crossref_work`
    ADD COLUMN needs_refresh TINYINT(1) NOT NULL DEFAULT 0 AFTER last_change_detected_at;

-- =============================================================================
-- FASE C — índices para candidatos INCREMENTAL (solo los que usan las queries)
-- idx_unpaywall_work_outcome / idx_unpaywall_work_retrieved ya existen.
-- idx_crossref_work_status / idx_crossref_work_synced ya existen.
-- =============================================================================

ALTER TABLE `uss-ca`.`orcid_person_sync`
    ADD KEY idx_orcid_person_sync_last (last_sync_status, last_sync_at);

ALTER TABLE `uss-ca`.`unpaywall_work`
    ADD KEY idx_unpaywall_work_needs_refresh (needs_refresh);

ALTER TABLE `uss-ca`.`crossref_work`
    ADD KEY idx_crossref_work_needs_refresh (needs_refresh);

-- =============================================================================
-- Notas de configuración (NO son SQL)
-- =============================================================================
-- orcid.refreshDays / ORCID_REFRESH_DAYS default 30 (nuevo).
-- crossref.refreshDays / CROSSREF_REFRESH_DAYS default 30 (ya existía).
-- unpaywall.refreshDays / UNPAYWALL_REFRESH_DAYS default 0 = STALE desactivado.
--   INCREMENTAL Unpaywall solo toma NEW / NEVER_SYNCED / ERROR / SOURCE_CHANGED /
--   MANUAL_REFRESH hasta que se suba el umbral (p. ej. 30).
-- openalex.refreshDays / OPENALEX_REFRESH_DAYS default 30 (ver proposed-openalex.sql).
