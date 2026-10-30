# Documentos de una solicitud

Ethics todavía no persiste documentos en una tabla propia. El prototipo guarda cada versión como una fila de `DocumentMetadata` en el almacén local. El tipo documental ya existía en `documentType`. No se creó otro sistema de clasificación.

El nombre del archivo se guarda para mostrarlo y para la auditoría. No identifica el documento.

## Plan de Gestión de Datos

El Plan de Gestión de Datos es un tipo documental transversal identificado mediante metadata estructurada:

`DATA_MANAGEMENT_PLAN`

y no mediante el nombre del archivo.

Pertenece a la solicitud. `ownerType` es `SUBMISSION` y `committeeType` queda vacío. No es un documento de CEC, CECUA ni Bioseguridad. Esas sedes siguen con sus propios requisitos.

Hay como máximo un documento lógico activo de ese tipo por solicitud. Su clave estable es el mismo id, `DATA_MANAGEMENT_PLAN`, en `requirementKey`. Cada carga nueva agrega una fila con `version` incrementada. La vigente es la de mayor `version`. Las anteriores conservan archivo, fecha, `uploadedBy` y el id de la fila. No se borran ni se pisan.

Un archivo cargado desde el bloque «Plan de Gestión de Datos» recibe el tipo aunque se llame `documento_final_2026.pdf`. Un archivo cargado como otro documento no lo recibe aunque se llame `Plan de Gestion de Datos.pdf`.

La consulta del prototipo es `DocumentService.dataManagementPlan(submissionId)`. En el backend, la misma regla está en `DataManagementPlanPolicy`: el canal de carga elige el tipo y el nombre solo se almacena. No hay endpoint nuevo porque no hay un recurso documental de Ethics al que filtrar.

## Permisos

Se reutilizan `VIEW_DOCUMENTS`, `DOWNLOAD_DOCUMENTS`, `UPLOAD_DOCUMENTS`, `UPLOAD_DOCUMENT_VERSION` y `VIEW_DOCUMENT_VERSIONS`. No hay capabilities propias del plan.

La carga sigue el cierre documental que ya existe: `caseDocumentsEditable`. Si los paquetes de la solicitud están cerrados y no hay una respuesta de investigador abierta, no aparece el botón de carga. Ver y descargar siguen las capabilities de documentos.

## Obligatoriedad

Pendiente: definir con DISEI/Majo/Andrea si el Plan de Gestión de Datos es obligatorio y bajo qué condiciones.

`SUBMIT_REQUEST` no exige el plan. No se bloquea el envío si falta.

## Demostración

No había un plan entre los documentos ya sembrados. Protocolos, cartas y resúmenes no se reclasificaron por nombre.

- `doc-pgd-v1` y `doc-pgd-v2` son el plan de `sub-1` (ETI-2026-001), versiones 1 y 2, con nombres `anexo_a.pdf` y `documento_final_2026.pdf`.
- `sub-2` (ETI-2026-002) no tiene plan. `doc-named-like-pgd` se llama «Plan de Gestion de Datos.pdf» y queda como adjunto genérico, sin `documentType`.

La clave local del prototipo es `ethics-uss-store-v11` para que esa semilla se cargue.
