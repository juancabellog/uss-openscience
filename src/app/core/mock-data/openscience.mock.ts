import {
  DataQualityIssue,
  InstitutionalIndicator,
  IntegrationStatus,
  OperationalMetric,
  Researcher,
  ResearchOutput
} from '../models/openscience.models';

/** Todos los valores de este archivo son MOCK / DEMO. No son cifras oficiales USS. */
export const MOCK_LAST_UPDATED = '2026-09-15T14:30:00-03:00';

export const MOCK_RESEARCHERS: Researcher[] = [
  {
    id: 1001,
    uuid: 'a3c1e8d2-4b71-4f0a-9c12-8d44e11a1001',
    firstName: 'Camila',
    lastName: 'Rojas Soto',
    email: 'camila.rojas@uss.cl',
    position: 'Profesora asociada',
    faculty: 'Medicina y Ciencia',
    orcid: '0000-0002-1841-2291',
    orcidVerified: true,
    primaryId: '60271101',
    scopusIds: ['57202352366'],
    scopusAffiliations: [{ affiliationId: '60000636', affiliationName: 'Universidad San Sebastián' }],
    publicationCount: 18,
    dataQualityStatus: 'ok'
  },
  {
    id: 1002,
    uuid: 'b7f4a91c-22e0-4aa3-81d8-0c55b22c1002',
    firstName: 'Andrés',
    lastName: 'Pérez Lagos',
    email: 'andres.perez@uss.cl',
    position: 'Profesor titular',
    faculty: 'Ingeniería, Arquitectura y Diseño',
    orcid: '0000-0003-4412-8804',
    orcidVerified: false,
    primaryId: '60271102',
    scopusIds: ['7005181514', '7005181514'],
    scopusAffiliations: [
      { affiliationId: '60000636', affiliationName: 'Universidad San Sebastián' },
      { affiliationId: '60005881', affiliationName: 'Universidad Mayor' }
    ],
    publicationCount: 41,
    dataQualityStatus: 'review'
  },
  {
    id: 1003,
    uuid: 'c0d8bb44-91a2-4e77-b113-77a1c33d1003',
    firstName: 'María José',
    lastName: 'González Vidal',
    email: 'maria.gonzalez@uss.cl',
    position: 'Profesora asistente',
    faculty: 'Psicología',
    primaryId: '60271103',
    scopusIds: [],
    scopusAffiliations: [],
    publicationCount: 7,
    dataQualityStatus: 'pending'
  },
  {
    id: 1004,
    uuid: 'd19eaa01-6c54-4b90-a8e2-12bbd44e1004',
    firstName: 'Felipe',
    lastName: 'Muñoz Contreras',
    email: 'felipe.munoz@uss.cl',
    position: 'Investigador postdoctoral',
    faculty: 'Odontología y Ciencias de la Rehabilitación',
    orcid: '0000-0001-9982-4410',
    orcidVerified: true,
    primaryId: '60271104',
    scopusIds: ['57218644112'],
    scopusAffiliations: [{ affiliationId: '60000636', affiliationName: 'Universidad San Sebastián' }],
    publicationCount: 12,
    dataQualityStatus: 'ok'
  },
  {
    id: 1005,
    uuid: 'e2aa1109-88c1-4d23-9f70-55ccd55f1005',
    firstName: 'Daniela',
    lastName: 'Silva Herrera',
    email: 'daniela.silva@uss.cl',
    position: 'Profesora asociada',
    faculty: 'Educación',
    orcid: '0000-0002-7710-3398',
    orcidVerified: false,
    primaryId: '60271105',
    scopusIds: ['57202352366'],
    scopusAffiliations: [{ affiliationId: '60000636', affiliationName: 'Universidad San Sebastián' }],
    publicationCount: 9,
    dataQualityStatus: 'review'
  },
  {
    id: 1006,
    uuid: 'f31bb220-19d4-4e01-8a61-66dde66a1006',
    firstName: 'Rodrigo',
    lastName: 'Cáceres Pinto',
    email: 'rodrigo.caceres@uss.cl',
    position: 'Profesor asistente',
    faculty: 'Derecho y Gobierno',
    orcid: '0000-0003-1204-7781',
    orcidVerified: true,
    primaryId: '60271106',
    scopusIds: ['57194422109'],
    scopusAffiliations: [{ affiliationId: '60000636', affiliationName: 'Universidad San Sebastián' }],
    publicationCount: 15,
    dataQualityStatus: 'ok'
  },
  {
    id: 1007,
    uuid: 'a44cc331-2ae5-4f12-9b72-77eef77b1007',
    firstName: 'Paula',
    lastName: 'Navarro Díaz',
    email: 'paula.navarro@uss.cl',
    position: 'Profesora titular',
    faculty: 'Enfermería',
    primaryId: '60271107',
    scopusIds: ['36611209877', '57290114420'],
    scopusAffiliations: [{ affiliationId: '60000636', affiliationName: 'Universidad San Sebastián' }],
    publicationCount: 28,
    dataQualityStatus: 'review'
  },
  {
    id: 1008,
    uuid: 'b55dd442-3bf6-4023-ac83-88ff088c1008',
    firstName: 'Ignacio',
    lastName: 'Vargas Méndez',
    email: 'ignacio.vargas@uss.cl',
    position: 'Profesor asociado',
    faculty: 'Economía y Negocios',
    orcid: '0000-0001-5502-6677',
    orcidVerified: true,
    primaryId: '60271108',
    scopusIds: ['24711900331'],
    scopusAffiliations: [{ affiliationId: '60000636', affiliationName: 'Universidad San Sebastián' }],
    publicationCount: 22,
    dataQualityStatus: 'ok'
  }
];

export const MOCK_OUTPUTS: ResearchOutput[] = [
  {
    id: 'ro-1',
    uuid: 'out-uuid-0001',
    title: 'Cobertura de metadatos en repositorios institucionales chilenos',
    year: 2025,
    type: 'Artículo',
    doi: '10.1234/demo.2025.001',
    journal: 'Revista Demo de Ciencia Abierta',
    contributorCount: 3,
    contributors: ['Rojas Soto, C.', 'Pérez Lagos, A.', 'Silva Herrera, D.'],
    metadataStatus: 'ok'
  },
  {
    id: 'ro-2',
    uuid: 'out-uuid-0002',
    title: 'Identificadores persistentes y calidad de autoría en PURE',
    year: 2025,
    type: 'Artículo',
    doi: '10.1234/demo.2025.002',
    journal: 'Informetrics Demo',
    contributorCount: 2,
    contributors: ['Pérez Lagos, A.', 'Navarro Díaz, P.'],
    metadataStatus: 'review'
  },
  {
    id: 'ro-3',
    uuid: 'out-uuid-0003',
    title: 'Formación en gestión de datos de investigación: un protocolo',
    year: 2024,
    type: 'Capítulo',
    contributorCount: 2,
    contributors: ['González Vidal, M. J.', 'Cáceres Pinto, R.'],
    metadataStatus: 'pending'
  },
  {
    id: 'ro-4',
    uuid: 'out-uuid-0004',
    title: 'Reutilización de conjuntos de datos en salud',
    year: 2024,
    type: 'Artículo',
    doi: '10.1234/demo.2024.014',
    journal: 'Open Health Demo',
    contributorCount: 4,
    contributors: ['Muñoz Contreras, F.', 'Rojas Soto, C.', 'Navarro Díaz, P.', 'Vargas Méndez, I.'],
    metadataStatus: 'ok'
  },
  {
    id: 'ro-5',
    uuid: 'out-uuid-0005',
    title: 'Afiliaciones múltiples en Scopus y su efecto en indicadores',
    year: 2023,
    type: 'Conferencia',
    doi: '10.1234/demo.2023.088',
    journal: 'Actas Demo CRIS',
    contributorCount: 2,
    contributors: ['Pérez Lagos, A.', 'Silva Herrera, D.'],
    metadataStatus: 'review'
  }
];

export const MOCK_ISSUES: DataQualityIssue[] = [
  {
    id: 'dq-01',
    code: 'DQ-01',
    rule: 'Scopus ID asociado a más de una persona',
    entityType: 'identifier',
    entityLabel: 'Scopus ID 57202352366',
    entityId: '57202352366',
    description: 'El mismo Scopus Author ID aparece en más de un registro de persona.',
    detectedAt: '2026-09-10T11:20:00-03:00',
    status: 'open',
    source: 'Replica local / Scopus CSV',
    currentData: 'Personas 1001 y 1005 comparten 57202352366.',
    externalEvidence: 'El identificador es tratado como ID externo estable de Scopus.',
    proposedAction: 'Revisar homonimia vs. fusión incorrecta. No eliminar IDs distintos de cada persona.'
  },
  {
    id: 'dq-02',
    code: 'DQ-02',
    rule: 'Mismo Scopus ID duplicado dentro de una persona',
    entityType: 'person',
    entityLabel: 'Pérez Lagos, Andrés',
    entityId: '1002',
    description: 'La persona tiene el mismo Scopus ID repetido en identifiers[].',
    detectedAt: '2026-09-10T11:22:00-03:00',
    status: 'in_review',
    source: 'PURE GET /persons/{uuid}',
    currentData: '7005181514 aparece dos veces (pureId distintos). Conservar la segunda ocurrencia.',
    externalEvidence: 'Regla operativa KEEP SECOND sobre el mismo identifier.id.',
    proposedAction: 'Eliminar la primera ocurrencia duplicada. Conservar los Scopus ID distintos.'
  },
  {
    id: 'dq-03',
    code: 'DQ-03',
    rule: 'Persona con múltiples Scopus ID distintos que requieren revisión',
    entityType: 'person',
    entityLabel: 'Navarro Díaz, Paula',
    entityId: '1007',
    description: 'La persona tiene dos Scopus Author ID distintos.',
    detectedAt: '2026-09-08T09:10:00-03:00',
    status: 'open',
    source: 'PURE identifiers',
    currentData: '36611209877 y 57290114420.',
    externalEvidence: 'No implica error automático; puede ser perfil fragmentado.',
    proposedAction: 'Revisión de Data Steward. No fusionar por nombre.'
  },
  {
    id: 'dq-04',
    code: 'DQ-04',
    rule: 'ORCID ausente o pendiente de validación',
    entityType: 'person',
    entityLabel: 'González Vidal, María José',
    entityId: '1003',
    description: 'No hay ORCID registrado en el perfil.',
    detectedAt: '2026-09-07T16:40:00-03:00',
    status: 'open',
    source: 'PURE person',
    currentData: 'orcid vacío.',
    externalEvidence: 'Pendiente de búsqueda en ORCID.org / OpenAlex.',
    proposedAction: 'Iniciar flujo de validación ORCID. No asignar ORCID por homonimia.'
  },
  {
    id: 'dq-05',
    code: 'DQ-05',
    rule: 'ORCID candidato con nombre no compatible',
    entityType: 'person',
    entityLabel: 'Silva Herrera, Daniela',
    entityId: '1005',
    description: 'Existe un ORCID candidato cuya forma del nombre no es compatible.',
    detectedAt: '2026-09-06T10:05:00-03:00',
    status: 'validated',
    source: 'OpenAlex / ORCID',
    currentData: 'ORCID 0000-0002-7710-3398 no autenticado.',
    externalEvidence: 'El nombre en ORCID no coincide con el perfil institucional.',
    proposedAction: 'No escribir ORCID en PURE hasta validación humana.'
  },
  {
    id: 'dq-06',
    code: 'DQ-06',
    rule: 'Afiliación ORCID pendiente de validación',
    entityType: 'person',
    entityLabel: 'Pérez Lagos, Andrés',
    entityId: '1002',
    description: 'El perfil ORCID no confirma afiliación USS de forma inequívoca.',
    detectedAt: '2026-09-05T12:00:00-03:00',
    status: 'corrected',
    source: 'ORCID employment',
    currentData: 'Afiliación USS + Universidad Mayor en Scopus.',
    externalEvidence: 'Múltiples afiliaciones son válidas; no borrar las anteriores.',
    proposedAction: 'Confirmar afiliación USS y conservar el resto.'
  }
];

export const MOCK_INTEGRATIONS: IntegrationStatus[] = [
  {
    id: 'pure',
    name: 'PURE',
    description: 'Personas, identificadores y producción científica.',
    health: 'operational',
    entities: [
      {
        entity: 'PEOPLE',
        latestExecution: {
          syncId: '1',
          entity: 'PEOPLE',
          status: 'COMPLETED',
          startedAt: '2026-09-15T09:12:00-03:00',
          finishedAt: '2026-09-15T09:18:00-03:00',
          processed: 1280,
          errors: 0
        }
      },
      {
        entity: 'RESEARCH_OUTPUT',
        latestExecution: {
          syncId: '2',
          entity: 'RESEARCH_OUTPUT',
          status: 'COMPLETED',
          startedAt: '2026-09-15T08:02:00-03:00',
          finishedAt: '2026-09-15T08:40:00-03:00',
          processed: 25000,
          errors: 0
        }
      },
      {
        entity: 'JOURNALS',
        latestExecution: {
          syncId: '3',
          entity: 'JOURNALS',
          status: 'COMPLETED',
          startedAt: '2026-09-14T11:02:00-03:00',
          finishedAt: '2026-09-14T11:20:00-03:00',
          processed: 41000,
          errors: 0
        }
      }
    ]
  },
  {
    id: 'orcid',
    name: 'ORCID',
    description: 'Validación de identidad digital de autores.',
    health: 'warning',
    lastSync: '2026-09-13T16:18:00-03:00',
    lastRun: '2026-09-13T16:18:00-03:00',
    processedCount: 86,
    errorCount: 4
  },
  {
    id: 'scopus',
    name: 'Scopus',
    description: 'Autores, Auth-ID y afiliaciones desde CSV.',
    health: 'operational',
    lastSync: '2026-09-14T11:02:00-03:00',
    lastRun: '2026-09-14T11:02:00-03:00',
    processedCount: 540,
    errorCount: 2
  },
  {
    id: 'openalex',
    name: 'OpenAlex',
    description: 'Candidatos ORCID y producción abierta.',
    health: 'pending',
    lastRun: '2026-09-01T10:00:00-03:00',
    processedCount: 0,
    errorCount: 0
  },
  {
    id: 'dspace',
    name: 'DSpace',
    description: 'Repositorio institucional.',
    health: 'unconfigured'
  },
  {
    id: 'scival',
    name: 'SciVal',
    description: 'Indicadores bibliométricos institucionales.',
    health: 'unconfigured'
  },
  {
    id: 'doi',
    name: 'DOI / Crossref / DataCite',
    description: 'Resolución y metadatos de identificadores persistentes.',
    health: 'pending'
  }
];

export const MOCK_OPERATIONAL_METRICS: OperationalMetric[] = [
  { id: 'persons', label: 'Personas registradas', value: '1280', hint: 'Réplica local PURE. DEMO.', demo: true },
  { id: 'researchers', label: 'Investigadores', value: '412', hint: 'Subset operativo. DEMO.', demo: true },
  { id: 'orcid', label: 'Personas con ORCID', value: '268', hint: 'No es indicador ANID. DEMO.', demo: true },
  { id: 'scopus', label: 'Cobertura Scopus', value: '194', hint: 'Autores con Auth-ID. DEMO.', demo: true },
  { id: 'findings', label: 'Hallazgos pendientes', value: '4', hint: 'Data Quality operacional. DEMO.', demo: true }
];

/** Capa 2: nombres oficiales. Valores no inventados. */
export const MOCK_INDICATORS: InstitutionalIndicator[] = [
  {
    id: 'r1',
    layer: 2,
    group: 'resultado',
    code: 'R1',
    name: 'Razón de depósito de datos sobre producción publicada',
    valueState: 'pending',
    valueLabel: 'Pendiente de medición'
  },
  {
    id: 'r2',
    layer: 2,
    group: 'resultado',
    code: 'R2',
    name: 'Dispersión de depositantes de datos',
    valueState: 'pending',
    valueLabel: 'Pendiente de medición'
  },
  {
    id: 'r3',
    layer: 2,
    group: 'resultado',
    code: 'R3',
    name: 'Razón de depósito por área OCDE u otra variable categórica',
    valueState: 'pending',
    valueLabel: 'Pendiente de medición'
  },
  {
    id: 'r4',
    layer: 2,
    group: 'resultado',
    code: 'R4',
    name: 'Citación de la producción disponible en el repositorio institucional',
    valueState: 'pending',
    valueLabel: 'Pendiente de medición'
  },
  {
    id: 'r5',
    layer: 2,
    group: 'resultado',
    code: 'R5',
    name: 'Reutilización de datos depositados',
    valueState: 'pending',
    valueLabel: 'Pendiente de medición'
  },
  {
    id: 'p1',
    layer: 2,
    group: 'proceso',
    code: 'P1',
    name: 'Completitud del depósito de la producción publicada',
    valueState: 'pending',
    valueLabel: 'Pendiente de medición'
  },
  {
    id: 'p2',
    layer: 2,
    group: 'proceso',
    code: 'P2',
    name: 'Cobertura externa del repositorio',
    valueState: 'pending',
    valueLabel: 'Pendiente de medición'
  },
  {
    id: 'p3',
    layer: 2,
    group: 'proceso',
    code: 'P3',
    name: 'Completitud de metadatos verificada externamente',
    valueState: 'pending',
    valueLabel: 'Pendiente de medición'
  },
  {
    id: 'f1',
    layer: 2,
    group: 'formacion',
    code: 'F1',
    name: 'Cobertura formativa sobre la población obligada por la Política ANID',
    valueState: 'pending',
    valueLabel: 'Pendiente de medición'
  },
  {
    id: 'f2',
    layer: 2,
    group: 'formacion',
    code: 'F2',
    name: 'Correspondencia entre formación, publicación y depósito',
    valueState: 'pending',
    valueLabel: 'Pendiente de medición'
  }
];
