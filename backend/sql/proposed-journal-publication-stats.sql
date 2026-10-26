-- PROPUESTO. NO ejecutar desde el agente.
-- Catálogo: uss-ca
-- Validación: Journals activos sin Research Outputs activos asociados.
-- Identidad: journal_id, fallback journal_uuid. NO ISSN.

-- Índices recomendados (solo si SHOW INDEX demuestra que faltan):
-- ALTER TABLE `uss-ca`.`pure_research_output_journal`
--     ADD KEY idx_ro_journal_journal_id (journal_id),
--     ADD KEY idx_ro_journal_uuid (journal_uuid),
--     ADD KEY idx_ro_journal_research_output (research_output_id);
-- ALTER TABLE `uss-ca`.`pure_research_output`
--     ADD KEY idx_ro_active_in_pure (active_in_pure);

-- A) Journals activos sin RO activos
SELECT COUNT(*) AS journals_without_active_publications
FROM `uss-ca`.`pure_journal` j
WHERE COALESCE(j.active_in_pure, 1) = 1
  AND NOT EXISTS (
      SELECT 1
      FROM `uss-ca`.`pure_research_output_journal` rj
      JOIN `uss-ca`.`pure_research_output` ro ON ro.id = rj.research_output_id
      WHERE (
            rj.journal_id = j.id
            OR (rj.journal_id IS NULL AND rj.journal_uuid IS NOT NULL AND rj.journal_uuid = j.uuid)
      )
        AND COALESCE(ro.active_in_pure, 1) = 1
  );

-- B) Journals activos con una o más publicaciones activas
SELECT COUNT(*) AS journals_with_active_publications
FROM `uss-ca`.`pure_journal` j
WHERE COALESCE(j.active_in_pure, 1) = 1
  AND EXISTS (
      SELECT 1
      FROM `uss-ca`.`pure_research_output_journal` rj
      JOIN `uss-ca`.`pure_research_output` ro ON ro.id = rj.research_output_id
      WHERE (
            rj.journal_id = j.id
            OR (rj.journal_id IS NULL AND rj.journal_uuid IS NOT NULL AND rj.journal_uuid = j.uuid)
      )
        AND COALESCE(ro.active_in_pure, 1) = 1
  );

-- C) Distribución publication_count | journals
SELECT
    COALESCE(pc.publication_count, 0) AS publication_count,
    COUNT(*) AS journals
FROM `uss-ca`.`pure_journal` j
LEFT JOIN (
    SELECT matched.journal_id, COUNT(DISTINCT matched.research_output_id) AS publication_count
    FROM (
        SELECT rj.journal_id AS journal_id, rj.research_output_id
        FROM `uss-ca`.`pure_research_output_journal` rj
        JOIN `uss-ca`.`pure_research_output` ro ON ro.id = rj.research_output_id
        WHERE rj.journal_id IS NOT NULL
          AND COALESCE(ro.active_in_pure, 1) = 1
        UNION ALL
        SELECT j2.id AS journal_id, rj.research_output_id
        FROM `uss-ca`.`pure_research_output_journal` rj
        JOIN `uss-ca`.`pure_research_output` ro ON ro.id = rj.research_output_id
        JOIN `uss-ca`.`pure_journal` j2 ON j2.uuid = rj.journal_uuid
        WHERE rj.journal_id IS NULL
          AND rj.journal_uuid IS NOT NULL
          AND COALESCE(ro.active_in_pure, 1) = 1
    ) matched
    GROUP BY matched.journal_id
) pc ON pc.journal_id = j.id
WHERE COALESCE(j.active_in_pure, 1) = 1
GROUP BY COALESCE(pc.publication_count, 0)
ORDER BY publication_count;

-- D) Primeros 50 Journals sin publicaciones activas
SELECT
    j.pure_id,
    j.uuid,
    COALESCE((
        SELECT t.title
        FROM `uss-ca`.`pure_journal_title` t
        WHERE t.journal_id = j.id AND TRIM(t.title) <> ''
        ORDER BY t.id
        LIMIT 1
    ), j.title) AS title,
    0 AS publication_count
FROM `uss-ca`.`pure_journal` j
WHERE COALESCE(j.active_in_pure, 1) = 1
  AND NOT EXISTS (
      SELECT 1
      FROM `uss-ca`.`pure_research_output_journal` rj
      JOIN `uss-ca`.`pure_research_output` ro ON ro.id = rj.research_output_id
      WHERE (
            rj.journal_id = j.id
            OR (rj.journal_id IS NULL AND rj.journal_uuid IS NOT NULL AND rj.journal_uuid = j.uuid)
      )
        AND COALESCE(ro.active_in_pure, 1) = 1
  )
ORDER BY title, j.id
LIMIT 50;

-- E) Relaciones duplicadas Journal–Research Output
SELECT
    COALESCE(rj.journal_id, j.uuid) AS journal_key,
    rj.journal_id,
    rj.journal_uuid,
    rj.research_output_id,
    COUNT(*) AS relation_rows
FROM `uss-ca`.`pure_research_output_journal` rj
LEFT JOIN `uss-ca`.`pure_journal` j ON j.uuid = rj.journal_uuid
GROUP BY COALESCE(rj.journal_id, j.uuid), rj.journal_id, rj.journal_uuid, rj.research_output_id
HAVING COUNT(*) > 1
ORDER BY relation_rows DESC, rj.research_output_id
LIMIT 100;
