# Reglas del formulario Nuevo Proyecto

Fuente: hoja «Reglas Forms Nuevo Proyecto» de `Condiconaes Plataforma DISEI.xlsx`.

La definición vive en `NewProjectFormDefinition` (`projects/ethics-uss/src/app/core/forms/new-project-form.definition.ts`). La visibilidad condicional está en `form-rules.ts` (`formative-ir` y `supervised-ir`). No hay un `@if` por pregunta en la plantilla.

Todas las preguntas son obligatorias cuando están visibles. La clasificación de P24 sigue en `EthicsClassificationService` / `classifyStudyObjects`. El resultado preliminar no se muestra al investigador.

| Id | Tipo | Obligatoria | Despliegue |
| --- | --- | --- | --- |
| P1 | Texto | Sí | Siempre. Título de la propuesta. |
| P2 | Selección única | Sí | Siempre. Fuente de financiamiento, incluida la grafía «FONDNECYT Postdoctoral». |
| P3 | Selección única | Sí | Siempre. Adjudicado, En postulación, No aplica. |
| P4 | Texto | Sí | Siempre. Nombre del IR. Precargable. |
| P5 | Texto | Sí | Siempre. Apellidos del IR. Precargable. |
| P6 | Texto | Sí | Siempre. Correo del IR. Precargable. |
| P7 | Selección única | Sí | Siempre. Sede/campus. Catálogo de 8 sedes. |
| P8 | Selección única | Sí | Siempre. Facultad/Unidad. El Excel duplica «Economía, Negocios y Gobierno»; el catálogo la muestra una vez. |
| P9 | Texto | Sí | Siempre. Carrera, programa u otro. Precargable si existe. |
| P10 | Selección única | Sí | Siempre. Tipo de cargo. Dispara P11–P17. |
| P11 | Texto | Sí si visible | P10 es estudiante de pregrado, magíster, doctorado o postdoctorante. |
| P12 | Texto | Sí si visible | Misma condición que P11. |
| P13 | Texto | Sí si visible | Misma condición que P11. |
| P14 | Selección única | Sí si visible | Misma condición que P11. Mismo catálogo de sedes que P7. |
| P15 | Selección única | Sí si visible | Misma condición que P11. Catálogo de facultad. |
| P16 | Selección única | Sí si visible | P10 es estudiante, postdoctorante, asistente de investigación o personal técnico. Opciones: Docente, Docente Investigador, Investigador. |
| P17 | Selección única | Sí si visible | Misma condición que P16. Encaje de la propuesta respecto del tutor. |
| P18 | Selección múltiple | Sí | Siempre. Tipo de actividad. |
| P19 | Selección única | Sí | Siempre. La etiqueta visible sigue «Es un proyecto original». El Excel dice «orginal». |
| P20 | Texto | Sí | Siempre. Lugar de ejecución en la USS. |
| P21 | Texto | Sí | Siempre. Lugar externo. |
| P22 | Selección múltiple | Sí | Siempre. Quién ejecuta el proyecto. |
| P23 | Selección múltiple | Sí | Siempre. Productos esperados. |
| P24 | Selección múltiple | Sí | Siempre. Objetos/sujetos de estudio. Alimenta la clasificación existente. Las dos filas «Bases de datos de dominio público» se conservan. |
| P25 | Declaración | Sí | Siempre. Veracidad de la información. |
| P26 | Declaración | Sí | Siempre. Declaración de Singapur, con enlace a `/declaracion-singapur.pdf`. |

P4–P10 se precargan desde `GET /api/v1/ethics/me/profile` solo si el dato existe y el campo sigue vacío y sin edición. Sede, facultad y programa quedan vacíos cuando PURE no los tiene. El patrocinante (P11–P16) no se precarga con el usuario autenticado.
