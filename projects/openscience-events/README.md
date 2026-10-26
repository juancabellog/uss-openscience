# OPENSCIENCE Events

## Despliegue en `/events/`

El build de producción debe usar `--base-href /events/` (`./buid.sh`).
El directorio publicado es `dist/openscience-events/browser`.

Las rutas internas (`/events/eventos/:slug/inscripcion`) no existen como archivos.
El servidor web de **solo** `/events/` debe devolver `index.html` en esos 404.
No aplicar esa regla a `/openscience` ni a `/openscience-api`.

- Apache: `deploy/events.apache.conf` o el `.htaccess` copiado al root de `/events/`.
- Nginx: `deploy/events.nginx.conf`.
- Tomcat: `WEB-INF/web.xml` de este proyecto (404 → `/index.html`).

Tras el deploy, `https://apps.cienciavida.org/events/eventos/evento-test/inscripcion`
debe devolver el HTML de Angular (no un 404 de Apache).

La API pública es `GET /openscience-api/v1/events/{slug}`.
Ese path no debe caer en el fallback de la SPA.


Aplicación Angular independiente para inscripción pública y administración de eventos académicos USS.

## Internacionalización

La plataforma usa un runtime i18n propio (`I18nService`), el mismo patrón de OPENSCIENCE-USS y ethics-uss, sin `ngx-translate` y sin acoplar el AppShell.

- Locales: `es-CL` (predeterminado) y `en-US`.
- Preferencia: `localStorage` `openscience-events.language` → idioma del navegador si es `es*` o `en*` → español.
- El selector ES/EN está en el encabezado público y administrativo.
- El cambio de idioma no recarga la aplicación ni borra los formularios.
- Las claves faltantes caen al español y no muestran la clave cruda.
- Los códigos técnicos (`DRAFT`, `TALK`, `ASISTIO`) no se traducen; solo sus etiquetas.
- Nombres, facultades y cargos del Directorio USS no se traducen.

Catálogos: `src/app/core/i18n/es.json` y `en.json`.

```bash
npm run build:events-i18n
npm run check:events-i18n
```

## Correos y actas

El frontend envía `language` (`es` | `en`) al solicitar y confirmar una inscripción. El backend normaliza el valor y usa español si no está soportado. El idioma queda en el `snapshot_json` de la inscripción.

La generación de acta PDF tiene selector de idioma propio, con español como predeterminado para ANID. El idioma de la interfaz no cambia actas ya generadas.

## SQL pendiente (no ejecutado)

`backend/sql/proposed-events-i18n.sql` agrega de forma no destructiva:

- `event.title_en`
- `event.description_en`
- `event.primary_language`
- `event_registration.preferred_language`
- `event_artifact.language`

Hasta ejecutar esa migración, las traducciones de título/descripción se aceptan en la API y se guardan solo si las columnas existen. El idioma de inscripción ya se conserva en el snapshot JSON.
