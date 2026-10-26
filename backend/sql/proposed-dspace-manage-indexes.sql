-- PROPUESTO. NO ejecutar desde el agente.
-- Consulta de Gestionar DSpace (listado por last_outcome + última sincronización).
-- Reutilizar primero los índices de proposed-dspace.sql:
--   idx_dspace_item_outcome, idx_dspace_item_access, idx_dspace_item_collection,
--   idx_dspace_item_doi, idx_dspace_item_match_status, idx_dspace_item_match_method.
-- Este índice cubre el ORDER BY por defecto (FOUND + last_sync_at DESC) a 50.000+.

ALTER TABLE `uss-ca`.`dspace_item`
    ADD INDEX idx_dspace_item_outcome_sync (last_outcome, last_sync_at);

-- DOI compartido: GROUP BY normalized_doi WHERE last_outcome = 'FOUND'.
-- idx_dspace_item_doi (normalized_doi) ya existe en proposed-dspace.sql.
ALTER TABLE `uss-ca`.`dspace_item`
    ADD INDEX idx_dspace_item_outcome_doi (last_outcome, normalized_doi);

-- Con/sin archivo ORIGINAL: EXISTS sobre item_uuid + bundle_name.
-- idx_dspace_bitstream_item y idx_dspace_bitstream_bundle ya existen.
ALTER TABLE `uss-ca`.`dspace_bitstream`
    ADD INDEX idx_dspace_bitstream_item_bundle (item_uuid, bundle_name);

-- Ya cubiertos por proposed-dspace.sql (no crear de nuevo):
--   dspace_item.uuid PK
--   idx_dspace_item_doi (normalized_doi)
--   idx_dspace_item_access (access_status)
--   idx_dspace_item_match_output (research_output_id)
--   dspace_bitstream.uuid PK
--   idx_dspace_bitstream_item (item_uuid)
-- No existe columna active_in_dspace; last_outcome es el flag local (idx_dspace_item_outcome).
