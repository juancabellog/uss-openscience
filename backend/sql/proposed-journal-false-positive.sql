-- OPCIONAL / DIAGNÓSTICO. No ejecutado por el agente.
-- Journal del Research Output PURE ID 9589074 (Anyons in 1 + 1 dimensions).

SELECT
    o.id,
    o.pure_id,
    o.uuid,
    o.title,
    rj.id AS relation_id,
    rj.pure_id AS association_pure_id,
    j.id AS journal_id,
    j.uuid AS journal_uuid,
    j.title AS journal_title,
    j.issn
FROM `uss-ca`.pure_research_output o
LEFT JOIN `uss-ca`.pure_research_output_journal rj ON rj.research_output_id = o.id
LEFT JOIN `uss-ca`.pure_journal j ON j.id = rj.journal_id
WHERE o.pure_id = 9589074;

-- Columnas de la relación y del catálogo
SHOW COLUMNS FROM `uss-ca`.pure_research_output_journal;
SHOW COLUMNS FROM `uss-ca`.pure_journal;

-- Publicaciones con ISSN 0370-2693
SELECT
    o.pure_id,
    o.title,
    j.issn,
    j.uuid,
    rj.pure_id AS association_pure_id
FROM `uss-ca`.pure_research_output o
JOIN `uss-ca`.pure_research_output_journal rj ON rj.research_output_id = o.id
JOIN `uss-ca`.pure_journal j ON j.id = rj.journal_id
WHERE REPLACE(REPLACE(UPPER(TRIM(j.issn)), '-', ''), ' ', '') = '03702693'
ORDER BY o.pure_id;

-- Posibles duplicados de catálogo (mismo ISSN, distinto id)
SELECT issn, COUNT(*) AS rows, GROUP_CONCAT(id) AS journal_ids
FROM `uss-ca`.pure_journal
WHERE issn IS NOT NULL
GROUP BY issn
HAVING COUNT(*) > 1;

-- Relación vs catálogo
SELECT
    rj.research_output_id,
    rj.pure_id AS association_pure_id,
    rj.journal_id,
    j.uuid,
    j.issn
FROM `uss-ca`.pure_research_output_journal rj
JOIN `uss-ca`.pure_journal j ON j.id = rj.journal_id
JOIN `uss-ca`.pure_research_output o ON o.id = rj.research_output_id
WHERE o.pure_id = 9589074;
