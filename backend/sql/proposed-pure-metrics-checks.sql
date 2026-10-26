-- PROPUESTO. NO ejecutar desde el agente.
-- Validación post-migración de métricas PURE. Catálogo: uss-ca

SELECT 'pure_journal_metric' AS tabla, COUNT(*) AS filas
FROM `uss-ca`.`pure_journal_metric`
UNION ALL
SELECT 'pure_journal_metric_fetch', COUNT(*) FROM `uss-ca`.`pure_journal_metric_fetch`
UNION ALL
SELECT 'pure_research_output_metric', COUNT(*) FROM `uss-ca`.`pure_research_output_metric`
UNION ALL
SELECT 'pure_research_output_metric_fetch', COUNT(*) FROM `uss-ca`.`pure_research_output_metric_fetch`
UNION ALL
SELECT 'pure_person_metric', COUNT(*) FROM `uss-ca`.`pure_person_metric`
UNION ALL
SELECT 'pure_person_metric_fetch', COUNT(*) FROM `uss-ca`.`pure_person_metric_fetch`;

-- Identity keys duplicadas (debe ser 0)
SELECT identity_key, COUNT(*) AS n
FROM `uss-ca`.`pure_journal_metric`
GROUP BY identity_key
HAVING n > 1;

SELECT identity_key, COUNT(*) AS n
FROM `uss-ca`.`pure_research_output_metric`
GROUP BY identity_key
HAVING n > 1;

SELECT identity_key, COUNT(*) AS n
FROM `uss-ca`.`pure_person_metric`
GROUP BY identity_key
HAVING n > 1;

-- CiteScore latest por revista
SELECT j.id, j.uuid, m.metric_year, m.decimal_value
FROM `uss-ca`.`pure_journal` j
JOIN `uss-ca`.`pure_journal_metric` m ON m.journal_id = j.id
WHERE m.collection_id = 'scopusJournal'
  AND m.metric_id = 'rating_citescore'
ORDER BY j.id, m.metric_year DESC
LIMIT 50;

-- Varias categorías / cuartiles el mismo año
SELECT journal_id, metric_year, COUNT(*) AS categorias, GROUP_CONCAT(integer_value ORDER BY integer_value) AS cuartiles
FROM `uss-ca`.`pure_journal_metric`
WHERE collection_id = 'scopusCitescore' AND metric_id = 'quartile'
GROUP BY journal_id, metric_year
HAVING categorias > 1
LIMIT 50;

-- FWCI
SELECT research_output_id, metric_id, decimal_value, integer_value, metric_year
FROM `uss-ca`.`pure_research_output_metric`
WHERE collection_id = 'sciVal' AND metric_id = 'fwci'
LIMIT 50;

-- Matriz h-index persona
SELECT person_id, source_term_en, category_term_en, metric_id, integer_value, decimal_value
FROM `uss-ca`.`pure_person_metric`
WHERE collection_id = 'hIndex'
ORDER BY person_id, source_term_en, category_term_en, metric_id
LIMIT 100;

-- Fetch FAILED vs EMPTY
SELECT collection_id, last_outcome, COUNT(*) AS n
FROM `uss-ca`.`pure_journal_metric_fetch`
GROUP BY collection_id, last_outcome;

SELECT collection_id, last_outcome, COUNT(*) AS n
FROM `uss-ca`.`pure_research_output_metric_fetch`
GROUP BY collection_id, last_outcome;

SELECT collection_id, last_outcome, COUNT(*) AS n
FROM `uss-ca`.`pure_person_metric_fetch`
GROUP BY collection_id, last_outcome;
