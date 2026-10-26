-- PROPUESTO. NO ejecutar desde el agente.
-- Incremental para bases que ya tienen app_user.
-- Soft delete de cuentas con historial. Sin FK sobre auditoría.
-- deleted_by NO referencia app_user: el actor también podría eliminarse después.

ALTER TABLE `uss-ca`.`app_user`
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER created_by,
    ADD COLUMN deleted_by BIGINT UNSIGNED NULL AFTER deleted_at;

ALTER TABLE `uss-ca`.`app_user`
    ADD KEY idx_app_user_deleted (deleted_at);

-- Verificación (solo lectura)
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'uss-ca'
  AND TABLE_NAME = 'app_user'
  AND COLUMN_NAME IN ('deleted_at', 'deleted_by')
ORDER BY COLUMN_NAME;
