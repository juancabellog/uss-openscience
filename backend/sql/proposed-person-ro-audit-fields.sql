-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente en el catálogo `uss-ca`.
--
-- Journal YA tiene created_by / modified_by / workflow_*.
-- Research Output YA tiene workflow_step / workflow_es / workflow_en.
-- Este script solo agrega created_by / modified_by a Personas y Producción.
--
-- Si MySQL responde Duplicate column name, omita esa sentencia y continue.
-- No inventa valores históricos: la próxima sync full rellena desde PURE.

ALTER TABLE `uss-ca`.`pure_person`
    ADD COLUMN created_by VARCHAR(128) NULL;

ALTER TABLE `uss-ca`.`pure_person`
    ADD COLUMN modified_by VARCHAR(128) NULL;

ALTER TABLE `uss-ca`.`pure_research_output`
    ADD COLUMN created_by VARCHAR(128) NULL;

ALTER TABLE `uss-ca`.`pure_research_output`
    ADD COLUMN modified_by VARCHAR(128) NULL;

-- Índice OPCIONAL. No ejecutar salvo que el filtro Estado + active_in_pure
-- lo justifique en EXPLAIN real. Cardinalidad de workflow_step es baja.
-- ALTER TABLE `uss-ca`.`pure_research_output`
--     ADD KEY idx_ro_active_workflow (active_in_pure, workflow_step);
