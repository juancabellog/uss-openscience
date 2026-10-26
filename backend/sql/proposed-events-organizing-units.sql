-- PROPUESTO. NO ejecutar desde el agente.
-- Catálogo institucional de unidades organizadoras para OPENSCIENCE Events.
-- No ALTER/INSERT/DELETE sobre `pure_*` ni `uss_directory_*`.
-- No modifica inscripciones ni el texto histórico de `event.organizing_unit`.
--
-- 1) Crea `event_organizing_unit` (código estable, ES/EN, activo, orden).
-- 2) Agrega `event.organizing_unit_code` nullable.
-- 3) Relaciona solo coincidencias inequívocas con el catálogo.
-- 4) Deja valores ambiguos o desconocidos con código NULL para revisión.
--
-- «Otro» no se crea como unidad oficial a partir de texto libre.
-- Ejecute primero los SELECT de revisión y, si está de acuerdo, el UPDATE.

CREATE TABLE IF NOT EXISTS `uss-ca`.`event_organizing_unit` (
    code VARCHAR(32) NOT NULL,
    name_es VARCHAR(255) NOT NULL,
    name_en VARCHAR(255) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (code),
    KEY idx_event_organizing_unit_active (active, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `uss-ca`.`event_organizing_unit` (code, name_es, name_en, active, sort_order)
VALUES
    ('VICERRECTORIA', 'Vicerrectoría', 'Vice-Rectory', 1, 1),
    ('INVESTIGACION_DESARROLLO', 'Investigación y Desarrollo', 'Research and Development', 1, 2),
    ('DOCTORADOS', 'Doctorados', 'Doctoral programs', 1, 3),
    ('DISEI', 'DISEI', 'DISEI', 1, 4),
    ('PROPIEDAD_INTELECTUAL', 'Propiedad Intelectual', 'Intellectual Property', 1, 5),
    ('CIENCIA_ABIERTA', 'Ciencia Abierta', 'Open Science', 1, 6),
    ('OTHER', 'Otro (especificar)', 'Other (specify)', 1, 99)
ON DUPLICATE KEY UPDATE
    name_es = VALUES(name_es),
    name_en = VALUES(name_en),
    active = VALUES(active),
    sort_order = VALUES(sort_order);

SET @events_unit_code_col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'uss-ca'
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'organizing_unit_code'
);
SET @events_unit_code_sql := IF(
    @events_unit_code_col = 0,
    'ALTER TABLE `uss-ca`.`event` ADD COLUMN organizing_unit_code VARCHAR(32) NULL',
    'SELECT 1'
);
PREPARE events_unit_code_stmt FROM @events_unit_code_sql;
EXECUTE events_unit_code_stmt;
DEALLOCATE PREPARE events_unit_code_stmt;

-- -----------------------------------------------------------------------------
-- Revisión (no escribe). Ejecutar antes de asignar códigos.
-- proposed_code NULL = conservar texto para revisión manual.
-- -----------------------------------------------------------------------------
SELECT
    e.id,
    e.public_slug,
    e.publication_status,
    e.organizing_unit,
    e.organizing_unit_code,
    CASE
        WHEN e.organizing_unit_code IS NOT NULL THEN e.organizing_unit_code
        WHEN LOWER(TRIM(e.organizing_unit)) IN ('vicerrectoría', 'vicerrectoria') THEN 'VICERRECTORIA'
        WHEN LOWER(TRIM(e.organizing_unit)) IN ('investigación y desarrollo', 'investigacion y desarrollo') THEN 'INVESTIGACION_DESARROLLO'
        WHEN LOWER(TRIM(e.organizing_unit)) = 'doctorados' THEN 'DOCTORADOS'
        WHEN LOWER(TRIM(e.organizing_unit)) = 'disei' THEN 'DISEI'
        WHEN LOWER(TRIM(e.organizing_unit)) IN ('propiedad intelectual') THEN 'PROPIEDAD_INTELECTUAL'
        WHEN LOWER(TRIM(e.organizing_unit)) IN ('ciencia abierta') THEN 'CIENCIA_ABIERTA'
        ELSE NULL
    END AS proposed_code
FROM `uss-ca`.`event` e
ORDER BY e.id;

-- -----------------------------------------------------------------------------
-- Asignación no destructiva: solo coincidencias exactas e inequívocas.
-- No cambia organizing_unit. No marca OTHER automáticamente.
-- -----------------------------------------------------------------------------
UPDATE `uss-ca`.`event`
SET organizing_unit_code = 'VICERRECTORIA'
WHERE organizing_unit_code IS NULL
  AND LOWER(TRIM(organizing_unit)) IN ('vicerrectoría', 'vicerrectoria');

UPDATE `uss-ca`.`event`
SET organizing_unit_code = 'INVESTIGACION_DESARROLLO'
WHERE organizing_unit_code IS NULL
  AND LOWER(TRIM(organizing_unit)) IN ('investigación y desarrollo', 'investigacion y desarrollo');

UPDATE `uss-ca`.`event`
SET organizing_unit_code = 'DOCTORADOS'
WHERE organizing_unit_code IS NULL
  AND LOWER(TRIM(organizing_unit)) = 'doctorados';

UPDATE `uss-ca`.`event`
SET organizing_unit_code = 'DISEI'
WHERE organizing_unit_code IS NULL
  AND LOWER(TRIM(organizing_unit)) = 'disei';

UPDATE `uss-ca`.`event`
SET organizing_unit_code = 'PROPIEDAD_INTELECTUAL'
WHERE organizing_unit_code IS NULL
  AND LOWER(TRIM(organizing_unit)) = 'propiedad intelectual';

UPDATE `uss-ca`.`event`
SET organizing_unit_code = 'CIENCIA_ABIERTA'
WHERE organizing_unit_code IS NULL
  AND LOWER(TRIM(organizing_unit)) = 'ciencia abierta';

-- Pendientes de revisión (texto conservado, sin código oficial)
SELECT id, public_slug, organizing_unit, organizing_unit_code
FROM `uss-ca`.`event`
WHERE organizing_unit IS NOT NULL
  AND TRIM(organizing_unit) <> ''
  AND organizing_unit_code IS NULL
ORDER BY id;
