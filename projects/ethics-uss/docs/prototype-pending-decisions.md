# Decisiones pendientes del prototipo

Este archivo registra inconsistencias e inferencias. No se corrigieron en silencio ni se inventaron reglas institucionales.

## 1. Bases de datos de dominio público (duplicada)

En la pregunta 24 del formulario Nuevo Proyecto, el Excel oficial lista dos veces:

> Bases de datos de dominio público

El prototipo **conserva ambas alternativas** (`cec-public-db-1` y `cec-public-db-2`) para no ocultar la inconsistencia.

Un levantamiento funcional previo usaba «Bases de datos no públicas» como disparador del Comité Ético Científico. Esa corrección silenciosa **ya no se aplica**.

Pendiente: confirmar si una de las dos filas debiera ser «Bases de datos no públicas», si ambas son públicas, y qué comité dispara cada una.

## 2. FONDECYT / FONDNECYT Postdoctoral

La hoja «Reglas Forms Nuevo Proyecto» escribe «FONDNECYT Postdoctoral».

**Nuevo Proyecto conserva esa grafía** como id y etiqueta. No se corrigió a «FONDECYT».

Pendiente: confirmar que el typo del documento fuente no corresponde a un instrumento distinto.

## 3. Facultad/Unidad repetida

El Excel de Facultad/Unidad repite «Economía, Negocios y Gobierno».

**El prototipo muestra la facultad una sola vez.** No se duplicó la opción en la UI.

Pendiente: confirmar el catálogo institucional de facultades/unidades.

## 4. Normalizaciones tipográficas

Se normalizó sin cambiar el sentido:

- «Centro de Estudios Cientificos» → «Centro de Estudios Científicos»
- Mayúsculas de «Comité Institucional de Ética en Cuidado y Uso de Animales»

Pendiente: confirmar textos oficiales de sedes y nombres de comités.

## 5. Obligatoriedad de campos

Las 26 preguntas de Nuevo Proyecto quedan obligatorias cuando están visibles. P11–P17 solo se exigen si su condición de despliegue se cumple.

Pendiente: confirmar la obligatoriedad definitiva de las 19 preguntas de Validación de Proyecto.

## 6. Patrocinante o tutor

`form-rules.ts` separa dos condiciones, como la hoja del Excel:

- P11–P15: estudiante de pregrado, magíster, doctorado o postdoctorante;
- P16 y P17: esa misma lista, más asistente de investigación y personal técnico.

La condición del Excel escribe «Estudiante de magíster», «Asistente de investigación» y «Personal técnico». El catálogo de P10 guarda «Estudiante de magister», «Asistente de Investigación» y «Personal Técnico». La visibilidad usa el catálogo ya almacenado. También se reportan, sin corregir, «Asistente  de Ivestigación» e «investigador» en minúscula en la celda de opciones.

No hay buscador PURE para el patrocinante. Sigue siendo ingreso manual.

Pendiente: confirmar los textos oficiales de P10 y si el tutor debe buscarse en PURE.

## 7. Clasificación de la pregunta 24

El motor mock solo usa los objetos/sujetos de estudio. Las dos alternativas «Bases de datos de dominio público» quedaron agrupadas visualmente en CEC, igual que el bloque 1-9 del Excel. No está confirmado que bases públicas deban derivar a CEC.

Pendiente: confirmar reglas extra basadas en título, tipo de proyecto, fuente de financiamiento o validación externa.

## 8. Modelo de respuestas (deuda técnica)

A partir de `NEW_PROJECT_V1` / `VALIDATION_V1` cada solicitud guarda:

- `formVersion`
- `answers` por `QuestionId` (`NP-001` …)
- `payload` plano por `fieldKey` (`title`, `irName`, …)

El `payload` se mantiene como adapter temporal para no romper servicios existentes (`EthicsSubmissionService`, semilla previa).

Pendiente: retirar el payload plano cuando el backend real use `answers` versionados.

## 9. Internacionalización

- Las traducciones al inglés de formularios institucionales requieren validación oficial por DISEI antes de pasar a producción.
- Definir si resoluciones futuras deben poder emitirse bilingües.
- Definir idioma de correos oficiales.
- Definir si la preferencia de idioma se almacenará posteriormente en perfil USS/backend.
- Definir idioma aplicable a documentos institucionales descargables.

El prototipo no traduce automáticamente el contenido de resoluciones ya emitidas ni el texto libre ingresado por usuarios. El documento imprimible de resolución permanece en español.

## 10. Otros pendientes de producto

- Administración dinámica de versiones de formulario.
- Administración avanzada del motor de reglas.
- Renovación, enmienda y seguimiento (solo previstos como «Próximamente»).
- Cierre automático por vencimiento.
- Firma electrónica real y correlativo institucional definitivo.

La nota anterior sobre enmienda y seguimiento quedó superada por la iteración DISEI de octubre 2026: el prototipo ya recorre ambos flujos. La nomenclatura oficial de archivos sigue pendiente (I-17 / I-18).

## 11. Observaciones DISEI que no se cierran en esta iteración

### A-03 — Pendiente de aclaración

El documento plantea un posible tratamiento en dos líneas y, a la vez, se solapa con A-05. No se implementó una solución de dos líneas. El listado de expedientes usa una fila por comité (A-05). Falta confirmar si A-03 exige algo adicional.

### R-01 — Pendiente de aclaración

El documento indica eliminar el indicador «Pendientes» del miembro de comité y también lo deja por confirmar. El indicador se mantiene. No se agregó funcionalidad nueva asociada.

### I-01 — Pendiente de aclaración

La pregunta institucional sobre el patrocinante sigue abierta en los textos de P10. La visibilidad ya no es una sola lista: P11–P15 usan la condición formativa y P16–P17 la condición más amplia.

### I-14 — Formato Documentos Tipo descargable — Pendiente de aclaración

No hay definición suficiente para un catálogo descargable de formatos tipo. No se inventó ese catálogo.

### R-04 — Pendiente de insumo

Los formularios oficiales de revisión por comité no fueron entregados. Se conservan los campos provisionales Metodología, Observaciones y Riesgos y resguardos, con identificadores `CEC_REVIEW_V1`, `CECUA_REVIEW_V1` y `BIOSECURITY_REVIEW_V1`.

### I-12 — Pendiente de insumo

El PDF oficial de observaciones, distinto por comité, no fue entregado. La vista previa usa la plantilla interna `PROTOTYPE_PDF_TEMPLATE` y un aviso de que no es el formato oficial.

## 12. Reasignación en etapa avanzada

Majo confirmó dos capabilities distintas.

`REASSIGN_REVIEWER` es la reasignación ordinaria, mientras la revisión sigue en su etapa operativa.

`REASSIGN_REVIEWER_ADVANCED_STAGE` es exclusiva de Presidencia. Se usa cuando la asignación ya está `COMPLETED`, cuando esa revisión ya fue enviada, o cuando el expediente está en observaciones, respuesta del investigador, preparación de resolución o validación de presidencia. Administración, secretaría, revisor e investigador no la tienen.

La pantalla sigue teniendo una sola acción, «Cambiar revisor». Si la etapa es avanzada y la persona tiene la capability, se muestra el aviso de que las actuaciones anteriores se conservan. El motivo sigue siendo obligatorio. La asignación anterior no se borra ni cambia de revisor: queda con `supersededAt`. La nueva ocupa el mismo slot y la misma ronda. Un expediente cerrado no se reabre.

## 13. Rol Miembro de Comité

`COMMITTEE_MEMBER` fue eliminado por definición funcional. La evaluación de proyectos se realiza mediante `REVIEWER`, asignado contextualmente mediante `ReviewerAssignment`. No se mantiene un perfil «Miembro + Revisor».

## 14. Precarga institucional

`GET /api/v1/ethics/me/profile` devuelve nombre, apellidos y correo del usuario autenticado y, si PURE ya tiene la persona por correo, `first_name`, `last_name`, `email` y `cargo`. Sede, facultad y programa no están en `pure_person` y quedan vacíos. Angular no consulta PURE.

Si la persona del prototipo tiene `purePersonId`, el bloque de identidad del nuevo proyecto se precarga y queda en solo lectura, también cuando un dato viene vacío. Se muestra «No disponible». Una persona institucional sin ficha PURE conserva datos locales editables. Un revisor externo no abre un proyecto como investigador. El patrocinante y el tutor siguen en texto libre.

### I-17 / I-18 — Pendiente de insumo

No hay nomenclatura institucional de archivos ni de resoluciones de enmienda y seguimiento. El prototipo usa solo los códigos internos `AMENDMENT`, `FOLLOW_UP` y `CLOSURE_FOLLOW_UP`, sin presentar una nomenclatura inventada.

## 15. Plan de Gestión de Datos

El plan se identifica con el tipo documental `DATA_MANAGEMENT_PLAN`, no con el nombre del archivo. Es transversal a la solicitud y puede tener varias versiones. El modelo está en `docs/ethics-documents.md`.

Pendiente: definir con DISEI/Majo/Andrea si el Plan de Gestión de Datos es obligatorio y bajo qué condiciones. `SUBMIT_REQUEST` no se bloquea si falta.

