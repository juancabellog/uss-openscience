-- PROPUESTO. NO ejecutar desde el agente.
-- Ejecutar manualmente UNA SOLA VEZ en el catálogo `uss-ca`
-- cuando se incorpore Employment ORCID a la copia local.
--
-- ORCID Public API GET /{orcid}/employments
--   affiliation-group[] → summaries[] → employment-summary
--   organization.name
--   organization.address.{city,region,country}
--   organization.disambiguated-organization.{identifier,source}
--   put-code, start-date, end-date
--
-- Indicadores de Identidad del investigador leen esta tabla.
-- NO consultar ORCID al abrir Indicadores.

CREATE TABLE IF NOT EXISTS `uss-ca`.`orcid_employment` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    person_id BIGINT UNSIGNED NOT NULL,
    orcid VARCHAR(19) NOT NULL,
    put_code BIGINT NOT NULL,
    organization_name VARCHAR(500) NULL,
    organization_city VARCHAR(255) NULL,
    organization_region VARCHAR(255) NULL,
    organization_country VARCHAR(8) NULL,
    organization_identifier VARCHAR(128) NULL,
    organization_identifier_type VARCHAR(64) NULL,
    start_year SMALLINT NULL,
    start_month TINYINT NULL,
    start_day TINYINT NULL,
    end_year SMALLINT NULL,
    end_month TINYINT NULL,
    end_day TINYINT NULL,
    current_affiliation TINYINT(1) NOT NULL DEFAULT 0,
    visibility VARCHAR(32) NULL,
    retrieved_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_orcid_employment_put (orcid, put_code),
    KEY idx_orcid_employment_person (person_id),
    KEY idx_orcid_employment_org (organization_name(191)),
    CONSTRAINT fk_orcid_employment_person
        FOREIGN KEY (person_id) REFERENCES `uss-ca`.`pure_person` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Índices recomendados para indicadores de identidad. NO ejecutar desde el agente.
-- -----------------------------------------------------------------------------
-- ALTER TABLE `uss-ca`.`pure_person_identifiers`
--     ADD KEY idx_ppi_type_person_value (identifier_type, person_id, identifier_value(64));
--
-- ALTER TABLE `uss-ca`.`pure_research_output_contributor`
--     ADD KEY idx_proc_person_uuid (person_uuid);
--
-- No se materializan researcher_signature_summary / researcher_signature_variant:
-- el recuento de firmas reutiliza AuthorNameMatcher.normalize en Java sobre
-- una consulta agregada de contribuidores. Si esa consulta resulta costosa
-- en producción, evaluar esas tablas derivadas en una fase posterior.
