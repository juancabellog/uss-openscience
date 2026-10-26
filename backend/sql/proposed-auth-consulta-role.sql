-- PROPUESTO. NO ejecutar desde el agente.
-- Incremental para bases que ya corrieron proposed-auth.sql.
-- Crea el rol CONSULTA (solo CONSULT en módulos funcionales).
-- NO asigna el rol a usuarios. Eso está en proposed-auth-consulta-backfill.sql.

INSERT IGNORE INTO `uss-ca`.`app_role` (code, name, description, active, system_role) VALUES
    ('CONSULTA', 'Consulta', 'Consulta de los módulos funcionales. Sin administración ni auditoría.', 1, 1);

INSERT IGNORE INTO `uss-ca`.`app_role_permission` (role_id, module_code, permission_code)
SELECT r.id, x.module_code, 'CONSULT'
FROM `uss-ca`.`app_role` r
JOIN (
    SELECT 'DASHBOARD' AS module_code
    UNION ALL SELECT 'PEOPLE'
    UNION ALL SELECT 'RESEARCH_OUTPUTS'
    UNION ALL SELECT 'JOURNALS'
    UNION ALL SELECT 'PROJECTS'
    UNION ALL SELECT 'ORGANIZATIONS'
    UNION ALL SELECT 'DIRECTORY'
    UNION ALL SELECT 'DATA_QUALITY'
    UNION ALL SELECT 'INDICATORS'
    UNION ALL SELECT 'INTEGRATIONS'
) x
WHERE r.code = 'CONSULTA';

-- Verificación del seed (solo lectura)
SELECT r.id, r.code, r.name, r.active, r.system_role
FROM `uss-ca`.`app_role` r
WHERE r.code = 'CONSULTA';

SELECT rp.module_code, rp.permission_code
FROM `uss-ca`.`app_role` r
JOIN `uss-ca`.`app_role_permission` rp ON rp.role_id = r.id
WHERE r.code = 'CONSULTA'
ORDER BY rp.module_code, rp.permission_code;
