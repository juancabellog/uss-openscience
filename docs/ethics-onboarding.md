# Alta de usuarios en Ética de Proyectos

El alta institucional empieza por el correo. Administración no escribe nombre ni apellidos antes de que el sistema resuelva si esos datos ya existen. El correo se normaliza con recorte y minúsculas para la búsqueda. Los dominios válidos son exactamente `uss.cl`, `correo.uss.cl` y `docente.uss.cl`.

La cuenta es la de OpenScience (`app_user`). Ética no crea una segunda cuenta ni un inicio de sesión propio. La invitación reutiliza el token de activación de OpenScience: aleatorio, de un solo uso, con vencimiento y sin contraseña ni datos sensibles en la URL. Los estados visibles son `PENDING`, `ACCEPTED`, `EXPIRED` y `CANCELLED`, derivados del estado de la cuenta y del token. No hay una tabla de invitación nueva.

## 1. Usuario OpenScience existente

Si ya hay `UserAccount` para ese correo, se reutiliza. No se pide contraseña ni nombre. Si Ética no está activa, Administración habilita el acceso. Eso deja `ETHICS` en `ACTIVE` y asegura `RESEARCHER` de forma idempotente. Después se envía un correo de bienvenida para ingresar con las credenciales actuales. Si el acceso ya estaba activo, no se duplica nada.

Si la cuenta sigue en `PENDING_ACTIVATION`, todavía no tiene contraseña. En ese caso se reenvía la invitación. La bienvenida espera a que la persona active la cuenta.

## 2. Persona PURE sin UserAccount

Si no hay cuenta, se busca la ficha en `pure_person` por el correo institucional. No hay coincidencia aproximada por nombre. Administración ve nombre, apellidos, correo y cargo en solo lectura. PURE sigue siendo la fuente de esos datos.

La invitación pide únicamente contraseña y confirmación. Al activarla se crea el `UserAccount`, se vincula la persona con su `purePersonId`, se activa Ética y se asegura `RESEARCHER`. Recién entonces se envía la bienvenida y el token queda usado.

## 3. Persona institucional no encontrada en PURE

Si el correo institucional no tiene cuenta ni ficha PURE, Administración solo envía la invitación de registro. La persona completa nombre, apellidos, campus, facultad o unidad y cargo, y define su contraseña. Esos catálogos son los del formulario de proyecto. No se pide carrera: `person` no tiene esa columna.

Una invitación de Ethics abre `https://apps.cienciavida.org/ethics/activar-cuenta`. En local la base es `ethics.app.publicUrl` / `ETHICS_PUBLIC_URL` (en este entorno, `http://localhost:4500`). La pantalla es `projects/ethics-uss/src/app/features/auth/activate-account.page.ts`. Llama al mismo `GET` y `POST /auth/activate`: mismo token, mismo `app_user` y misma contraseña. Al terminar, el enlace vuelve a `/ingresar` de Ethics. OpenScience conserva `/auth/activate-account` solo para altas que no son de Ethics. Si OpenScience ya apunta a `https://apps.cienciavida.org/openscience`, una base local de Ethics se descarta y el correo usa `https://apps.cienciavida.org/ethics`. `./package-prod.sh` y `./run-prod.sh` escriben esa URL. Hay que volver a empaquetar o reiniciar el proceso de producción para que el correo que ya salió deje de usar localhost.

`GET /auth/activate` responde `valid`, `status`, `maskedEmail`, `profileRequired`, `ethicsInvitation` y `pureReadonly`. Si `profileRequired` es verdadero, el formulario pide nombres, apellidos, sede, facultad o unidad y cargo, y el `POST /auth/activate` envía `givenName`, `familyName`, `campus`, `faculty` y `positionTitle` junto con `token` y `password`. Si es PURE, esos datos se muestran en solo lectura y la contraseña basta. `AUTH_PROFILE_REQUIRED` deja el formulario en pantalla. El enlace inválido, vencido o usado sale del `status` del GET, o de `AUTH_TOKEN_INVALID` en el POST.

Los dominios institucionales siguen siendo `uss.cl`, `correo.uss.cl` y `docente.uss.cl`. Crear cuenta llama a `POST /auth/ethics-start` y no rechaza el dominio en el navegador. `ethics.testInstitutionalEmails` (variable `ETHICS_TEST_INSTITUTIONAL_EMAILS`) es una lista vacía por defecto. Solo admite correos completos, sin dominios ni comodines, para repetir el alta de una persona que no está en PURE. No convierte Gmail en un dominio del producto. En el Liberty local se define en `backend/src/main/liberty/config/bootstrap.properties`, que no se versiona. Para retirarla, borrar el valor y reiniciar el servidor.

Al completar el registro se crea una `Person` con `person_kind = INSTITUTIONAL` y `purePersonId` nulo. Sigue siendo institucional por el correo USS. No se escribe en `pure_person` y no se convierte en revisor externo. El acceso a Ética y `RESEARCHER` quedan activos antes de esa fila. Si el insert de la persona falla, la cuenta igual queda activada.

La fila `person` se crea solo si esa tabla ya existe. Al preparar las tablas de Ethics, si `person` no tiene `person_kind`, se agrega `VARCHAR(32) NOT NULL DEFAULT 'INSTITUTIONAL'`. Si esa columna no se puede agregar, el insert la omite y la activación continúa. El resto del script propuesto no se ejecuta desde la aplicación.

## Revisor externo

Este flujo no admite correos ajenos a USS, como `gmail.com`. El alta de un revisor externo es otra pantalla de Administración, exige `MANAGE_USERS` y no asigna `RESEARCHER`. No existe el investigador externo.

## Autoregistro público

Crear cuenta también empieza por el correo institucional y usa la misma resolución. La pantalla lee el código de `POST /auth/ethics-start`, no el HTTP 200. `ETHICS_INVITE_SENT` con `emailSent` muestra «Revisa tu correo». `ETHICS_INVITE_RESENT` con `emailSent` avisa que se envió un enlace nuevo. `ETHICS_USE_LOGIN` dice que ya existe una cuenta y ofrece ir a iniciar sesión. `NOT_INSTITUTIONAL_DOMAIN` mantiene la restricción institucional. Si `emailSent` es falso, el aviso es que el correo no pudo enviarse. No se indica si la persona está en PURE ni se muestran datos internos.

## Auditoría

Se registran `USER_INVITATION_SENT`, `ETHICS_ACCESS_ENABLED`, `USER_REGISTERED` y `RESEARCHER_ASSIGNED`, con actor, usuario de destino y fecha. No se guardan contraseñas ni tokens en claro.
