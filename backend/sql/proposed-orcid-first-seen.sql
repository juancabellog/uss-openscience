-- PROPUESTO. NO ejecutar desde el agente.
-- Fecha LOCAL: primera vez que OPENSCIENCE-USS observó un ORCID en PURE.
-- NO es la fecha en que PURE agregó el identificador.
-- NO reconstruye historia anterior al inicio del tracking.

ALTER TABLE `uss-ca`.`pure_person`
    ADD COLUMN orcid_first_seen_at DATETIME(6) NULL,
    ADD COLUMN orcid_first_seen_source VARCHAR(16) NULL;

-- Sin índice adicional: el universo de personas cabe en un scan (~miles de filas).
-- Evaluar INDEX(orcid_first_seen_at) solo si el EXPLAIN de la serie mensual lo justifica.

-- NO ejecutar el UPDATE siguiente para fingir una fecha de alta en PURE.
-- Es un baseline opcional si se quiere iniciar el tracking sin esperar el próximo FULL PEOPLE.
-- Semántica: "presente al iniciar seguimiento" (BASELINE), no "agregado en esa fecha".
--
UPDATE `uss-ca`.`pure_person`
SET orcid_first_seen_at = NOW(6),
orcid_first_seen_source = 'BASELINE'
 WHERE orcid IS NOT NULL
   AND TRIM(orcid) <> ''
   AND orcid_first_seen_at IS NULL;
