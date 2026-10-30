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

Al completar el registro se crea una `Person` con `person_kind = INSTITUTIONAL` y `purePersonId` nulo. Sigue siendo institucional por el correo USS. No se escribe en `pure_person` y no se convierte en revisor externo. Después se crea el `UserAccount`, se activa Ética y se asigna `RESEARCHER`.

La fila `person` se crea solo si esa tabla ya existe. El script propuesto no se ejecuta desde la aplicación. Si la tabla no está, la cuenta y el acceso a Ética igual se activan.

## Revisor externo

Este flujo no admite correos ajenos a USS, como `gmail.com`. El alta de un revisor externo es otra pantalla de Administración, exige `MANAGE_USERS` y no asigna `RESEARCHER`. No existe el investigador externo.

## Autoregistro público

Crear cuenta también empieza por el correo institucional y usa la misma resolución. Si ya hay una cuenta utilizable, la respuesta es: «Si ya tienes una cuenta, utiliza Iniciar sesión.» No se indica si la persona está en PURE ni se muestran datos internos. Una invitación nueva o reenviada solo confirma que, si el correo es institucional, llegarán los pasos siguientes.

## Auditoría

Se registran `USER_INVITATION_SENT`, `ETHICS_ACCESS_ENABLED`, `USER_REGISTERED` y `RESEARCHER_ASSIGNED`, con actor, usuario de destino y fecha. No se guardan contraseñas ni tokens en claro.
