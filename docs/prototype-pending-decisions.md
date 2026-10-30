# Decisiones pendientes

El registro histórico del prototipo está en `projects/ethics-uss/docs/prototype-pending-decisions.md`. Esta nota deja las diferencias de la hoja DISEI que no se corrigieron en silencio.

1. P24 repite «Bases de datos de dominio público». Se conservan las dos filas ya implementadas (`cec-public-db-1`, `cec-public-db-2`), ambas con la clasificación CEC existente. No se reintrodujo «Bases de datos no públicas».
2. P2 conserva «FONDNECYT Postdoctoral». Posible error de tipeo, pendiente de validación DISEI.
3. P8 repite «Economía, Negocios y Gobierno» y escribe «educación» en minúscula. El catálogo mantiene una sola facultad y la grafía «Educación» ya usada.
4. La reasignación en etapa avanzada quedó definida: solo Presidencia, con `REASSIGN_REVIEWER_ADVANCED_STAGE`. `REASSIGN_REVIEWER` no la cubre. El detalle está en `docs/ethics-permissions.md`.
5. Otras diferencias con la configuración existente:
   - P10 del Excel escribe «Asistente  de Ivestigación» e «investigador». El catálogo almacenado sigue «Asistente de Investigación» e «Investigador».
   - La condición de P11–P17 usa «magíster», «Asistente de investigación» y «Personal técnico». La visibilidad compara contra los ids del catálogo.
   - P17 del Excel dice «Se enmarca enn un proyecto». El catálogo mantiene «Se enmarca en un proyecto».
   - P19 del Excel dice «orginal». La etiqueta visible sigue «original».
   - P7 del Excel dice «Cientificos». El catálogo sigue «Científicos».
   - La matriz V3 eliminó el rol Miembro de Comité. La evaluación usa `REVIEWER` mediante `ReviewerAssignment`.
   - `pure_person` no trae sede, facultad ni programa. Esos campos quedan vacíos.
   - No existe un buscador institucional reutilizable para el patrocinante o tutor.
6. Definir con DISEI/Majo/Andrea si el Plan de Gestión de Datos es obligatorio y bajo qué condiciones. Hoy se identifica, se guarda y se versiona con `DATA_MANAGEMENT_PLAN`. No bloquea el envío. El detalle está en `docs/ethics-documents.md`.
