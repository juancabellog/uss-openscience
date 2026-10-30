# Autorización de ética en OpenScience-USS

La autoridad es `AuthorizationService`. El nombre de un rol no autoriza nada: agrupa capabilities y se muestra a las personas mediante `idLabel`.

Ethics tiene una interfaz de login propia, pero reutiliza el mismo sistema de identidad y autenticación de OpenScience: `POST /api/v1/auth/login` escribe la cookie `OS_SESSION`. Compartir autenticación no implica compartir la pantalla de login. No hay tabla de usuarios, contraseña ni sesión propias de Ethics.

## 1. RoleDefinition

Sale de `backend/src/main/resources/ethics/permissions-V4.yml`, leído por `YamlRoleDefinitionProvider`. `permissions-V3.yml` queda como versión anterior y ya no se carga.

Los roles configurables son `ADMINISTRATOR`, `COMMITTEE_PRESIDENT`, `EXECUTIVE_SECRETARY`, `REVIEWER` y `RESEARCHER`.

`COMMITTEE_MEMBER` fue eliminado por definición funcional. La evaluación de proyectos se realiza mediante `REVIEWER`, asignado contextualmente con `ReviewerAssignment`. No hay alias `MEMBER`, `COMMITTEE_REVIEWER_MEMBER` ni `INTERNAL_MEMBER`. Un dato persistido con `roleId = COMMITTEE_MEMBER` no se convierte a `REVIEWER`: el cargador demo lo rechaza y lo deja registrado para revisión.

Cada rol tiene `id`, `idLabel` y capabilities agrupadas en `global`, `committee`, `project` y `process`. Esas cuatro palabras clasifican la capability. No son el alcance.

## 2. Capability

Una capability significa que la persona podría hacer la acción. No significa que pueda hacerla en cualquier estado del expediente.

Si el identificador no existe en el YAML, la respuesta es denegar.

## 3. idLabel

El backend devuelve la clave, no el texto:

```json
{ "id": "REVIEWER", "idLabel": "roles.reviewer" }
```

`ethics-uss` la traduce con el i18n que ya tiene. Español: Revisor/a. Inglés: Reviewer.

## 4. RoleAssignment

Una asignación no copia capabilities. Referencia un rol:

`userId`, `roleId`, `scopeType`, `scopeId`, `status`, `validFrom`, `validUntil`, y los campos de auditoría `createdBy`, `createdAt`, `modifiedBy`, `modifiedAt`.

Una persona puede tener varias asignaciones a la vez.

## 5. Scope

`GLOBAL` aplica en el ámbito institucional. `COMMITTEE` aplica a los recursos de ese comité. `PROJECT` aplica a ese proyecto. `PROCESS` aplica solo a esa ronda, por ejemplo `CECUA-2026-004:R1`.

`ScopeMatcher` es el único lugar que decide si una asignación entra en el contexto. Un contexto contradictorio o desconocido no concede nada.

Pilar es presidenta de CECUA. Sus capabilities, aunque estén escritas bajo `process`, valen para CECUA. No valen para CEC ni para Bioseguridad.

## 6. Estado de la asignación

`ACTIVE` puede conceder capabilities, dentro de su vigencia. `COMPLETED` queda como historia y no concede capabilities operativas. `REVOKED` tampoco concede.

Cerrar la revisión de una ronda no borra a la persona ni la revisión hecha. Solo termina esa asignación. Una ronda o renovación posterior crea asignaciones nuevas. No se reactiva la histórica.

## 7. AuthorizationContext

Puede traer `committeeType`, `projectId`, `committeeCaseId`, `processId` y `workflowState`. No todos hacen falta siempre. Si falta el dato que el alcance necesita, se deniega.

## 8. Capabilities efectivas

1. Se toma el usuario autenticado.
2. Se leen sus asignaciones.
3. Se descartan las que no están operativas.
4. Se conservan las que coinciden con el contexto.
5. Se cargan las definiciones de esos roles.
6. Se reúnen las capabilities y se quitan duplicados.
7. Si hay estado de workflow, se retiran las acciones que ese estado no permite.

No se suman todos los roles de la persona sin mirar el contexto.

`can(user, capability, context)` responde sí o no. `getEffectiveCapabilities` devuelve el conjunto. Sin estado, el conjunto incluye las acciones posibles. `can` de una acción sin estado responde no.

## 9. Varios roles

Ignacio es miembro de CECUA y revisor de `CECUA-2026-004:R1`. En esa ronda recibe la unión de ambos roles. En `CECUA-2026-004:R2`, donde no está asignado como revisor, conserva las capabilities de miembro y no las de revisor.

Alberto solo es revisor de R1. Ve su revisión. No ve otros proyectos, otras revisiones ni las sesiones del comité.

Sofía es miembro de CECUA y no revisora de R1. No recibe `VIEW_OWN_REVIEW`, `EDIT_OWN_REVIEW` ni `SUBMIT_OWN_REVIEW`.

## 10. Restricciones de workflow

Viven en `EthicsWorkflowPolicy`, tomadas del workflow de `ethics-uss`. No están en el YAML.

Ejemplos: `UPLOAD_DOCUMENT` y `RESPOND_OBSERVATIONS` solo en `AWAITING_RESEARCHER`. `ASSIGN_REVIEWERS` en la fase de asignación. `REASSIGN_REVIEWER` mientras el expediente sigue abierto, incluida la preparación y la validación de la resolución. `PREPARE_RESOLUTION` en `PENDING_RESOLUTION`. `VALIDATE_RESOLUTION` y `RETURN_RESOLUTION` en `PENDING_PRESIDENT_VALIDATION`. Editar o enviar la revisión propia en `PENDING_ACCEPTANCE` o `IN_REVIEW`.

`REASSIGN_REVIEWER` cubre la reasignación ordinaria: la revisión sigue en su etapa operativa (`NOTIFIED`, `ADMISSIBILITY`, `RETURNED`, `PENDING_ASSIGNMENT`, `PENDING_ACCEPTANCE`, `IN_REVIEW`, `PENDING_SESSION`, `PENDING_DOCUMENTS`) y la asignación no está `COMPLETED` ni tiene una revisión enviada.

`REASSIGN_REVIEWER_ADVANCED_STAGE` es otra capability. La tiene solo `COMMITTEE_PRESIDENT`. Cubre el reemplazo cuando la asignación ya está `COMPLETED`, cuando esa revisión ya fue enviada, o cuando el expediente está en `WITH_OBSERVATIONS`, `AWAITING_RESEARCHER`, `PENDING_RESOLUTION` o `PENDING_PRESIDENT_VALIDATION`. `REASSIGN_REVIEWER` no la sustituye. Administración y secretaría conservan la ordinaria y no reciben la avanzada.

El motivo es obligatorio. El cambio queda auditado con la etapa y si fue ordinaria o avanzada. La revisión enviada conserva a su autor. La asignación anterior no se borra: queda con `supersededAt` y la nueva ocupa el mismo slot y la misma ronda. Un expediente `APPROVED`, `REJECTED`, `EXEMPT` o `WITHDRAWN` no se reasigna con ninguna de las dos.

## 11. ReviewerAssignment

No hay dos verdades.

`ReviewerAssignment` es el hecho de que una persona revisa un proceso. `ReviewerAssignmentSource` lo expone. El compuesto lo convierte en una asignación contextual de rol `REVIEWER` y entonces aplica las capabilities definidas para ese rol.

Hoy, como OpenScience todavía no persiste esa tabla, `DemoReviewerAssignmentSource` lee las filas `REVIEWER` de `demo-role-assignments-V2.yml`. Esas filas no se cargan además como asignación estructural.

Cuando exista la tabla, ese origen leerá `ReviewerAssignment`. Una fila `PENDING` o `ACCEPTED`, no reemplazada, será operativa. `COMPLETED`, `REJECTED`, `CONFLICT` o una fila superseded no conceden capabilities. El servicio de autorización no cambia.

El seed del prototipo y el YAML demo no describen el mismo momento: en el seed, la revisión de Ignacio sobre `case-2` está `COMPLETED` y la de Alberto está `ACCEPTED`. El YAML demo los deja `ACTIVE` en R1 para poder probar el modelo. Al pasar a la tabla, manda `ReviewerAssignment`.

## 12. Investigador responsable

`Project.researcherId` del prototipo ya dice que Juan Pérez (`u-inv-1`) es responsable de `prj-1` a `prj-6`. `DemoProjectOwnershipSource` usa esa relación y concede `RESEARCHER` en cada uno de esos proyectos.

La fila `RESEARCHER` del YAML se valida y no se concede otra vez. El código `ETI-2026-002` es `prj-2`.

En producción, esa relación sale del proyecto de ética. No hace falta duplicarla en `user_role_assignment`, salvo un responsable que no sea el investigador del proyecto.

## 13. YAML demo

`permissions-V4.yml` no tiene usuarios, traducciones ni una política escrita. `permissions-V3.yml` se conserva y no se carga.

El selector de usuarios era una herramienta del prototipo inicial. A partir de esta versión, las pruebas funcionales se realizan con cuentas reales de OpenScience y asignaciones reales de permisos. `demo-role-assignments-V2.yml` se conserva como fixture de tests y de la demo aislada. No lo carga `EthicsAuthorizationConfig`. El modo demo solo puede habilitarse explícitamente en desarrollo (`ethics.demo.enabled` y `environment.demoEnabled`) y nunca constituye un mecanismo de autorización. Por defecto está apagado. En producción no está disponible, aunque la bandera local se active.

Los `userKey` del fixture son los id del prototipo (`u-admin`, `u-pres-cecua`, `u-sec-cecua`, `u-rev-4`, `u-rev-ext`, `u-inv-1`). Sofía (`u-rev-5`) no tiene fila. Alberto Soto es revisor externo; no es investigador. No se agregan filas al YAML para que el archivo coincida.

`MANAGE_USERS` está solo en `ADMINISTRATOR`, en alcance global. Abre Administración → Usuarios. Presidencia y secretaría no la tienen. El backend rechaza investigador, presidencia, secretaría y administración sobre una persona externa.

Una referencia desconocida se informa y el arranque falla. No queda una configuración a medias.

## 14. Base de datos futura

La cuenta sigue siendo `app_user`. El acceso por aplicación, la persona compartida y la asignación de Ethics están propuestos en `backend/sql/proposed-person-user.sql`. Ese script no se ejecuta desde el agente.

La tabla de roles de Ethics se llama `ethics_role_assignment`. No apunta a `app_role` y no repite `application_id`: el archivo `permissions-V4.yml` ya es el namespace de Ethics. `EthicsAdminRepository` lee esa tabla para la sesión real. El correo y un `userId` enviado por el navegador no eligen el rol. Un revisor sin `ReviewerAssignment` no obtiene la revisión.

`juan.cabello@uss.cl` no está en ese YAML ni en el selector demo. Es la cuenta real de OpenScience. Al arrancar, `ethics.bootstrap.adminEmails` la busca en `app_user`. Si no existe, registra `Bootstrap admin user not found.` y no crea cuenta ni contraseña. Si existe, deja `user_application` ETHICS en ACTIVE y `ethics_role_assignment` RESEARCHER y ADMINISTRATOR, ambas con alcance GLOBAL / `*`. Esas filas son solo de Ethics: no agregan `PLATFORM_ADMIN` ni tocan Eventos u OpenScience.

El modelo completo está en `docs/person-user-model.md`.

Columnas previstas de `ethics_role_assignment`:

- `id`
- `user_id`
- `role_id`
- `scope_type` (`GLOBAL`, `COMMITTEE`, `PROJECT`, `PROCESS`)
- `scope_id`
- `status` (`ACTIVE`, `COMPLETED`, `REVOKED`)
- `valid_from`
- `valid_until`
- `created_by`
- `created_at`
- `modified_by`
- `modified_at`

No se guardan traducciones. Las filas `REVIEWER` no duplican `ReviewerAssignment`. El `RESEARCHER` de una cuenta nueva es el acceso por defecto a Ethics (`GLOBAL` / `*`). `Project.researcherId` sigue identificando al responsable de un proyecto, y la fila YAML de ese caso no se concede otra vez.

## 15. Gestión de roles y asignaciones

Funcionalidad futura, para una persona autorizada. Todavía no hay pantalla.

Debe permitir elegir a la persona, asignar uno o más roles, elegir el tipo de alcance y el comité, proyecto o proceso, activar, finalizar y revocar, y consultar las asignaciones vigentes y su historial.

Ejemplo: Alberto Soto, `REVIEWER`, `PROCESS`, `CECUA-2026-004:R1`, `ACTIVE`.

Esa pantalla escribe `user_role_assignment`. En producción no se edita el YAML.

La auditoría de esa gestión usa los campos de la tabla: quién asignó, cuándo, alcance, vigencia, quién finalizó o revocó, y en qué fecha.

## 16. i18n

Las claves viven en `projects/ethics-uss/scripts/build-i18n.mjs` y en los JSON generados.

| Clave | Español | Inglés |
| --- | --- | --- |
| `roles.administrator` | Administración DISEI | DISEI Administration |
| `roles.committee_president` | Presidente/a de Comité | Committee President |
| `roles.executive_secretary` | Secretario/a Ejecutivo/a | Executive Secretary |
| `roles.reviewer` | Revisor/a | Reviewer |
| `roles.researcher` | Investigador/a | Researcher |

El perfil del prototipo muestra `translate(idLabel)`. No guarda texto traducido.

## 17. Ejemplos

Camila Herrera, `u-admin`, `ADMINISTRATOR` global: recibe el conjunto DISEI de `permissions-V3.yml`, incluidas solicitud, consolidación, validación y devolución de resolución. No es el conjunto reducido anterior. No tiene `REASSIGN_REVIEWER_ADVANCED_STAGE`.

Rodrigo Pino, `u-sec-cecua`, secretaría de CECUA: `ASSIGN_REVIEWERS` y `REASSIGN_REVIEWER` dentro de CECUA. En CEC, ninguna.

`GET /api/v1/ethics/me` devuelve el usuario autenticado y sus roles operativos, con `id` e `idLabel`. No devuelve las capabilities de todos los proyectos.

`GET /api/v1/ethics/permissions` devuelve las capabilities efectivas del contexto pedido (`committeeType`, `projectId`, `committeeCaseId`, `processId`, `workflowState`). La interfaz no debería llamar `can` una vez por botón.

## Home institucional

Camila tiene `ADMINISTRATOR` / `GLOBAL` / `*`. El inicio no pregunta el nombre del rol. Muestra un módulo solo si la capability está en `permissions-V3.yml`:

- `VIEW_PENDING_CLASSIFICATION`: solicitudes pendientes de clasificación.
- `VIEW_ADMISSIBILITY`: casos en admisibilidad.
- `VIEW_RESOLUTION`: casos en `PENDING_RESOLUTION` o `PENDING_PRESIDENT_VALIDATION`.
- `VIEW_EXPIRATIONS`: aprobaciones con semáforo `PROXIMO`.
- `VIEW_AUDIT`: actividad reciente, si hay eventos.

`capabilities.global` sigue siendo una clasificación del YAML, no el alcance: en un contexto CECUA Camila conserva también las capabilities agrupadas bajo `project`.

## Home de revisor

`RoleAssignment` autoriza. `ReviewerAssignment` es el trabajo de revisión. El inicio no cuenta roles y no tiene un módulo de miembro de comité.

El prototipo lee las `ReviewerAssignment` del seed local. El backend todavía no tiene esa tabla: `DemoReviewerAssignmentSource` lee las filas `REVIEWER` de `demo-role-assignments-V2.yml` y no las carga otra vez como asignación estructural. Esas filas no se usan como ítems del Home. En el seed, las revisiones de Ignacio y Sofía están `COMPLETED` y la de Alberto está `ACCEPTED`. El YAML demo deja a Ignacio y Alberto `ACTIVE` en R1 como respaldo temporal de autorización. La interfaz no usa ese `ACTIVE` para editar: manda el estado del seed. `PENDING` y `ACCEPTED` permiten el trabajo. `COMPLETED` permite consultar la revisión propia y no permite editar, enviar, aceptar ni rechazar.

El inicio de revisor muestra pendientes y en curso cuando hay `VIEW_OWN_REVIEW`. No muestra «Proyectos del comité»: ese módulo exige `VIEW_COMMITTEE_SESSIONS`, que el revisor no tiene. `COMPLETED` queda en Revisiones asignadas. Ser revisor no abre sesiones, el pool de revisores ni todos los proyectos del comité.

Ignacio es solo `REVIEWER`. Su `committees: ['CECUA']` describe composición institucional y no autoriza. Sofía no tiene fila YAML. Sigue en el selector porque `asg-1b` y `asg-2b` son `ReviewerAssignment` reales y `COMPLETED`: consulta histórica, sin capacidades operativas. Alberto usa el mismo rol `REVIEWER`. «Externo/a» es metadata de la persona.

`CommitteeCase.reviewerIds` es una copia que `ReviewerService` reescribe al asignar, reasignar o rechazar. No concede acceso. Un revisor ve un proyecto cuando tiene una `ReviewerAssignment` legible de ese proceso.

`VIEW_PUBLISHED_OBSERVATIONS` no abre la revisión privada del otro revisor. El revisor no tiene `VIEW_ALL_REVIEWS` ni `VIEW_REVIEWER_ASSIGNMENTS`. El historial de asignaciones se filtra a la suya.

`VALIDATE_RESOLUTION` y `RETURN_RESOLUTION` están en administración y presidencia. Secretaría no las tiene. `EDIT_CONSOLIDATED_OBSERVATIONS` y `SAVE_CONSOLIDATED_OBSERVATIONS` están en administración, presidencia y secretaría. El borrador no se muestra al revisor ni al investigador.

## Lo que la interfaz todavía resuelve en local

El prototipo aún no pide `GET /api/v1/ethics/permissions`. El espejo local usa los mismos identificadores que `permissions-V3.yml`. El menú, el inicio, los proyectos y el expediente consultan `permissionService.can(capability, context)`. El backend vuelve a autorizar y no confía en un rol enviado por el navegador.
