-- OPCIONAL / DIAGNÓSTICO. No ejecutado por el agente.
-- Detecta el falso positivo histórico: misma organization_uuid
-- aparece como ORGANIZATION removed y added en el mismo padre y sync.

SELECT
    c.sync_execution_id,
    c.record_uuid AS person_uuid,
    c.old_value AS removed_org,
    a.new_value AS added_org,
    COUNT(*) AS pairs
FROM `uss-ca`.sync_change c
JOIN `uss-ca`.sync_change a
    ON a.sync_execution_id = c.sync_execution_id
   AND a.record_uuid = c.record_uuid
   AND a.change_scope = 'ORGANIZATION'
   AND a.description = 'Organization added'
   AND LOWER(TRIM(a.new_value)) = LOWER(TRIM(c.old_value))
WHERE c.change_scope = 'ORGANIZATION'
  AND c.description = 'Organization removed'
  AND c.old_value IS NOT NULL
GROUP BY c.sync_execution_id, c.record_uuid, c.old_value, a.new_value
ORDER BY c.sync_execution_id DESC, pairs DESC;

-- Personas UPDATED cuyo único child change fue ese par remove+add
-- (útil para estimar cuántos de los 121 son falso positivo).

SELECT
    e.id AS sync_id,
    e.updated_count,
    COUNT(DISTINCT c.record_uuid) AS parents_with_org_flip
FROM `uss-ca`.sync_execution e
JOIN `uss-ca`.sync_change c
    ON c.sync_execution_id = e.id
   AND c.change_scope = 'ORGANIZATION'
   AND c.description = 'Organization removed'
JOIN `uss-ca`.sync_change a
    ON a.sync_execution_id = c.sync_execution_id
   AND a.record_uuid = c.record_uuid
   AND a.change_scope = 'ORGANIZATION'
   AND a.description = 'Organization added'
   AND LOWER(TRIM(a.new_value)) = LOWER(TRIM(c.old_value))
WHERE e.entity = 'PEOPLE'
GROUP BY e.id, e.updated_count
ORDER BY e.id DESC;

-- NO ejecutar limpieza automática.
-- Si se desea conservar evidencia del bug: no borrar esas filas.
-- Si se desea un historial limpio antes del próximo test DEV, borrar
-- solo el syncId de prueba, no producción.
