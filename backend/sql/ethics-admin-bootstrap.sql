-- Bootstrap administrativo de Ethics para una cuenta OpenScience que YA existe.
-- No inserta app_user, no escribe password_hash y no otorga PLATFORM_ADMIN.
-- Idempotente: se puede ejecutar más de una vez.
--
-- Tablas reales:
--   uss-ca.app_user                 cuenta compartida. UNIQUE(email_normalized).
--   uss-ca.pure_person              ficha PURE, solo lectura. Se consulta, no se escribe.
--   uss-ca.app_application          catálogo OPENSCIENCE / EVENTOS / ETHICS.
--   uss-ca.user_application         acceso por aplicación. UNIQUE(user_id, application_id).
--   uss-ca.ethics_role_assignment   rol de Ethics. No tiene application_id: la tabla ya es de Ethics.
--   uss-ca.app_security_event       auditoría. No guarda contraseñas.
--
-- person y app_user.person_id siguen en proposed-person-user.sql.
-- No se agrega un UNIQUE nuevo sobre ethics_role_assignment: puede haber historial
-- y este script no revisó las filas vivas. La idempotencia la da WHERE NOT EXISTS.
--
-- Si la primera consulta no devuelve una fila, detenerse.
-- Este script no crea la cuenta.

SELECT id, email, email_normalized, status, deleted_at
FROM `uss-ca`.`app_user`
WHERE email_normalized = 'juan.cabello@uss.cl'
  AND deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS `uss-ca`.`app_application` (
    code VARCHAR(32) NOT NULL,
    name_es VARCHAR(128) NOT NULL,
    name_en VARCHAR(128) NOT NULL,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `uss-ca`.`app_application` (code, name_es, name_en) VALUES
    ('OPENSCIENCE', 'OpenScience', 'OpenScience'),
    ('EVENTOS', 'Eventos', 'Events'),
    ('ETHICS', 'Ética de proyectos', 'Project ethics');

CREATE TABLE IF NOT EXISTS `uss-ca`.`user_application` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    application_id VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT UNSIGNED NULL,
    modified_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    modified_by BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_application (user_id, application_id),
    KEY idx_user_application_status (application_id, status),
    CONSTRAINT fk_user_application_user
        FOREIGN KEY (user_id) REFERENCES `uss-ca`.`app_user` (id),
    CONSTRAINT fk_user_application_app
        FOREIGN KEY (application_id) REFERENCES `uss-ca`.`app_application` (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `uss-ca`.`ethics_role_assignment` (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    valid_from DATE NULL,
    valid_until DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT UNSIGNED NULL,
    modified_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    modified_by BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    KEY idx_ethics_role_user (user_id, role_id, status),
    CONSTRAINT fk_ethics_role_user
        FOREIGN KEY (user_id) REFERENCES `uss-ca`.`app_user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE `uss-ca`.`user_application` ua
JOIN `uss-ca`.`app_user` u ON u.id = ua.user_id
SET ua.status = 'ACTIVE'
WHERE u.email_normalized = 'juan.cabello@uss.cl'
  AND u.deleted_at IS NULL
  AND ua.application_id = 'ETHICS'
  AND ua.status <> 'ACTIVE';

INSERT INTO `uss-ca`.`user_application` (user_id, application_id, status)
SELECT u.id, 'ETHICS', 'ACTIVE'
FROM `uss-ca`.`app_user` u
WHERE u.email_normalized = 'juan.cabello@uss.cl'
  AND u.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM `uss-ca`.`user_application` ua
      WHERE ua.user_id = u.id
        AND ua.application_id = 'ETHICS'
  );

UPDATE `uss-ca`.`ethics_role_assignment` a
JOIN `uss-ca`.`app_user` u ON u.id = a.user_id
SET a.status = 'ACTIVE'
WHERE u.email_normalized = 'juan.cabello@uss.cl'
  AND u.deleted_at IS NULL
  AND a.role_id = 'RESEARCHER'
  AND a.scope_type = 'GLOBAL'
  AND a.scope_id = '*'
  AND a.status <> 'ACTIVE';

INSERT INTO `uss-ca`.`ethics_role_assignment` (user_id, role_id, scope_type, scope_id, status)
SELECT u.id, 'RESEARCHER', 'GLOBAL', '*', 'ACTIVE'
FROM `uss-ca`.`app_user` u
WHERE u.email_normalized = 'juan.cabello@uss.cl'
  AND u.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM `uss-ca`.`ethics_role_assignment` a
      WHERE a.user_id = u.id
        AND a.role_id = 'RESEARCHER'
        AND a.scope_type = 'GLOBAL'
        AND a.scope_id = '*'
  );

UPDATE `uss-ca`.`ethics_role_assignment` a
JOIN `uss-ca`.`app_user` u ON u.id = a.user_id
SET a.status = 'ACTIVE'
WHERE u.email_normalized = 'juan.cabello@uss.cl'
  AND u.deleted_at IS NULL
  AND a.role_id = 'ADMINISTRATOR'
  AND a.scope_type = 'GLOBAL'
  AND a.scope_id = '*'
  AND a.status <> 'ACTIVE';

INSERT INTO `uss-ca`.`ethics_role_assignment` (user_id, role_id, scope_type, scope_id, status)
SELECT u.id, 'ADMINISTRATOR', 'GLOBAL', '*', 'ACTIVE'
FROM `uss-ca`.`app_user` u
WHERE u.email_normalized = 'juan.cabello@uss.cl'
  AND u.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM `uss-ca`.`ethics_role_assignment` a
      WHERE a.user_id = u.id
        AND a.role_id = 'ADMINISTRATOR'
        AND a.scope_type = 'GLOBAL'
        AND a.scope_id = '*'
  );

INSERT INTO `uss-ca`.`app_security_event` (event_type, actor_user_id, target_user_id, target_role_id, detail)
SELECT 'ETHICS_BOOTSTRAP', NULL, u.id, NULL, 'ETHICS ACTIVE, RESEARCHER, ADMINISTRATOR'
FROM `uss-ca`.`app_user` u
WHERE u.email_normalized = 'juan.cabello@uss.cl'
  AND u.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM `uss-ca`.`app_security_event` e
      WHERE e.target_user_id = u.id
        AND e.event_type = 'ETHICS_BOOTSTRAP'
        AND e.detail = 'ETHICS ACTIVE, RESEARCHER, ADMINISTRATOR'
  );

SELECT u.id AS user_id, u.email, ua.application_id, ua.status
FROM `uss-ca`.`app_user` u
LEFT JOIN `uss-ca`.`user_application` ua ON ua.user_id = u.id
WHERE u.email_normalized = 'juan.cabello@uss.cl';

SELECT u.id AS user_id, a.role_id, a.scope_type, a.scope_id, a.status
FROM `uss-ca`.`app_user` u
JOIN `uss-ca`.`ethics_role_assignment` a ON a.user_id = u.id
WHERE u.email_normalized = 'juan.cabello@uss.cl'
ORDER BY a.role_id;

SELECT id, first_name, last_name, email, cargo
FROM `uss-ca`.`pure_person`
WHERE LOWER(TRIM(email)) = 'juan.cabello@uss.cl';
