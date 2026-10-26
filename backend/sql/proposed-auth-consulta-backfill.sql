-- PROPUESTO. NO ejecutar desde el agente.
-- Regularización de usuarios auto-registrados que quedaron sin rol.
--
-- NO hay columna origin en app_user.
-- Se identifica SELF_REGISTRATION por:
--   1) evento SELF_REGISTRATION_REQUESTED con detail = email_normalized
--   2) created_by IS NULL (el alta admin guarda el id del administrador)
--   3) no es bootstrap (USER_CREATED.detail = 'bootstrap')
--   4) no tiene ningún role assignment
--
-- NO usar: "todos los usuarios sin rol → CONSULTA".

-- =============================================================================
-- 1) LISTAR candidatos (solo lectura)
-- =============================================================================
SELECT
    u.id,
    u.email,
    u.email_normalized,
    u.status,
    u.created_by,
    u.created_at,
    u.activated_at
FROM `uss-ca`.`app_user` u
LEFT JOIN `uss-ca`.`app_user_role` ur ON ur.user_id = u.id
WHERE ur.user_id IS NULL
  AND u.created_by IS NULL
  AND EXISTS (
      SELECT 1
      FROM `uss-ca`.`app_security_event` e
      WHERE e.event_type = 'SELF_REGISTRATION_REQUESTED'
        AND e.detail = u.email_normalized
  )
  AND NOT EXISTS (
      SELECT 1
      FROM `uss-ca`.`app_security_event` e
      WHERE e.event_type = 'USER_CREATED'
        AND e.target_user_id = u.id
        AND e.detail = 'bootstrap'
  )
ORDER BY u.id;

-- =============================================================================
-- 2) VERIFICAR estado y que CONSULTA exista (solo lectura)
-- =============================================================================
SELECT r.id, r.code, r.active
FROM `uss-ca`.`app_role` r
WHERE r.code = 'CONSULTA';

SELECT
    u.id,
    u.email,
    u.status,
    GROUP_CONCAT(r.code ORDER BY r.code) AS roles
FROM `uss-ca`.`app_user` u
LEFT JOIN `uss-ca`.`app_user_role` ur ON ur.user_id = u.id
LEFT JOIN `uss-ca`.`app_role` r ON r.id = ur.role_id
WHERE EXISTS (
      SELECT 1
      FROM `uss-ca`.`app_security_event` e
      WHERE e.event_type = 'SELF_REGISTRATION_REQUESTED'
        AND e.detail = u.email_normalized
  )
GROUP BY u.id, u.email, u.status
ORDER BY u.id;

-- =============================================================================
-- 3) ASIGNAR CONSULTA SOLO a esos candidatos (ejecutar después de revisar 1 y 2)
--    Idempotente. No toca cuentas con rol, bootstrap, admin-created ni DISABLED.
-- =============================================================================
INSERT IGNORE INTO `uss-ca`.`app_user_role` (user_id, role_id)
SELECT u.id, r.id
FROM `uss-ca`.`app_user` u
JOIN `uss-ca`.`app_role` r ON r.code = 'CONSULTA' AND r.active = 1
LEFT JOIN `uss-ca`.`app_user_role` ur ON ur.user_id = u.id
WHERE ur.user_id IS NULL
  AND u.created_by IS NULL
  AND u.status IN ('PENDING_ACTIVATION', 'ACTIVE')
  AND EXISTS (
      SELECT 1
      FROM `uss-ca`.`app_security_event` e
      WHERE e.event_type = 'SELF_REGISTRATION_REQUESTED'
        AND e.detail = u.email_normalized
  )
  AND NOT EXISTS (
      SELECT 1
      FROM `uss-ca`.`app_security_event` e
      WHERE e.event_type = 'USER_CREATED'
        AND e.target_user_id = u.id
        AND e.detail = 'bootstrap'
  );
