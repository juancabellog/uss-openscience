import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'app', 'core', 'i18n');

function set(tree, path, value) {
  const parts = path.split('.');
  let node = tree;
  for (let i = 0; i < parts.length - 1; i += 1) {
    node[parts[i]] ??= {};
    node = node[parts[i]];
  }
  node[parts[parts.length - 1]] = value;
}

function pair(esTree, enTree, path, es, en) {
  set(esTree, path, es);
  set(enTree, path, en);
}

const es = {};
const en = {};
const p = (path, spanish, english) => pair(es, en, path, spanish, english);

p('common.language', 'Idioma', 'Language');
p('common.dash', '—', '—');
p('common.select', 'Seleccione…', 'Select…');
p('common.cancel', 'Cancelar', 'Cancel');
p('common.save', 'Guardar', 'Save');
p('common.continue', 'Continuar', 'Continue');
p('common.back', 'Volver', 'Back');
p('common.open', 'Abrir', 'Open');
p('common.view', 'Ver', 'View');
p('common.delete', 'Eliminar', 'Remove');
p('common.required', '*', '*');
p('common.yes', 'Sí', 'Yes');
p('common.no', 'No', 'No');
p('common.na', 'No aplica', 'Not applicable');
p('common.noAnswer', 'Sin respuesta', 'No answer');
p('common.noTitle', 'Sin título', 'Untitled');
p('common.untitledRequest', 'Solicitud sin título', 'Untitled application');
p('common.selected', 'Seleccionado', 'Selected');
p('common.notSelected', 'No seleccionado', 'Not selected');
p('common.logout', 'Salir', 'Sign out');
p('common.switchProfile', 'Cambiar perfil', 'Switch profile');
p('common.prototype', 'Prototipo', 'Prototype');
p('common.load', 'carga', 'load');
p('common.of', 'de', 'of');

p('brand.name', 'Ética de Proyectos', 'Project Ethics');
p('brand.university', 'Universidad San Sebastián', 'Universidad San Sebastián');
p('brand.crumb', 'Plataforma de Ética · Prototipo', 'Ethics Platform · Prototype');
p('brand.full', 'Plataforma de Ética de Proyectos', 'Project Ethics Platform');

p('navigation.home', 'Inicio', 'Home');
p('navigation.applications', 'Solicitudes', 'Applications');
p('navigation.cases', 'Expedientes', 'Cases');
p('navigation.committees', 'Comités', 'Committees');
p('navigation.sessions', 'Sesiones', 'Sessions');
p('navigation.reviewers', 'Revisores', 'Reviewers');
p('navigation.expirations', 'Vencimientos', 'Expirations');
p('navigation.settings', 'Configuración', 'Settings');
p('navigation.audit', 'Auditoría', 'Audit');
p('navigation.myProjects', 'Mis proyectos', 'My projects');
p('navigation.newApplication', 'Nueva solicitud', 'New application');
p('navigation.notifications', 'Notificaciones', 'Notifications');
p('navigation.history', 'Historial', 'History');
p('navigation.assignedReviews', 'Revisiones asignadas', 'Assigned reviews');

p('auth.prototypeNote', 'Prototipo funcional · sin autenticación real', 'Functional prototype · no real authentication');
p('auth.selectProfile', 'Seleccione un perfil de demostración para recorrer la experiencia de cada rol.', 'Select a demonstration profile to explore each role.');

p('dashboard.home', 'Inicio', 'Home');
p('dashboard.myProjects', 'Mis proyectos', 'My projects');
p('dashboard.underReview', 'En evaluación', 'Under review');
p('dashboard.withComments', 'Con observaciones', 'With comments');
p('dashboard.activeApprovals', 'Aprobaciones vigentes', 'Active approvals');
p('dashboard.expiringSoon', 'Próximas a vencer', 'Expiring soon');
p('dashboard.newEthicsApplication', 'Nueva solicitud ética', 'New ethics application');
p('dashboard.project', 'Proyecto', 'Project');
p('dashboard.type', 'Tipo', 'Type');
p('dashboard.globalStatus', 'Estado general', 'Overall status');
p('dashboard.committees', 'Comités', 'Committees');
p('dashboard.viewProject', 'Ver proyecto', 'View project');
p('dashboard.assignments', 'Asignaciones', 'Assignments');
p('dashboard.pending', 'Pendientes', 'Pending');
p('dashboard.inReview', 'En revisión', 'Under review');
p('dashboard.goAssignedReviews', 'Ir a revisiones asignadas', 'Go to assigned reviews');
p('dashboard.pendingClassification', 'Solicitudes de certificación pendientes', 'Certification applications pending');
p('dashboard.admissibility', 'Admisibilidad', 'Admissibility');
p('dashboard.resolutions', 'Resoluciones', 'Resolutions');
p('dashboard.expiring', 'Próximos a vencer', 'Expiring soon');
p('dashboard.diseiApplications', 'Solicitudes DISEI', 'DISEI applications');
p('dashboard.cases', 'Expedientes', 'Cases');
p('dashboard.pendingAdmissibility', 'Pendientes de admisibilidad', 'Pending admissibility');
p('dashboard.pendingAssignment', 'Pendientes de asignación', 'Pending assignment');
p('dashboard.reviewsInProgress', 'Revisiones en curso', 'Reviews in progress');
p('dashboard.pendingSession', 'Pendientes de sesión', 'Pending session');
p('dashboard.pendingResolution', 'Pendientes de resolución', 'Pending resolution');
p('dashboard.openCaseInbox', 'Abrir bandeja de expedientes', 'Open case inbox');
p('dashboard.typeNew', 'Nuevo', 'New');
p('dashboard.typeValidation', 'Validación', 'Validation');

p('projects.title', 'Proyectos', 'Projects');
p('projects.emptyTitle', 'Sin proyectos', 'No projects');
p('projects.emptyMessage', 'Cree una solicitud para iniciar una evaluación.', 'Create an application to start a review.');
p('projects.viewProject', 'Ver proyecto', 'View project');
p('projects.ir', 'IR', 'PI');
p('projects.summary', 'Resumen', 'Summary');
p('projects.documents', 'Documentación', 'Documents');
p('projects.history', 'Historial', 'History');
p('projects.received', 'Ingreso', 'Received');
p('projects.expires', 'Vence', 'Expires');
p('projects.openCase', 'Abrir expediente', 'Open case');
p('projects.comingSoon', 'Próximamente: Enmienda · Renovación · Seguimiento', 'Coming soon: Amendment · Renewal · Follow-up');
p('projects.requirementsComplete', '{done} de {total} requisitos completos.', '{done} of {total} requirements complete.');
p('projects.notifyCommittee', 'Notificar comité', 'Notify committee');
p('projects.packageClosed', 'Paquete de antecedentes cerrado para esta ronda.', 'Supporting package closed for this round.');
p('projects.cycle', 'Ciclo {cycle}', 'Cycle {cycle}');
p('projects.semaphore', 'Semáforo: {semaphore} · {days} días', 'Status: {semaphore} · {days} days');
p('projects.expiredAlert', 'Aprobación vencida. 15 días para regularizar renovación (previsto, no automático).', 'Approval expired. 15 days to regularize renewal (planned, not automatic).');
p('projects.resolution', 'Resolución {correlative}', 'Resolution {correlative}');
p('projects.downloadResolution', 'Descargar resolución', 'Download resolution');
p('projects.committeeRequestedChanges', 'El comité ha solicitado modificaciones.', 'The committee has requested changes.');
p('projects.answerPlaceholder', 'Respuesta obligatoria', 'Required response');
p('projects.saveAnswer', 'Guardar respuesta', 'Save response');
p('projects.answered', 'Respondida', 'Answered');
p('projects.pending', 'Pendiente', 'Pending');
p('projects.sendAnswers', 'Enviar respuestas al comité', 'Send responses to the committee');
p('projects.reason', 'Motivo: {reason}', 'Reason: {reason}');
p('projects.notifyConfirm', 'Al enviar los antecedentes al comité se cerrará esta versión y no podrá modificar sus documentos hasta que exista una nueva ronda o devolución.', 'Sending the file to the committee will lock this version. You will not be able to change documents until a new round or return.');
p('projects.confirmSend', 'Confirmar envío', 'Confirm submission');
p('projects.timeline.application', 'Solicitud', 'Application');
p('projects.timeline.documents', 'Antecedentes', 'Documents');
p('projects.timeline.send', 'Envío', 'Submission');
p('projects.timeline.admissibility', 'Admisibilidad', 'Admissibility');
p('projects.timeline.review', 'Revisión', 'Review');
p('projects.timeline.comments', 'Observaciones', 'Comments');
p('projects.timeline.response', 'Respuesta', 'Response');
p('projects.timeline.session', 'Sesión', 'Session');
p('projects.timeline.resolution', 'Resolución', 'Resolution');

p('submission.choice.title', 'Nueva solicitud', 'New application');
p('submission.choice.intro', 'Seleccione el tipo de ingreso. Cada camino usa un formulario distinto.', 'Select the application type. Each path uses a different form.');
p('submission.choice.newTitle', 'Nuevo proyecto', 'New project');
p('submission.choice.newBody', 'Proyecto que inicia el proceso de evaluación ética USS.', 'A project that starts the USS ethics review process.');
p('submission.choice.validationTitle', 'Validación de proyecto', 'Project validation');
p('submission.choice.validationBody', 'Proyecto que cuenta con aprobaciones externas y requiere validación USS.', 'A project with external approvals that requires USS validation.');
p('submission.new.title', 'Nuevo proyecto', 'New project');
p('submission.new.tutorHighlight', 'Sección destacada por regla de prototipo (PROTOTYPE_RULE): el cargo del IR sugiere tutor.', 'Highlighted by a prototype rule (PROTOTYPE_RULE): the PI role suggests a tutor is needed.');
p('submission.new.tutorOptional', 'Esta sección no se destaca para el cargo seleccionado. Puede completar los datos si corresponde. En la revisión DISEI se mostrará «No aplica» si queda vacía.', 'This section is not highlighted for the selected role. You may still complete it if applicable. DISEI review will show “Not applicable” if it remains empty.');
p('submission.new.preliminary', 'Clasificación preliminar', 'Preliminary classification');
p('submission.new.selectForSuggestion', 'Seleccione al menos una alternativa para obtener una sugerencia.', 'Select at least one option to obtain a suggestion.');
p('submission.new.mayRequire', 'De acuerdo con la información entregada, este proyecto podría requerir evaluación por:', 'Based on the information provided, this project may require review by:');
p('submission.new.preliminaryNote', 'Esta clasificación es preliminar y será validada por DISEI.', 'This classification is preliminary and will be validated by DISEI.');
p('submission.new.suggested', 'Clasificación sugerida:', 'Suggested classification:');
p('submission.new.draftSaved', 'Borrador guardado.', 'Draft saved.');
p('submission.new.completeToSubmit', 'Complete el título y ambas declaraciones para enviar.', 'Complete the title and both declarations to submit.');
p('submission.new.saveDraft', 'Guardar borrador', 'Save draft');
p('submission.new.submit', 'Enviar solicitud', 'Submit application');
p('submission.validation.title', 'Validación de proyecto', 'Project validation');
p('submission.validation.needActa', 'Cada comité seleccionado requiere el acta de origen y el protocolo aprobado.', 'Each selected committee requires the origin minutes and the approved protocol.');
p('submission.validation.submit', 'Enviar solicitud', 'Submit application');
p('validation.transversal', 'Documentación transversal', 'Cross-cutting documentation');
p('validation.letter', 'Carta de solicitud de validación', 'Validation request letter');
p('validation.needLetter', 'Cargue la carta de solicitud de validación.', 'Upload the validation request letter.');
p('validation.needCommittee', 'Seleccione al menos un comité.', 'Select at least one committee.');
p('validation.removeCommittee', 'Hay documentos asociados a {committees}. Confirme para retirar esa asociación. La carta de solicitud se mantiene.', 'Documents are associated with {committees}. Confirm to remove that association. The request letter stays.');
p('validation.docs.acta', 'Acta/certificado de aprobación del comité de origen', 'Approval minutes or certificate from the committee of origin');
p('validation.docs.protocolo', 'Protocolo/documentación aprobada', 'Approved protocol or documentation');
p('validation.docs.adjudicacion', 'Carta de adjudicación, si corresponde', 'Award letter, if applicable');
p('validation.docs.seguimiento', 'Informe de seguimiento, si corresponde', 'Follow-up report, if applicable');
p('validation.docs.enmiendas', 'Enmiendas, si corresponde', 'Amendments, if applicable');
p('validation.docs.otros', 'Otros', 'Other');
p('cases.waitingResearcher', 'Esperando respuesta del investigador', 'Waiting for the investigator response');
p('cases.waitingReviews', 'Las revisiones de esta ronda aún no están completas.', 'The reviews for this round are not complete yet.');
p('cases.restricted', 'No tiene acceso a este expediente.', 'You do not have access to this case.');
p('submission.notFound', 'Solicitud no encontrada.', 'Application not found.');

p('readonly.type', 'Tipo', 'Type');
p('readonly.status', 'Estado', 'Status');
p('readonly.receivedDate', 'Fecha de ingreso', 'Submission date');
p('readonly.formReceived', 'Formulario recibido', 'Form received');
p('readonly.questionsAnswered', '{answered} de {total} preguntas respondidas', '{answered} of {total} questions answered');
p('readonly.newProjectType', 'Solicitud de Nuevo Proyecto', 'New project application');
p('readonly.validationType', 'Solicitud de Validación de Proyecto', 'Project validation application');
p('readonly.classified', 'Clasificada', 'Classified');
p('readonly.previewUnavailable', 'Vista previa no disponible en el prototipo (archivo mock).', 'Preview is not available in the prototype (mock file).');

p('forms.noDerivation', 'Sin derivación', 'No referral');
p('forms.newProject.title', 'Nuevo proyecto', 'New project');
p('forms.newProject.sections.A', 'Datos del proyecto', 'Project details');
p('forms.newProject.sections.B', 'Investigador responsable', 'Principal investigator');
p('forms.newProject.sections.C', 'Patrocinante o tutor', 'Sponsor or tutor');
p('forms.newProject.sections.D', 'Características del proyecto', 'Project characteristics');
p('forms.newProject.sections.E', 'Equipo y resultados', 'Team and outputs');
p('forms.newProject.sections.F', 'Metodología', 'Methodology');
p('forms.newProject.sections.G', 'Declaraciones', 'Declarations');
p('forms.validation.title', 'Validación de proyecto', 'Project validation');
p('forms.validation.sections.A', 'Investigador responsable', 'Principal investigator');
p('forms.validation.sections.B', 'Proyecto', 'Project');
p('forms.validation.sections.C', 'Comités a validar', 'Committees to validate');
p('forms.validation.sections.D', 'Aprobación externa', 'External approval');
p('forms.validation.sections.E', 'Documentación', 'Documentation');

const np = {
  NP001: {
    label: ['Título de la Propuesta', 'Proposal title'],
    help: ['El mismo título debe ser utilizado para todos los procesos con todos los comités.', 'The same title must be used for every process with every committee.']
  },
  NP002: { label: ['Tipo de proyecto o fuente de financiamiento', 'Project type or funding source'] },
  NP003: { label: ['Estado del financiamiento', 'Funding status'] },
  NP004: { label: ['Nombre Responsable de la ejecución del Proyecto (IR)', 'First name of the Principal Investigator (PI)'] },
  NP005: { label: ['Apellidos Responsable de la ejecución del proyecto (IR)', 'Last name of the Principal Investigator (PI)'] },
  NP006: { label: ['Correo Electrónico Responsable de la ejecución del proyecto (IR)', 'Email of the Principal Investigator (PI)'] },
  NP007: { label: ['Sede - Campus USS donde se ejecuta', 'USS campus where the project is carried out'] },
  NP008: { label: ['Facultad/Unidad', 'Faculty/Unit'] },
  NP009: {
    label: ['Carrera, programa, otro', 'Program, degree or other'],
    help: ['Si no aplica, indicar N/A.', 'If not applicable, enter N/A.']
  },
  NP010: { label: ['Tipo de Cargo', 'Position type'] },
  NP011: { label: ['Nombre del patrocinante o tutor', 'Sponsor or tutor first name'] },
  NP012: { label: ['Apellidos del patrocinante o tutor', 'Sponsor or tutor last name'] },
  NP013: { label: ['Correo electrónico del patrocinante o tutor', 'Sponsor or tutor email'] },
  NP014: { label: ['Sede - Campus USS del patrocinante o tutor', 'USS campus of the sponsor or tutor'] },
  NP015: { label: ['Facultad/Unidad patrocinante o tutor', 'Faculty/Unit of the sponsor or tutor'] },
  NP016: { label: ['Tipo de cargo patrocinante o tutor', 'Position type of the sponsor or tutor'] },
  NP017: { label: ['Esta propuesta', 'This proposal'] },
  NP018: {
    label: ['Tipo de Actividad', 'Activity type'],
    help: ['Marque todas las opciones que corresponda.', 'Select all that apply.']
  },
  NP019: { label: ['Esta actividad', 'This activity'] },
  NP020: {
    label: ['Indique el lugar de ejecución en la USS', 'Indicate the execution site at USS'],
    help: ['Dónde se obtendrán datos.', 'Where data will be obtained.']
  },
  NP021: {
    label: ['Indique el lugar externo de ejecución, no USS', 'Indicate the external execution site, not USS'],
    help: ['Dónde se obtendrán datos.', 'Where data will be obtained.']
  },
  NP022: {
    label: ['El proyecto será ejecutado por', 'The project will be carried out by'],
    help: ['Marque todas las opciones que corresponda.', 'Select all that apply.']
  },
  NP023: {
    label: ['Producto(s) Esperado(s)', 'Expected product(s)'],
    help: ['Marque todas las opciones que corresponda.', 'Select all that apply.']
  },
  NP024: {
    label: ['La metodología de la propuesta incluye los siguientes Objetos/Sujetos de estudio', 'The proposal methodology includes the following study objects/subjects'],
    help: ['Marque todas las opciones que corresponda.', 'Select all that apply.']
  },
  NP025: { label: ['Declaro que la información incluida en este formulario es veraz y corresponde a la totalidad de los antecedentes respecto de la actividad identificada.', 'I declare that the information in this form is truthful and covers all background information for the identified activity.'] },
  NP026: { label: ['Declaro conocer y adscribir a la Declaración de Singapur sobre Integridad en la Investigación.', 'I declare that I know and adhere to the Singapore Statement on Research Integrity.'] }
};

for (const [id, item] of Object.entries(np)) {
  p(`forms.newProject.questions.${id}.label`, item.label[0], item.label[1]);
  if (item.help) {
    p(`forms.newProject.questions.${id}.help`, item.help[0], item.help[1]);
  }
}

const npOptions = {
  NP002: [
    ['ANID_Anillo', 'ANID Anillo', 'ANID Anillo'],
    ['ANID_Basal', 'ANID Basal', 'ANID Basal'],
    ['ANID_Desafios_Publicos', 'ANID Desafíos Públicos', 'ANID Public Challenges'],
    ['Centros_FONDAP', 'Centros FONDAP', 'FONDAP Centers'],
    ['CORFO', 'CORFO', 'CORFO'],
    ['FONDECYT_Exploracion', 'FONDECYT Exploración', 'FONDECYT Exploration'],
    ['FONDECYT_Iniciacion', 'FONDECYT Iniciación', 'FONDECYT Initiation'],
    ['FONDECYT_Postdoctoral', 'FONDECYT Postdoctoral', 'FONDECYT Postdoctoral'],
    ['FONDECYT_Regular', 'FONDECYT Regular', 'FONDECYT Regular'],
    ['FONDEF_IDeA_I_D', 'FONDEF IDeA I+D', 'FONDEF IDeA R&D'],
    ['FONDEF_IDeA_I_T', 'FONDEF IDeA I+T', 'FONDEF IDeA I+T'],
    ['FONDEF_VIU', 'FONDEF VIU', 'FONDEF VIU'],
    ['FONIS', 'FONIS', 'FONIS'],
    ['Gobierno_Regional', 'Gobierno Regional', 'Regional Government'],
    ['Internacional', 'Internacional', 'International'],
    ['Nucleos_Milenio', 'Núcleos Milenio', 'Millennium Nuclei'],
    ['Fondos_Interno_USS', 'Fondos Interno USS', 'USS Internal Funds'],
    ['Fondos_Interno_USS_Inteligencia_Artificial', 'Fondos Interno USS: Inteligencia Artificial', 'USS Internal Funds: Artificial Intelligence'],
    ['Fondo_Interno_USS_Proyectos_de_Vinculacion_con_el_Medio_VcM', 'Fondo Interno USS: Proyectos de Vinculación con el Medio (VcM)', 'USS Internal Fund: Community Engagement Projects (VcM)'],
    ['Fondo_Interno_USS_Innovacion_e_Investigacion_en_Docencia_Universitaria_CIES', 'Fondo Interno USS: Innovación e Investigación en Docencia Universitaria (CIES)', 'USS Internal Fund: Innovation and Research in University Teaching (CIES)'],
    ['Fondo_Interno_USS_Investigacion_en_Politicas_Publicas_USS_IPSUSS', 'Fondo Interno USS: Investigación en Políticas Públicas USS (IPSUSS)', 'USS Internal Fund: Public Policy Research (IPSUSS)'],
    ['TESIS_Pregrado', 'TESIS Pregrado', 'Undergraduate thesis'],
    ['TESIS_Magister', 'TESIS Magister', 'Master’s thesis'],
    ['TESIS_Doctorado', 'TESIS Doctorado', 'Doctoral thesis'],
    ['Otros', 'Otros', 'Other']
  ],
  NP003: [
    ['AWARDED', 'Adjudicado', 'Awarded'],
    ['APPLYING', 'En postulación', 'Under application'],
    ['NOT_APPLICABLE', 'No aplica', 'Not applicable']
  ],
  NP007: [
    ['Santiago_Bellavista', 'Santiago - Bellavista', 'Santiago - Bellavista'],
    ['Santiago_Los_Leones', 'Santiago - Los Leones', 'Santiago - Los Leones'],
    ['Santiago_Ciudad_Universitaria_Huechuraba', 'Santiago - Ciudad Universitaria (Huechuraba)', 'Santiago - University City (Huechuraba)'],
    ['Concepcion_Tres_Pascualas', 'Concepción - Tres Pascualas', 'Concepción - Tres Pascualas'],
    ['Concepcion_Paicavi', 'Concepción - Paicaví', 'Concepción - Paicaví'],
    ['Valdivia_Sede', 'Valdivia - Sede', 'Valdivia - Campus'],
    ['Valdivia_Centro_de_Estudios_Cientificos', 'Valdivia - Centro de Estudios Científicos', 'Valdivia - Centro de Estudios Científicos'],
    ['Patagonia', 'Patagonia', 'Patagonia']
  ],
  NP010: [
    ['Estudiante_de_pregrado', 'Estudiante de pregrado', 'Undergraduate student'],
    ['Estudiante_de_magister', 'Estudiante de magister', 'Master’s student'],
    ['Estudiante_de_doctorado', 'Estudiante de doctorado', 'Doctoral student'],
    ['Postdoctorante', 'Postdoctorante', 'Postdoctoral fellow'],
    ['Asistente_de_Investigacion', 'Asistente de Investigación', 'Research assistant'],
    ['Personal_Tecnico', 'Personal Técnico', 'Technical staff'],
    ['Docente', 'Docente', 'Faculty member'],
    ['Co_Investigador', 'Co-Investigador', 'Co-investigator'],
    ['Investigador', 'Investigador', 'Researcher'],
    ['Director_Unidad_Administrativa', 'Director Unidad Administrativa', 'Administrative unit director']
  ],
  NP016: [
    ['Docente', 'Docente', 'Faculty member'],
    ['Docente_Investigador', 'Docente Investigador', 'Faculty researcher'],
    ['Investigador', 'Investigador', 'Researcher']
  ],
  NP017: [
    ['Es_un_proyecto_original', 'Es un proyecto original', 'It is an original project'],
    ['Se_enmarca_en_un_proyecto_vigente_de_mi_tutor_o_patrocinante_sin_incorporar_cambios', 'Se enmarca en un proyecto vigente de mi tutor o patrocinante, sin incorporar cambios', 'It is part of an active project of my tutor or sponsor, with no changes'],
    ['Se_enmarca_en_un_proyecto_vigente_de_mi_tutor_o_patrocinante_incorporando_cambios_o_nuevos_elementos', 'Se enmarca en un proyecto vigente de mi tutor o patrocinante, incorporando cambios o nuevos elementos', 'It is part of an active project of my tutor or sponsor, incorporating changes or new elements']
  ],
  NP018: [
    ['Actividad_de_graduacion_de_pregrado', 'Actividad de graduación de pregrado', 'Undergraduate graduation activity'],
    ['Actividad_de_graduacion_de_Magister', 'Actividad de graduación de Magister', 'Master’s graduation activity'],
    ['Asesorias_Tecnicas_Especializadas', 'Asesorías Técnicas Especializadas', 'Specialized technical advice'],
    ['Curso_proyecto_de_investigacion', 'Curso proyecto de investigación', 'Research project course'],
    ['Curso_metodologia_de_la_investigacion_de_pregrado', 'Curso metodología de la investigación de pregrado', 'Undergraduate research methods course'],
    ['Estudio_de_Caso', 'Estudio de Caso', 'Case study'],
    ['Proyecto_de_Investigacion', 'Proyecto de Investigación', 'Research project'],
    ['Proyecto_de_Vinculacion_con_el_Medio', 'Proyecto de Vinculación con el Medio', 'Community engagement project'],
    ['Proyecto_Piloto_pruebas_de_concepto_obtencion_de_datos_preliminares_pruebas_de_metodologias_etc', 'Proyecto Piloto (pruebas de concepto, obtención de datos preliminares, pruebas de metodologías, etc.)', 'Pilot project (proof of concept, preliminary data, methodology testing, etc.)'],
    ['Revision_Bibliografica', 'Revisión Bibliográfica', 'Literature review'],
    ['Trabajo_entorno_a_necesidades_del_comercio_la_industria_y_sociedad', 'Trabajo entorno a necesidades del comercio, la industria y sociedad', 'Work addressing needs of commerce, industry and society'],
    ['Tesis_de_Pregrado', 'Tesis de Pregrado', 'Undergraduate thesis'],
    ['Tesis_de_Magister', 'Tesis de Magister', 'Master’s thesis'],
    ['Tesis_de_Doctorado', 'Tesis de Doctorado', 'Doctoral thesis']
  ],
  NP019: [
    ['Es_un_proyecto_original', 'Es un proyecto original', 'It is an original project'],
    ['Se_enmarca_en_un_proyecto_vigente_que_cuenta_con_certificaciones_eticas_y_o_bioseguridad_y_NO_incorpora_cambios_o_nuevos_elementos', 'Se enmarca en un proyecto vigente que cuenta con certificaciones éticas y/o bioseguridad y NO incorpora cambios o nuevos elementos', 'It is part of an active project with ethics and/or biosafety certifications and does NOT add changes or new elements'],
    ['Se_enmarca_en_un_proyecto_vigente_que_cuenta_con_certificaciones_eticas_y_o_bioseguridad_y_SI_incorpora_cambios_o_nuevos_elementos', 'Se enmarca en un proyecto vigente que cuenta con certificaciones éticas y/o bioseguridad y SÍ incorpora cambios o nuevos elementos', 'It is part of an active project with ethics and/or biosafety certifications and DOES add changes or new elements']
  ],
  NP022: [
    ['Estudiantes_de_pregrado', 'Estudiantes de pregrado', 'Undergraduate students'],
    ['Estudiantes_de_postgrado', 'Estudiantes de postgrado', 'Graduate students'],
    ['Docentes', 'Docentes', 'Faculty members'],
    ['Docentes_investigadores', 'Docentes investigadores', 'Faculty researchers'],
    ['Investigadores', 'Investigadores', 'Researchers'],
    ['Personal_Tecnico_de_apoyo_a_la_Investigacion', 'Personal Técnico de apoyo a la Investigación', 'Technical research support staff'],
    ['Personal_Administrativo', 'Personal Administrativo', 'Administrative staff']
  ],
  NP023: [
    ['Envio_publicacion_en_revista_de_especialidad', 'Envío/publicación en revista de especialidad', 'Submission/publication in a specialist journal'],
    ['Libro_o_capitulo_de_libro', 'Libro o capítulo de libro', 'Book or book chapter'],
    ['Informe_final_de_curso_de_metodologia_de_investigacion_de_pregrado', 'Informe final de curso de metodología de investigación de pregrado', 'Final report of an undergraduate research methods course'],
    ['Informe_final_de_curso_proyecto_de_investigacion_de_pregrado', 'Informe final de curso proyecto de investigación de pregrado', 'Final report of an undergraduate research project course'],
    ['Tesis_o_seminario_de_grado', 'Tesis o seminario de grado', 'Thesis or degree seminar'],
    ['Producto_susceptible_a_proteccion_de_propiedad_intelectual', 'Producto susceptible a protección de propiedad intelectual', 'Output eligible for intellectual property protection'],
    ['Postulacion_presentacion_en_congresos_simposios_o_seminarios', 'Postulación/presentación en congresos, simposios o seminarios', 'Application/presentation at congresses, symposia or seminars'],
    ['Postulacion_a_Fondos_externos_concursables', 'Postulación a Fondos externos concursables', 'Application to competitive external funds'],
    ['Generacion_de_Material_Metodologia_otro_para_mejorar_la_docencia_aprendizaje_en_la_USS', 'Generación de Material/Metodología/otro para mejorar la docencia/aprendizaje en la USS', 'Creation of material/methodology/other to improve teaching/learning at USS'],
    ['Generacion_de_Material_Metodologia_otro_para_mejorar_la_docencia_aprendizaje_general', 'Generación de Material/Metodología/otro para mejorar la docencia/aprendizaje general', 'Creation of material/methodology/other to improve teaching/learning in general'],
    ['Generacion_de_Bases_de_Datos', 'Generación de Bases de Datos', 'Database generation'],
    ['Indicaciones_para_politicas_publicas', 'Indicaciones para políticas públicas', 'Guidance for public policy'],
    ['Material_informativo_para_la_comunidad', 'Material informativo para la comunidad', 'Informative material for the community']
  ],
  NP024: [
    ['cec_samples', 'Personas a las cuales se les extraerán muestras biológicas (sangre, tejido, secreciones, etc.)', 'Individuals from whom biological samples will be collected (blood, tissue, secretions, etc.)'],
    ['cec_drugs', 'Personas a las cuales se les administrarán fármacos o en los que se usarán dispositivos de uso médico', 'Individuals who will receive drugs or on whom medical devices will be used'],
    ['cec_surveys', 'Personas a las cuales se les realizarán encuestas, entrevistas, cuestionarios, etc.', 'Individuals who will participate in surveys, interviews, questionnaires, etc.'],
    ['cec_records', 'Personas a las cuales se les realizarán registros de localización (GPS), registros gráficos y/o sonoros (registros en audio, video, fotografías)', 'Individuals from whom location records (GPS) or graphic and/or sound records (audio, video, photographs) will be collected'],
    ['cec_vulnerable', 'Personas menores de edad, personas provenientes de pueblos originarios, estudiantes, privados de libertad, personas con algún grado de discapacidad que requieren resguardos adicionales u otras personas que requieren resguardos adicionales', 'Minors, people from Indigenous peoples, students, persons deprived of liberty, people with a disability requiring additional safeguards, or other persons requiring additional safeguards'],
    ['cec_records_clinical', 'Fichas clínicas, sicológicas o sociales', 'Clinical, psychological or social records'],
    ['cec_social', 'Manipulación del medio ambiente social y/o cultural (Ej. Estudios etnográficos, intervenciones urbanas, etc.)', 'Manipulation of the social and/or cultural environment (e.g. ethnographic studies, urban interventions, etc.)'],
    ['cec_public_db_1', 'Bases de datos de dominio público', 'Public-domain databases'],
    ['cec_public_db_2', 'Bases de datos de dominio público', 'Public-domain databases'],
    ['cecua_vertebrates', 'Animales vertebrados', 'Vertebrate animals'],
    ['cecua_samples', 'Obtención de muestras biológicas de animales y/o piezas anatómicas derivadas de animales', 'Collection of biological samples from animals and/or anatomical parts derived from animals'],
    ['cecua_prior', 'Muestras biológicas de animales y/o piezas anatómicas derivadas de animales obtenidas previamente', 'Previously obtained biological samples from animals and/or anatomical parts derived from animals'],
    ['cecua_commercial', 'Muestras biológicas de origen comercial provenientes de animales', 'Commercially sourced biological samples from animals'],
    ['bio_reagents', 'El uso de reactivos químicos, drogas, hormonas, radioisótopos', 'Use of chemical reagents, drugs, hormones, radioisotopes'],
    ['bio_human', 'El uso de muestras biológicas proveniente de personas', 'Use of biological samples from people'],
    ['bio_animal', 'El uso de muestras biológicas provenientes de animales', 'Use of biological samples from animals'],
    ['bio_cell_commercial', 'Uso de líneas celulares comerciales', 'Use of commercial cell lines'],
    ['bio_cell_generated', 'Generación de líneas celulares desde muestras animales o humanas', 'Generation of cell lines from animal or human samples'],
    ['bio_agents', 'El uso y/o generación de agentes biológicos (como bacterias, virus, hongos, parásitos, material infeccioso, organismos genéticamente modificados)', 'Use and/or generation of biological agents (such as bacteria, viruses, fungi, parasites, infectious material, genetically modified organisms)'],
    ['bio_env_samples', 'Manipulación del medio ambiente natural: toma y uso de muestras ambientales', 'Manipulation of the natural environment: collection and use of environmental samples'],
    ['bio_env_apply', 'Manipulación del medio ambiente natural: Aplicación de elementos externos al ambiente', 'Manipulation of the natural environment: application of external elements to the environment'],
    ['NONE', 'Ninguno de los anteriores', 'None of the above']
  ]
};

npOptions.NP008 = [
  ['Arquitectura_Arte_y_Diseno', 'Arquitectura, Arte y Diseño', 'Architecture, Art and Design'],
  ['Ciencias', 'Ciencias', 'Sciences'],
  ['Ciencias_de_la_Rehabilitacion_y_Calidad_de_Vida', 'Ciencias de la Rehabilitación y Calidad de Vida', 'Rehabilitation Sciences and Quality of Life'],
  ['Ciencias_para_el_cuidado_de_la_Salud', 'Ciencias para el cuidado de la Salud', 'Health Care Sciences'],
  ['Derecho_y_Ciencias_Sociales', 'Derecho y Ciencias Sociales', 'Law and Social Sciences'],
  ['Economia_Negocios_y_Gobierno', 'Economía, Negocios y Gobierno', 'Economics, Business and Government'],
  ['Educacion', 'Educación', 'Education'],
  ['Ingenieria', 'Ingeniería', 'Engineering'],
  ['Medicina', 'Medicina', 'Medicine'],
  ['Medicina_Veterinaria', 'Medicina Veterinaria', 'Veterinary Medicine'],
  ['Odontologia', 'Odontología', 'Dentistry']
];
npOptions.NP014 = npOptions.NP007;
npOptions.NP015 = npOptions.NP008;

for (const [qid, options] of Object.entries(npOptions)) {
  for (const [key, spanish, english] of options) {
    p(`forms.newProject.questions.${qid}.options.${key}`, spanish, english);
  }
}

const val = {
  VAL001: { label: ['Nombre Responsable de la ejecución del Proyecto (IR)', 'First name of the Principal Investigator (PI)'] },
  VAL002: { label: ['Apellidos Responsable de la ejecución del proyecto (IR)', 'Last name of the Principal Investigator (PI)'] },
  VAL003: { label: ['Correo Electrónico Responsable de la ejecución del proyecto (IR)', 'Email of the Principal Investigator (PI)'] },
  VAL004: { label: ['Categoría', 'Category'] },
  VAL005: { label: ['Facultad/Unidad', 'Faculty/Unit'] },
  VAL006: { label: ['Sede - Campus USS donde se ejecutará', 'USS campus where the project will be carried out'] },
  VAL007: { label: ['Tipo de proyecto o fuente de financiamiento', 'Project type or funding source'] },
  VAL008: { label: ['Título de la propuesta', 'Proposal title'] },
  VAL009: { label: ['Código asignado por la fuente de financiamiento', 'Code assigned by the funding source'] },
  VAL010: { label: ['¿Qué comité(s) debe(n) validar?', 'Which committee(s) must validate?'] },
  VAL011: { label: ['Institución que emitió la aprobación', 'Institution that issued the approval'] },
  VAL012: { label: ['¿El título del acta de aprobación es idéntico al declarado arriba?', 'Is the title in the approval record identical to the one declared above?'] },
  VAL013: { label: ['¿Cambia el equipo que ejecuta?', 'Does the executing team change?'] },
  VAL014: { label: ['¿Cambia algún procedimiento respecto del protocolo aprobado?', 'Does any procedure change with respect to the approved protocol?'] },
  VAL015: { label: ['¿La ejecución del proyecto continúa también en la institución de origen?', 'Will the project also continue at the originating institution?'] },
  VAL016: { label: ['Fecha estimada de inicio de actividades en USS', 'Estimated start date of activities at USS'] },
  VAL017: {
    label: ['Carta de solicitud de validación', 'Validation request letter'],
    help: ['Solo PDF. Límite visual del conjunto: 100 MB.', 'PDF only. Visual set limit: 100 MB.']
  },
  VAL018: { label: ['Documentación de respaldo', 'Supporting documentation'] },
  VAL019: {
    label: ['Carga de documentación', 'Document upload'],
    help: ['Los archivos se muestran por nombre, tipo y tamaño. No se expone la ruta interna.', 'Files are shown by name, type and size. The internal path is not exposed.']
  }
};

for (const [id, item] of Object.entries(val)) {
  p(`forms.validation.questions.${id}.label`, item.label[0], item.label[1]);
  if (item.help) {
    p(`forms.validation.questions.${id}.help`, item.help[0], item.help[1]);
  }
}

const valOptions = {
  VAL004: [
    ['Docente', 'Docente', 'Faculty member'],
    ['Docente_Investigador', 'Docente Investigador', 'Faculty researcher'],
    ['Investigador', 'Investigador', 'Researcher']
  ],
  VAL010: [
    ['CEC', 'Comité Ético Científico', 'Scientific Ethics Committee'],
    ['CECUA', 'Comité Institucional de Ética en Cuidado y Uso de Animales', 'Institutional Animal Care and Use Ethics Committee'],
    ['BIOSEGURIDAD', 'Comité de Bioseguridad', 'Biosafety Committee']
  ],
  VAL012: [
    ['yes', 'Sí', 'Yes'],
    ['no', 'No, cambió', 'No, it changed']
  ],
  VAL013: [
    ['yes', 'Sí', 'Yes'],
    ['no', 'No', 'No']
  ],
  VAL014: [
    ['yes', 'Sí', 'Yes'],
    ['no', 'No', 'No']
  ],
  VAL015: [
    ['yes', 'Sí', 'Yes'],
    ['no', 'No', 'No']
  ],
  VAL018: [
    ['acta', 'Acta o certificado de aprobación emitida por comité de origen', 'Approval minutes or certificate issued by the originating committee'],
    ['protocolo', 'Protocolo completo y/o documentación aprobada', 'Full protocol and/or approved documentation'],
    ['adjudicacion', 'Carta de adjudicación del proyecto', 'Project award letter'],
    ['seguimiento', 'Informe de seguimiento del comité de origen', 'Follow-up report from the originating committee'],
    ['enmiendas', 'Aprobación de enmiendas al proyecto emitidas por comité de origen', 'Approval of project amendments issued by the originating committee'],
    ['otros', 'Otros', 'Other']
  ]
};

for (const [qid, options] of Object.entries(valOptions)) {
  for (const [key, spanish, english] of options) {
    p(`forms.validation.questions.${qid}.options.${key}`, spanish, english);
  }
}

p('classification.automatic', 'Clasificación automática', 'Automatic classification');
p('classification.automaticHint', 'Sugerencia del sistema a partir de los objetos o sujetos de estudio. DISEI puede confirmarla o modificarla.', 'System suggestion based on study objects or subjects. DISEI may confirm or change it.');
p('classification.validated', 'Clasificación validada por DISEI', 'Classification validated by DISEI');
p('classification.justification', 'Justificación del cambio', 'Change justification');
p('classification.justificationPlaceholder', 'Describa el motivo del cambio', 'Describe the reason for the change');
p('classification.justificationHint', 'Indique el motivo por el cual la clasificación validada difiere de la clasificación sugerida automáticamente.', 'Indicate why the validated classification differs from the automatic suggestion.');
p('classification.confirmReferral', 'Confirmar derivación', 'Confirm referral');
p('classification.needJustification', 'Indique la justificación del cambio.', 'A justification is required.');
p('classification.selectOne', 'Seleccione una clasificación.', 'Select a classification.');
p('classification.confirmTitle', 'Confirmar derivación', 'Confirm referral');
p('classification.confirmBody', 'Está a punto de confirmar la derivación de este proyecto.', 'You are about to confirm the referral of this project.');
p('classification.inboxTitle', 'Solicitudes de certificación pendientes', 'Certification applications pending');
p('classification.emptyTitle', 'Bandeja al día', 'Inbox is up to date');
p('classification.emptyMessage', 'No hay solicitudes esperando validación DISEI.', 'There are no applications waiting for DISEI validation.');
p('classification.code', 'Código', 'Code');
p('classification.project', 'Proyecto', 'Project');
p('classification.researcher', 'Investigador', 'Researcher');
p('classification.type', 'Tipo', 'Type');
p('classification.date', 'Fecha', 'Date');
p('classification.automaticCol', 'Clasificación automática', 'Automatic classification');
p('classification.review', 'Revisar', 'Review');

p('cases.title', 'Expedientes', 'Cases');
p('cases.open', 'Abrir', 'Open');
p('cases.committee', 'Comité', 'Committee');
p('cases.status', 'Estado', 'Status');
p('cases.back', 'Volver', 'Back');
p('cases.admissibility', 'Admisibilidad', 'Admissibility');
p('cases.admissibilityHint', 'Revise que los antecedentes estén completos antes de continuar con la asignación de revisores.', 'Review that the file is complete before continuing with reviewer assignment.');
p('cases.completeFile', 'Expediente completo', 'Complete file');
p('cases.returnToFix', 'Devolver para subsanar', 'Return for correction');
p('cases.returnTitle', 'Devolver expediente para subsanar', 'Return the file for correction');
p('cases.returnBody', 'El investigador podrá corregir o completar los antecedentes antes de volver a enviar el expediente al comité.', 'The researcher will be able to correct or complete the documentation before sending the file back to the committee.');
p('cases.returnReason', 'Motivo de devolución', 'Reason for return');
p('cases.returnReasonRequired', 'Indique el motivo de devolución.', 'Enter the reason for the return.');
p('cases.assignReviewers', 'Asignación de revisores', 'Reviewer assignment');
p('cases.assignHint', 'Siempre dos revisores. Sugerencias ordenadas por carga y especialidad.', 'Always two reviewers. Suggestions ordered by workload and specialty.');
p('cases.reviewer1', 'Revisor 1', 'Reviewer 1');
p('cases.reviewer2', 'Revisor 2', 'Reviewer 2');
p('cases.existingSession', 'Sesión ordinaria existente', 'Existing ordinary session');
p('cases.unassigned', 'Sin asignar', 'Unassigned');
p('cases.newExtraordinary', 'Nueva sesión extraordinaria', 'New extraordinary session');
p('cases.time', 'Hora', 'Time');
p('cases.createExtraordinary', 'Crear extraordinaria', 'Create extraordinary session');
p('cases.assignAndSession', 'Asignar revisores y sesión', 'Assign reviewers and session');
p('cases.reviewN', 'Revisión {n}', 'Review {n}');
p('cases.consolidatedObservation', 'Observación consolidada para investigador', 'Consolidated comment for the researcher');
p('cases.topic', 'Tema', 'Topic');
p('cases.text', 'Texto', 'Text');
p('cases.addObservation', 'Añadir observación', 'Add comment');
p('cases.publishObservations', 'Publicar observaciones', 'Publish comments');
p('cases.prepareResolution', 'Preparar resolución', 'Prepare resolution');
p('cases.decisionType', 'Tipo de decisión', 'Decision type');
p('cases.decisionCatalog', 'Catálogo de estado', 'Status catalog');
p('cases.rationale', 'Fundamento', 'Rationale');
p('cases.sendToPresidency', 'Enviar a presidencia', 'Send to the chair');
p('cases.correlative', 'Correlativo {correlative} · {workflow}', 'Reference {correlative} · {workflow}');
p('cases.validateResolution', 'Validar resolución', 'Validate resolution');
p('cases.publishConfirm', '¿Publicar las observaciones al investigador? Las revisiones originales no se modifican.', 'Publish the comments to the researcher? Original reviews are not changed.');
p('cases.publish', 'Publicar', 'Publish');
p('cases.reviewerLoad', '{name} · {specialty} · carga {load}', '{name} · {specialty} · load {load}');

p('reviews.title', 'Revisiones asignadas', 'Assigned reviews');
p('reviews.session', 'sesión {session}', 'session {session}');
p('reviews.sessionPending', 'por definir', 'to be defined');
p('reviews.openAssignment', 'Abrir asignación', 'Open assignment');
p('reviews.accept', 'Aceptar', 'Accept');
p('reviews.reject', 'Rechazar', 'Decline');
p('reviews.methodology', 'Metodología', 'Methodology');
p('reviews.risks', 'Riesgos y resguardos', 'Risks and safeguards');
p('reviews.notes', 'Observaciones', 'Comments');
p('reviews.saveDraft', 'Guardar borrador', 'Save draft');
p('reviews.submitReview', 'Enviar revisión', 'Submit review');
p('reviews.submittedReadonly', 'Revisión enviada. Quedó en solo lectura.', 'Review submitted. It is now read-only.');
p('reviews.rejectReason', 'Motivo del rechazo (obligatorio)', 'Reason for declining (required)');
p('reviews.confirmReject', 'Confirmar rechazo', 'Confirm decline');
p('reviews.declare', 'Declaro respecto del presente proyecto:', 'I declare regarding this project:');
p('reviews.noConflict', 'Sin conflicto de interés', 'No conflict of interest');
p('reviews.hasConflict', 'Existe potencial conflicto', 'There is a potential conflict');
p('reviews.defaultTitle', 'Revisión', 'Review');
p('reviews.criteria.metodologia', 'Metodología', 'Methodology');
p('reviews.criteria.riesgos', 'Riesgos y resguardos', 'Risks and safeguards');
p('reviews.criteria.observaciones', 'Observaciones', 'Comments');
p('reviews.criteria.bienestar', 'Bienestar', 'Welfare');

p('sessions.title', 'Sesiones del comité', 'Committee sessions');
p('sessions.new', 'Nueva sesión', 'New session');
p('sessions.committee', 'Comité', 'Committee');
p('sessions.type', 'Tipo', 'Type');
p('sessions.date', 'Fecha', 'Date');
p('sessions.time', 'Hora', 'Time');
p('sessions.create', 'Crear sesión', 'Create session');
p('sessions.projectsCount', '{count} proyectos', '{count} projects');
p('sessions.detailTitle', 'Sesión {date} {time}', 'Session {date} {time}');

p('reviewers.title', 'Revisores', 'Reviewers');
p('reviewers.name', 'Nombre', 'Name');
p('reviewers.specialty', 'Especialidad', 'Specialty');
p('reviewers.committee', 'Comité', 'Committee');
p('reviewers.load', 'Carga', 'Workload');
p('reviewers.availability', 'Disponibilidad', 'Availability');

p('expirations.title', 'Vencimientos', 'Expirations');
p('expirations.issuedExpires', 'Emitido {issued} · Vence {expires}', 'Issued {issued} · Expires {expires}');
p('expirations.days', '{semaphore} · {days} días', '{semaphore} · {days} days');
p('expirations.critical', 'Alerta crítica. 15 días para regularizar renovación (previsto).', 'Critical alert. 15 days to regularize renewal (planned).');
p('expirations.viewCase', 'Ver expediente', 'View case');

p('notifications.title', 'Notificaciones', 'Notifications');
p('notifications.SUBMISSION_RECEIVED.title', 'Solicitud recibida', 'Application received');
p('notifications.SUBMISSION_RECEIVED.body', '{projectTitle}', '{projectTitle}');
p('notifications.SUBMISSION_SENT.title', 'Solicitud enviada', 'Application submitted');
p('notifications.SUBMISSION_SENT.body', 'DISEI validará la clasificación preliminar.', 'DISEI will validate the preliminary classification.');
p('notifications.CLASSIFICATION_DONE.title', 'Clasificación realizada', 'Classification completed');
p('notifications.CLASSIFICATION_DONE.body', 'Ya puede cargar antecedentes por comité.', 'You can now upload supporting documents by committee.');
p('notifications.DOCUMENTS_SENT.title', 'Antecedentes enviados', 'Documents submitted');
p('notifications.DOCUMENTS_SENT.body', 'El expediente {caseCode} fue notificado.', 'Case {caseCode} was notified.');
p('notifications.REVIEW_ASSIGNED.title', 'Asignación de revisor', 'Reviewer assignment');
p('notifications.REVIEW_ASSIGNED.body', 'Tiene una nueva revisión en {caseCode}.', 'You have been assigned to review {caseCode}.');
p('notifications.OBSERVATIONS_AVAILABLE.title', 'Observaciones disponibles', 'Comments available');
p('notifications.OBSERVATIONS_AVAILABLE.body', 'El comité solicitó modificaciones.', 'The committee requested changes.');
p('notifications.RESPONSE_RECEIVED.title', 'Respuesta recibida', 'Response received');
p('notifications.RESPONSE_RECEIVED.body', 'El IR respondió {observationCode}.', 'The PI responded to {observationCode}.');
p('notifications.RESOLUTION_ISSUED.title', 'Resolución emitida', 'Resolution issued');
p('notifications.RESOLUTION_ISSUED.body', '{correlative} ya está disponible.', '{correlative} is now available.');

p('audit.title', 'Historial y auditoría', 'History and audit');
p('audit.reason', 'Motivo: {reason}', 'Reason: {reason}');
p('audit.actions.CREATE_SUBMISSION', 'Creó solicitud', 'Created an application');
p('audit.actions.SUBMIT_REQUEST', 'Envió solicitud ética', 'Submitted an ethics application');
p('audit.actions.VALIDATE_CLASSIFICATION', 'Validó la clasificación', 'Validated the classification');
p('audit.actions.CLASSIFICATION_CHANGED', 'Modificó la clasificación', 'Changed the classification');
p('audit.actions.CLASSIFICATION_CONFIRMED', 'Confirmó la clasificación', 'Confirmed the classification');
p('audit.actions.CREATE_CASE', 'Derivó expediente', 'Created a committee case');
p('audit.actions.NOTIFY_COMMITTEE', 'Notificó al comité', 'Notified the committee');
p('audit.actions.ADMISSIBILITY', 'Resolvió admisibilidad', 'Resolved admissibility');
p('audit.actions.ASSIGN_REVIEWERS', 'Asignó revisores', 'Assigned reviewers');
p('audit.actions.REVIEW_DECISION', 'Decidió asignación', 'Decided an assignment');
p('audit.actions.REVIEW_SUBMITTED', 'Envió revisión', 'Submitted a review');
p('audit.actions.PUBLISH_OBSERVATIONS', 'Publicó observaciones', 'Published comments');
p('audit.actions.ANSWER_OBSERVATIONS', 'Respondió observaciones', 'Answered comments');
p('audit.actions.PREPARE_RESOLUTION', 'Preparó resolución', 'Prepared a resolution');
p('audit.actions.VALIDATE_RESOLUTION', 'Validó resolución', 'Validated a resolution');
p('audit.actions.CREATE_SESSION', 'Creó sesión', 'Created a session');
p('audit.actions.UPLOAD_DOCUMENT', 'Cargó documento', 'Uploaded a document');
p('audit.actions.ADMIT_CASE', 'Declaró expediente completo', 'Declared the file complete');
p('audit.actions.RETURN_CASE', 'Devolvió para subsanar', 'Returned the file for correction');
p('audit.actions.DECLARE_CONFLICT', 'Declaró conflicto de interés', 'Declared a conflict of interest');
p('audit.actions.ACCEPT_ASSIGNMENT', 'Aceptó asignación', 'Accepted the assignment');
p('audit.actions.REJECT_ASSIGNMENT', 'Rechazó asignación', 'Declined the assignment');
p('audit.actions.SUBMIT_RESPONSES', 'Envió respuestas', 'Submitted responses');
p('audit.actions.ISSUE_RESOLUTION', 'Emitió resolución', 'Issued a resolution');
p('audit.actions.DOCUMENT_UPLOADED', 'Cargó un documento', 'Uploaded a document');
p('audit.actions.COMMITTEE_NOTIFIED', 'Notificó al comité', 'Notified the committee');
p('audit.actions.COMMITTEE_CONTACT', 'Contactó al comité', 'Contacted the committee');
p('audit.actions.ADMISSIBILITY_ACCEPTED', 'Aceptó la admisibilidad', 'Accepted admissibility');
p('audit.actions.REVIEW_SUBMITTED', 'Envió la revisión', 'Submitted the review');
p('audit.actions.OBSERVATIONS_CONSOLIDATED', 'Consolidó observaciones', 'Consolidated observations');
p('audit.actions.OBSERVATIONS_PUBLISHED', 'Publicó observaciones', 'Published observations');
p('audit.actions.RESEARCHER_RESPONSE_SUBMITTED', 'Envió la respuesta', 'Submitted the response');
p('audit.actions.RESOLUTION_PREPARED', 'Preparó la resolución', 'Prepared the resolution');
p('audit.actions.RESOLUTION_RETURNED_TO_SECRETARY', 'Devolvió la resolución', 'Returned the resolution');
p('audit.actions.RESOLUTION_VALIDATED', 'Validó la resolución', 'Validated the resolution');
p('audit.actions.AMENDMENT_REQUESTED', 'Solicitó una enmienda', 'Requested an amendment');
p('audit.actions.FOLLOW_UP_REQUESTED', 'Solicitó un seguimiento', 'Requested a follow-up');
p('audit.actions.COMMITTEE_NOTIFICATION', 'Notificación al comité', 'Committee notification');
p('audit.actions.OBSERVATION_NOTICE', 'Aviso de observaciones', 'Observation notice');
p('cases.diseiCode', 'Código DISEI', 'DISEI code');
p('cases.committeeCode', 'Código del comité', 'Committee code');
p('cases.action', 'Acción', 'Action');
p('cases.consolidate', 'Consolidar observaciones', 'Consolidate observations');
p('cases.previewPdf', 'Vista previa PDF', 'PDF preview');
p('cases.currentRound', 'Ronda en curso', 'Current round');
p('cases.roundHistory', 'Historial de rondas', 'Round history');
p('cases.roundLabel', 'Observaciones {n}', 'Observations {n}');
p('cases.published', 'Publicada', 'Published');
p('cases.closedRound', 'Cerrada', 'Closed');
p('cases.publishDefault', 'El comité registró observaciones sobre el proyecto. Revise el detalle en la plataforma y responda dentro del plazo indicado.', 'The committee recorded observations on the project. Review the detail in the platform and respond within the stated deadline.');
p('cases.publishMessageLabel', 'Mensaje al investigador', 'Message to the investigator');
p('cases.preparedDocuments', 'Resolución y documentos preparados', 'Prepared resolution and documents');
p('cases.attachedDocuments', 'Documentos que acompañan la resolución', 'Documents accompanying the resolution');
p('cases.validationActions', 'Acciones de validación', 'Validation actions');
p('cases.returnToSecretary', 'Devolver a Secretario Ejecutivo', 'Return to Executive Secretary');
p('cases.signaturePrototype', 'Firma de prototipo', 'Prototype signature');
p('cases.stampPrototype', 'Timbre de prototipo del comité', 'Prototype committee stamp');
p('projects.committeeCode', 'Código del comité', 'Committee code');
p('projects.received', 'Fecha de ingreso', 'Intake date');
p('projects.expires', 'Fecha de vencimiento', 'Expiry date');
p('projects.openCase', 'Abrir expediente', 'Open case');
p('projects.requiredProgress', '{done} de {total} obligatorios completos', '{done} of {total} required documents complete');
p('projects.requiredDocs', 'Documentos obligatorios', 'Required documents');
p('projects.optionalDocs', 'Documentos opcionales', 'Optional documents');
p('projects.missing', 'Falta', 'Missing');
p('projects.notLoaded', 'No cargado', 'Not uploaded');
p('projects.versionHistory', 'Historial versiones', 'Version history');
p('projects.chooseType', 'Tipo de documento', 'Document type');
p('projects.version', 'Versión', 'Version');
p('projects.uploadedAt', 'Fecha', 'Date');
p('projects.notifyDraft', 'Informo que se han cargado los antecedentes requeridos para la revisión del proyecto.', 'I report that the documents required for the project review have been uploaded.');
p('projects.notifyMessage', 'Mensaje', 'Message');
p('projects.confirmSend', 'Enviar', 'Send');
p('projects.publishedObservations', 'Observaciones publicadas', 'Published observations');
p('projects.download', 'Descargar', 'Download');
p('projects.resolutions', 'Resoluciones', 'Resolutions');
p('projects.downloadResolution', 'Descargar PDF', 'Download PDF');
p('projects.resolutionTitle', 'Resolución', 'Resolution');
p('projects.amendmentResolution', 'Resolución de enmienda', 'Amendment resolution');
p('projects.followUpResolution', 'Resolución de seguimiento', 'Follow-up resolution');
p('projects.requestAmendment', 'Solicitar enmienda', 'Request amendment');
p('projects.requestFollowUp', 'Solicitar seguimiento', 'Request follow-up');
p('projects.followUpType', 'Tipo de seguimiento', 'Follow-up type');
p('projects.followUp', 'Seguimiento', 'Follow-up');
p('projects.closureFollowUp', 'Seguimiento de cierre de proyecto', 'Project closure follow-up');
p('projects.justification', 'Justificación', 'Justification');
p('projects.amendmentUploadHint', 'Cargue nuevas versiones desde el listado del comité. Las versiones ya aprobadas se conservan.', 'Upload new versions from the committee list. Approved versions are kept.');
p('projects.committeeHistory', 'Historial del comité', 'Committee history');
p('projects.contactCommittee', 'Contacto con comité', 'Contact the committee');
p('projects.lockedAfterResolution', 'La carga y las observaciones quedan en lectura porque el comité ya emitió resolución.', 'Uploads and observations are read-only because the committee already issued a resolution.');
p('projects.noUploadedDocuments', 'No hay documentos cargados.', 'No documents have been uploaded.');
p('reviews.responsibleInvestigator', 'Investigador responsable', 'Responsible investigator');
p('reviews.fundingSource', 'Fuente de financiamiento', 'Funding source');
p('reviews.documents', 'Documentos del proyecto', 'Project documents');
p('reviews.formProvisional', 'Formulario provisional de prototipo.', 'Provisional prototype form.');
p('pdf.prototypeDisclaimer', 'Vista previa de prototipo. No corresponde al formato oficial del comité.', 'Prototype preview. This is not the official committee format.');
p('shell.activeCommittee', 'Comité activo', 'Active committee');
p('common.continue', 'Continuar', 'Continue');
p('common.confirm', 'Confirmar', 'Confirm');
p('catalogs.docs.cecLetterPresident', 'Carta al Presidente/a del Comité Ético Científico (CEC) solicitando revisión', 'Letter to the President of the Scientific Ethics Committee requesting review');
p('catalogs.docs.cecCommitment', 'Carta de compromiso del investigador', 'Investigator commitment letter');
p('catalogs.docs.cecProtocol', 'Proyecto de investigación in extenso', 'Full research project');
p('catalogs.docs.cecSummary', 'Resumen del proyecto de investigación', 'Research project summary');
p('catalogs.docs.cecEthicsAnalysis', 'Análisis de aspectos éticos del proyecto', 'Ethical analysis of the project');
p('catalogs.docs.cecCv', 'Currículum vitae de todos los investigadores participantes', 'Curriculum vitae of all participating investigators');
p('catalogs.docs.cecSupportLetter', 'Carta de respaldo de la autoridad de la unidad académica', 'Support letter from the academic unit authority');
p('catalogs.docs.cecConsent', 'Formulario de Consentimiento Informado', 'Informed consent form');
p('catalogs.docs.cecAssent', 'Formulario de Asentimiento Informado para participantes menores de 18 años', 'Informed assent form for participants under 18');
p('catalogs.docs.cecBioConsent', 'Formulario de Consentimiento Informado para uso futuro de muestras biológicas', 'Informed consent form for future use of biological samples');
p('catalogs.docs.cecInstruments', 'Instrumentos de recolección de datos', 'Data collection instruments');
p('catalogs.docs.cecRecruitment', 'Instrumentos de reclutamiento o publicidad', 'Recruitment or publicity instruments');
p('catalogs.docs.cecOtherCec', 'Resolución de otro CEC', 'Resolution from another ethics committee');
p('catalogs.docs.cecDirectorLetter', 'Modelo de carta para Director/a del establecimiento', 'Letter template for the institution director');
p('catalogs.docs.cecScientificApproval', 'Documento que certifique evaluación o aprobación científica', 'Document certifying scientific evaluation or approval');
p('catalogs.docs.cecuaProtocol', 'Proyecto de investigación in extenso', 'Full research project');
p('catalogs.docs.cecuaSummary', 'Resumen del proyecto en español', 'Project summary in Spanish');
p('catalogs.docs.cecuaAnimalProtocol', 'Protocolo de Cuidado y Uso de Animales o Protocolo POE', 'Animal care and use protocol or SOP protocol');
p('catalogs.docs.cecuaSupervision', 'Pauta de supervisión', 'Supervision guideline');
p('catalogs.docs.cecuaRecord', 'Hoja de registro', 'Record sheet');
p('catalogs.docs.cecuaScientific', 'Documento que certifique evaluación o aprobación científica', 'Document certifying scientific evaluation or approval');
p('catalogs.docs.cecuaFeasibility', 'Carta de factibilidad del Bioterio', 'Animal facility feasibility letter');
p('catalogs.docs.bioProtocol', 'Proyecto de investigación in extenso', 'Full research project');
p('catalogs.docs.bioSummary', 'Resumen del proyecto en español', 'Project summary in Spanish');
p('catalogs.docs.bioBiosafety', 'Protocolo de Bioseguridad', 'Biosafety protocol');
p('catalogs.docs.bioScientific', 'Documento que certifique evaluación o aprobación científica', 'Document certifying scientific evaluation or approval');

p('settings.title', 'Configuración del prototipo', 'Prototype settings');
p('settings.futureApi', 'API futura: {api}', 'Future API: {api}');
p('settings.storage', 'Los datos viven en localStorage del navegador. Restablecer vuelve a la semilla de demostración.', 'Data lives in the browser localStorage. Reset restores the demonstration seed.');
p('settings.reset', 'Restablecer datos demo', 'Reset demo data');

p('committees.title', 'Comités', 'Committees');
p('committees.independent', 'Cada comité mantiene estado, documentos, revisores, ciclos, sesión y resolución de forma independiente.', 'Each committee keeps status, documents, reviewers, cycles, session and resolution independently.');

p('empty.none.title', 'Sin elementos', 'No items');
p('empty.none.message', 'No hay información para mostrar en este momento.', 'There is no information to show at this time.');

p('validation.required', 'Campo obligatorio', 'Required field');
p('validation.email', 'Correo electrónico inválido', 'Invalid email address');
p('validation.minOne', 'Debe seleccionar al menos una opción', 'Select at least one option');
p('validation.justification', 'Debe justificar el cambio', 'A justification is required');
p('validation.allComments', 'Debe responder todas las observaciones', 'All comments must be answered');

p('errors.notFound', 'No encontrado', 'Not found');
p('errors.generic', 'Ha ocurrido un error.', 'An error occurred.');
p('errors.pdfOnly', 'Solo se aceptan archivos PDF.', 'Only PDF files are accepted.');
p('errors.fileLimit', 'El conjunto de archivos supera el límite visual de 100 MB.', 'The file set exceeds the visual 100 MB limit.');

p('privacy.committeeOnly', '🔒 Solo comité', '🔒 Committee only');
p('privacy.researcherVisible', '👤 Visible al investigador', '👤 Visible to the researcher');
p('privacy.publicable', '🌐 Público / publicable', '🌐 Public / publishable');

p('catalogs.roles.ADMINISTRATOR', 'Administración DISEI', 'DISEI Administration');
p('catalogs.roles.COMMITTEE_PRESIDENT', 'Presidente/a', 'Committee President');
p('catalogs.roles.EXECUTIVE_SECRETARY', 'Secretario/a ejecutivo/a', 'Executive Secretary');
p('catalogs.roles.COMMITTEE_MEMBER', 'Miembro comité', 'Committee Member');
p('catalogs.roles.RESEARCHER', 'Investigador/a', 'Researcher');

p('catalogs.committees.CEC', 'Comité Ético Científico', 'Scientific Ethics Committee');
p('catalogs.committees.CECUA', 'Comité Institucional de Ética en Cuidado y Uso de Animales', 'Institutional Animal Care and Use Ethics Committee');
p('catalogs.committees.BIOSEGURIDAD', 'Comité de Bioseguridad', 'Biosafety Committee');
p('catalogs.committees.NO_REQUIERE_COMITE', 'No requiere comité', 'No committee required');

p('catalogs.submissionTypes.NEW_PROJECT', 'Nuevo proyecto', 'New project');
p('catalogs.submissionTypes.VALIDATION', 'Validación de proyecto', 'Project validation');

p('catalogs.statuses.DRAFT', 'Borrador', 'Draft');
p('catalogs.statuses.PENDING_CLASSIFICATION', 'Pendiente de clasificación', 'Pending classification');
p('catalogs.statuses.IN_EVALUATION', 'En evaluación', 'Under review');
p('catalogs.statuses.MIXED', 'Evaluación mixta', 'Mixed review');
p('catalogs.statuses.PENDING_DOCUMENTS', 'Antecedentes pendientes', 'Pending documents');
p('catalogs.statuses.NOTIFIED', 'Notificado al comité', 'Notified to the committee');
p('catalogs.statuses.ADMISSIBILITY', 'Admisibilidad', 'Admissibility');
p('catalogs.statuses.RETURNED', 'Devuelto para subsanar', 'Returned for correction');
p('catalogs.statuses.PENDING_ASSIGNMENT', 'Pendiente de asignación', 'Pending assignment');
p('catalogs.statuses.PENDING_ACCEPTANCE', 'Pendiente de aceptación', 'Pending acceptance');
p('catalogs.statuses.IN_REVIEW', 'En revisión', 'Under review');
p('catalogs.statuses.WITH_OBSERVATIONS', 'Con observaciones', 'With comments');
p('catalogs.statuses.AWAITING_RESEARCHER', 'En espera respuesta IR', 'Awaiting PI response');
p('catalogs.statuses.PENDING_SESSION', 'Pendiente de sesión', 'Pending session');
p('catalogs.statuses.PENDING_RESOLUTION', 'Pendiente de resolución', 'Pending resolution');
p('catalogs.statuses.PENDING_PRESIDENT_VALIDATION', 'Pendiente validación presidente', 'Pending chair validation');
p('catalogs.statuses.APPROVED', 'Aprobado', 'Approved');
p('catalogs.statuses.REJECTED', 'Rechazado', 'Rejected');
p('catalogs.statuses.WITHDRAWN', 'Retirado', 'Withdrawn');
p('catalogs.statuses.EXEMPT', 'Exento', 'Exempt');
p('catalogs.statuses.TO_BE_DETERMINED', 'Por determinar', 'To be determined');
p('catalogs.statuses.INVALID', 'Inválido', 'Invalid');
p('catalogs.statuses.PENDING', 'Pendiente', 'Pending');
p('catalogs.statuses.CLASSIFIED', 'Clasificada', 'Classified');
p('catalogs.statuses.SUBMITTED', 'Enviada', 'Submitted');

p('catalogs.assignmentStatus.PENDING', 'Pendiente', 'Pending');
p('catalogs.assignmentStatus.ACCEPTED', 'Aceptada', 'Accepted');
p('catalogs.assignmentStatus.REJECTED', 'Rechazada', 'Declined');
p('catalogs.assignmentStatus.CONFLICT', 'Conflicto', 'Conflict');
p('catalogs.assignmentStatus.COMPLETED', 'Completada', 'Completed');

p('catalogs.observationStatus.DRAFT', 'Borrador', 'Draft');
p('catalogs.observationStatus.PUBLISHED', 'Publicada', 'Published');
p('catalogs.observationStatus.PENDING_RESPONSE', 'Pendiente de respuesta', 'Pending response');
p('catalogs.observationStatus.ANSWERED', 'Respondida', 'Answered');

p('catalogs.sessionTypes.ORDINARY', 'Ordinaria', 'Ordinary');
p('catalogs.sessionTypes.EXTRAORDINARY', 'Extraordinaria', 'Extraordinary');
p('catalogs.sessionStatus.SCHEDULED', 'Programada', 'Scheduled');
p('catalogs.sessionStatus.IN_PROGRESS', 'En curso', 'In progress');
p('catalogs.sessionStatus.CLOSED', 'Cerrada', 'Closed');

p('catalogs.decisions.RECEIVED', 'Recibido', 'Received');
p('catalogs.decisions.IN_REVIEW', 'En revisión', 'Under review');
p('catalogs.decisions.WITH_OBSERVATIONS', 'Con observaciones', 'With comments');
p('catalogs.decisions.AWAITING_IR', 'En espera respuesta IR', 'Awaiting PI response');
p('catalogs.decisions.APPROVED', 'Aprobado', 'Approved');
p('catalogs.decisions.REJECTED', 'Rechazado', 'Rejected');
p('catalogs.decisions.WITHDRAWN', 'Retirado', 'Withdrawn');
p('catalogs.decisions.EXEMPT', 'Exento', 'Exempt');
p('catalogs.decisions.TO_BE_DETERMINED', 'Por determinar', 'To be determined');
p('catalogs.decisions.INVALID', 'Inválido', 'Invalid');
p('catalogs.decisions.PENDING', 'Pendiente', 'Pending');

p('catalogs.resolutionKinds.APPROVED', 'Aprobado', 'Approved');
p('catalogs.resolutionKinds.REJECTED', 'Rechazado', 'Rejected');
p('catalogs.resolutionKinds.EXEMPT', 'Exento', 'Exempt');
p('catalogs.resolutionKinds.WITHDRAWN', 'Retirado', 'Withdrawn');
p('catalogs.resolutionKinds.TO_BE_DETERMINED', 'Por determinar', 'To be determined');

p('catalogs.resolutionWorkflow.DRAFT', 'Borrador', 'Draft');
p('catalogs.resolutionWorkflow.PENDING_PRESIDENT', 'Pendiente presidencia', 'Pending chair');
p('catalogs.resolutionWorkflow.VALIDATED', 'Validada', 'Validated');
p('catalogs.resolutionWorkflow.ISSUED', 'Emitida', 'Issued');

p('catalogs.semaphore.VIGENTE', 'Vigente', 'Active');
p('catalogs.semaphore.PROXIMO', 'Próximo a vencer', 'Expiring soon');
p('catalogs.semaphore.VENCIDO', 'Vencido', 'Expired');

p('catalogs.availability.ALTA', 'Alta', 'High');
p('catalogs.availability.MEDIA', 'Media', 'Medium');
p('catalogs.availability.BAJA', 'Baja', 'Low');

p('catalogs.requiredDocs.protocolo', 'Protocolo de investigación', 'Research protocol');
p('catalogs.requiredDocs.consentimiento', 'Consentimiento informado', 'Informed consent');
p('catalogs.requiredDocs.instrumentos', 'Instrumentos de recolección', 'Data collection instruments');
p('catalogs.requiredDocs.carta-ir', 'Carta del investigador responsable', 'Principal investigator letter');
p('catalogs.requiredDocs.autorizacion', 'Autorización institucional', 'Institutional authorization');
p('catalogs.requiredDocs.protocolo-animal', 'Protocolo de uso de animales', 'Animal use protocol');
p('catalogs.requiredDocs.justificacion', 'Justificación científica', 'Scientific justification');
p('catalogs.requiredDocs.bienestar', 'Plan de bienestar animal', 'Animal welfare plan');
p('catalogs.requiredDocs.capacitacion', 'Constancia de capacitación del equipo', 'Team training certificate');
p('catalogs.requiredDocs.instalaciones', 'Descripción de instalaciones', 'Facility description');
p('catalogs.requiredDocs.protocolo-bio', 'Protocolo de bioseguridad', 'Biosafety protocol');
p('catalogs.requiredDocs.fichas', 'Fichas de seguridad de reactivos', 'Reagent safety data sheets');
p('catalogs.requiredDocs.residuos', 'Plan de manejo de residuos', 'Waste management plan');
p('catalogs.requiredDocs.lab', 'Autorización de laboratorio', 'Laboratory authorization');

writeFileSync(join(root, 'es.json'), `${JSON.stringify(es, null, 2)}\n`);
writeFileSync(join(root, 'en.json'), `${JSON.stringify(en, null, 2)}\n`);
console.log('Wrote ethics-uss es.json and en.json');
