-- PROPUESTO. NO ejecutado por el agente.
-- Corrige la pérdida de precisión de FTE en pure_person_organization.
--
-- Definición anterior (live):
--   fte DECIMAL(5,2)
-- Definición nueva:
--   fte DECIMAL(7,4) NULL
--
-- No intenta reconstruir valores ya redondeados (0.80 no se convierte a 0.795).
-- La próxima sincronización PURE escribirá el valor real.

ALTER TABLE `uss-ca`.`pure_person_organization`
    MODIFY COLUMN fte DECIMAL(7,4) NULL;

-- Validación
SHOW COLUMNS
FROM `uss-ca`.`pure_person_organization`
LIKE 'fte';

-- Revisión de valores actuales (seguirán con la escala histórica hasta la sync)
SELECT
    person_id,
    organization_uuid,
    fte
FROM `uss-ca`.`pure_person_organization`
WHERE fte IS NOT NULL
ORDER BY person_id;

-- OPCIONAL / solo DEV: conservar el syncId del falso positivo FTE como evidencia.
-- Si se desea un historial limpio antes de re-probar, borrar solo el sync de prueba.
