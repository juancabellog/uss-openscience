# Contexto para seguir Ética de Proyectos USS

Fecha del corte: 3 de noviembre de 2026.  
Último commit en `master`: `c3e1720` — «Incorpora Ética de Proyectos sobre la cuenta compartida de OpenScience.»  
Repositorio: https://github.com/juancabellog/uss-openscience  
Este archivo es un resumen de trabajo. Si contradice el código, manda el código. Los documentos en `docs/` describen decisiones; algunos párrafos quedaron viejos (ver al final).

## Qué es este workspace

Monorepo local en `/Users/manolocabello/Documents/uss/CA/workspace`.

- `backend/`: API Jakarta/CDI sobre Open Liberty. Base `uss-ca` (MariaDB). JNDI `jdbc/openscience`. El datasource es `transactional="false"`.
- `src/`: frontend OpenScience (Angular). Puerto de desarrollo **4200**.
- `projects/ethics-uss/`: frontend Ética de Proyectos (Angular, zoneless). Puerto de desarrollo **4400**. El usuario a veces lo abre en **4500**.
- `projects/events-uss/`: Eventos. Puerto **4300**.
- `docs/`: decisiones de producto. No son la especificación viva si el código ya avanzó.

Ética no es otra cuenta ni otro login de identidad. Reutiliza `app_user` y la cookie `OS_SESSION` (path `/`).

## Cómo correrlo

Node fijado: `~/.nvm/versions/node/v22.23.2`.  
Frontend ética: `npm run start:ethics` (proxy en `projects/ethics-uss/proxy.conf.json`: `/api` → `http://127.0.0.1:9080`).  
Backend: Liberty en el puerto **9080**.

| Entorno | Login y API OpenScience | API Ética |
| --- | --- | --- |
| Desarrollo | `/api/v1` | `/api/v1/ethics` |
| Producción | `/openscience-api/v1` | `/openscience-api/v1/ethics` |

En Hidra, Tomcat publica el context `/openscience-api` y lo reenvía a Liberty `/api`. Un `POST https://apps.cienciavida.org/api/v1/auth/login` no llega al backend: el host estático responde 200 y no hay log. Producción ya está en `projects/ethics-uss/src/environments/environment.production.ts`. Desarrollo no se toca para “arreglar” producción.

## Reglas que no se negocian

- No ejecutar SQL. `backend/sql/proposed-person-user.sql` y `backend/sql/ethics-admin-bootstrap.sql` son propuestas. La app no los corre. Si `person` no existe, el alta igual activa la cuenta y Ética.
- No agregar `"type": "module"` a `package.json`. Los scripts usan `node --experimental-strip-types`. El aviso `MODULE_TYPELESS_PACKAGE_JSON` es esperado.
- No editar a mano `projects/ethics-uss/src/app/core/i18n/es.json` ni `en.json`. La fuente es `projects/ethics-uss/scripts/build-i18n.mjs`. Regenerar con el Node de arriba. Los JSON de OpenScience (`src/app/core/i18n/`) sí se editan a mano cuando cambia un texto de OpenScience.
- Demo apagada. `environment.demoEnabled` y `ethics.demo.enabled` quedan en false. No prender el selector de perfiles para probar. Las pruebas funcionales usan cuentas reales.
- Dominios institucionales exactos: `uss.cl`, `correo.uss.cl`, `docente.uss.cl`. Nunca `endsWith("uss.cl")`.
- Una sola cuenta por correo. No crear un segundo `app_user` para Ética.
- `ADMINISTRATOR` de Ética no es `PLATFORM_ADMIN`.
- Persona con `purePersonId` no nulo: datos PURE de solo lectura. No hay override local.
- Persona local institucional puede tener `purePersonId` nulo y seguir siendo `INSTITUTIONAL`.
- Revisor externo no recibe `RESEARCHER`. No existe investigador externo.
- `MANAGE_USERS` solo lo tiene `ADMINISTRATOR`. Hace falta para Administración → Usuarios.
- Weld, si se toca Java de autorización: `AuthorizationService` necesita constructor protegido sin argumentos. `EthicsAdminRepository` va con `@Typed(EthicsAdminRepository.class)`. No publicarlo como `RoleAssignmentProvider`: eso lo produce `EthicsAuthorizationConfig`. `DatabaseRoleAssignmentRepository` no es CDI y lanza `ETHICS_ROLE_ASSIGNMENT_TABLE_PENDING`; no cambiar el provider persistido a esa clase.
- No hacer commit ni push salvo que lo pidan. No cambiar `git config`. No `--no-verify`, no force push.
- Cambios de UI web: probar el flujo en el navegador. Sin sesión, `/usuarios` y `/proyectos/nuevo/proyecto` redirigen a `/ingresar`. No activar demo para saltarse eso.

## Identidad y alta

Detalle en `docs/ethics-onboarding.md` y `docs/person-user-model.md`.

Administración empieza solo con el correo. `resolveInstitutionalUser` responde `EXISTING_USER`, `PURE_PERSON` o `NEW_INSTITUTIONAL_PERSON`.

- Cuenta OpenScience existente: se reutiliza, sin contraseña nueva. Ética se habilita y se asegura `RESEARCHER`. La bienvenida sale después de que Ética queda activa. Si la cuenta está `PENDING_ACTIVATION`, se reenvía la invitación y la bienvenida espera.
- Ficha PURE sin cuenta: solo lectura. La invitación pide contraseña. Al activar se crea `UserAccount`, se vincula `purePersonId`, se activa Ética y se asegura `RESEARCHER`.
- Institucional no encontrado: invitación de registro. Se crea `Person` `INSTITUTIONAL` con `purePersonId` nulo. Catálogos del formulario de proyecto (campus, facultad, cargo). No se pide carrera. No se escribe `pure_person`.
- Autoregistro público: también solo correo. Si ya hay cuenta: «Si ya tienes una cuenta, utiliza Iniciar sesión.» No se revela si está en PURE.
- Revisor externo: formulario aparte de Administración. No es este flujo.

Invitaciones: tokens de OpenScience, un solo uso, con vencimiento. No hay tabla nueva de invitaciones. Estados visibles: `PENDING`, `ACCEPTED`, `EXPIRED`, `CANCELLED`.

Idioma de correo: `MailLocale.resolve(preferredLanguage, flowLocale)`. Orden: idioma guardado, si no el del flujo, si no `es`. Solo `es` y `en`. El dominio no elige idioma. La invitación de Administración no manda el idioma de la UI del admin.

## Autorización

Detalle en `docs/ethics-permissions.md`. La autoridad es `AuthorizationService`. El nombre del rol no autoriza: autoriza la capability, en el alcance, y el workflow puede retirarla.

Roles de `permissions-V4.yml` (el que se carga): `ADMINISTRATOR`, `COMMITTEE_PRESIDENT`, `EXECUTIVE_SECRETARY`, `REVIEWER`, `RESEARCHER`. `COMMITTEE_MEMBER` no existe. No hay alias.

`idLabel` es una clave (`roles.researcher`), no el texto. El frontend la traduce.

Asignación: `ethics_role_assignment`. Alcances `GLOBAL`, `COMMITTEE`, `PROJECT`, `PROCESS`. Estados `ACTIVE`, `COMPLETED`, `REVOKED`. Vigente: `ACTIVE` y el día no es anterior a `valid_from` ni posterior a `valid_until`. El día de `valid_until` todavía cuenta.

`REVIEWER` es contextual. No se asigna ni se quita en Administración → Usuarios. Sale de la revisión.

`RESEARCHER` global se crea al habilitar Ética institucional y se puede quitar. No es irremovible. Al volver a asignarlo, `ensureStructuralRole` debe dejar la fila `ACTIVE`, `valid_until` nulo y, si `valid_from` es futuro, también nulo. Si solo se pasaba el estado a `ACTIVE` y quedaba una fecha de término vencida, el POST respondía 200, el combo se limpiaba y el chip no aparecía. Los otros roles sí aparecían porque no tenían fila previa. Si después del write la función sigue sin estar vigente, el backend responde `ROLE_NOT_ASSIGNED` y la pantalla no finge éxito.

Revocar no borra la fila: `status = REVOKED` y, si existe la columna, `valid_until = CURRENT_DATE`. No se puede quitar al último administrador activo. Quitar el propio `ADMINISTRATOR` refresca capacidades y sale de `/usuarios` si se pierde `MANAGE_USERS`. Auditoría `ROLE_REVOKED`.

Bootstrap de `juan.cabello@uss.cl`: `ethics.bootstrap.adminEmails`. Si la cuenta no existe, no se crea ni se escribe contraseña. Si existe, deja Ética activa y `RESEARCHER` + `ADMINISTRATOR` globales. No otorga `PLATFORM_ADMIN`.

## Frontend de ética

- Login propio en `role-select.page.ts`, visualmente alineado con OpenScience. El POST es el de OpenScience. Mostrar/ocultar contraseña: botón `type="button"`, iconos `eye` / `eye-off` en `eth-icon`, `aria-pressed`, empieza en `type="password"`. El mismo patrón está en el reset de OpenScience. No hay librería nueva de iconos.
- Menú en `projects/ethics-uss/src/app/core/layout/navigation.ts`, función `visibleNavigation()`. Grupos: Inicio (fuera de grupo), GESTIÓN, EVALUACIÓN, MIS REVISIONES, SEGUIMIENTO, ADMINISTRACIÓN. Sección vacía no se muestra. Notificaciones no van en el sidebar: están en el header, hacia `/notificaciones`. `CREATE_REQUEST` solo en `RESEARCHER` (desaparece «Nueva solicitud»). `VIEW_PROJECTS` y `VIEW_NOTIFICATIONS` siguen para `ADMINISTRATOR`.
- Secciones colapsables salvo Inicio. Varias pueden estar abiertas. La de la ruta activa queda abierta y no se cierra mientras se está ahí. `localStorage`: `ethics.sidebar.sections`. Por defecto abiertas: management, evaluation, myReviews, administration. followUp cerrada. Chevron −90° al colapsar, 160 ms; `prefers-reduced-motion` lo anula. Lista colapsada: `display: none`.
- Tipografía del sidebar: Inicio y padres de sección comparten nivel (0.9375 rem, peso 600). Hijos 0.8125 rem, peso 400; el hijo activo pesa 500 y no crece. El padre del ítem activo no pasa a peso 700. Sangría: 0.75 rem / 2 rem. Colores de siempre (`#f4efe4` y blancos translúcidos).
- Directorio de usuarios: solo backend, `GET /api/v1/ethics/users`. Filtros en servidor. Paginación 1-based, tamaño 25, tope 100. Debounce 300 ms. Sin fallback demo.
- Nuevo proyecto: un componente, un `FormGroup`, pasos 1–7, una sola ruta `proyectos/nuevo/proyecto`. Continuar, Atrás y el click del stepper usan `wizardMove` en `wizard-step-navigation.ts`. Atrás no valida. Adelante exige que cada paso anterior esté completo (pregunta visible y obligatoria, con respuesta real y control válido). El espacio en blanco no es respuesta. Ocultas por `displayWhen` no cuentan. El click no guarda borrador, no hace reset ni `setValue`. Enviar sigue mirando título, verdad y Singapur.
- Affordance del stepper: cada paso es `button type="button"`. La píldora completa es el target. `--available` y `--completed` usan `cursor: pointer` y un hover con `--eth-bg` y `--eth-primary-soft`. `--current` y `--locked` usan `cursor: default` y no tienen hover. El actual sigue en `--eth-accent` / `--eth-accent-soft`. Focus: `:focus-visible` con outline `--eth-primary`. No poner `cursor: pointer` a todos los pasos.

## Correo, documentos y otros ya cerrados

- Plan de gestión de datos: identificación `DATA_MANAGEMENT_PLAN`.
- Declaración de Singapur: el PDF público está en `projects/ethics-uss/public/declaracion-singapur.pdf`.
- Placeholder «null» del título en el paso 1: se corrigió con `[attr.placeholder]` y `asText`. No reabrirlo.
- Checkbox de confirmar deshabilitación: va junto a la acción, no flotando.
- Editar a otro usuario no refresca el menú de quien está logueado. Editarse a sí mismo sí (`GET /ethics/me` y `GET /ethics/permissions`).

## Avisos que no son el bug

- `WASSessionCore`: `JSESSIONID` de largo 28 en vez de 23. Aparece en cada request y la respuesta igual es 200. No es la causa de un alta de rol que “no hace nada”.
- CORS: `openscience.frontend.origin` no incluye `http://localhost:4400` ni `:4500`. El warning se loguea. En desarrollo el browser habla con el dev server y el proxy reenvía `/api`, así que la respuesta se puede leer. No ampliar el allowlist a orígenes arbitrarios. Si se agrega algo, solo esos dos orígenes de ética.

## Documentos útiles

- `docs/ethics-onboarding.md`
- `docs/person-user-model.md`
- `docs/ethics-permissions.md` — la sección 15 dice que todavía no hay pantalla de roles. Eso está viejo: Administración → Usuarios ya asigna y revoca roles estructurales.
- `docs/new-project-form-rules.md`
- `docs/committee-workspace-visibility.md`
- `docs/ethics-documents.md`
- `docs/role-permission-matrix.md`

- 2026-11-03. Producción: `GET /auth/activate` 200 y `POST` 400 `AUTH_PROFILE_REQUIRED`, con la pantalla solo de contraseña y el texto de enlace vencido. El `GET` ya puede devolver `profileRequired`, `ethicsInvitation` y `pureReadonly`. El formulario muestra el perfil local solo si `profileRequired` es verdadero y usa el catálogo de Nuevo Proyecto. `AUTH_PROFILE_REQUIRED` ya no usa el texto del enlace. El nombre vacío en una invitación pendiente es el estado esperado hasta que la persona lo escribe.

- 2026-11-03. Contradicción cerrada: el código aceptaba solo contraseña en toda invitación de Ética (`acceptsSharedActivation`). `docs/ethics-onboarding.md` §3 y el encargo de producto piden el perfil local si no hay PURE. Manda el encargo. PURE sigue en solo lectura y solo pide contraseña. Sin PURE, la activación pide nombres, apellidos, sede, facultad y cargo. No se creó pantalla de perfil: no hay una sección reutilizable; existe `GET /ethics/me/profile` y el paso B del proyecto bloquea la identidad PURE. `pure_person` no tiene sede, facultad ni programa, así que no se inventan. Un dato PURE vacío se muestra «No disponible» en la activación.

- 2026-11-03. El texto «La cuenta aún no está habilitada.» es `AUTH_PENDING`: `app_user` sigue sin contraseña. «Habilitada» en administración es el acceso `ETHICS`. Si la cuenta está pendiente, el aviso de habilitar ya no dice que use las credenciales de OpenScience.

- 2026-11-03. Login de María José: `POST /auth/login` 401 en 6 ms es `AUTH_PENDING`. Habilitar Ethics deja `user_application` en `ACTIVE` y no pone contraseña ni pasa `app_user` a `ACTIVE`. La activación con contraseña también vale si Ethics ya quedó `ACTIVE`. La ficha muestra «Invitación pendiente» y avisa que falta la contraseña.

- 2026-11-03. Activación desde Ética: el token común sigue siendo válido con `app_user` en `PENDING_ACTIVATION`. No habilita `OPENSCIENCE`. Producción confirmó el rechazo: `POST /auth/activate` devolvía 400 `AUTH_PROFILE_REQUIRED` en milisegundos, sin guardar la contraseña, cuando la invitación dejó el nombre vacío. Una fila `user_application` `ETHICS/PENDING` ahora acepta solo la contraseña compartida. Después `ETHICS` queda `ACTIVE` y se asegura `RESEARCHER`, también si el nombre sigue vacío. El catálogo institucional sigue exigiéndose en altas que no son de Ethics. Archivos: `AuthService.java`, `EthicsOnboardingService.java`, `EthicsAccountActivated.java`. El enlace de Majo sigue usable hasta que se despliegue este backend.

- 2026-11-03. Espacio de trabajo en Ética: una persona puede ejercer varias funciones a la vez. Si esas funciones cambian la navegación, el encabezado ofrece «Trabajando como». Esa elección cambia menú, inicio, etiquetas y filtros, y guarda `ethics.active.workspace` en el navegador. No cambia la identidad, `OS_SESSION`, `app_user`, las asignaciones ni las capabilities. La autorización sigue siendo capability, alcance y estado del workflow. `GET /ethics/me/functions` solo lee asignaciones vigentes para distinguir, por ejemplo, presidencia de CEC y de CECUA. `/ethics/me` sigue devolviendo el id del rol sin alcance.

- 2026-11-03. Retomar borrador: guardar no devolvía el id al wizard y el listado abría el detalle en solo lectura, así que cada vuelta parecía una solicitud nueva. Ahora el mismo `projectId` abre `/proyectos/:id/borrador` en el wizard existente. Continuar solicitud aparece solo si el borrador es propio, sigue en `DRAFT` y hay `CREATE_REQUEST`. El segundo guardado actualiza ese registro. El perfil no pisa respuestas ya guardadas. Archivos: `ethics-submission.service.ts`, `new-project-wizard.page.ts`, `draft-resume.ts`, listado y detalle de proyectos, `app.routes.ts`.

- 2026-11-03. Eliminar usuario en Administración: `GET /ethics/users/{id}/removal` y `DELETE /ethics/users/{id}`. Solo se quita el alta de Ethics si no hay snapshot, fila `REVIEWER`, alcance `PROJECT`/`PROCESS`, estado `COMPLETED` ni función de comité. Deshabilitar sigue en `INACTIVE`, no en `REVOKED`. No se borra `app_user`, `person` ni `pure_person`. Una invitación pendiente invalida el token `ACTIVATION`. El último administrador activo tampoco se deshabilita. Project, Submission, Review y Document no están en MySQL: el chequeo no los ve y el modal lo dice. Pendiente: cuando esas tablas existan, sumarlas al mismo chequeo antes de permitir el DELETE.

- 2026-11-03. Tabla de Administración → Usuarios: la fila muestra solo «Ver detalle» y un menú `⋯` compacto (`button.ghost.compact`, 32 px, sin salto de línea). Invitación pendiente no ofrece «Habilitar acceso»; ofrece reenviar y, si el alta se puede quitar, cancelar la invitación. Habilitar, deshabilitar, editar roles y eliminar quedan en el menú según el estado.

- 2026-11-03. `/openscience/auth/activate-account` la sirve OpenScience: `src/app/features/auth/activate-account.component.ts`. Ethics no tiene esa ruta. `GET /auth/activate` devuelve `profileRequired`, `ethicsInvitation` y `pureReadonly`. Sin PURE, la pantalla pide nombres, apellidos, sede, facultad y cargo además de la contraseña. Con PURE, esos datos van en solo lectura. `AUTH_PROFILE_REQUIRED` mantiene el formulario y no usa el texto de enlace inválido. El bundle que vio producción era el anterior: solo contraseña, y cualquier error del POST caía en «El enlace no es válido o ha expirado.». Ese cambio sigue sin commit.

- 2026-11-04. Activación no-PURE: la pantalla real sigue siendo `src/app/features/auth/activate-account.component.ts` en `/openscience/auth/activate-account`. No se duplica en `/ethics/activar-cuenta` porque el token y `app_user` son de OpenScience; si `ethicsInvitation` es verdadero el texto dice «Activar acceso a Ética de Proyectos». `GET /auth/activate` manda `profileRequired` antes del POST. `ethics.testInstitutionalEmails` / `ETHICS_TEST_INSTITUTIONAL_EMAILS` queda vacío en producción. Un correo completo de esa lista recorre el mismo alta no-PURE (`NEW_INSTITUTIONAL_PERSON`, `purePersonId` null, ETHICS y RESEARCHER). No abre `@gmail.com`. Para quitar la prueba, vaciar la propiedad y reiniciar.

- 2026-11-04. El correo de una invitación Ethics ya no usa `/openscience/auth/activate-account`. `ActivationOrigin.ETHICS_INVITE` arma `{ETHICS_PUBLIC_URL}/activar-cuenta`. La pantalla está en Ethics y, al activar, vuelve a `/ingresar`. El token, `app_user` y la contraseña no cambian. OpenScience sigue activando solo sus propias altas. Si la URL de OpenScience es pública, una base `localhost` de Ethics se reemplaza por `https://apps.cienciavida.org/ethics`. Hay que reiniciar el proceso de producción para que el próximo correo deje de salir con localhost.

- 2026-11-04. La pantalla que sigue mostrando solo la contraseña y «Defina una contraseña para habilitar su cuenta en OPENSCIENCE-USS.» es el frontend publicado en `/openscience/`. Su chunk de activación no lee `profileRequired`. El API de ese mismo host ya devuelve `profileRequired`, `ethicsInvitation` y `pureReadonly`. Hay que desplegar el build de OpenScience (`src/`, `dist/openscience-uss`), no solo Ethics. El contexto de la invitación no va dentro del token: `GET /auth/activate` mira `user_application` ETHICS `PENDING` o `ACTIVE` y si el correo está en PURE. Sin PURE, `profileRequired` queda verdadero con la misma regla que `AUTH_PROFILE_REQUIRED`.

- 2026-11-04. Feedback de operaciones lentas: `shared/operation-feedback/`. El botón cambia el texto y se deshabilita al instante; el panel bloqueante espera 500 ms y no es solo un fondo gris; la explicación contextual espera 2 s y el aviso de demora, 8 s. No hay porcentaje ni interceptor global. Quedó en la invitación de Ethics, la cuenta pública, la activación, el borrador y el envío de la solicitud, la carga de documentos, el alta de usuario de OpenScience y el enlace de inscripción de Events. `docs/ui-operation-feedback.md`.

- 2026-11-04. `POST /auth/activate` devolvía 500 `Unknown column 'person_kind'` al crear la persona institucional: la tabla `person` de producción no tiene esa columna. El acceso a Ética se habilita antes del insert. Al arrancar, si la columna falta, se agrega; si no se puede, el insert la omite. Un intento que ya consumió el token entra por `/ingresar`: la misma elegibilidad habilita Ethics y, si no hay PURE ni persona, guarda nombre y apellido desde `app_user`. Sede, facultad y cargo de ese intento no quedaron grabados.

- 2026-11-04. Crear cuenta en Ethics llama `POST /auth/ethics-start` sin rechazar el dominio en el navegador. La misma política `EthicsTestInstitutionalEmails.eligible` vale para ese alta, para la invitación de Administración y para `RESEARCHER`. `ethics.testInstitutionalEmails` / `ETHICS_TEST_INSTITUTIONAL_EMAILS` sigue vacía por defecto. En local está en `bootstrap.properties`, que no se versiona. Un correo de esa lista entra como `NEW_INSTITUTIONAL_PERSON` si no está en PURE. Otro Gmail sigue rechazado. Para quitar la prueba, vaciar la propiedad y reiniciar Liberty. La pantalla no trata todo HTTP 200 como el mismo texto: `ETHICS_INVITE_SENT` y `ETHICS_INVITE_RESENT` muestran «Revisa tu correo» solo si `emailSent` es verdadero; `ETHICS_USE_LOGIN` indica que la cuenta ya existe.

## Cómo trabajar en paralelo

Este archivo es el traspaso. Cursor y ChatGPT no ven el chat del otro.

- Decir en cada lado qué archivo se está tocando.
- No rehacer login, menú, alta por correo, permisos V4 ni el stepper salvo un bug concreto.
- Un cambio de comportamiento va en el código y, si hace falta, en `docs/`. No dejar la decisión solo en el chat.
- Al cerrar una tarea, anotar aquí una línea con la fecha y qué quedó distinto, para que el otro lado no parta de este corte.
