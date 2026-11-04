# Feedback de operaciones

Toda operación iniciada por el usuario que tarde perceptiblemente debe mostrar estado de ejecución y resultado. El backdrop, el cambio de opacidad o deshabilitar un control no constituye feedback suficiente por sí solo.

El comportamiento vive en `shared/operation-feedback/`. Las tres aplicaciones no comparten un proyecto Angular, así que el estado es una clase sin dependencia de framework y cada pantalla la conecta a su propio botón, texto y, cuando hace falta, al panel `uss-operation-feedback`. Los estilos (`uss-op-spinner`, `uss-op-panel`) se importan en la hoja global de OpenScience, Events y Ethics.

## Variantes

- Localizada. El botón se deshabilita al instante, cambia su texto y muestra un indicador. La página sigue visible. Sirve para guardar, enviar, subir o inscribir.
- Bloqueante. Además, después de medio segundo aparece un panel con el nombre de la operación. El fondo oscuro no se muestra solo: el panel explica qué está ocurriendo. Sirve cuando la acción incluye una espera larga y no conviene lanzar otra igual, como crear un usuario y enviar la invitación.

## Tiempos

No hay porcentajes ni etapas inventadas.

- Al recibir la acción: el botón queda deshabilitado y dice qué está haciendo. Un segundo clic no abre otra operación.
- A partir de 500 ms: si la operación es bloqueante, aparece el panel.
- A partir de 2 s: se agrega la explicación contextual.
- A partir de 8 s: se agrega que sigue en curso y está tardando más de lo habitual. Ese texto desaparece al terminar, sea éxito o error.

Una operación que termina antes de medio segundo no muestra el panel.

## Resultado

El éxito usa el canal que la pantalla ya tenía (aviso, toast o cambio de paso) y el texto depende de la respuesta. Si el correo no se envió, no se dice que se envió. El error quita la carga, vuelve a habilitar la acción, conserva el formulario y muestra el mensaje de dominio con `role="alert"`.

## Accesibilidad

El botón lleva `aria-busy` mientras trabaja. El indicador gráfico es decorativo. El panel usa `role="status"` y recibe el foco mientras está abierto; al cerrarse, el foco vuelve al control anterior. El movimiento se reduce si el sistema lo pide.

## Dónde está aplicado

- Ethics, administración: crear o reenviar una invitación.
- Ethics, crear cuenta pública y activar el acceso.
- Ethics, guardar borrador y enviar la solicitud.
- Ethics, subir un documento.
- OpenScience, crear o guardar un usuario de administración.
- Events, enviar el enlace de inscripción.
