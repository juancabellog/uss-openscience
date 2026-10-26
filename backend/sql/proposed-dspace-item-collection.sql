-- PROPUESTO. NO ejecutar desde el agente.
-- Relación N:M ítem ↔ colección (owning + mapped). DSpace 9.2 expone ambos.
-- owning_collection_uuid en dspace_item se mantiene como atajo 1:1.
-- Ejecutar UNA SOLA VEZ si proposed-dspace.sql ya se aplicó.

CREATE TABLE IF NOT EXISTS `uss-ca`.`dspace_item_collection` (
    item_uuid CHAR(36) NOT NULL,
    collection_uuid CHAR(36) NOT NULL,
    relation VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (item_uuid, collection_uuid, relation),
    KEY idx_dspace_item_collection_col (collection_uuid, relation),
    CONSTRAINT fk_dspace_item_collection_item
        FOREIGN KEY (item_uuid) REFERENCES `uss-ca`.`dspace_item` (uuid)
        ON DELETE CASCADE,
    CONSTRAINT fk_dspace_item_collection_col
        FOREIGN KEY (collection_uuid) REFERENCES `uss-ca`.`dspace_collection` (uuid)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
