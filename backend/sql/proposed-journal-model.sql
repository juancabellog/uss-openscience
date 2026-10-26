-- PROPUESTO. NO ejecutar desde el agente.
-- Catálogo: uss-ca
--
-- FASE A — Nuevo modelo Journal (padre + hijos). No DROP de columnas legacy.
-- FASE B — Sincronizar JOURNALS desde PURE (botón Revistas).
-- FASE C — Queries de validación (abajo).
-- FASE D — Relación Research Output (journal_uuid, journal_id nullable).
-- FASE E — Deprecar title/issn/title_pure_id/issn_pure_id (solo documentar).
-- FASE F — DROP legacy: solo cuando el usuario lo autorice (bloque al final).

-- ============================================================
-- FASE A: columnas padre
-- ============================================================
ALTER TABLE `uss-ca`.`pure_journal`
    ADD COLUMN IF NOT EXISTS pure_id BIGINT UNSIGNED NULL AFTER id,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS created_date_pure DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS modified_by VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS modified_date_pure DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS pure_version VARCHAR(80) NULL,
    ADD COLUMN IF NOT EXISTS publisher_uuid CHAR(36) NULL,
    ADD COLUMN IF NOT EXISTS publisher_system_name VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS type_uri VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS type_en VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS type_es VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS workflow_step VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS workflow_description_en VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS workflow_description_es VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS system_name VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS active_in_pure TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS last_seen_in_pure_at DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS last_seen_sync_id VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS last_change_detected_at DATETIME(6) NULL,
    ADD COLUMN IF NOT EXISTS removed_from_pure_at DATETIME(6) NULL;

-- uuid ya debe ser UNIQUE. Si no lo es:
-- ALTER TABLE `uss-ca`.`pure_journal` ADD UNIQUE KEY uq_pure_journal_uuid (uuid);
ALTER TABLE `uss-ca`.`pure_journal`
    ADD UNIQUE KEY uq_pure_journal_pure_id (pure_id);

ALTER TABLE `uss-ca`.`pure_journal`
    ADD KEY idx_pure_journal_active (active_in_pure),
    ADD KEY idx_pure_journal_workflow (workflow_step),
    ADD KEY idx_pure_journal_type (type_uri),
    ADD KEY idx_pure_journal_publisher (publisher_uuid);

-- ============================================================
-- FASE A: children
-- UNIQUE(journal_id, pure_id) cuando PURE entrega pureId del child.
-- NO UNIQUE(issn) global: un ISSN no es identidad del Journal.
-- NO UNIQUE(journal_id, issn): el mismo ISSN dos veces es evidencia DQ.
-- ============================================================
CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_journal_title` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    journal_id BIGINT UNSIGNED NOT NULL,
    pure_id BIGINT UNSIGNED NULL,
    title VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_journal_title_pure (journal_id, pure_id),
    KEY idx_pure_journal_title_journal (journal_id),
    KEY idx_pure_journal_title_text (title(191)),
    CONSTRAINT fk_pure_journal_title_journal
        FOREIGN KEY (journal_id) REFERENCES `uss-ca`.`pure_journal` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_journal_issn` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    journal_id BIGINT UNSIGNED NOT NULL,
    pure_id BIGINT UNSIGNED NULL,
    issn VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_journal_issn_pure (journal_id, pure_id),
    KEY idx_pure_journal_issn_journal (journal_id),
    KEY idx_pure_journal_issn_value (issn),
    CONSTRAINT fk_pure_journal_issn_journal
        FOREIGN KEY (journal_id) REFERENCES `uss-ca`.`pure_journal` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_journal_identifier` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    journal_id BIGINT UNSIGNED NOT NULL,
    pure_id BIGINT UNSIGNED NULL,
    type_discriminator VARCHAR(64) NULL,
    id_source VARCHAR(64) NULL,
    identifier_value VARCHAR(255) NULL,
    type_uri VARCHAR(255) NULL,
    type_en VARCHAR(255) NULL,
    type_es VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_journal_identifier_pure (journal_id, pure_id),
    KEY idx_pure_journal_identifier_journal (journal_id),
    KEY idx_pure_journal_identifier_value (identifier_value),
    CONSTRAINT fk_pure_journal_identifier_journal
        FOREIGN KEY (journal_id) REFERENCES `uss-ca`.`pure_journal` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_journal_keyword_group` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    journal_id BIGINT UNSIGNED NOT NULL,
    pure_id BIGINT UNSIGNED NULL,
    type_discriminator VARCHAR(64) NULL,
    logical_name VARCHAR(128) NULL,
    name_en VARCHAR(255) NULL,
    name_es VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_journal_kwg_pure (journal_id, pure_id),
    KEY idx_pure_journal_kwg_journal (journal_id),
    CONSTRAINT fk_pure_journal_kwg_journal
        FOREIGN KEY (journal_id) REFERENCES `uss-ca`.`pure_journal` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- classifications[] no traen pureId en el ejemplo. Business key = URI.
CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_journal_classification` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    keyword_group_id BIGINT UNSIGNED NOT NULL,
    uri VARCHAR(255) NOT NULL,
    term_en VARCHAR(255) NULL,
    term_es VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_journal_class_uri (keyword_group_id, uri),
    KEY idx_pure_journal_class_group (keyword_group_id),
    CONSTRAINT fk_pure_journal_class_group
        FOREIGN KEY (keyword_group_id) REFERENCES `uss-ca`.`pure_journal_keyword_group` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- keywordContainers: el ejemplo ASJC no los trae, pero el DTO PURE sí los modela
-- en Research Output. Se persisten para no perderlos en un PUT futuro.
CREATE TABLE IF NOT EXISTS `uss-ca`.`pure_journal_keyword` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    keyword_group_id BIGINT UNSIGNED NOT NULL,
    pure_id BIGINT UNSIGNED NULL,
    state VARCHAR(64) NULL,
    origin VARCHAR(64) NULL,
    keyword_uri VARCHAR(255) NULL,
    keyword_en VARCHAR(255) NULL,
    keyword_es VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pure_journal_keyword_pure (keyword_group_id, pure_id),
    KEY idx_pure_journal_keyword_group (keyword_group_id),
    CONSTRAINT fk_pure_journal_keyword_group
        FOREIGN KEY (keyword_group_id) REFERENCES `uss-ca`.`pure_journal_keyword_group` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- FASE D: relación Research Output → Journal (no catálogo)
-- ============================================================
ALTER TABLE `uss-ca`.`pure_research_output_journal`
    ADD COLUMN IF NOT EXISTS journal_uuid CHAR(36) NULL AFTER journal_id;

ALTER TABLE `uss-ca`.`pure_research_output_journal`
    MODIFY journal_id BIGINT UNSIGNED NULL;

ALTER TABLE `uss-ca`.`pure_research_output_journal`
    ADD KEY idx_ro_journal_uuid (journal_uuid);

-- Backfill uuid desde el journal ya asociado. No inventa identidad por ISSN.
UPDATE `uss-ca`.`pure_research_output_journal` rj
JOIN `uss-ca`.`pure_journal` j ON j.id = rj.journal_id
SET rj.journal_uuid = j.uuid
WHERE rj.journal_uuid IS NULL
  AND j.uuid IS NOT NULL
  AND TRIM(j.uuid) <> '';

-- Tras Journal sync, resolver FK pendientes:
UPDATE `uss-ca`.`pure_research_output_journal` rj
JOIN `uss-ca`.`pure_journal` j ON j.uuid = rj.journal_uuid
SET rj.journal_id = j.id
WHERE rj.journal_uuid IS NOT NULL
  AND (rj.journal_id IS NULL OR rj.journal_id <> j.id);

-- ============================================================
-- FASE C: validación
-- ============================================================

-- A) total Journals activos
SELECT COUNT(*) AS total_journals
FROM `uss-ca`.`pure_journal`
WHERE COALESCE(active_in_pure, 1) = 1;

-- B) 0 / 1 / 2+ ISSN
SELECT
    CASE
        WHEN cnt = 0 THEN '0'
        WHEN cnt = 1 THEN '1'
        ELSE '2+'
    END AS issn_bucket,
    COUNT(*) AS journals
FROM (
    SELECT j.id, COUNT(i.id) AS cnt
    FROM `uss-ca`.`pure_journal` j
    LEFT JOIN `uss-ca`.`pure_journal_issn` i ON i.journal_id = j.id
    WHERE COALESCE(j.active_in_pure, 1) = 1
    GROUP BY j.id
) x
GROUP BY issn_bucket
ORDER BY issn_bucket;

-- C) Journal real + 2 ISSN
SELECT j.id, j.uuid, j.pure_id, i.pure_id AS issn_pure_id, i.issn
FROM `uss-ca`.`pure_journal` j
LEFT JOIN `uss-ca`.`pure_journal_issn` i ON i.journal_id = j.id
WHERE j.uuid = 'c3615b25-65ab-4855-917c-b77afccbf206';

-- D) titles
SELECT t.pure_id, t.title
FROM `uss-ca`.`pure_journal` j
JOIN `uss-ca`.`pure_journal_title` t ON t.journal_id = j.id
WHERE j.uuid = 'c3615b25-65ab-4855-917c-b77afccbf206';

-- E) Scopus identifiers
SELECT i.type_discriminator, i.id_source, i.pure_id, i.identifier_value, i.type_en, i.type_es
FROM `uss-ca`.`pure_journal` j
JOIN `uss-ca`.`pure_journal_identifier` i ON i.journal_id = j.id
WHERE j.uuid = 'c3615b25-65ab-4855-917c-b77afccbf206';

-- F) ASJC
SELECT g.logical_name, c.uri, c.term_en, c.term_es
FROM `uss-ca`.`pure_journal` j
JOIN `uss-ca`.`pure_journal_keyword_group` g ON g.journal_id = j.id
JOIN `uss-ca`.`pure_journal_classification` c ON c.keyword_group_id = g.id
WHERE j.uuid = 'c3615b25-65ab-4855-917c-b77afccbf206';

-- G) Research Outputs que referencian ese Journal
SELECT o.id, o.pure_id, o.uuid, o.title, rj.journal_id, rj.journal_uuid, rj.pure_id AS association_pure_id
FROM `uss-ca`.`pure_research_output` o
JOIN `uss-ca`.`pure_research_output_journal` rj ON rj.research_output_id = o.id
WHERE rj.journal_uuid = 'c3615b25-65ab-4855-917c-b77afccbf206'
   OR rj.journal_id IN (
        SELECT id FROM `uss-ca`.`pure_journal` WHERE uuid = 'c3615b25-65ab-4855-917c-b77afccbf206'
   );

-- H) UUID referenciados por publicaciones y ausentes en catálogo
SELECT DISTINCT rj.journal_uuid, COUNT(*) AS research_outputs
FROM `uss-ca`.`pure_research_output_journal` rj
LEFT JOIN `uss-ca`.`pure_journal` j ON j.uuid = rj.journal_uuid
WHERE rj.journal_uuid IS NOT NULL
  AND j.id IS NULL
GROUP BY rj.journal_uuid;

-- I) ISSN asociados a más de un Journal
SELECT i.issn, COUNT(DISTINCT i.journal_id) AS journals
FROM `uss-ca`.`pure_journal_issn` i
GROUP BY i.issn
HAVING COUNT(DISTINCT i.journal_id) > 1;

-- ISSN duplicado DENTRO del mismo Journal (DQ, no ocultar)
SELECT journal_id, issn, COUNT(*) AS rows
FROM `uss-ca`.`pure_journal_issn`
GROUP BY journal_id, issn
HAVING COUNT(*) > 1;

-- ============================================================
-- FASE F OPCIONAL — NO ejecutar ahora
-- ============================================================
-- ALTER TABLE `uss-ca`.`pure_journal`
--     DROP COLUMN title_pure_id,
--     DROP COLUMN title,
--     DROP COLUMN issn_pure_id,
--     DROP COLUMN issn;
