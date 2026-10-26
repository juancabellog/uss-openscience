-- SELECT de validación Scopus IDs. NO ejecutar desde el agente.
-- Criterio institucional reutilizado por PersonRepository:
--   identifier_type = 'Scopus ID'
-- NO se filtra por identifier_typeDiscriminator.
--
-- Distinción:
--   Listado / filtro / mini-stats:
--     cuentan registros con identifier_value no nulo y no vacío.
--   Popover GET /people/{id}/scopus-identifiers:
--     count = todos los registros Scopus ID (incluye valor nulo/vacío).
--     distinctCount = TRIM(identifier_value) no nulo / no vacío.
--
-- El PURE ID del popover es pure_person_identifiers.pure_id,
-- no pure_person.pure_id.

-- A. Scopus IDs de una persona (ejemplo person_id = 3024)
SELECT
    id,
    person_id,
    pure_id,
    identifier_type,
    identifier_value
FROM `uss-ca`.`pure_person_identifiers`
WHERE person_id = 3024
  AND identifier_type = 'Scopus ID'
ORDER BY id;

-- B. Cantidad de registros Scopus ID por persona
--    (todos los registros del tipo, incluidos valor nulo/vacío)
SELECT
    p.id AS person_id,
    p.pure_id AS person_pure_id,
    COUNT(i.id) AS scopus_record_count
FROM `uss-ca`.`pure_person` p
LEFT JOIN `uss-ca`.`pure_person_identifiers` i
    ON i.person_id = p.id
   AND i.identifier_type = 'Scopus ID'
GROUP BY p.id, p.pure_id
ORDER BY scopus_record_count DESC, p.id;

-- B2. Misma métrica que el listado / filtro / mini-stats
--     (solo valores no nulos y no vacíos)
SELECT
    p.id AS person_id,
    COUNT(i.id) AS scopus_id_count
FROM `uss-ca`.`pure_person` p
LEFT JOIN `uss-ca`.`pure_person_identifiers` i
    ON i.person_id = p.id
   AND i.identifier_type = 'Scopus ID'
   AND i.identifier_value IS NOT NULL
   AND TRIM(i.identifier_value) <> ''
GROUP BY p.id
ORDER BY scopus_id_count DESC, p.id;

-- C. IDs distintos por persona (TRIM, excluye nulo/vacío)
SELECT
    p.id AS person_id,
    COUNT(i.id) AS scopus_record_count,
    COUNT(DISTINCT NULLIF(TRIM(i.identifier_value), '')) AS distinct_scopus_ids
FROM `uss-ca`.`pure_person` p
LEFT JOIN `uss-ca`.`pure_person_identifiers` i
    ON i.person_id = p.id
   AND i.identifier_type = 'Scopus ID'
GROUP BY p.id
HAVING scopus_record_count > 0
ORDER BY distinct_scopus_ids DESC, scopus_record_count DESC, p.id;

-- D. Mismo Scopus ID repetido dentro de una persona
SELECT
    i.person_id,
    TRIM(i.identifier_value) AS scopus_id,
    COUNT(*) AS records
FROM `uss-ca`.`pure_person_identifiers` i
WHERE i.identifier_type = 'Scopus ID'
  AND i.identifier_value IS NOT NULL
  AND TRIM(i.identifier_value) <> ''
GROUP BY i.person_id, TRIM(i.identifier_value)
HAVING COUNT(*) > 1
ORDER BY records DESC, i.person_id, scopus_id;

-- E. Personas con múltiples Scopus IDs distintos
SELECT
    i.person_id,
    COUNT(*) AS scopus_record_count,
    COUNT(DISTINCT TRIM(i.identifier_value)) AS distinct_scopus_ids
FROM `uss-ca`.`pure_person_identifiers` i
WHERE i.identifier_type = 'Scopus ID'
  AND i.identifier_value IS NOT NULL
  AND TRIM(i.identifier_value) <> ''
GROUP BY i.person_id
HAVING COUNT(DISTINCT TRIM(i.identifier_value)) > 1
ORDER BY distinct_scopus_ids DESC, scopus_record_count DESC, i.person_id;

-- Matching: TRIM(scopus_author.auth_id) = TRIM(pure_person_identifiers.identifier_value)
-- Ambos como STRING. No CAST a número.
-- Si hay varios scopus_author con el mismo auth_id, el backend toma MIN(id).

-- F. auth_id duplicado en scopus_author (inconsistencia; no debería ocurrir)
SELECT
    TRIM(auth_id) AS auth_id,
    COUNT(*) AS rows_in_scopus_author,
    GROUP_CONCAT(id ORDER BY id) AS scopus_author_ids
FROM `uss-ca`.`scopus_author`
WHERE auth_id IS NOT NULL
  AND TRIM(auth_id) <> ''
GROUP BY TRIM(auth_id)
HAVING COUNT(*) > 1
ORDER BY rows_in_scopus_author DESC, auth_id;

-- A. Scopus IDs PURE + author_name (ejemplo person_id = 3024)
SELECT
    ppi.id,
    ppi.pure_id,
    ppi.identifier_value,
    sa.id AS scopus_author_id,
    sa.author_name,
    sa.number_documents,
    sa.orcid
FROM `uss-ca`.`pure_person_identifiers` ppi
LEFT JOIN `uss-ca`.`scopus_author` sa
    ON ppi.identifier_value IS NOT NULL
   AND TRIM(ppi.identifier_value) <> ''
   AND TRIM(sa.auth_id) = TRIM(ppi.identifier_value)
WHERE ppi.person_id = 3024
  AND ppi.identifier_type = 'Scopus ID'
ORDER BY ppi.id;

-- B. Identifiers PURE que NO están en scopus_author
SELECT
    ppi.id,
    ppi.person_id,
    ppi.pure_id,
    ppi.identifier_value
FROM `uss-ca`.`pure_person_identifiers` ppi
LEFT JOIN `uss-ca`.`scopus_author` sa
    ON ppi.identifier_value IS NOT NULL
   AND TRIM(ppi.identifier_value) <> ''
   AND TRIM(sa.auth_id) = TRIM(ppi.identifier_value)
WHERE ppi.identifier_type = 'Scopus ID'
  AND ppi.identifier_value IS NOT NULL
  AND TRIM(ppi.identifier_value) <> ''
  AND sa.id IS NULL
ORDER BY ppi.person_id, ppi.id;

-- C. Identifiers PURE que SÍ están en scopus_author
SELECT
    ppi.id,
    ppi.person_id,
    ppi.pure_id,
    ppi.identifier_value,
    sa.id AS scopus_author_id,
    sa.author_name
FROM `uss-ca`.`pure_person_identifiers` ppi
JOIN `uss-ca`.`scopus_author` sa
    ON TRIM(sa.auth_id) = TRIM(ppi.identifier_value)
WHERE ppi.identifier_type = 'Scopus ID'
  AND ppi.identifier_value IS NOT NULL
  AND TRIM(ppi.identifier_value) <> ''
ORDER BY ppi.person_id, ppi.id;

-- D. Cobertura: Scopus IDs PURE totales vs encontrados en scopus_author
SELECT
    COUNT(*) AS pure_scopus_id_records,
    COUNT(DISTINCT NULLIF(TRIM(ppi.identifier_value), '')) AS pure_distinct_scopus_ids,
    COUNT(sa.id) AS found_in_scopus_author,
    COUNT(*) - COUNT(sa.id) AS missing_in_scopus_author
FROM `uss-ca`.`pure_person_identifiers` ppi
LEFT JOIN `uss-ca`.`scopus_author` sa
    ON ppi.identifier_value IS NOT NULL
   AND TRIM(ppi.identifier_value) <> ''
   AND TRIM(sa.auth_id) = TRIM(ppi.identifier_value)
WHERE ppi.identifier_type = 'Scopus ID';
