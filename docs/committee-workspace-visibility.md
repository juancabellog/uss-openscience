# Visibilidad del espacio de trabajo del comité

La matriz sale del código actual. La política está en `projects/ethics-uss/src/app/core/workflow/committee-workspace.policy.ts` y reutiliza `sectionsFor`, `canAssignReviewers`, `canConsolidate`, `canPrepareResolution`, `canValidateResolution` y `seesInternalReviews` de `case-workflow.ts`.

No se agregaron permisos. Donde el detalle de proyecto y el expediente no coincidían, se conservó la regla del expediente, que ya distinguía roles.

## Matriz

| Contenido | Administración | Presidencia | Secretaría | Miembro asignado | Miembro no asignado | Investigador |
|---|---|---|---|---|---|---|
| Documentos del comité | Sí | Sí | Sí | Sí, en el proyecto | Sí, en el proyecto. En `/expedientes/:id` ve el aviso de acceso restringido | Sí |
| Cargar documento | Sí, si el paquete no está cerrado y falta el requisito | Igual | Igual | Igual | Igual en el proyecto | Igual |
| Notificar comité | Sí, si los requisitos obligatorios están completos y el paquete no está cerrado | Igual | Igual | Igual | Igual en el proyecto | Igual |
| Admisibilidad y asignación | Sí, en los estados que ya las mostraban | Sí | Sí | No | No | No |
| Revisiones individuales de la ronda | Sí, en estados de revisión | Sí | Sí | Solo la propia, y el enlace a `/revisiones/:id` | No | No |
| Consolidar y publicar | Sí | No | Sí | No | No | No |
| Observaciones publicadas y PDF | Sí | Sí | Sí | Sí | Sí, en el proyecto | Sí, solo `RESEARCHER_VISIBLE` y no borrador |
| Responder observaciones | No | No | No | No | No | Sí |
| Resolución emitida | Sí | Sí | Sí | Sí | Sí, en el proyecto | Sí |
| Resolución no emitida, preparar, devolver, validar | Preparar y validar | Validar y devolver. Ve el borrador. No prepara | Preparar y enviar a presidencia. Ve el borrador | No | No | No |
| Enmienda y seguimiento | Solo el estado si el expediente ya tiene `processKind` | Igual | Igual | Igual | Igual | Igual |
| Historial del comité | Eventos del expediente | Eventos del expediente | Eventos del expediente | Eventos visibles para el investigador | Igual | Eventos externos. Sin asignación, revisión interna ni consolidación |
| Auditoría del sistema | Sigue en Auditoría | Igual | Igual | Igual | Igual | Igual |

## Una fila por tipo de comité

Un proyecto tiene una única representación visible por tipo de comité. Los ciclos, renovaciones, enmiendas y seguimientos se representan dentro del workspace e historial de ese comité y nunca como comités duplicados.

La vista de Proyectos agrupa los `CommitteeCase` por `projectId` + `committeeType` aunque el almacenamiento traiga más de uno. El estado, el código y el ingreso de la fila son los del expediente activo. El vencimiento es el de la aprobación que ya tiene `approvalExpiresAt`. No se inventa una renovación: ese proceso no está modelado.

Decisión del mock «Modelo murino de inflamación intestinal»:

- `CECUA-2026-004` (`case-2`) es la relación visible. Está en espera de respuesta del investigador, con revisiones, documentos y sesión.
- `CECUA-2026-014` (`case-adm`) se creó solo para demostrar admisibilidad. No tiene documentos, revisiones ni resolución. Sigue guardado. En Proyectos no tiene fila propia; aparece como registro del mismo CECUA. `/expedientes/case-adm` sigue abriendo su pantalla.

El estado «Enmienda en borrador» de la rehabilitación cardíaca sale de `processKind: AMENDMENT` y `processStatus: DRAFT` que el expediente ya tenía. El vencimiento sigue siendo el de la aprobación existente.

## Decisiones que se documentan y no se reinterpretan

- La carga no depende del rol. Depende de `packageFrozen` y de que el requisito todavía no tenga archivo. Por eso no aparece «Cargar nueva versión» cuando ya hay un archivo: esa acción no existía.
- No hay formulario de «Contactar comité» en el código. La acción existente es «Notificar comité», y queda en Documentos, con la misma confirmación de siempre.
- `CommitteeCaseService.startProcess` existe, pero ninguna pantalla lo llama. Las tarjetas de enmienda y seguimiento muestran el estado si el expediente ya lo trae. No se agregaron botones que iniciarían un flujo nuevo. La línea «Próximamente» sigue en el resumen del proyecto. No hay renovación implementada.
- La pestaña Historial del proyecto se retiró de la navegación. Los eventos no se borraron: siguen en Auditoría y, cuando pertenecen a un expediente, en el historial de ese comité. La creación de la solicitud y la clasificación de Neurocognición no tienen expediente, así que ya no tienen una pestaña propia; permanecen en la auditoría. El historial del comité sigue filtrado: investigador y miembro solo ven las acciones externas.
- En `/expedientes/:id`, un miembro que no está en `reviewerIds` sigue viendo el aviso de acceso restringido.

## Filtro de estado en Proyectos

El filtro rápido de la vista de Proyectos usa el estado general del proyecto: `ProjectService.globalStatus()`. Es el mismo valor que la tarjeta muestra como estado general cuando la persona ve todos los comités de ese proyecto. No usa estados de un `CommitteeCase`.

Más adelante puede existir un filtro aparte, «Estado del comité», para valores como en revisión, esperando respuesta del investigador, pendiente de presidencia o enmienda en borrador. Ese filtro sería independiente y no se mezcla con el estado general.
