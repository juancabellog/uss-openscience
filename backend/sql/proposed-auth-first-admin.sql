-- PROPUESTO. NO ejecutar desde el agente.
-- Primer administrador PENDING_ACTIVATION, sin password.
-- Requiere que FASE B de proposed-auth.sql ya haya creado PLATFORM_ADMIN.

INSERT INTO `uss-ca`.`app_user` (
    email,
    email_normalized,
    first_name,
    last_name,
    password_hash,
    status,
    auth_provider,
    preferred_language
)
SELECT
    'juan.cabello@uss.cl',
    'juan.cabello@uss.cl',
    'Juan',
    'Cabello',
    NULL,
    'PENDING_ACTIVATION',
    'LOCAL',
    'es'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM `uss-ca`.`app_user`
    WHERE email_normalized = 'juan.cabello@uss.cl'
);

INSERT INTO `uss-ca`.`app_user_role` (user_id, role_id)
SELECT u.id, r.id
FROM `uss-ca`.`app_user` u
JOIN `uss-ca`.`app_role` r ON r.code = 'PLATFORM_ADMIN'
WHERE u.email_normalized = 'juan.cabello@uss.cl'
  AND NOT EXISTS (
      SELECT 1
      FROM `uss-ca`.`app_user_role` ur
      WHERE ur.user_id = u.id
        AND ur.role_id = r.id
  );

SELECT u.id, u.email, u.status, u.password_hash, r.code AS role_code
FROM `uss-ca`.`app_user` u
LEFT JOIN `uss-ca`.`app_user_role` ur ON ur.user_id = u.id
LEFT JOIN `uss-ca`.`app_role` r ON r.id = ur.role_id
WHERE u.email_normalized = 'juan.cabello@uss.cl';
