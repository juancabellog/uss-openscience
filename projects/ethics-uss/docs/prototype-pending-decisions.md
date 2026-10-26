# Decisiones pendientes del prototipo

Este archivo registra inconsistencias e inferencias. No se corrigieron en silencio ni se inventaron reglas institucionales.

## 1. Bases de datos de dominio público (duplicada)

En la pregunta 24 del formulario Nuevo Proyecto, el Excel oficial lista dos veces:

> Bases de datos de dominio público

El prototipo **conserva ambas alternativas** (`cec-public-db-1` y `cec-public-db-2`) para no ocultar la inconsistencia.

Un levantamiento funcional previo usaba «Bases de datos no públicas» como disparador del Comité Ético Científico. Esa corrección silenciosa **ya no se aplica**.

Pendiente: confirmar si una de las dos filas debiera ser «Bases de datos no públicas», si ambas son públicas, y qué comité dispara cada una.

## 2. FONDECYT / FONDNECYT Postdoctoral

En Validación de Proyecto el archivo fuente escribe «FONDNECYT Postdoctoral».

**La UI muestra «FONDECYT Postdoctoral»**, reutilizando el catálogo de Nuevo Proyecto.

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

El prototipo marca con * un subconjunto (título, datos IR, declaraciones, acta/protocolo en validación, etc.).

Pendiente: confirmar la obligatoriedad definitiva de cada una de las 26 + 19 preguntas, incluyendo patrocinante, lugares de ejecución, tipo de actividad y productos.

## 6. Patrocinante o tutor

La visibilidad destacada usa `PROTOTYPE_RULE` en `form-rules.ts`:

- se destaca si el cargo del IR es estudiante, postdoctorante, asistente o personal técnico;
- en la vista DISEI las preguntas 11-16 siempre existen;
- si el cargo no sugiere tutor y no hay respuesta, se muestra «No aplica»;
- no es una regla institucional definitiva.

Pendiente: confirmar cuándo el tutor es obligatorio, opcional o no aplica.

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

La pregunta institucional sobre la regla que muestra «Patrocinante o tutor» sigue abierta. Se retiró de la interfaz el texto técnico `PROTOTYPE_RULE`. La visibilidad de la sección sigue la lista de I-02 (estudiante de pregrado, magíster, doctorado y postdoctorante).

### I-14 — Formato Documentos Tipo descargable — Pendiente de aclaración

No hay definición suficiente para un catálogo descargable de formatos tipo. No se inventó ese catálogo.

### R-04 — Pendiente de insumo

Los formularios oficiales de revisión por comité no fueron entregados. Se conservan los campos provisionales Metodología, Observaciones y Riesgos y resguardos, con identificadores `CEC_REVIEW_V1`, `CECUA_REVIEW_V1` y `BIOSECURITY_REVIEW_V1`.

### I-12 — Pendiente de insumo

El PDF oficial de observaciones, distinto por comité, no fue entregado. La vista previa usa la plantilla interna `PROTOTYPE_PDF_TEMPLATE` y un aviso de que no es el formato oficial.

### I-17 / I-18 — Pendiente de insumo

No hay nomenclatura institucional de archivos ni de resoluciones de enmienda y seguimiento. El prototipo usa solo los códigos internos `AMENDMENT`, `FOLLOW_UP` y `CLOSURE_FOLLOW_UP`, sin presentar una nomenclatura inventada.
