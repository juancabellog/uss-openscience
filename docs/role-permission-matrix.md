# Matriz de permisos del prototipo ethics-uss

Esta matriz es la configuración del prototipo. No es la norma institucional.

La interfaz oculta o deshabilita según `PermissionService.can(user, capability, context)`. Un servidor futuro tiene que volver a autorizar cada acción. El frontend no es una barrera de seguridad.

La fuente activa es `backend/src/main/resources/ethics/permissions-V3.yml`. El espejo de la interfaz está en `projects/ethics-uss/src/app/features/dashboard/home-institutional.ts` y lo usa `role-capabilities.ts`. No hay editor administrativo.

Una persona puede tener más de un rol. El rol principal solo etiqueta el perfil demo. Las capacidades no se suman en bloque: cada rol aporta las suyas únicamente cuando su alcance corresponde al caso.

## Alcances

| Rol | Alcance |
| --- | --- |
| ADMINISTRATOR | GLOBAL |
| COMMITTEE_PRESIDENT | COMMITTEE |
| EXECUTIVE_SECRETARY | COMMITTEE |
| REVIEWER | CASE. Solo un proceso con `ReviewerAssignment` legible. |
| RESEARCHER | Proyecto propio. |

`PENDING` en `ReviewerAssignment` significa asignado y pendiente de aceptación. No se agregó un estado `ASSIGNED`.

Una asignación `REJECTED` o `CONFLICT` no da acceso de revisor.

## Rol eliminado

`COMMITTEE_MEMBER` ya no es un rol. Andrea lo eliminó. La evaluación se hace con `REVIEWER` y una `ReviewerAssignment`. «Miembro» puede seguir describiendo composición del comité, asistencia o directorio. Esa metadata no autoriza.

## Capacidades por rol

El detalle está en `permissions-V3.yml`. No se recupera una capability que ese archivo no concede.

Administración, presidencia y secretaría comparten visibilidad de admisibilidad, asignación, revisiones, consolidación y su edición o guardado, publicación y preparación de resolución, según los ✓ del YAML. `VALIDATE_RESOLUTION` y `RETURN_RESOLUTION` quedan en administración y presidencia. Secretaría no las tiene.

El revisor ve observaciones publicadas y la respuesta del investigador. No ve la revisión privada del otro revisor, el borrador de consolidación, las sesiones ni el pool. `SUBMIT_OWN_REVIEW` exige su `ReviewerAssignment`. En administración, esa capability no fabrica una revisión.

`SUBMIT_REQUEST` está en administración e investigación. Exige un contexto válido: el investigador envía la suya; la administración no envía una solicitud sin proyecto.

## Restricciones contextuales

- Cada CommitteeCase sigue exigiendo dos revisores, en slot 1 y slot 2. La misma persona no puede ocupar ambos.
- `EDIT_OWN_REVIEW` y `SUBMIT_OWN_REVIEW` solo con el caso en `IN_REVIEW` o `PENDING_ACCEPTANCE`.
- `VIEW_OWN_REVIEW` no abre la revisión del otro slot.
- Rechazar una asignación la deja en `REJECTED` y el caso vuelve a `PENDING_ASSIGNMENT`, con el aviso «1 revisor requiere reasignación» cuando corresponde.
- `REASSIGN_REVIEWER` es la reasignación ordinaria, mientras la revisión sigue en su etapa operativa y la asignación no está cerrada ni enviada. `REASSIGN_REVIEWER_ADVANCED_STAGE` es exclusiva de Presidencia. Cubre una asignación `COMPLETED`, una revisión ya enviada, o un expediente en observaciones, respuesta del investigador o resolución todavía abierta. Administración y secretaría no la tienen. El motivo es obligatorio. La asignación anterior no se borra ni se reescribe: queda en el historial, con su revisión y su revisor, y la nueva ocupa el mismo slot. Un caso `APPROVED`, `REJECTED`, `EXEMPT` o `WITHDRAWN` no se reasigna.
- Una ronda posterior no reabre la asignación completada. Al retomar la revisión se crea otra `ReviewerAssignment` del mismo slot. No hay recomendación automática de otras personas.
- Los eventos `REVIEWER_ASSIGNED`, `REVIEWER_REASSIGNED`, `REVIEWER_ACCEPTED` y `REVIEWER_REJECTED` no están en el historial externo. El investigador no ve esas identidades. El historial operativo muestra el slot, el revisor anterior, el nuevo, quién hizo el cambio y el motivo. El registro técnico guarda eso mismo, sin mostrar identificadores internos.

## Dónde quedó PENDING_ASSIGNMENT

No se creó un estado de admisibilidad nuevo. `PENDING_ASSIGNMENT` y `PENDING_ACCEPTANCE` ya existían.

`CommitteeCaseService.admit` pasa el caso a `PENDING_ASSIGNMENT` cuando la admisibilidad permite continuar. Ese es el punto en que el flujo ya terminaba la revisión de antecedentes y empezaba la revisión por pares.

Asignar a los dos revisores pasa a `PENDING_ACCEPTANCE`. Cuando ambos aceptan, el caso pasa a `IN_REVIEW`. El resto del flujo no se modificó.

Las etiquetas quedaron:

- `PENDING_ASSIGNMENT`: Pendiente de asignación de revisores / Pending reviewer assignment
- `PENDING_ACCEPTANCE`: Pendiente de aceptación de revisores / Pending reviewer acceptance

## Asignación

La acción «Asignar revisores» está en Proyecto → Comité → Revisores.

«Cambiar revisor» no aplica el cambio al seleccionar. Pide un revisor distinto y un motivo, y solo persiste al confirmar. Si la asignación de esa ronda ya está cerrada, el botón no aparece.

La pantalla lateral Revisores es el catálogo. No asigna.

## PENDIENTE DISEI

- Quién puede asignar y reasignar quedó en `permissions-V3.yml`: administración, presidencia y secretaría tienen `ASSIGN_REVIEWERS` y `REASSIGN_REVIEWER`. Solo presidencia tiene `REASSIGN_REVIEWER_ADVANCED_STAGE`.
- Quien revisa tiene el rol `REVIEWER` y una `ReviewerAssignment`. No existe un rol de miembro que revise.
- El revisor ve observaciones publicadas y la respuesta del investigador, limitadas a su proceso. No ve la revisión privada del otro revisor.
- Si reasignar durante una evaluación ya avanzada debe cambiar el estado del caso. El prototipo solo mueve el estado cuando el caso está en `PENDING_ASSIGNMENT` o `PENDING_ACCEPTANCE`. Una asignación completada no se reasigna en ningún estado.
- Si `EDIT_OWN_REVIEW` debe existir en otros estados además de `IN_REVIEW` y `PENDING_ACCEPTANCE`.
- Especialidad y disponibilidad de un revisor externo. No están modeladas para Alberto Soto y no se inventaron.
