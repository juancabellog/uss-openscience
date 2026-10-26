-- PROPUESTO. NO ejecutar desde el agente.
-- Diagnóstico de rendimiento: Producción científica.
-- Catálogo: uss-ca

-- ============================================================
-- 1) Índices actuales (comparar antes de crear)
-- ============================================================
SHOW INDEX FROM `uss-ca`.`pure_research_output`;
SHOW INDEX FROM `uss-ca`.`pure_research_output_electronic_version`;
SHOW INDEX FROM `uss-ca`.`pure_research_output_contributor`;
SHOW INDEX FROM `uss-ca`.`pure_research_output_journal`;
SHOW INDEX FROM `uss-ca`.`pure_research_output_status`;
SHOW INDEX FROM `uss-ca`.`pure_journal`;
SHOW INDEX FROM `uss-ca`.`pure_journal_title`;

-- ============================================================
-- 2) EXPLAIN ANALYZE — listado default (año DESC, page 0)
-- ============================================================
EXPLAIN ANALYZE
SELECT
    page.id,
    page.pure_id,
    page.uuid,
    page.title,
    page.year,
    page.type,
    (
        SELECT ev.doi
        FROM `uss-ca`.`pure_research_output_electronic_version` ev
        WHERE ev.research_output_id = page.id
          AND ev.type_discriminator = 'DoiElectronicVersion'
          AND ev.doi IS NOT NULL
          AND TRIM(ev.doi) <> ''
        ORDER BY ev.id
        LIMIT 1
    ) AS doi,
    (
        SELECT COUNT(c.id)
        FROM `uss-ca`.`pure_research_output_contributor` c
        WHERE c.research_output_id = page.id
    ) AS contributor_count,
    (
        SELECT COUNT(y.id)
        FROM `uss-ca`.`pure_research_output_electronic_version` y
        WHERE y.research_output_id = page.id
          AND y.type_discriminator = 'DoiElectronicVersion'
    ) AS doi_version_count
FROM (
    SELECT
        o.id,
        o.pure_id,
        o.uuid,
        o.title,
        y.publication_year AS year,
        COALESCE(NULLIF(TRIM(o.type_en), ''), NULLIF(TRIM(o.type_es), '')) AS type,
        COALESCE(o.active_in_pure, 1) AS active_in_pure
    FROM `uss-ca`.`pure_research_output` o
    LEFT JOIN (
        SELECT ranked.research_output_id, ranked.publication_year
        FROM (
            SELECT
                s.research_output_id,
                s.publication_year,
                ROW_NUMBER() OVER (
                    PARTITION BY s.research_output_id
                    ORDER BY
                        CASE WHEN s.current_status = 1 THEN 0 ELSE 1 END,
                        CASE WHEN s.current_status = 1 THEN s.id ELSE 2147483647 END,
                        s.publication_year DESC,
                        s.id
                ) AS rn
            FROM `uss-ca`.`pure_research_output_status` s
            WHERE s.publication_year IS NOT NULL
        ) ranked
        WHERE ranked.rn = 1
    ) y ON y.research_output_id = o.id
    WHERE COALESCE(o.active_in_pure, 1) = 1
    ORDER BY (y.publication_year IS NULL), y.publication_year DESC, o.title ASC, o.id ASC
    LIMIT 25 OFFSET 0
) page;

-- ============================================================
-- 3) EXPLAIN ANALYZE — COUNT del listado default
-- ============================================================
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM `uss-ca`.`pure_research_output` o
WHERE COALESCE(o.active_in_pure, 1) = 1;

-- ============================================================
-- 4) EXPLAIN ANALYZE — stats universo DOI (una pasada)
-- ============================================================
EXPLAIN ANALYZE
SELECT
    COUNT(*) AS total,
    COALESCE(SUM(CASE WHEN ev.valid_doi_count > 0 THEN 1 ELSE 0 END), 0) AS with_doi,
    COALESCE(SUM(CASE WHEN ev.valid_doi_count IS NULL OR ev.valid_doi_count = 0 THEN 1 ELSE 0 END), 0) AS without_doi,
    COALESCE(SUM(CASE WHEN ev.doi_version_count > 1 THEN 1 ELSE 0 END), 0) AS multiple_doi
FROM `uss-ca`.`pure_research_output` o
LEFT JOIN (
    SELECT
        y.research_output_id,
        COUNT(y.id) AS doi_version_count,
        SUM(CASE WHEN y.doi IS NOT NULL AND TRIM(y.doi) <> '' THEN 1 ELSE 0 END) AS valid_doi_count
    FROM `uss-ca`.`pure_research_output_electronic_version` y
    WHERE y.type_discriminator = 'DoiElectronicVersion'
    GROUP BY y.research_output_id
) ev ON ev.research_output_id = o.id
WHERE COALESCE(o.active_in_pure, 1) = 1;

-- ============================================================
-- 5) EXPLAIN ANALYZE — DOI compartido (sigue siendo el más caro)
-- ============================================================
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM (
    SELECT 1
    FROM `uss-ca`.`pure_research_output_electronic_version` dup
    JOIN `uss-ca`.`pure_research_output` parent ON parent.id = dup.research_output_id
    WHERE dup.type_discriminator = 'DoiElectronicVersion'
      AND dup.doi IS NOT NULL
      AND TRIM(dup.doi) <> ''
      AND COALESCE(parent.active_in_pure, 1) = 1
    GROUP BY LOWER(TRIM(dup.doi))
    HAVING COUNT(DISTINCT dup.research_output_id) > 1
) shared;

-- ============================================================
-- 6) Índices candidatos — crear SOLO los que SHOW INDEX no tenga
--    Orden: A → status → C → D → E. No crear B todavía.
-- ============================================================

-- A) DOI versions por RO (listado, EXISTS, stats)
-- CREATE INDEX idx_proev_ro_type
--     ON `uss-ca`.`pure_research_output_electronic_version` (research_output_id, type_discriminator);

-- Año display / sort default
-- CREATE INDEX idx_pros_ro_year
--     ON `uss-ca`.`pure_research_output_status` (research_output_id, current_status, publication_year);

-- C) Conteos de contribuidores
-- CREATE INDEX idx_proc_research_output
--     ON `uss-ca`.`pure_research_output_contributor` (research_output_id);

-- D) Relación Journal por RO
-- CREATE INDEX idx_proj_research_output
--     ON `uss-ca`.`pure_research_output_journal` (research_output_id);

-- E) Relación Journal por journal_id (solo si no existe)
-- CREATE INDEX idx_proj_journal_id
--     ON `uss-ca`.`pure_research_output_journal` (journal_id);

-- NO crear ahora:
-- (type_discriminator, doi, research_output_id) — LOWER(TRIM(doi)) no lo usa.
-- INDEX(active_in_pure) solo — baja selectividad (~todos = 1).
-- FULLTEXT(title) — LIKE %texto% no se resolvió como cuello P0.
