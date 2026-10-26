-- SELECT de validación de ordenamiento. NO ejecutar desde el agente.
-- El backend aplica además NULLS LAST:
--   (expr IS NULL), expr ASC|DESC
-- y un desempate estable por id.

-- A. Research Outputs por doiVersionCount DESC
SELECT
    o.id,
    o.pure_id,
    o.title,
    (
        SELECT COUNT(y.id)
        FROM `uss-ca`.`pure_research_output_electronic_version` y
        WHERE y.research_output_id = o.id
          AND y.type_discriminator = 'DoiElectronicVersion'
    ) AS doi_version_count
FROM `uss-ca`.`pure_research_output` o
ORDER BY
    (doi_version_count IS NULL),
    doi_version_count DESC,
    o.title ASC,
    o.id ASC
LIMIT 25;

-- B. Research Outputs por contributorsCount DESC
SELECT
    o.id,
    o.pure_id,
    o.title,
    (
        SELECT COUNT(c.id)
        FROM `uss-ca`.`pure_research_output_contributor` c
        WHERE c.research_output_id = o.id
    ) AS contributor_count
FROM `uss-ca`.`pure_research_output` o
ORDER BY
    (contributor_count IS NULL),
    contributor_count DESC,
    o.title ASC,
    o.id ASC
LIMIT 25;

-- C. Personas por publicationCount DESC
SELECT
    p.id,
    p.pure_id,
    p.last_name,
    p.first_name,
    (
        SELECT COUNT(DISTINCT c.research_output_id)
        FROM `uss-ca`.`pure_research_output_contributor` c
        WHERE c.person_uuid = p.uuid
    ) AS publication_count
FROM `uss-ca`.`pure_person` p
ORDER BY
    (publication_count IS NULL),
    publication_count DESC,
    p.last_name ASC,
    p.id ASC
LIMIT 25;

-- Índices recomendados (NO ejecutar hasta revisarlos):
-- SHOW INDEX FROM `uss-ca`.`pure_research_output_electronic_version`;
-- SHOW INDEX FROM `uss-ca`.`pure_research_output_contributor`;
-- SHOW INDEX FROM `uss-ca`.`pure_person`;
--
-- CREATE INDEX idx_ro_ev_ro_type
--     ON `uss-ca`.`pure_research_output_electronic_version` (research_output_id, type_discriminator);
--
-- CREATE INDEX idx_ro_contributor_ro
--     ON `uss-ca`.`pure_research_output_contributor` (research_output_id);
--
-- CREATE INDEX idx_ro_contributor_person_uuid
--     ON `uss-ca`.`pure_research_output_contributor` (person_uuid, research_output_id);
--
-- CREATE INDEX idx_person_last_first
--     ON `uss-ca`.`pure_person` (last_name, first_name);
--
-- CREATE INDEX idx_person_pure_id
--     ON `uss-ca`.`pure_person` (pure_id);
