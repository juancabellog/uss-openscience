import { StoreState, User } from '../core/models/ethics.models';

export const DEMO_USERS: User[] = [
  { id: 'u-admin', displayName: 'Camila Herrera', email: 'camila.herrera@uss.cl', role: 'ADMINISTRATOR', committees: [] },
  { id: 'u-pres-cecua', displayName: 'Dra. Pilar Núñez', email: 'pilar.nunez@uss.cl', role: 'COMMITTEE_PRESIDENT', committees: ['CECUA'] },
  { id: 'u-sec-cecua', displayName: 'Rodrigo Pino', email: 'rodrigo.pino@uss.cl', role: 'EXECUTIVE_SECRETARY', committees: ['CECUA'] },
  { id: 'u-rev-4', displayName: 'Dr. Ignacio Bravo', email: 'ignacio.bravo@uss.cl', role: 'COMMITTEE_MEMBER', committees: ['CECUA'], specialty: 'Medicina veterinaria', activeLoad: 2, availability: 'MEDIA' },
  { id: 'u-rev-5', displayName: 'Dra. Sofía Lagos', email: 'sofia.lagos@uss.cl', role: 'COMMITTEE_MEMBER', committees: ['CECUA'], specialty: 'Fisiología animal', activeLoad: 2, availability: 'ALTA' },
  { id: 'u-inv-1', displayName: 'Juan Pérez', email: 'juan.perez@uss.cl', role: 'RESEARCHER', committees: [] }
];

export function createSeed(): StoreState {
  return {
    users: DEMO_USERS,
    nextResolutionSeq: 24,
    projects: [
      {
        id: 'prj-1',
        code: 'ETI-2026-001',
        title: 'Neurocognición 2027',
        fundingSource: 'FONDECYT Regular',
        fundingStatus: 'AWARDED',
        researcherId: 'u-inv-1',
        type: 'NEW_PROJECT',
        createdAt: '2026-03-12T09:10:00',
        updatedAt: '2026-10-18T16:40:00'
      },
      {
        id: 'prj-2',
        code: 'ETI-2026-002',
        title: 'Modelo murino de inflamación intestinal',
        fundingSource: 'FONDECYT Iniciación',
        fundingStatus: 'AWARDED',
        researcherId: 'u-inv-1',
        type: 'NEW_PROJECT',
        createdAt: '2026-06-02T11:00:00',
        updatedAt: '2026-10-15T10:20:00'
      },
      {
        id: 'prj-3',
        code: 'ETI-2026-003',
        title: 'Bienestar estudiantil y carga académica',
        fundingSource: 'Fondos Interno USS',
        fundingStatus: 'APPLYING',
        researcherId: 'u-inv-1',
        type: 'NEW_PROJECT',
        createdAt: '2026-10-18T08:30:00',
        updatedAt: '2026-10-18T08:45:00'
      },
      {
        id: 'prj-4',
        code: 'ETI-2025-018',
        title: 'Seguimiento clínico de rehabilitación cardiaca',
        fundingSource: 'FONIS',
        fundingStatus: 'AWARDED',
        researcherId: 'u-inv-1',
        type: 'NEW_PROJECT',
        createdAt: '2025-11-02T10:00:00',
        updatedAt: '2025-11-20T12:00:00'
      },
      {
        id: 'prj-5',
        code: 'ETI-2026-004',
        title: 'Validación USS de ensayo clínico multicéntrico',
        fundingSource: 'FONDECYT Postdoctoral',
        fundingCode: '3240123',
        researcherId: 'u-inv-1',
        type: 'VALIDATION',
        createdAt: '2026-09-04T14:00:00',
        updatedAt: '2026-09-20T09:15:00'
      },
      {
        id: 'prj-6',
        code: 'ETI-2026-005',
        title: 'Estudio piloto de microbioma oral en estudiantes de odontología',
        fundingSource: 'FONIS',
        researcherId: 'u-inv-1',
        type: 'VALIDATION',
        createdAt: '2026-10-16T10:00:00',
        updatedAt: '2026-10-16T11:20:00'
      }
    ],
    submissions: [
      {
        id: 'sub-1',
        projectId: 'prj-1',
        type: 'NEW_PROJECT',
        status: 'CLASSIFIED',
        payload: { irName: 'Juan', irLastName: 'Pérez' },
        classification: { automatic: ['CEC', 'BIOSEGURIDAD'], validated: ['CEC', 'BIOSEGURIDAD'], validatedBy: 'u-admin', validatedAt: '2026-03-14T10:00:00' },
        submittedAt: '2026-03-12T11:00:00',
        createdAt: '2026-03-12T09:10:00',
        updatedAt: '2026-03-14T10:00:00'
      },
      {
        id: 'sub-2',
        projectId: 'prj-2',
        type: 'NEW_PROJECT',
        status: 'CLASSIFIED',
        payload: { irName: 'Juan', irLastName: 'Pérez' },
        classification: { automatic: ['CECUA'], validated: ['CECUA'], validatedBy: 'u-admin', validatedAt: '2026-06-04T09:00:00' },
        submittedAt: '2026-06-02T12:00:00',
        createdAt: '2026-06-02T11:00:00',
        updatedAt: '2026-06-04T09:00:00'
      },
      {
        id: 'sub-3',
        projectId: 'prj-3',
        type: 'NEW_PROJECT',
        status: 'SUBMITTED',
        formVersion: 'NEW_PROJECT_V1',
        // DEMO/MOCK: datos ficticios para completar las 26 preguntas. No son información institucional.
        payload: {
          title: 'Bienestar estudiantil y carga académica',
          fundingSource: 'Fondos Interno USS',
          fundingStatus: 'APPLYING',
          irName: 'Juan',
          irLastName: 'Pérez',
          irEmail: 'juan.perez@uss.cl',
          campus: 'Santiago - Bellavista',
          faculty: 'Educación',
          program: 'Pedagogía en Educación Media',
          irPosition: 'Docente',
          sponsorName: '',
          sponsorLastName: '',
          sponsorEmail: '',
          sponsorCampus: '',
          sponsorFaculty: '',
          sponsorPosition: '',
          proposalFrame: 'Es un proyecto original',
          activityTypes: ['Proyecto de Investigación', 'Proyecto de Vinculación con el Medio'],
          activityFrame: 'Es un proyecto original',
          ussPlace: 'Facultad de Educación, Campus Bellavista',
          externalPlace: 'Establecimientos educacionales de la Región Metropolitana (convenio en gestión)',
          team: ['Estudiantes de pregrado', 'Docentes investigadores'],
          products: [
            'Envío/publicación en revista de especialidad',
            'Postulación/presentación en congresos, simposios o seminarios'
          ],
          studyObjects: ['cec-surveys', 'cec-vulnerable'],
          truth: true,
          singapore: true
        },
        classification: { automatic: ['CEC'], validated: null },
        submittedAt: '2026-10-18T08:45:00',
        createdAt: '2026-10-18T08:30:00',
        updatedAt: '2026-10-18T08:45:00'
      },
      {
        id: 'sub-4',
        projectId: 'prj-4',
        type: 'NEW_PROJECT',
        status: 'CLASSIFIED',
        payload: { irName: 'Juan', irLastName: 'Pérez' },
        classification: { automatic: ['CEC'], validated: ['CEC'], validatedBy: 'u-admin', validatedAt: '2025-11-05T09:00:00' },
        submittedAt: '2025-11-02T11:00:00',
        createdAt: '2025-11-02T10:00:00',
        updatedAt: '2025-11-05T09:00:00'
      },
      {
        id: 'sub-5',
        projectId: 'prj-5',
        type: 'VALIDATION',
        status: 'CLASSIFIED',
        payload: { committees: ['CEC'], institution: 'Universidad de Chile' },
        classification: { automatic: ['CEC'], validated: ['CEC'], validatedBy: 'u-admin', validatedAt: '2026-09-08T10:00:00' },
        submittedAt: '2026-09-04T15:00:00',
        createdAt: '2026-09-04T14:00:00',
        updatedAt: '2026-09-08T10:00:00'
      },
      {
        id: 'sub-6',
        projectId: 'prj-6',
        type: 'VALIDATION',
        status: 'SUBMITTED',
        formVersion: 'VALIDATION_V1',
        // DEMO/MOCK: solicitud de validación completa para revisar las 19 preguntas.
        payload: {
          irName: 'Juan',
          irLastName: 'Pérez',
          irEmail: 'juan.perez@uss.cl',
          category: 'Investigador',
          faculty: 'Odontología',
          campus: 'Santiago - Los Leones',
          fundingSource: 'FONIS',
          title: 'Estudio piloto de microbioma oral en estudiantes de odontología',
          fundingCode: 'SA26I00018',
          committees: ['CEC', 'BIOSEGURIDAD'],
          institution: 'Pontificia Universidad Católica de Chile',
          sameTitle: 'yes',
          teamChanged: 'no',
          procedureChanged: 'no',
          continuesOrigin: 'yes',
          startDate: '2026-11-15',
          carta: { name: 'carta-validacion-demo.pdf', type: 'PDF', sizeLabel: '0.4 MB' },
          supportDocs: ['acta', 'protocolo', 'adjudicacion'],
          uploads: [
            { name: 'acta-origen-demo.pdf', type: 'PDF', sizeLabel: '1.2 MB', key: 'acta' },
            { name: 'protocolo-aprobado-demo.pdf', type: 'PDF', sizeLabel: '3.8 MB', key: 'protocolo' },
            { name: 'carta-adjudicacion-demo.pdf', type: 'PDF', sizeLabel: '0.6 MB', key: 'adjudicacion' }
          ]
        },
        classification: { automatic: ['CEC', 'BIOSEGURIDAD'], validated: null },
        submittedAt: '2026-10-16T11:20:00',
        createdAt: '2026-10-16T10:00:00',
        updatedAt: '2026-10-16T11:20:00'
      }
    ],
    cases: [
      {
        id: 'case-1-cec',
        code: 'CEC-2026-001',
        projectId: 'prj-1',
        submissionId: 'sub-1',
        committee: 'CEC',
        status: 'APPROVED',
        receivedAt: '2026-03-14T10:00:00',
        notifiedAt: '2026-03-20T09:00:00',
        sessionId: 'ses-1',
        reviewerIds: ['u-rev-4', 'u-rev-5'],
        currentCycle: 1,
        packageFrozen: true,
        processKind: 'INITIAL',
        processStatus: 'RESOLVED',
        resolutionId: 'res-1',
        approvalIssuedAt: '2026-09-15T12:00:00',
        approvalExpiresAt: '2027-09-15T12:00:00'
      },
      {
        id: 'case-1-bio',
        code: 'BIO-2026-001',
        projectId: 'prj-1',
        submissionId: 'sub-1',
        committee: 'BIOSEGURIDAD',
        status: 'IN_REVIEW',
        receivedAt: '2026-03-14T10:00:00',
        notifiedAt: '2026-09-01T09:00:00',
        sessionId: 'ses-3',
        reviewerIds: ['u-rev-4', 'u-rev-5'],
        currentCycle: 1,
        packageFrozen: true
      },
      {
        id: 'case-2',
        code: 'CECUA-2026-004',
        projectId: 'prj-2',
        submissionId: 'sub-2',
        committee: 'CECUA',
        status: 'AWAITING_RESEARCHER',
        receivedAt: '2026-06-04T09:00:00',
        notifiedAt: '2026-06-12T11:00:00',
        sessionId: 'ses-2',
        reviewerIds: ['u-rev-4', 'u-rev-5'],
        currentCycle: 2,
        packageFrozen: true
      },
      // DEMO/MOCK: expediente ficticio de admisibilidad del mismo comité CECUA, para que la secretaría demo pueda recorrerlo.
      {
        id: 'case-adm',
        code: 'CECUA-2026-014',
        projectId: 'prj-2',
        submissionId: 'sub-2',
        committee: 'CECUA',
        status: 'ADMISSIBILITY',
        receivedAt: '2026-10-20T09:00:00',
        notifiedAt: '2026-10-20T09:30:00',
        reviewerIds: [],
        currentCycle: 1,
        packageFrozen: true
      },
      // DEMO/MOCK: expediente ficticio pendiente de presidencia. No está en la clasificación original de prj-1.
      {
        id: 'case-pres',
        code: 'CECUA-2026-021',
        projectId: 'prj-1',
        submissionId: 'sub-1',
        committee: 'CECUA',
        status: 'PENDING_PRESIDENT_VALIDATION',
        receivedAt: '2026-08-01T09:00:00',
        notifiedAt: '2026-08-02T09:00:00',
        reviewerIds: ['u-rev-4', 'u-rev-5'],
        currentCycle: 1,
        packageFrozen: true,
        processKind: 'INITIAL',
        resolutionId: 'res-pres'
      },
      {
        id: 'case-4',
        code: 'CEC-2025-018',
        projectId: 'prj-4',
        submissionId: 'sub-4',
        committee: 'CEC',
        status: 'APPROVED',
        receivedAt: '2025-11-05T09:00:00',
        notifiedAt: '2025-11-08T09:00:00',
        reviewerIds: ['u-rev-4', 'u-rev-5'],
        currentCycle: 1,
        packageFrozen: false,
        processKind: 'AMENDMENT',
        processStatus: 'DRAFT',
        resolutionId: 'res-4',
        approvalIssuedAt: '2025-11-20T12:00:00',
        approvalExpiresAt: '2026-11-20T12:00:00'
      },
      {
        id: 'case-5',
        code: 'CEC-2026-011',
        projectId: 'prj-5',
        submissionId: 'sub-5',
        committee: 'CEC',
        status: 'PENDING_DOCUMENTS',
        receivedAt: '2026-09-08T10:00:00',
        reviewerIds: [],
        currentCycle: 1,
        packageFrozen: false
      }
    ],
    documents: [
      { id: 'doc-1', ownerType: 'CASE', ownerId: 'case-1-cec', name: 'Protocolo Neurocognición.pdf', kind: 'REQUIRED', requirementKey: 'cec-protocol', sizeLabel: '2.1 MB', uploadedAt: '2026-03-18T10:00:00', version: 3 },
      { id: 'doc-1b', ownerType: 'CASE', ownerId: 'case-1-cec', name: 'Protocolo Neurocognición v2.pdf', kind: 'REQUIRED', requirementKey: 'cec-protocol', sizeLabel: '1.8 MB', uploadedAt: '2026-03-10T10:00:00', version: 2 },
      { id: 'doc-cec-letter', ownerType: 'CASE', ownerId: 'case-1-cec', name: 'Carta presidente.pdf', kind: 'REQUIRED', requirementKey: 'cec-letter-president', sizeLabel: '0.2 MB', uploadedAt: '2026-03-18T10:00:00', version: 1 },
      { id: 'doc-cec-commitment', ownerType: 'CASE', ownerId: 'case-1-cec', name: 'Carta compromiso.pdf', kind: 'REQUIRED', requirementKey: 'cec-commitment', sizeLabel: '0.2 MB', uploadedAt: '2026-03-18T10:00:00', version: 1 },
      { id: 'doc-cec-summary', ownerType: 'CASE', ownerId: 'case-1-cec', name: 'Resumen.pdf', kind: 'REQUIRED', requirementKey: 'cec-summary', sizeLabel: '0.3 MB', uploadedAt: '2026-03-18T10:00:00', version: 1 },
      { id: 'doc-cec-ethics', ownerType: 'CASE', ownerId: 'case-1-cec', name: 'Analisis etico.pdf', kind: 'REQUIRED', requirementKey: 'cec-ethics-analysis', sizeLabel: '0.4 MB', uploadedAt: '2026-03-18T10:00:00', version: 1 },
      { id: 'doc-cec-cv', ownerType: 'CASE', ownerId: 'case-1-cec', name: 'CV equipo.pdf', kind: 'REQUIRED', requirementKey: 'cec-cv', sizeLabel: '0.8 MB', uploadedAt: '2026-03-18T10:00:00', version: 1 },
      { id: 'doc-cec-support', ownerType: 'CASE', ownerId: 'case-1-cec', name: 'Carta respaldo.pdf', kind: 'REQUIRED', requirementKey: 'cec-support-letter', sizeLabel: '0.2 MB', uploadedAt: '2026-03-18T10:00:00', version: 1 },
      { id: 'doc-bio-1', ownerType: 'CASE', ownerId: 'case-1-bio', name: 'Proyecto in extenso.pdf', kind: 'REQUIRED', requirementKey: 'bio-protocol', sizeLabel: '2.1 MB', uploadedAt: '2026-03-18T10:00:00', version: 1 },
      { id: 'doc-bio-2', ownerType: 'CASE', ownerId: 'case-1-bio', name: 'Resumen bioseguridad.pdf', kind: 'REQUIRED', requirementKey: 'bio-summary', sizeLabel: '0.3 MB', uploadedAt: '2026-03-18T10:00:00', version: 1 },
      { id: 'doc-bio-3', ownerType: 'CASE', ownerId: 'case-1-bio', name: 'Protocolo bioseguridad.pdf', kind: 'REQUIRED', requirementKey: 'bio-biosafety', sizeLabel: '1.1 MB', uploadedAt: '2026-03-18T10:00:00', version: 1 },
      { id: 'doc-2', ownerType: 'CASE', ownerId: 'case-2', name: 'Protocolo animal v2.pdf', kind: 'REQUIRED', requirementKey: 'cecua-animal-protocol', sizeLabel: '3.4 MB', uploadedAt: '2026-06-10T10:00:00', version: 2 },
      { id: 'doc-val-letter', ownerType: 'SUBMISSION', ownerId: 'sub-5', name: 'Carta de solicitud de validacion.pdf', kind: 'REQUIRED', requirementKey: 'validation-letter', sizeLabel: '0.4 MB', uploadedAt: '2026-09-04T15:00:00', version: 1 },
      { id: 'doc-val-protocol', ownerType: 'CASE', ownerId: 'case-5', name: 'Protocolo validacion.pdf', kind: 'REQUIRED', requirementKey: 'cec-protocol', sizeLabel: '2.4 MB', uploadedAt: '2026-09-04T15:00:00', version: 1 }
    ],
    assignments: [
      { id: 'asg-1a', caseId: 'case-1-bio', reviewerId: 'u-rev-4', slot: 1, status: 'COMPLETED', assignedAt: '2026-09-02T09:00:00', decidedAt: '2026-09-03T08:00:00', sessionId: 'ses-3' },
      { id: 'asg-1b', caseId: 'case-1-bio', reviewerId: 'u-rev-5', slot: 2, status: 'COMPLETED', assignedAt: '2026-09-02T09:00:00', decidedAt: '2026-09-03T09:10:00', sessionId: 'ses-3' },
      { id: 'asg-2a', caseId: 'case-2', reviewerId: 'u-rev-4', slot: 1, status: 'COMPLETED', assignedAt: '2026-06-14T09:00:00', decidedAt: '2026-06-15T09:00:00', sessionId: 'ses-2' },
      { id: 'asg-2b', caseId: 'case-2', reviewerId: 'u-rev-5', slot: 2, status: 'COMPLETED', assignedAt: '2026-06-14T09:00:00', decidedAt: '2026-06-15T10:00:00', sessionId: 'ses-2' }
    ],
    reviews: [
      {
        id: 'rev-1a',
        assignmentId: 'asg-1a',
        caseId: 'case-1-bio',
        reviewerId: 'u-rev-4',
        cycleNumber: 1,
        criteria: { metodologia: 'Describir el nivel de contención.', riesgos: 'Falta el plan de residuos.', observaciones: 'Precisar los agentes biológicos.' },
        submittedAt: '2026-09-20T18:00:00',
        privacy: 'COMMITTEE_ONLY'
      },
      {
        id: 'rev-1b',
        assignmentId: 'asg-1b',
        caseId: 'case-1-bio',
        reviewerId: 'u-rev-5',
        cycleNumber: 1,
        criteria: { metodologia: 'El diseño experimental es suficiente.', riesgos: 'Incluir contingencia ante derrame.', observaciones: 'Ajustar la capacitación del equipo.' },
        submittedAt: '2026-09-21T11:00:00',
        privacy: 'COMMITTEE_ONLY'
      },
      {
        id: 'rev-2a',
        assignmentId: 'asg-2a',
        caseId: 'case-2',
        reviewerId: 'u-rev-4',
        cycleNumber: 1,
        criteria: { metodologia: 'Falta justificar el número de animales.', bienestar: 'Completar plan de analgesia.' },
        submittedAt: '2026-07-01T18:00:00',
        privacy: 'COMMITTEE_ONLY'
      },
      {
        id: 'rev-2b',
        assignmentId: 'asg-2b',
        caseId: 'case-2',
        reviewerId: 'u-rev-5',
        cycleNumber: 1,
        criteria: { metodologia: 'Adecuada, con observaciones menores.', bienestar: 'Clarificar criterios de punto final.' },
        submittedAt: '2026-07-02T11:00:00',
        privacy: 'COMMITTEE_ONLY'
      }
    ],
    cycles: [
      { id: 'cyc-1-cec', caseId: 'case-1-cec', cycleNumber: 1, openedAt: '2026-03-21T09:00:00', closedAt: '2026-09-15T12:00:00' },
      { id: 'cyc-1-bio', caseId: 'case-1-bio', cycleNumber: 1, openedAt: '2026-09-02T09:00:00' },
      { id: 'cyc-2', caseId: 'case-2', cycleNumber: 1, openedAt: '2026-06-14T09:00:00', closedAt: '2026-10-15T10:20:00', status: 'PUBLISHED' },
      { id: 'cyc-2b', caseId: 'case-2', cycleNumber: 2, openedAt: '2026-10-15T10:21:00', status: 'OPEN' },
      { id: 'cyc-4', caseId: 'case-4', cycleNumber: 1, openedAt: '2025-11-08T09:00:00', closedAt: '2025-11-20T12:00:00' },
      { id: 'cyc-pres', caseId: 'case-pres', cycleNumber: 1, openedAt: '2026-08-02T09:00:00', closedAt: '2026-10-01T09:00:00', status: 'PUBLISHED' }
    ],
    observations: [
      {
        id: 'obs-1',
        code: 'OBS-001',
        caseId: 'case-2',
        cycleNumber: 1,
        topic: 'Tamaño muestral',
        text: 'Justifique el número de animales y la potencia estadística utilizada.',
        relatedDocuments: ['Protocolo animal'],
        status: 'PENDING_RESPONSE',
        privacy: 'RESEARCHER_VISIBLE'
      },
      {
        id: 'obs-2',
        code: 'OBS-002',
        caseId: 'case-2',
        cycleNumber: 1,
        topic: 'Bienestar animal',
        text: 'Complete el plan de analgesia y los criterios de punto final humanitario.',
        relatedDocuments: ['Plan de bienestar'],
        status: 'PENDING_RESPONSE',
        privacy: 'RESEARCHER_VISIBLE'
      }
    ],
    responses: [],
    sessions: [
      { id: 'ses-1', committee: 'CEC', date: '2026-09-10', time: '10:00', type: 'ORDINARY', status: 'CLOSED', caseIds: ['case-1-cec'] },
      { id: 'ses-2', committee: 'CECUA', date: '2026-10-28', time: '09:30', type: 'ORDINARY', status: 'SCHEDULED', caseIds: ['case-2'] },
      { id: 'ses-3', committee: 'BIOSEGURIDAD', date: '2026-11-05', time: '11:00', type: 'ORDINARY', status: 'SCHEDULED', caseIds: ['case-1-bio'] }
    ],
    resolutions: [
      {
        id: 'res-1',
        correlative: '0021_14032026',
        caseId: 'case-1-cec',
        kind: 'APPROVED',
        decisionCatalog: 'Aprobado',
        text: 'Se aprueba el protocolo de Neurocognición 2027, con las salvaguardas de consentimiento informado presentadas.',
        validityNote: 'Vigencia de 12 meses desde la emisión.',
        workflow: 'ISSUED',
        preparedBy: 'u-sec-cecua',
        validatedBy: 'u-pres-cecua',
        issuedAt: '2026-09-15T12:00:00',
        intakeDate: '2026-03-14'
      },
      {
        id: 'res-4',
        correlative: '0018_05112025',
        caseId: 'case-4',
        kind: 'APPROVED',
        decisionCatalog: 'Aprobado',
        text: 'Se aprueba el seguimiento clínico de rehabilitación cardiaca.',
        validityNote: 'Vigencia de 12 meses desde la emisión.',
        workflow: 'ISSUED',
        preparedBy: 'u-sec-cecua',
        validatedBy: 'u-pres-cecua',
        issuedAt: '2025-11-20T12:00:00',
        intakeDate: '2025-11-05'
      },
      // DEMO/MOCK: resolución ficticia asociada solo a case-pres.
      {
        id: 'res-pres',
        correlative: '0022_01102026',
        caseId: 'case-pres',
        kind: 'APPROVED',
        decisionCatalog: 'Aprobado',
        text: 'Se propone aprobar el componente animal del protocolo, sujeto a validación de presidencia.',
        validityNote: 'Vigencia propuesta de 12 meses desde la emisión.',
        workflow: 'PENDING_PRESIDENT',
        preparedBy: 'u-sec-cecua',
        intakeDate: '2026-08-01',
        signatureStatus: 'NONE',
        stampStatus: 'NONE',
        processKind: 'INITIAL'
      }
    ],
    notifications: [
      { id: 'n-1', userId: 'u-inv-1', title: 'Resolución emitida', body: 'CEC aprobó Neurocognición 2027.', createdAt: '2026-09-15T12:05:00', read: false, link: '/proyectos/prj-1' },
      { id: 'n-2', userId: 'u-inv-1', title: 'Observaciones disponibles', body: 'CECUA publicó observaciones para su proyecto.', createdAt: '2026-10-15T10:20:00', read: false, link: '/proyectos/prj-2' },
      { id: 'n-3', userId: 'u-admin', title: 'Solicitud recibida', body: 'Bienestar estudiantil y carga académica espera clasificación.', createdAt: '2026-10-18T08:46:00', read: false, link: '/solicitudes/sub-3' },
      { id: 'n-4', userId: 'u-rev-4', title: 'Asignación de revisión', body: 'Se le asignó Bioseguridad de Neurocognición 2027.', createdAt: '2026-09-02T09:01:00', read: true, link: '/revisiones/asg-1a' },
      { id: 'n-5', userId: 'u-inv-1', title: 'Aprobación próxima a vencer', body: 'Seguimiento clínico vence el 20/11/2026.', createdAt: '2026-10-01T08:00:00', read: false, link: '/proyectos/prj-4' }
    ],
    audit: [
      { id: 'aud-1', actorId: 'u-inv-1', actorName: 'Juan Pérez', role: 'RESEARCHER', date: '2026-03-12T09:14:00', action: 'CREATE_SUBMISSION', objectType: 'EthicsSubmission', objectId: 'sub-1', description: 'Creó solicitud', projectId: 'prj-1' },
      { id: 'aud-2', actorId: 'u-admin', actorName: 'Camila Herrera', role: 'ADMINISTRATOR', date: '2026-03-14T10:22:00', action: 'VALIDATE_CLASSIFICATION', objectType: 'EthicsClassification', objectId: 'sub-1', description: 'Confirmó clasificación automática', newValue: 'CEC, BIOSEGURIDAD', projectId: 'prj-1' },
      { id: 'aud-3', actorId: 'u-inv-1', actorName: 'Juan Pérez', role: 'RESEARCHER', date: '2026-03-20T12:31:00', action: 'NOTIFY_COMMITTEE', objectType: 'CommitteeCase', objectId: 'case-1-cec', description: 'Notificó Comité Ético Científico', projectId: 'prj-1' },
      { id: 'aud-4', actorId: 'u-sec-cecua', actorName: 'Rodrigo Pino', role: 'EXECUTIVE_SECRETARY', date: '2026-10-15T10:20:00', action: 'OBSERVATIONS_PUBLISHED', objectType: 'Observation', objectId: 'case-2', caseId: 'case-2', description: 'Publicó observaciones al investigador', projectId: 'prj-2' }
    ],
    communications: []
  };
}
