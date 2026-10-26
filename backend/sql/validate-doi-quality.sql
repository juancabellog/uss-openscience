-- SELECT de validación DOI. NO ejecutar desde el agente.
-- Universo de estas consultas: TODAS las filas de pure_research_output.
-- La UI / API, si existe active_in_pure, filtra por defecto ACTIVE
-- (COALESCE(active_in_pure, 1) = 1). Para replicar la UI, agregar ese predicado
-- en cada JOIN/WHERE sobre pure_research_output.
--
-- Relación:
--   pure_research_output.id
--     = pure_research_output_electronic_version.research_output_id
-- Solo DoiElectronicVersion cuenta para estas métricas.
--
-- Normalización usada por el backend (además de LOWER(TRIM)):
--   quita https://dx.doi.org/, http://dx.doi.org/,
--   https://doi.org/, http://doi.org/, doi:

-- A. Total publicaciones
SELECT COUNT(*) AS total
FROM `uss-ca`.`pure_research_output` x;

-- B. Publicaciones con DOI válido
SELECT COUNT(*) AS with_doi
FROM `uss-ca`.`pure_research_output` x
WHERE EXISTS (
    SELECT 1
    FROM `uss-ca`.`pure_research_output_electronic_version` y
    WHERE y.research_output_id = x.id
      AND y.type_discriminator = 'DoiElectronicVersion'
      AND y.doi IS NOT NULL
      AND TRIM(y.doi) <> ''
);

-- C. Publicaciones sin DOI válido
SELECT COUNT(*) AS without_doi
FROM `uss-ca`.`pure_research_output` x
WHERE NOT EXISTS (
    SELECT 1
    FROM `uss-ca`.`pure_research_output_electronic_version` y
    WHERE y.research_output_id = x.id
      AND y.type_discriminator = 'DoiElectronicVersion'
      AND y.doi IS NOT NULL
      AND TRIM(y.doi) <> ''
);

-- Invariante: A debe ser igual a B + C.

-- D. Distribución de cantidad DoiElectronicVersion por publicación
--    COUNT(y.id) distingue 0 de 1. NO usar COUNT(*).
SELECT
    n,
    COUNT(*) AS publications
FROM (
    SELECT
        x.id,
        COUNT(y.id) AS n
    FROM `uss-ca`.`pure_research_output` x
    LEFT JOIN `uss-ca`.`pure_research_output_electronic_version` y
      ON x.id = y.research_output_id
     AND y.type_discriminator = 'DoiElectronicVersion'
    GROUP BY x.id
) z
GROUP BY n
ORDER BY n;

-- E. Publicaciones con >1 DoiElectronicVersion
SELECT COUNT(*) AS publications_with_multiple_doi_versions
FROM (
    SELECT
        x.id,
        COUNT(y.id) AS n
    FROM `uss-ca`.`pure_research_output` x
    LEFT JOIN `uss-ca`.`pure_research_output_electronic_version` y
      ON x.id = y.research_output_id
     AND y.type_discriminator = 'DoiElectronicVersion'
    GROUP BY x.id
    HAVING COUNT(y.id) > 1
) multi;

-- F. Filas DoiElectronicVersion cuyo doi es NULL o vacío
--    Hallazgo de calidad de datos. NO es publicationsWithoutDoi.
SELECT COUNT(y.id) AS doi_electronic_versions_without_doi
FROM `uss-ca`.`pure_research_output_electronic_version` y
WHERE y.type_discriminator = 'DoiElectronicVersion'
  AND (y.doi IS NULL OR TRIM(y.doi) = '');

-- G. Mismo DOI repetido dentro de la misma publicación
SELECT
    y.research_output_id,
    LOWER(TRIM(y.doi)) AS normalized_doi,
    COUNT(y.id) AS version_rows
FROM `uss-ca`.`pure_research_output_electronic_version` y
WHERE y.type_discriminator = 'DoiElectronicVersion'
  AND y.doi IS NOT NULL
  AND TRIM(y.doi) <> ''
GROUP BY
    y.research_output_id,
    LOWER(TRIM(y.doi))
HAVING COUNT(y.id) > 1
ORDER BY version_rows DESC, y.research_output_id, normalized_doi;

-- H. DOI asociado a más de una publicación
SELECT
    normalized_doi,
    COUNT(*) AS publications
FROM (
    SELECT
        LOWER(TRIM(doi)) AS normalized_doi,
        research_output_id
    FROM `uss-ca`.`pure_research_output_electronic_version`
    WHERE type_discriminator = 'DoiElectronicVersion'
      AND doi IS NOT NULL
      AND TRIM(doi) <> ''
    GROUP BY
        LOWER(TRIM(doi)),
        research_output_id
) x
GROUP BY normalized_doi
HAVING COUNT(*) > 1
ORDER BY publications DESC, normalized_doi;

-- I. Research Outputs afectados por un DOI compartido
SELECT COUNT(*) AS publications_with_shared_doi
FROM `uss-ca`.`pure_research_output` x
WHERE EXISTS (
    SELECT 1
    FROM `uss-ca`.`pure_research_output_electronic_version` ev
    JOIN (
        SELECT LOWER(TRIM(dup.doi)) AS doi_norm
        FROM `uss-ca`.`pure_research_output_electronic_version` dup
        WHERE dup.type_discriminator = 'DoiElectronicVersion'
          AND dup.doi IS NOT NULL
          AND TRIM(dup.doi) <> ''
        GROUP BY LOWER(TRIM(dup.doi))
        HAVING COUNT(DISTINCT dup.research_output_id) > 1
    ) shared ON shared.doi_norm = LOWER(TRIM(ev.doi))
    WHERE ev.research_output_id = x.id
      AND ev.type_discriminator = 'DoiElectronicVersion'
      AND ev.doi IS NOT NULL
      AND TRIM(ev.doi) <> ''
);

-- J. Detalle de publicaciones para un DOI compartido conocido
SELECT
    o.id,
    o.pure_id,
    o.title,
    y.id AS electronic_version_id,
    y.pure_id AS electronic_version_pure_id,
    y.doi
FROM `uss-ca`.`pure_research_output` o
JOIN `uss-ca`.`pure_research_output_electronic_version` y
  ON y.research_output_id = o.id
WHERE y.type_discriminator = 'DoiElectronicVersion'
  AND y.doi IS NOT NULL
  AND TRIM(y.doi) <> ''
  AND LOWER(TRIM(y.doi)) = LOWER(TRIM('10.1093/bioinformatics/btag389'))
ORDER BY o.pure_id, y.id;

-- Índices recomendados (NO ejecutar hasta revisarlos):
-- SHOW INDEX FROM `uss-ca`.`pure_research_output_electronic_version`;
--
-- CREATE INDEX idx_ro_ev_ro_type
--     ON `uss-ca`.`pure_research_output_electronic_version` (research_output_id, type_discriminator);
--
-- CREATE INDEX idx_ro_ev_type_doi
--     ON `uss-ca`.`pure_research_output_electronic_version` (type_discriminator, doi);
