-- PROPUESTO. NO ejecutar desde el agente.
-- El error Unpaywall UNPAYWALL_UPSERT / "Data too long for column 'record_uuid'"
-- NO es de unpaywall_work. Es de la auditoría:
--
--   uss-ca.sync_change.record_uuid
--
-- Unpaywall / Crossref / OpenAlex guardan el DOI ahí (antes era UUID PURE de 36).
-- DOI de 41–52+ caracteres revienta CHAR(36) / VARCHAR(36).
-- sync_error.record_uuid es CHAR(36); Java hoy recorta a 36, pero hay que
-- agrandarla para no perder el DOI en el detalle de errores.
--
-- Ejecutar primero la inspección. Luego el ALTER si el tipo es < 255.

-- =============================================================================
-- 1. Inspección: tipo real de record_uuid
-- =============================================================================

SELECT
    TABLE_NAME,
    COLUMN_NAME,
    COLUMN_TYPE,
    CHARACTER_MAXIMUM_LENGTH,
    IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'uss-ca'
  AND COLUMN_NAME = 'record_uuid'
ORDER BY TABLE_NAME;

-- =============================================================================
-- 2. ALTER — solo si CHARACTER_MAXIMUM_LENGTH < 255
-- =============================================================================

ALTER TABLE `uss-ca`.`sync_change`
    MODIFY COLUMN record_uuid VARCHAR(255) NULL;

ALTER TABLE `uss-ca`.`sync_error`
    MODIFY COLUMN record_uuid VARCHAR(255) NULL;

-- data_quality_finding no participa en este upsert. No tocarla aquí.
