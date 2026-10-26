# Plataforma de Ética de Proyectos — USS

Prototipo funcional del frontend independiente **ethics-uss**.

No hay backend, MySQL ni autenticación real. Los flujos usan servicios mock y `localStorage`.

## Objetivo

Validar con DISEI la navegación, los formularios, la clasificación, el workflow por comité, las revisiones privadas, las observaciones, las sesiones, la resolución, la vigencia, la auditoría y los roles.

## Cómo ejecutar

En la raíz del monorepo:

```bash
npm install
npm run start:ethics
```

La app queda en [http://localhost:4400](http://localhost:4400).

Build:

```bash
npm run build:ethics
```

`npm start` sigue abriendo OpenScience-USS (:4200). Events usa `:4300`. Este prototipo no modifica esos proyectos.

## Roles demo

En `/ingresar` o en el selector del topbar:

| Perfil | Usuario | Uso |
|---|---|---|
| Administración DISEI | Camila Herrera | Clasificación y visión global |
| Presidente/a CECUA | Dra. Pilar Núñez | Comité Institucional de Ética en Cuidado y Uso de Animales |
| Secretario/a ejecutivo/a CECUA | Rodrigo Pino | El mismo comité |
| Miembro comité | Dr. Ignacio Bravo | Revisor 1 de CECUA |
| Miembro comité | Dra. Sofía Lagos | Revisor 2 de CECUA |
| Investigador/a | Juan Pérez | Solicitudes, observaciones y resoluciones |

## Rutas principales

- `/` dashboard según rol
- `/proyectos/nuevo` elección Nuevo / Validación
- `/proyectos/nuevo/proyecto` wizard de nuevo proyecto
- `/proyectos/nuevo/validacion` wizard de validación
- `/proyectos/:id` expediente del proyecto
- `/solicitudes` bandeja DISEI
- `/expedientes/:id` trabajo de secretario/presidente
- `/revisiones/:id` revisión privada
- `/sesiones` calendario mock
- `/auditoria` historial

## Cómo cambiar de rol

Use el selector **Cambiar perfil** del topbar. No hay login real.

## Arquitectura mock

- Modelos en `src/app/core/models`
- Contratos de servicio en `src/app/core/services`
- Semilla y reglas en `src/app/mocks`
- Persistencia: `EthicsStore` → `localStorage` clave `ethics-uss-store-v2`
- API futura prevista: `/api/v1/ethics`

Los componentes no importan los mocks de semilla para leer datos de negocio; pasan por servicios.

## Cómo reemplazar mocks por APIs

1. Mantener las interfaces de los servicios.
2. Cambiar la implementación para usar `HttpClient` contra `/api/v1/ethics`.
3. Quitar `EthicsStore` y la semilla.
4. Sustituir `DemoAuthService` por el auth compartido de OpenScience-USS.

## Internationalization

Runtime i18n (no rebuild, no route change) with a custom `I18nService` and an impure `translate` pipe. No extra npm package.

Available languages:

- Spanish (`es`) — default
- English (`en`)

Language resolution on start:

1. `localStorage` key `ethics-uss.language`
2. browser language (`es*` → `es`, `en*` → `en`)
3. fallback `es`

Missing keys fall back to Spanish. In development the service logs a warning. Raw keys are never shown.

Translation files:

- `src/app/core/i18n/es.json`
- `src/app/core/i18n/en.json`

Domain tree: `common`, `navigation`, `dashboard`, `projects`, `submission`, `forms`, `classification`, `committees`, `reviews`, `observations`, `sessions`, `resolutions`, `notifications`, `audit`, `validation`, `errors`, `catalogs`.

### How to add a translation

1. Add the same key to `es.json` and `en.json`.
2. Use `{{ 'domain.key' | translate }}` or `i18n.translate('domain.key', params)`.
3. Run `npm run check:ethics-i18n`.

### How to add a future language

1. Add the language to `APP_LANGUAGES` in `i18n.models.ts`.
2. Create `xx.json` with the same tree as `es.json`.
3. Register it in `CATALOGS` inside `i18n.service.ts`.
4. Add the option to `LanguageSwitcherComponent`.

### Form options

Institutional forms use `labelKey` / `helperTextKey`, never visible hardcoded labels.

- Question: `forms.newProject.questions.NP024.label`
- Option: `forms.newProject.questions.NP024.options.cec_surveys`
- Stored value remains the technical ID (`cec-surveys`, `AWARDED`, `CEC`)

Classification always compares IDs, never translated text.

### Catalogs

Roles, committees, statuses, decisions, session types and similar catalogs use a stable code plus `catalogs.*` keys.

Official names such as Universidad San Sebastián, DISEI, PURE and OpenScience-USS are not translated.

User-entered content (titles, comments, observations, resolution text, historical audit values) is never auto-translated.

To regenerate translation files after editing `scripts/build-i18n.mjs`:

```bash
node projects/ethics-uss/scripts/build-i18n.mjs
npm run check:ethics-i18n
```

English form wording is a prototype translation pending official DISEI validation.

## Decisiones pendientes

Ver `docs/prototype-pending-decisions.md`.

## Datos de ejemplo

1. Neurocognición 2027 — CEC aprobado, Bioseguridad en revisión
2. Modelo murino — CECUA con observaciones
3. Bienestar estudiantil — pendiente de clasificación DISEI
4. Rehabilitación cardiaca — aprobación próxima a vencer
5. Validación externa — antecedentes pendientes
