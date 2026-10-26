-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente en el catálogo `uss-ca` ANTES de desplegar el JAR con EXT-01..EXT-12.
--
-- Consistencia externa transversal. PURE sigue siendo source of truth institucional.
-- Las fuentes externas son evidencia. No se escribe en PURE desde estas reglas.
--
-- CROSSREF-003/004/005/006 quedan superadas por EXT-01/03/04/12 (N fuentes).
-- Los findings históricos de esas CROSSREF se auto-resuelven en la próxima
-- ejecución FULL porque el scan las marca como evaluadas sin emitir candidatos.
--
-- EXT-12: la planilla dice "financiamiento"; la condición funcional es
-- "DOI exacto y datos de autores en PURE y en la fuente externa".
-- No se modifica el Excel.

-- =============================================================================
-- FASE A — evidencias N por finding
-- =============================================================================

CREATE TABLE IF NOT EXISTS `uss-ca`.`data_quality_finding_evidence` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    finding_id BIGINT UNSIGNED NOT NULL,
    source VARCHAR(32) NOT NULL,
    source_record_id VARCHAR(64) NULL,
    source_field VARCHAR(64) NULL,
    external_value TEXT NULL,
    normalized_external_value VARCHAR(500) NULL,
    comparison_result VARCHAR(32) NOT NULL,
    external_last_sync_at DATETIME(6) NULL,
    metadata_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_dq_evidence_finding (finding_id, source),
    KEY idx_dq_evidence_source (source, comparison_result),
    CONSTRAINT fk_dq_evidence_finding
        FOREIGN KEY (finding_id) REFERENCES `uss-ca`.`data_quality_finding` (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- FASE B — reglas EXT-01 .. EXT-12
-- source_family = EXTERNAL (no una fuente rígida).
-- El catálogo Java (DataQualityCatalog) también las upserta al arrancar.
-- =============================================================================

INSERT INTO `uss-ca`.`data_quality_rule` (
    code, source_family, entity_type, field_name, name, description, rule_text, alert_text,
    bulk_procedure, category, correction_mode, enabled
) VALUES
('EXT-01', 'EXTERNAL', 'RESEARCH_OUTPUT', 'title',
 'Título vs fuentes externas',
 'Comparar el título PURE con el título asociado al mismo DOI en fuentes locales.',
 'DOI exacto localizado. Conservar título PURE y títulos externos. No reemplazar automáticamente.',
 'El título institucional difiere de al menos una fuente externa comparable.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('EXT-02', 'EXTERNAL', 'RESEARCH_OUTPUT', 'publication_year',
 'Año de publicación vs fuentes externas',
 'El año de publicación PURE debe coincidir con el año publicado del mismo DOI.',
 'Usar solo fechas de publicación (no deposited/indexed/created). Revisión manual; no reemplazar.',
 'El año de publicación PURE difiere de una fuente externa comparable.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('EXT-03', 'EXTERNAL', 'RESEARCH_OUTPUT', 'journal_title',
 'Revista vs fuente/container externo',
 'Contrastar la revista PURE con journal/source/container asociado al DOI.',
 'No comparar institución contra revista. Conservar ambos valores.',
 'La revista PURE difiere de una fuente externa comparable.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('EXT-04', 'EXTERNAL', 'RESEARCH_OUTPUT', 'journal_issn',
 'ISSN vs ISSN externos',
 'El ISSN PURE debe coincidir con al menos uno de los ISSN externos del mismo DOI.',
 'Normalizar guion/caso; conservar originales. No exigir arrays idénticos.',
 'Ningún ISSN PURE coincide con los ISSN externos comparables.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('EXT-05', 'EXTERNAL', 'RESEARCH_OUTPUT', 'citation_volume',
 'Volumen vs fuentes externas',
 'Comparar volumen cuando ambas fuentes tengan dato comparable.',
 'Trim y normalización mínima. No inventar volumen desde citation string.',
 'El volumen PURE difiere de una fuente externa comparable.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('EXT-06', 'EXTERNAL', 'RESEARCH_OUTPUT', 'citation_issue',
 'Número/issue vs fuentes externas',
 'Comparar issue/número cuando ambas fuentes tengan dato comparable.',
 'No interpretar Supplement 1 como 1.',
 'El número/issue PURE difiere de una fuente externa comparable.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('EXT-07', 'EXTERNAL', 'RESEARCH_OUTPUT', 'citation_pages',
 'Páginas / article number',
 'Comparar únicamente el mismo tipo de dato: pages↔pages o articleNumber↔articleNumber.',
 'No equiparar pages con article number. Eso es NOT_COMPARABLE, no inconsistencia.',
 'Páginas o article number PURE no son comparables o difieren de la evidencia externa.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('EXT-08', 'EXTERNAL', 'RESEARCH_OUTPUT', 'publisher',
 'Publisher vs fuentes externas',
 'Comparar publisher cuando ambas fuentes lo informen, sin alias no aprobado.',
 'Trim, Unicode y case. Springer Nature ≠ Springer Nature Switzerland AG.',
 'El publisher PURE difiere de una fuente externa comparable.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('EXT-09', 'EXTERNAL', 'RESEARCH_OUTPUT', 'access_type',
 'Tipo de acceso vs OA externo',
 'Contrastar accessType PURE con OA persistido (Unpaywall principal; OpenAlex si existe).',
 'Conservar granularidad gold/green/hybrid/bronze/closed. No modificar PURE.',
 'El tipo de acceso PURE difiere de la evidencia OA externa.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('EXT-10', 'EXTERNAL', 'RESEARCH_OUTPUT', 'license_type',
 'Licencia vs licencia externa',
 'Contrastar licencia PURE con licencia externa del mismo DOI.',
 'Normalizar CC conocidas y URLs Creative Commons equivalentes. Conservar original.',
 'La licencia PURE difiere de una fuente externa comparable.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('EXT-11', 'EXTERNAL', 'RESEARCH_OUTPUT', 'funding_award',
 'Número de adjudicación vs fuentes externas',
 'Comparar award number exacto cuando esté en PURE y en la fuente externa.',
 'No equivaler nombres de financiador. Sin award estructurado en PURE no hay finding.',
 'El número de adjudicación PURE difiere de una fuente externa comparable.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1),
('EXT-12', 'EXTERNAL', 'RESEARCH_OUTPUT', 'authors',
 'Autores vs fuentes externas',
 'Comparar autores entre PURE y fuentes externas del mismo DOI.',
 'Condición funcional (corrección de copy/paste de la planilla): DOI exacto y datos de autores en PURE y en la fuente externa. Posición distinta es informativa, no inconsistencia. Listas truncadas no generan finding grave por autores faltantes.',
 'La autoría PURE presenta diferencias relevantes respecto de fuentes externas comparables.',
 NULL, 'EXTERNAL_CONSISTENCY', 'VIEW_ONLY', 1)
ON DUPLICATE KEY UPDATE
    source_family = VALUES(source_family),
    entity_type = VALUES(entity_type),
    field_name = VALUES(field_name),
    name = VALUES(name),
    description = VALUES(description),
    rule_text = VALUES(rule_text),
    alert_text = VALUES(alert_text),
    bulk_procedure = VALUES(bulk_procedure),
    category = VALUES(category),
    correction_mode = VALUES(correction_mode),
    enabled = VALUES(enabled);

-- Índice de filtro por fuente (el EXISTS usa idx_dq_evidence_finding).
-- No se tocan tablas PURE ni se llama a APIs externas.
