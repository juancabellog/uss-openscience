-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente en el catálogo `uss-ca` ANTES de reintentar Crossref.
--
-- Causa: sync CROSSREF_UPSERT
--   Data truncation: Data too long for column 'name' at row 1
-- Tabla/columna real:
--   `uss-ca`.`crossref_author_affiliation`.`name`  (hoy VARCHAR(1000))
--
-- Los DOI NEJM (p.ej. 10.1056/nejmoa2102968) traen affiliation.name
-- concatenado que supera 1000 caracteres. No es funder_name ni full_name.
--
-- No se toca PURE. No se llama a Crossref desde este script.

ALTER TABLE `uss-ca`.`crossref_author_affiliation`
    MODIFY COLUMN `name` TEXT NULL;
