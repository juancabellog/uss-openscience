import {
  Callout,
  Card,
  CardBody,
  CardHeader,
  Divider,
  Grid,
  H1,
  H2,
  H3,
  Stack,
  Stat,
  Table,
  Text,
  TodoList,
  useHostTheme
} from "cursor/canvas";

export default function OpenscienceEventsArchitecture() {
  const theme = useHostTheme();

  return (
    <Stack gap={24}>
      <Stack gap={8}>
        <H1>OPENSCIENCE Events — arquitectura actualizada</H1>
        <Text tone="secondary">
          Propuesta para aprobación. No hay código ni SQL ejecutado. La matriz Excel no está en
          el workspace: el modelo usa las columnas declaradas (RUT, nombres, apellidos, sede,
          facultad/vicerrectoría, cargo, correo) y trata Empresa como candidato a correo hasta
          inspeccionar una planilla real.
        </Text>
      </Stack>

      <Callout tone="warning" title="Decisiones que no se revierten en v1">
        La matriz es una entidad institucional compartida, no una tabla del módulo de eventos.
        La inscripción pública no exige cuenta. La IA nunca confirma asistencia. PURE no se
        sobrescribe. Ausente de la matriz no implica persona externa.
      </Callout>

      <Grid columns={4} gap={12}>
        <Stat value="2" label="Frontends Angular" />
        <Stat value="1" label="Backend Open Liberty" />
        <Stat value="uss-ca" label="Catálogo MySQL" />
        <Stat value="FULL-only" label="Importación de matriz" />
      </Grid>

      <H2>Límites del sistema</H2>
      <Table
        headers={["Capa", "Qué es", "Qué no es"]}
        rows={[
          [
            "uss-ca / directorio USS",
            "Personas institucionales importadas desde Excel, con historial",
            "No es réplica de PURE ni fuente de Personas OPENSCIENCE"
          ],
          [
            "Módulo Events (Java)",
            "Eventos, inscripciones, actas, evidencias, asistencia",
            "No escribe en pure_person ni en Organizations"
          ],
          [
            "projects/openscience-events",
            "App pública de inscripción + consola del organizador",
            "No reutiliza el menú de OPENSCIENCE-USS"
          ],
          [
            "openscience-uss",
            "Puede consultar indicadores y, más adelante, el directorio",
            "No es el canal de inscripción pública"
          ]
        ]}
      />

      <H2>1. Modelo de datos</H2>
      <Text>
        Prefijo `uss_directory_*` para la matriz (compartida). Prefijo `event_*` para el
        dominio académico. Archivos binarios fuera de MySQL; la base solo guarda metadatos y
        rutas. Sin FK estricta hacia `pure_person`: resolución por UUID/id nullable, igual que
        Organizations.
      </Text>

      <H3>Directorio institucional (independiente de eventos)</H3>
      <Table
        headers={["Tabla", "Rol", "Identidad / notas"]}
        rows={[
          [
            "uss_directory_import",
            "Cada planilla cargada",
            "id, filename, sha256, imported_by, started/finished, status, counts"
          ],
          [
            "uss_directory_person",
            "Persona vigente de la matriz",
            "id local. Match operativo: email_normalized UNIQUE. rut_hmac UNIQUE nullable"
          ],
          [
            "uss_directory_person_email",
            "Correos adicionales",
            "email_normalized UNIQUE. Incluye Empresa si es un correo válido"
          ],
          [
            "uss_directory_person_history",
            "Versión por importación",
            "snapshot inmutable de campos visibles + import_id"
          ],
          [
            "uss_directory_person_pure_link",
            "Vínculo opcional a PURE",
            "directory_person_id + pure_person_uuid. Nunca escribe hacia PURE"
          ]
        ]}
        striped
      />

      <Card collapsible defaultOpen={false}>
        <CardHeader>Columnas de uss_directory_person</CardHeader>
        <CardBody>
          <Table
            headers={["Columna", "Origen", "Regla"]}
            rows={[
              ["id", "local", "PK"],
              ["email", "correo institucional", "original tal como vino"],
              ["email_normalized", "normalizado", "lower + trim + NFC. UNIQUE"],
              ["given_names", "nombres", "texto"],
              ["family_names", "apellidos", "texto"],
              ["campus", "sede", "texto; no se resuelve a Organization"],
              ["faculty_or_vr", "facultad/vicerrectoría", "texto de la planilla"],
              ["position_title", "cargo", "texto"],
              ["empresa_raw", "Empresa", "se conserva crudo"],
              ["rut_encrypted", "RUT", "cifrado; nunca en API pública"],
              ["rut_hmac", "RUT", "HMAC para match; no reversible"],
              ["pure_person_uuid", "link opcional", "nullable, sin FK destructiva"],
              ["active_in_directory", "censo de import FULL", "0 si falta en una carga completa exitosa"],
              ["last_seen_import_id", "importación", "equivalente a last_seen_sync_id"],
              ["created_at / updated_at", "local", "auditoría de fila"]
            ]}
          />
          <Text size="small" tone="secondary">
            RUT no se usa como identidad pública. Nombre, cargo y sede tampoco. Si una fila no
            tiene correo usable, queda en cola de revisión del import y no entra al catálogo
            operativo.
          </Text>
        </CardBody>
      </Card>

      <H3>Eventos, inscripción y asistencia</H3>
      <Table
        headers={["Tabla", "Rol", "Claves"]}
        rows={[
          ["event", "Ficha del evento", "id, public_slug UNIQUE, publication_status, registration_open"],
          ["event_registration", "Inscripción", "id, public_code UNIQUE, event_id+seq_no UNIQUE"],
          ["event_attendance", "Estado de asistencia 1:1 con inscripción", "registration_id UNIQUE"],
          ["event_document", "Evidencia (acta firmada, imagen, CSV)", "event_id, storage_key, kind"],
          ["event_generated_artifact", "PDF de lista generado", "event_id, version, storage_key"],
          ["event_attendance_import", "Lote de resultados propuesto", "event_id, document_id, status"],
          ["event_attendance_import_row", "Fila propuesta", "import_id, proposed_status, match_status"],
          ["event_email_challenge", "Verificación de correo", "token_hash, email_normalized, expires_at"],
          ["event_verified_session", "Sesión de correo ya verificado", "session_hash, email_normalized, expires_at"]
        ]}
        striped
      />

      <Callout tone="info" title="Columna Empresa">
        Se persiste como `empresa_raw`. Si el valor normalizado parece un correo, se agrega a
        `uss_directory_person_email` como secundario. No se interpreta como razón social. Si al
        inspeccionar la planilla resulta ser otra cosa, se deja de indexar como email y se
        mantiene el crudo.
      </Callout>

      <H2>2. Flujo de inscripción pública</H2>
      <Text>
        Sin cuenta. El correo se verifica antes de consultar la matriz. No hay búsqueda pública
        de personas por correo no verificado.
      </Text>
      <Table
        headers={["Paso", "Actor", "Qué ocurre", "Qué no ocurre"]}
        rows={[
          ["1", "Visitante", "Abre URL pública del evento", "No ve listados de inscritos"],
          ["2", "Visitante", "Ingresa correo", "No se consulta la matriz todavía"],
          ["3", "Plataforma", "Envía enlace de un solo uso", "El token no viaja en query de APIs de directorio"],
          ["4", "Visitante", "Abre el enlace; se crea sesión verificada", "No se crea usuario OPENSCIENCE"],
          ["5a", "Plataforma", "Match por email_normalized en directorio activo", "No se muestra RUT ni filas vecinas"],
          ["5b", "Visitante", "Si hay match: nombre, facultad, sede, cargo precargados y editables", "No se etiqueta como interna automáticamente más allá del origen MATRIX"],
          ["6", "Visitante", "Si no hay match: pide nombre completo, facultad/institución, cargo", "No se clasifica como EXTERNA"],
          ["7", "Visitante", "Confirma inscripción", "Snapshot de datos al momento de confirmar"],
          ["8", "Plataforma", "Guarda inscripción PENDIENTE de asistencia y envía confirmación", "No marca ASISTIO"]
        ]}
      />
      <Text size="small" tone="secondary">
        Si ya existe `event_verified_session` vigente para ese correo, se omite el reenvío.
        Si la persona está autenticada en OPENSCIENCE-USS con email verificado, se reutiliza
        ese correo y se salta el desafío, sin exigir rol de organizador.
      </Text>
      <Text>
        `affiliation_origin`: `DIRECTORY` | `SELF_DECLARED` | `WALK_IN`. Es procedencia del
        dato, no un censo de internos/externos.
      </Text>

      <H2>3. Carga y actualización de la matriz USS</H2>
      <Table
        headers={["Etapa", "Regla"]}
        rows={[
          ["Carga", "Solo rol con ADMINISTRAR el directorio. Se guarda el archivo original fuera de MySQL."],
          ["Parseo", "Mapeo por encabezados, no por posición. Filas sin correo usable → REVIEW_QUEUE."],
          ["Normalización", "email: trim, lower, NFC. RUT: dígito verificador, hmac + cifrado. Nombres: trim/NFC."],
          ["Match", "1) email_normalized 2) rut_hmac 3) si ambos conflictúan → fila ambigua, no upsert automático."],
          ["Diff", "created / updated / unchanged / reactivated / marked inactive. Orden de columnas irrelevante."],
          ["Inactivos", "Solo al terminar una importación FULL exitosa. Falla o cancelación no marca inactivos."],
          ["PURE", "El import no toca pure_person. El link opcional se calcula aparte y es nullable."],
          ["Historial", "Cada import deja snapshot en uss_directory_person_history y un registro de auditoría."]
        ]}
      />
      <Callout tone="warning" title="RUT">
        No se expone en Events, ni en Excel de inscritos por defecto, ni en APIs públicas. El
        organizador trabaja con nombre, correo, facultad, sede y cargo. El RUT queda en el
        directorio para trazabilidad y match interno.
      </Callout>

      <H2>4. Diseño del PDF de asistencia</H2>
      <Grid columns={2} gap={16}>
        <Stack gap={8}>
          <Text weight="semibold">Contenido fijo</Text>
          <Text>
            Encabezado institucional USS. Nombre del evento. Fecha y horario. Modalidad y
            lugar/sede. Unidad organizadora. Identificador del evento y versión del PDF
            (`event_id` + `artifact_version`).
          </Text>
        </Stack>
        <Stack gap={8}>
          <Text weight="semibold">Listado numerado</Text>
          <Text>
            N° de lista (estable por evento, `seq_no`). Código de inscripción (`public_code`).
            Nombre completo. Correo si `event.include_email_on_pdf`. Facultad/institución.
            Cargo si corresponde. Columna Firma. Filas en blanco al final: 10 por defecto,
            configurable en el evento (`blank_signature_rows`).
          </Text>
        </Stack>
      </Grid>
      <Text size="small" tone="secondary">
        El PDF se genera bajo demanda o al cerrar inscripciones, se versiona y se guarda como
        `event_generated_artifact.kind = ATTENDANCE_LIST_PDF`. Reimprimir sin cambios de
        inscritos reutiliza la versión; un alta posterior genera versión N+1 y no reescribe la
        anterior.
      </Text>

      <H2>5. Documentos de evidencia</H2>
      <Table
        headers={["Tipo", "Quién lo crea", "Almacenamiento"]}
        rows={[
          ["ATTENDANCE_LIST_PDF", "sistema", "privado; metadatos + hash + version"],
          ["SIGNED_ACTA_PDF", "organizador (carga)", "privado; múltiples por evento"],
          ["SIGNED_ACTA_IMAGE", "organizador (carga)", "jpg/png/webp; múltiples"],
          ["ATTENDANCE_RESULT_FILE", "IA externa o planilla manual", "csv/xlsx del formato canónico"],
          ["OTHER", "organizador", "respaldo adicional"]
        ]}
      />
      <Text>
        Los binarios viven en un directorio o object store privado, no en BLOB MySQL. Cada
        fila tiene `storage_key`, `sha256`, `content_type`, `byte_size`, `uploaded_by`,
        `created_at`. Consulta y descarga solo con permiso de organizador. No hay URL pública
        adivinable.
      </Text>

      <H2>6. Formato de importación de asistencia</H2>
      <Text>
        El mismo archivo sirve para Fase 1 (IA externa / revisión manual) y Fase 2 (Ollama).
        Extensión CSV UTF-8 o XLSX. Una fila por persona observada o propuesta.
      </Text>
      <Table
        headers={["Columna", "Obligatoria", "Uso"]}
        rows={[
          ["event_slug o event_id", "sí", "debe coincidir con el evento destino"],
          ["registration_public_code", "si existe", "match exacto y preferente"],
          ["seq_no", "no", "número de lista del PDF"],
          ["email", "si no hay código", "normalizado para match"],
          ["full_name", "sí si es walk-in o ambiguo", "ayuda a desambiguar"],
          ["proposed_status", "sí", "PENDIENTE | ASISTIO | NO_ASISTIO | REVISAR"],
          ["confidence", "no", "0–1; informativo, no confirma"],
          ["source_page", "no", "página o recorte de acta"],
          ["notes", "no", "texto libre del revisor o del modelo"]
        ]}
        striped
      />
      <Callout tone="danger" title="La IA no cierra el estado">
        El import crea filas `PROPOSED`. El estado vigente de `event_attendance` no cambia
        hasta que un usuario autorizado confirma el lote. Mientras exista un documento de
        acta en revisión, el sistema no marca NO_ASISTIO al resto del padrón.
      </Callout>

      <H2>7. Interfaz de revisión y confirmación</H2>
      <Table
        headers={["Vista", "Contenido"]}
        rows={[
          ["Resumen del lote", "filas OK, ambiguas, no encontradas, walk-in sugeridos, conflictos"],
          ["Coincidencia exacta", "código de inscripción o email único → aplicar ASISTIO/NO_ASISTIO propuesto"],
          ["Ambiguas", "mismo nombre / email parecido / seq_no cruzado; el organizador elige o descarta"],
          ["No encontradas", "crear walk-in, ignorar, o pedir más datos"],
          ["Confirmar", "botón explícito; guarda validator_user_id, validated_at, document_ids"]
        ]}
      />
      <Text>
        Walk-in: crea `event_registration` con `affiliation_origin = WALK_IN`, asigna el
        siguiente `seq_no`, y deja asistencia ASISTIO solo tras la confirmación del lote (o
        tras alta manual autorizada en sala).
      </Text>

      <H2>8. Vinculación con PURE</H2>
      <Table
        headers={["Regla", "Detalle"]}
        rows={[
          ["Dirección del dato", "Matriz y Events leen PURE. Nunca escriben Personas, orgs ni emails PURE."],
          ["Match", "email_normalized vs pure_person.email; si hay identificador nacional en PURE, rut_hmac."],
          ["Persistencia", "uss_directory_person_pure_link y, opcional, event_registration.pure_person_uuid."],
          ["Si no hay match", "la inscripción sigue siendo válida. No se fuerza EXTERNA."],
          ["Si hay match posterior", "se enlaza por UUID; no hace falta re-inscribir ni re-sincronizar Personas."],
          ["Organizations", "facultad/sede de la matriz son texto institucional, no UUID PURE, hasta un cruce futuro explícito."]
        ]}
      />

      <H2>9. Indicadores</H2>
      <Text>
        Se calculan sobre el evento y, aparte, sobre un rango de eventos. Personas únicas se
        identifican por `email_normalized` verificado; si falta correo (walk-in), por
        `registration_id` y no se fusionan por nombre.
      </Text>
      <Table
        headers={["Indicador", "Definición", "Excluye"]}
        rows={[
          ["Inscritos", "registros confirmados del evento, incluidos walk-in ya creados", "desafíos de email no confirmados"],
          ["Asistentes", "attendance_status = ASISTIO confirmado", "propuestas de IA no confirmadas"],
          ["Ausentes", "NO_ASISTIO confirmado", "PENDIENTE y cualquier evento con actas sin cerrar"],
          ["Por revisar", "REVISAR o match ambiguo abierto", "filas ya confirmadas"],
          ["Personas únicas", "distinct email_normalized en inscritos del conjunto", "duplicados del mismo correo"],
          ["Tasa de asistencia", "asistentes / inscritos con estado cerrado", "eventos con padrón aún PENDIENTE"]
        ]}
        striped
      />
      <Callout tone="info" title="No hay KPI institucional inventado">
        No se publican tasas de internos vs externos mientras `affiliation_origin` no sea un
        censo cerrado. DIRECTORY significa “estaba en la matriz”, no “es académico USS según
        PURE”.
      </Callout>

      <H2>10. Plan incremental</H2>
      <TodoList
        todos={[
          {
            id: "p0",
            content:
              "Fase 0 — Directorio USS: SQL propuesto, import Excel, historial, RUT cifrado, RBAC. Sin Events.",
            status: "pending"
          },
          {
            id: "p1",
            content: "Fase 1 — Alta de eventos, slug público, estados de publicación, Angular events.",
            status: "pending"
          },
          {
            id: "p2",
            content: "Fase 2 — Inscripción pública: email, verificación, precarga matriz, confirmación.",
            status: "pending"
          },
          {
            id: "p3",
            content: "Fase 3 — PDF de acta versionado + filas en blanco configurables + almacenamiento.",
            status: "pending"
          },
          {
            id: "p4",
            content: "Fase 4 — Carga de evidencias (PDF/imagen), consulta y descarga.",
            status: "pending"
          },
          {
            id: "p5",
            content:
              "Fase 5 — Import CSV/XLSX de asistencia, preview, ambiguos, confirmación humana, walk-in.",
            status: "pending"
          },
          {
            id: "p6",
            content: "Fase 6 — Indicadores de inscritos/asistentes/ausentes/únicos. Sin DQ nueva.",
            status: "pending"
          },
          {
            id: "p7",
            content: "Fase 7 — Link opcional a pure_person por email. Solo lectura.",
            status: "pending"
          },
          {
            id: "p8",
            content:
              "Fase 8 — Conector Ollama/multimodal que emite el mismo CSV. Sigue exigiendo confirmación.",
            status: "pending"
          }
        ]}
      />

      <Divider />
      <H2>RBAC y superficies</H2>
      <Table
        headers={["Superficie", "Módulo", "Permiso"]}
        rows={[
          ["Inscripción pública / verificar email", "público limitado", "sin cuenta; rate-limit"],
          ["Crear/editar evento, PDF, evidencias", "EVENTS", "ADMINISTER"],
          ["Ver inscritos e indicadores del evento", "EVENTS", "CONSULT"],
          ["Confirmar asistencia de un lote", "EVENTS", "APPROVE (o ADMINISTER si aún no se separa)"],
          ["Importar matriz USS", "DIRECTORY (nuevo, compartido)", "ADMINISTER"],
          ["Consultar directorio (sin RUT)", "DIRECTORY", "CONSULT; no expuesto en la app pública"]
        ]}
      />
      <Text size="small" tone="secondary" style={{ color: theme.text.tertiary }}>
        Angular workspace ya define newProjectRoot = projects, así que
        projects/openscience-events encaja sin un segundo repo. El backend Java del módulo
        Events vive junto a OpenLiberty, paquete separado (cl.uss.ca.events), reutilizando
        auth, auditoría y JDBC existentes.
      </Text>
    </Stack>
  );
}
